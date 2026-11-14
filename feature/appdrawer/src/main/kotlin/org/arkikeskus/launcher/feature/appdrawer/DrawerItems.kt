package org.arkikeskus.launcher.feature.appdrawer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.SearchResult
import org.arkikeskus.launcher.model.catching
import org.arkikeskus.launcher.ui.AppShortcuts
import org.arkikeskus.launcher.ui.HomeDragController
import org.arkikeskus.launcher.ui.LauncherIcons
import org.arkikeskus.launcher.ui.drawer.DrawerColors
import org.arkikeskus.launcher.ui.expressive.ExpressiveActionRow
import org.arkikeskus.launcher.ui.expressive.ExpressiveSectionTitle

// The items of the drawer's lazy list, written once for both layouts (split out of
// AppDrawerScreen.kt). The list layout (a LazyColumn) and the grid layout (a LazyVerticalGrid) show
// the same things in the same order; only how one item is added differs, and that is [DrawerItems].

/** What an app row or cell in the drawer needs: its badge and look, and where taps, drags and drops go. */
internal class DrawerAppDeps(
    val badges: Map<String, Int>,
    val badgeShowCount: Boolean,
    val badgeScale: Float,
    val showLabels: Boolean,
    /** The icon size of a grid cell (folder tiles and the most used apps use it in both layouts). */
    val iconSize: Dp,
    val onAppClick: (AppItem) -> Unit,
    val onAppLongClick: (AppItem, Rect) -> Unit,
    val dragController: HomeDragController,
    val folderDrag: DrawerFolderDrag,
    /** The tab the apps are shown from (null = all apps), where a drag out of the drawer started. */
    val sourceTab: Long?,
    val onDragOutStart: () -> Unit,
    val onDropOnHome: (AppItem, Int, Int, Int) -> Unit,
    val onDropOnDock: (AppItem) -> Unit,
    val onDropOnTab: (AppItem, Long?) -> Unit,
    val haptics: HapticFeedback,
    val locked: Boolean,
    /** Closes the drawer (after a search result or a shortcut chip opened something). */
    val onClose: () -> Unit,
)

/**
 * Adds drawer items to the list or to the grid: [row] spans the whole width, [cell] is one grid cell
 * (a row of its own in the list), [appItems] are the apps themselves and [privateSpaceItems] the
 * private space section.
 */
internal sealed class DrawerItems(val deps: DrawerAppDeps) {
    abstract fun row(key: Any?, contentType: Any, content: @Composable () -> Unit)
    abstract fun cell(key: Any, contentType: Any, content: @Composable () -> Unit)

    /** [apps] as rows or cells; with [topChips] the first app's shortcuts are offered as chips too. */
    abstract fun appItems(apps: List<AppItem>, topChips: Boolean = false)
    abstract fun privateSpaceItems(space: PrivateSpaceDrawer, colors: DrawerColors)
}

/** The list layout: every item is a row. */
internal class DrawerListItems(private val scope: LazyListScope, deps: DrawerAppDeps) : DrawerItems(deps) {
    override fun row(key: Any?, contentType: Any, content: @Composable () -> Unit) =
        scope.item(key = key, contentType = contentType) { content() }

    override fun cell(key: Any, contentType: Any, content: @Composable () -> Unit) = row(key, contentType, content)

    override fun appItems(apps: List<AppItem>, topChips: Boolean) {
        scope.items(items = apps, key = { it.key }, contentType = { "app" }) { app ->
            AppListRow(
                app = app,
                badgeCount = deps.badges[app.badgeKey] ?: 0,
                badgeShowCount = deps.badgeShowCount,
                badgeScale = deps.badgeScale,
                showLabels = deps.showLabels,
                onAppClick = deps.onAppClick,
                onAppLongClick = deps.onAppLongClick,
                dragController = deps.dragController,
                folderDrag = deps.folderDrag,
                sourceTab = deps.sourceTab,
                onDragOutStart = deps.onDragOutStart,
                onDropOnHome = deps.onDropOnHome,
                onDropOnDock = deps.onDropOnDock,
                onDropOnTab = deps.onDropOnTab,
                haptics = deps.haptics,
                locked = deps.locked,
            )
            // The best match's shortcuts, right under it.
            if (topChips && app.key == apps.first().key) {
                TopAppShortcutChips(app, onOpened = deps.onClose)
            }
        }
    }

    override fun privateSpaceItems(space: PrivateSpaceDrawer, colors: DrawerColors) = scope.privateSpaceRows(space, colors)
}

/** The grid layout: rows span every column, apps and folder tiles take one cell each. */
internal class DrawerGridItems(private val scope: LazyGridScope, deps: DrawerAppDeps) : DrawerItems(deps) {
    override fun row(key: Any?, contentType: Any, content: @Composable () -> Unit) =
        scope.item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = contentType) { content() }

    override fun cell(key: Any, contentType: Any, content: @Composable () -> Unit) =
        scope.item(key = key, contentType = contentType) { content() }

    override fun appItems(apps: List<AppItem>, topChips: Boolean) {
        // The best match's shortcuts first, as chips above the app grid.
        if (topChips) {
            row("top-shortcuts", "shortcut-chips") { TopAppShortcutChips(apps.first(), onOpened = deps.onClose) }
        }
        scope.appCells(
            apps, deps.badges, deps.badgeShowCount, deps.badgeScale, deps.showLabels, deps.iconSize, deps.onAppClick,
            deps.onAppLongClick, deps.dragController, deps.folderDrag, deps.sourceTab, deps.onDragOutStart,
            deps.onDropOnHome, deps.onDropOnTab, deps.onDropOnDock, deps.haptics, deps.locked,
        )
    }

    override fun privateSpaceItems(space: PrivateSpaceDrawer, colors: DrawerColors) =
        scope.privateSpaceCells(space, colors, deps.showLabels, deps.iconSize)
}

/**
 * The drawer while nothing is searched: the clipboard row, the most used apps, the folder tiles, the
 * apps (in runs under their letter when headings are on), then, on the all-apps tab, Android's
 * private space and the hidden apps row.
 */
internal fun DrawerItems.browse(
    clipboardRow: Boolean,
    frequentApps: List<AppItem>,
    columns: Int,
    folderTiles: List<DrawerFolderUi>,
    letterRuns: List<Pair<Char?, List<AppItem>>>,
    onFolderClick: (DrawerFolderUi) -> Unit,
    space: PrivateSpaceDrawer?,
    colors: DrawerColors,
    hiddenCount: Int,
    hiddenLock: Boolean,
    onHiddenApps: () -> Unit,
) {
    if (clipboardRow) row("clipboard", "clipboard") { DrawerClipboardRow() }
    if (frequentApps.isNotEmpty()) {
        row(null, "frequent") {
            FrequentAppsCard(
                apps = frequentApps,
                columns = columns,
                badges = deps.badges,
                badgeShowCount = deps.badgeShowCount,
                badgeScale = deps.badgeScale,
                showLabels = deps.showLabels,
                iconSize = deps.iconSize,
                onAppClick = deps.onAppClick,
                onAppLongClick = deps.onAppLongClick,
            )
        }
    }
    for (folder in folderTiles) {
        cell("folder-${folder.id}", "folder") {
            DrawerFolderTile(
                folder = folder,
                showLabel = deps.showLabels,
                drag = deps.folderDrag,
                tileSize = deps.iconSize,
                badgeCount = folder.apps.sumOf { deps.badges[it.badgeKey] ?: 0 },
                badgeShowCount = deps.badgeShowCount,
                badgeScale = deps.badgeScale,
                onClick = { onFolderClick(folder) },
            )
        }
    }
    letterRuns.forEachIndexed { runIndex, (letter, runApps) ->
        if (letter != null) row("letter-$runIndex", "letter") { DrawerLetterHeader(letter) }
        appItems(runApps)
    }
    if (deps.sourceTab == null) {
        space?.let { privateSpaceItems(it, colors) }
        if (hiddenCount > 0) row("hidden-apps", "hidden") { HiddenAppsRow(hiddenCount, hiddenLock, onHiddenApps) }
    }
}

/**
 * The search results, section by section: the sum, actions, apps, settings, contacts, shortcuts and
 * a web search, or one "no results" line when there is nothing at all.
 */
internal fun DrawerItems.searchResults(
    query: String,
    calc: SearchResult.Calculation?,
    actionResults: List<SearchResult.Action>,
    apps: List<AppItem>,
    settingResults: List<SearchResult.Setting>,
    contactResults: List<SearchResult.Contact>,
    shortcutResults: List<SearchResult.Shortcut>,
    searchWeb: Boolean,
    searchWebEngine: String,
    searchInlineShortcuts: Boolean,
) {
    calc?.let { c ->
        row(null, "calc") {
            val context = LocalContext.current
            val copiedMsg = stringResource(R.string.search_calc_copied)
            CalcResultCard(c) {
                val clip = context.getSystemService(ClipboardManager::class.java)
                clip?.setPrimaryClip(ClipData.newPlainText("result", c.result))
                Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }
    if (actionResults.isNotEmpty()) {
        row(null, "header") { ExpressiveSectionTitle(stringResource(R.string.search_section_actions)) }
        for (action in actionResults) {
            row(action.id, "action") {
                val context = LocalContext.current
                ExpressiveActionRow(label = action.label, description = action.detail, trailingIcon = LauncherIcons.OpenInNew) {
                    if (startSearchAction(context, action)) deps.onClose()
                }
            }
        }
    }
    if (apps.isNotEmpty()) {
        row(null, "header") { ExpressiveSectionTitle(stringResource(R.string.search_section_apps)) }
        appItems(apps, topChips = searchInlineShortcuts)
    }
    if (settingResults.isNotEmpty()) {
        row(null, "header") { ExpressiveSectionTitle(stringResource(R.string.search_section_settings)) }
        for (setting in settingResults) {
            row(setting.id, "setting") {
                val context = LocalContext.current
                ExpressiveActionRow(label = setting.title, description = "") {
                    catching { context.startActivity(Intent(setting.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
        }
    }
    if (contactResults.isNotEmpty()) {
        row(null, "header") { ExpressiveSectionTitle(stringResource(R.string.search_section_contacts)) }
        for (contact in contactResults) {
            row(contact.id, "contact") { ContactResultRow(contact, onOpened = deps.onClose) }
        }
    }
    if (shortcutResults.isNotEmpty()) {
        row(null, "header") { ExpressiveSectionTitle(stringResource(R.string.search_section_shortcuts)) }
        for (shortcut in shortcutResults) {
            row(shortcut.id, "shortcut") {
                val context = LocalContext.current
                ExpressiveActionRow(label = shortcut.label, description = shortcut.appLabel) {
                    AppShortcuts.startById(context, shortcut.packageName, shortcut.shortcutId, shortcut.userSerial)
                    deps.onClose()
                }
            }
        }
    }
    if (searchWeb) {
        row(null, "web") {
            val context = LocalContext.current
            ExpressiveActionRow(
                label = stringResource(R.string.search_web, query.trim()),
                description = "",
                trailingIcon = LauncherIcons.Search,
            ) { launchWebSearch(context, query.trim(), searchWebEngine) }
        }
    }
    if (calc == null && apps.isEmpty() && settingResults.isEmpty() && contactResults.isEmpty() &&
        shortcutResults.isEmpty() && actionResults.isEmpty() && !searchWeb
    ) {
        row(null, "empty") {
            Text(
                text = stringResource(R.string.search_no_results),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            )
        }
    }
}
