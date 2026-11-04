package org.arkikeskus.launcher.data.search

import kotlinx.coroutines.flow.first
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.model.SearchResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Matches launchable apps by (effective) label (forgivingly, best first: [AppMatcher]), excluding hidden apps — preserving the drawer's
 * existing rule that hidden apps stay out of search results. Snapshots the current app + hidden
 * sets per query.
 */
@Singleton
class AppSearchProvider @Inject constructor(
    private val appRepository: AppRepository,
    private val settingsRepository: SettingsRepository,
) : SearchProvider {

    override suspend fun isEnabled(): Boolean = true

    override suspend fun query(query: String): List<SearchResult> {
        val q = query.trim()
        val hidden = settingsRepository.hiddenApps.first()
        val visible = appRepository.apps.first().filterNot { it.key in hidden }
        // Best match first, with initials, gaps and a typo allowed (see AppMatcher).
        return AppMatcher.rank(visible, q) { it.label }.map { SearchResult.App(it) }
    }
}
