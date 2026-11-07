package org.arkikeskus.launcher.feature.home

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.HomeLayoutRepository
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.local.HomeItemEntity
import org.arkikeskus.launcher.model.ScreenType

/**
 * The first-run experience, split out of [HomeViewModel]: decided ONCE per install — on an empty
 * home surface, seed the smartspace widget + the default dock (phone/messages/Settings/browser/
 * camera) and show the intro. Device-local flags so removing items never re-seeds, and updating
 * users (non-empty home) get the flags silently without any of it.
 */
internal class HomeSeeder(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val homeLayoutRepository: HomeLayoutRepository,
    private val appRepository: AppRepository,
) {
    /** Runs the once-per-install decisions; [onShowOnboarding] fires for fresh installs only. */
    suspend fun seedIfNeeded(onShowOnboarding: () -> Unit) {
        val seeded = settingsRepository.defaultLayoutSeededOnce()
        val onboarded = settingsRepository.onboardingDoneOnce()
        if (seeded && onboarded) return
        // Decide freshness ONCE and persist the decision BEFORE any seeding writes: a death
        // mid-seed leaves the home non-empty, which must not flip a fresh install into an
        // "updating user" on the retry (dock never seeded, intro never shown).
        val fresh = settingsRepository.firstRunFreshOnce() ?: run {
            val decided = homeLayoutRepository.isHomeEmpty(ScreenType.OUTER)
            settingsRepository.setFirstRunFresh(decided)
            decided
        }
        if (!seeded) {
            if (fresh) {
                // The app list needs a beat on a cold start; a fresh device always has apps.
                val apps = appRepository.apps.first { it.isNotEmpty() }
                val dockKeys = resolveDefaultDockKeys(context, apps)
                // Seed BOTH display profiles. The view model starts before it knows which display
                // it is on, so seeding only "the current one" always seeded the outer profile and
                // left the inner display with an empty home and dock forever (the seed runs once
                // per install).
                for (type in ScreenType.entries) {
                    val settings = settingsRepository.settings(type).first()
                    // Idempotent on a retry: the widget only while home is still empty, the dock
                    // only while the favorites are still empty.
                    if (homeLayoutRepository.isHomeEmpty(type)) {
                        // Full width so the centered clock sits in the middle of the screen.
                        homeLayoutRepository.addBuiltin(
                            HomeItemEntity.BUILTIN_SMARTSPACE,
                            settings.home.columns, SMARTSPACE_DEFAULT_SPAN_Y, settings.home.columns, settings.home.rows, type
                        )
                    }
                    if (dockKeys.isNotEmpty() && settingsRepository.dockFavorites(type).first().isEmpty()) {
                        settingsRepository.seedDock(dockKeys, type)
                        // The seed is 5 apps but the dock defaults to 4 columns — widen to fit.
                        if (dockKeys.size > settings.dock.columns) {
                            settingsRepository.setDockColumns(dockKeys.size, type)
                        }
                    }
                }
            }
            settingsRepository.setDefaultLayoutSeeded()
        }
        if (!onboarded) {
            if (fresh) onShowOnboarding() else settingsRepository.setOnboardingDone()
        }
    }
}

/**
 * The home screen's quiet hygiene, split out of [HomeViewModel]: shortcut-cache invalidation on
 * package updates, ghost-row deletion on uninstall, and a once-per-launch stale-row sweep.
 */
internal class HomeHygiene(
    private val appRepository: AppRepository,
    private val homeLayoutRepository: HomeLayoutRepository,
) {
    fun start(scope: CoroutineScope, onPackageEvent: (String) -> Unit) {
        // A package update can change a pinned shortcut's label/icon; drop its cached resolutions
        // so the uiState combine re-resolves them (cache keys are "package/id/serial").
        scope.launch {
            appRepository.packageEvents.collect { pkg -> onPackageEvent(pkg) }
        }
        // Ghost-row hygiene: an uninstalled app's home rows never render but still occupy their
        // cells, silently blocking placement/resizes/moves with no visible reason. Delete them on
        // the uninstall event, and sweep once per launch for uninstalls the launcher slept through.
        scope.launch {
            appRepository.packageRemovals.collect { (pkg, serial) ->
                homeLayoutRepository.removeAppRowsForPackage(pkg, serial)
            }
        }
        scope.launch {
            appRepository.apps.first { it.isNotEmpty() } // let the app providers settle on a cold start
            homeLayoutRepository.removeStaleAppRows(appRepository::isAppInstalled)
        }
    }
}
