package org.arkikeskus.launcher.feature.home

import android.content.ComponentName
import org.arkikeskus.launcher.data.local.HomeItemEntity
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.AppPair
import org.arkikeskus.launcher.ui.AppShortcuts

// From the stored home rows to what the home screen shows (split out of HomeViewModel.kt). Plain
// functions of the rows, the apps and the pairs, so the mapping can be read (and tested) on its own.

/** The key a pinned system shortcut's resolved label and icon are kept under. */
internal fun shortcutKey(row: HomeItemEntity): String = "${row.packageName}/${row.shortcutId}/${row.userSerial}"

/**
 * What the home screen shows for its stored rows: each row on home becomes an app, a folder (with
 * its apps), a shortcut, a widget, the placeholder of a restored widget still to be set up, a
 * built-in widget, or a stack of widgets. A row whose app, pair or provider is gone shows nothing.
 * [appsByKey] are the installed apps, [appPairs] the saved pairs, and [resolvedShortcut] gives a
 * pinned system shortcut's label and icon by [shortcutKey] (null while not resolved).
 */
internal fun homeEntries(
    homeItems: List<HomeItemEntity>,
    appsByKey: Map<String, AppItem>,
    appPairs: List<AppPair>,
    resolvedShortcut: (String) -> AppShortcuts.Resolved?,
): List<HomeEntry> {
    // Children grouped by their folder (or stack) id, kept in stored order.
    val childrenByFolder = homeItems
        .filter { it.containerId != HomeItemEntity.HOME }
        .groupBy { it.containerId }
    return homeItems
        .filter { it.containerId == HomeItemEntity.HOME }
        .mapNotNull { row ->
            when {
                row.builtinType == HomeItemEntity.BUILTIN_WIDGET_STACK -> stackOf(row, childrenByFolder[row.id].orEmpty())
                row.isBuiltin -> builtinOf(row)
                row.isFolder -> {
                    // distinctBy: a duplicate child row (old data from before the merge fix, or
                    // an edited backup) must never reach the folder grid's app-key lazy keys.
                    val folderApps = childrenByFolder[row.id].orEmpty()
                        .mapNotNull { appsByKey[it.key] }
                        .distinctBy { it.key }
                    PlacedFolder(row.id, row.folderName.orEmpty(), folderApps, row.page, row.cellX, row.cellY)
                }
                row.isShortcut -> shortcutOf(row, appsByKey, appPairs, resolvedShortcut)
                row.isWidget -> widgetOf(row)
                // A restored widget row carries a provider but no bound id: a placeholder until it is
                // set up again. Anything else is a plain app.
                row.widgetProvider != null -> pendingOf(row)
                else -> appsByKey[row.key]?.let { PlacedApp(it, row.page, row.cellX, row.cellY) }
            }
        }
}

/** A stack of widgets with what it holds, in stored order; nothing when it holds nothing anymore. */
private fun stackOf(row: HomeItemEntity, children: List<HomeItemEntity>): PlacedWidgetStack? {
    val items = children.mapNotNull { child ->
        when {
            child.isBuiltin && child.builtinType != HomeItemEntity.BUILTIN_WIDGET_STACK -> builtinOf(child)
            child.isWidget -> widgetOf(child)
            else -> pendingOf(child)
        }
    }
    if (items.isEmpty()) return null
    return PlacedWidgetStack(
        rowId = row.id,
        items = items,
        page = row.page, cellX = row.cellX, cellY = row.cellY,
        spanX = row.spanX, spanY = row.spanY,
    )
}

private fun builtinOf(row: HomeItemEntity): PlacedBuiltin? = PlacedBuiltin(
    rowId = row.id,
    type = row.builtinType ?: return null,
    page = row.page, cellX = row.cellX, cellY = row.cellY,
    spanX = row.spanX, spanY = row.spanY,
)

private fun widgetOf(row: HomeItemEntity): PlacedWidget? {
    val provider = ComponentName.unflattenFromString(row.widgetProvider.orEmpty()) ?: return null
    return PlacedWidget(
        rowId = row.id,
        appWidgetId = row.appWidgetId ?: return null,
        provider = provider,
        page = row.page, cellX = row.cellX, cellY = row.cellY,
        spanX = row.spanX, spanY = row.spanY,
    )
}

/** The placeholder of a widget whose provider is known but which isn't bound on this device. */
private fun pendingOf(row: HomeItemEntity): PendingWidget? {
    val provider = row.widgetProvider?.let { ComponentName.unflattenFromString(it) } ?: return null
    return PendingWidget(
        rowId = row.id,
        provider = provider,
        page = row.page, cellX = row.cellX, cellY = row.cellY,
        spanX = row.spanX, spanY = row.spanY,
    )
}

/**
 * A shortcut row: a saved app pair (shown only while the pair and both its apps exist), a
 * launcher-made "open in a floating window" shortcut (it is its app), or a pinned system shortcut
 * (shown once its label and icon are resolved).
 */
private fun shortcutOf(
    row: HomeItemEntity,
    appsByKey: Map<String, AppItem>,
    appPairs: List<AppPair>,
    resolvedShortcut: (String) -> AppShortcuts.Resolved?,
): PlacedShortcut? {
    val shortcutId = row.shortcutId ?: return null
    val pairId = HomeItemEntity.appPairShortcutPairId(shortcutId)
    val floatingClass = HomeItemEntity.floatingShortcutClass(shortcutId)
    return when {
        pairId != null -> {
            val pair = appPairs.firstOrNull { it.id == pairId } ?: return null
            val firstApp = appsByKey[pair.app1Key] ?: return null
            val secondApp = appsByKey[pair.app2Key] ?: return null
            PlacedShortcut(
                rowId = row.id,
                packageName = row.packageName,
                shortcutId = shortcutId,
                userSerial = row.userSerial,
                label = pair.name,
                icon = null,
                page = row.page,
                cellX = row.cellX,
                cellY = row.cellY,
                appPair = pair,
                pairApps = firstApp to secondApp,
                // Missing (uninstalled) third app: the pair still shows and works.
                pairThirdApp = if (pair.hasThirdApp) appsByKey[pair.app3Key] else null,
            )
        }
        floatingClass != null -> {
            val app = appsByKey["${row.packageName}/$floatingClass/${row.userSerial}"] ?: return null
            PlacedShortcut(
                rowId = row.id,
                packageName = row.packageName,
                shortcutId = shortcutId,
                userSerial = row.userSerial,
                label = app.label,
                icon = null,
                page = row.page,
                cellX = row.cellX,
                cellY = row.cellY,
                floatingApp = app,
            )
        }
        else -> {
            val resolved = resolvedShortcut(shortcutKey(row)) ?: return null
            PlacedShortcut(
                rowId = row.id,
                packageName = row.packageName,
                shortcutId = shortcutId,
                userSerial = row.userSerial,
                label = resolved.label,
                icon = resolved.icon,
                page = row.page,
                cellX = row.cellX,
                cellY = row.cellY,
            )
        }
    }
}
