package org.arkikeskus.launcher.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.component.liquidGlass
import org.arkikeskus.launcher.ui.component.LocalGlassLevel
import org.arkikeskus.launcher.ui.expressive.DarkExpressivePalette
import org.arkikeskus.launcher.ui.expressive.ExpressivePalette
import org.arkikeskus.launcher.ui.expressive.LightExpressivePalette
import org.arkikeskus.launcher.ui.expressive.LocalAccent
import org.arkikeskus.launcher.ui.expressive.ExpressiveTheme
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette

/** One action row in the long-press popup (e.g. "App info", "Remove from home"); optional leading icon. */
data class PopupAction(val label: String, @DrawableRes val icon: Int? = null, val onClick: () -> Unit)

/**
 * The ways to get rid of an app, offered together on the popup's Remove page, each with a line
 * that says what it does. Null: not possible here (no "Remove from home" in the drawer for an app
 * that is not on home; no Uninstall for a system app).
 */
data class RemoveChoices(
    /** Off home: the app stays installed and in the list. */
    val fromHome: (() -> Unit)? = null,
    /** Off the dock: the app stays installed, in the list and on home. */
    val fromDock: (() -> Unit)? = null,
    val hide: (() -> Unit)? = null,
    val uninstall: (() -> Unit)? = null,
) {
    val any: Boolean get() = fromHome != null || fromDock != null || hide != null || uninstall != null
}

/** Which part of the popup is showing: the menu, the Customize page or the Remove page. */
private enum class PopupPage { MAIN, CUSTOMIZE, REMOVE }

/** One icon+label row in a generic [IconMenuPopup] (e.g. "Launcher", "Wallpaper"). */
data class IconMenuItem(@DrawableRes val icon: Int, val label: String, val onClick: () -> Unit)

private const val POPUP_MARGIN_PX = 24
private val CARD_WIDTH = 232.dp

/**
 * The Pixel-style long-press popup, shared by the home screen and the app drawer. Shows the app's
 * deep shortcuts (queried off the main thread) above the caller-supplied [actions], with a caret
 * pointing at the icon ([anchor] in root coords; [preferAbove] for dock/bottom items). Styled with
 * the shared Version C expressive palette.
 */
@Composable
fun AppActionPopup(
    app: AppItem,
    anchor: IntOffset,
    preferAbove: Boolean,
    actions: List<PopupAction>,
    onDismiss: () -> Unit,
    onPinShortcut: ((AppShortcuts.Item) -> Unit)? = null,
    onRename: ((String) -> Unit)? = null,
    /** Up to four everyday actions drawn as an icon row at the top (App info, Split, Floating…). */
    quickActions: List<PopupAction> = emptyList(),
    /** How the app looks and where it is filed (icon, aspect ratio, folder…), on a page of their own
     *  behind "Customize" in the icon row; Rename joins them when [onRename] is set. */
    customize: List<PopupAction> = emptyList(),
    /** Remove from home / Hide / Uninstall, together on one page behind "Remove…". */
    remove: RemoveChoices? = null,
) {
    val context = LocalContext.current
    var shortcuts by remember(app.key) { mutableStateOf<List<AppShortcuts.Item>>(emptyList()) }
    var showRenameDialog by remember { mutableStateOf(false) }

    LaunchedEffect(app.key) {
        shortcuts = withContext(Dispatchers.IO) { AppShortcuts.query(context, app) }
    }

    if (showRenameDialog) {
        RenameDialog(
            initialName = app.label,
            onConfirm = { newName ->
                onRename?.invoke(newName)
                showRenameDialog = false
                onDismiss()
            },
            onReset = {
                onRename?.invoke("")
                showRenameDialog = false
                onDismiss()
            },
            onDismiss = {
                showRenameDialog = false
                onDismiss()
            },
        )
    }

    val renameLabel = stringResource(R.string.rename)
    // With a Customize page, Rename lives there; the older layout keeps it as the first row.
    val grouped = quickActions.isNotEmpty() || customize.isNotEmpty() || remove != null
    var page by remember(app.key) { mutableStateOf(PopupPage.MAIN) }
    val customizeRows = remember(customize, onRename, renameLabel) {
        if (onRename != null) listOf(PopupAction(renameLabel, LauncherIcons.Edit) { showRenameDialog = true }) + customize else customize
    }
    val effectiveActions = remember(actions, onRename, renameLabel, grouped) {
        if (!grouped && onRename != null && actions.none { it.icon == LauncherIcons.Edit }) {
            listOf(
                PopupAction(
                    label = renameLabel,
                    icon = LauncherIcons.Edit,
                    onClick = { showRenameDialog = true },
                ),
            ) + actions
        } else {
            actions
        }
    }

    // The app's own notifications, newest first, above its shortcuts.
    val notifications = LocalAppNotifications.current()
        .filter { it.packageName == app.packageName && it.userSerial == app.userSerial }
        .take(MAX_POPUP_NOTIFICATIONS)

    ExpressivePopupCard(anchor = anchor, preferAbove = preferAbove, onDismiss = onDismiss) {
        when (page) {
            PopupPage.CUSTOMIZE -> {
                PopupPageHeader(stringResource(R.string.popup_customize)) { page = PopupPage.MAIN }
                customizeRows.forEach { action ->
                    PopupRow(action.label, action.icon) {
                        if (action.icon == LauncherIcons.Edit && onRename != null && action.label == renameLabel) {
                            showRenameDialog = true
                        } else {
                            action.onClick()
                            onDismiss()
                        }
                    }
                }
                return@ExpressivePopupCard
            }
            PopupPage.REMOVE -> {
                val choices = remove ?: return@ExpressivePopupCard
                PopupPageHeader(stringResource(R.string.popup_remove_title, app.label)) { page = PopupPage.MAIN }
                choices.fromHome?.let { go ->
                    RemoveChoiceRow(LauncherIcons.Close, stringResource(R.string.popup_remove_home), stringResource(R.string.popup_remove_home_desc)) {
                        go(); onDismiss()
                    }
                }
                choices.fromDock?.let { go ->
                    RemoveChoiceRow(LauncherIcons.Close, stringResource(R.string.popup_remove_dock), stringResource(R.string.popup_remove_dock_desc)) {
                        go(); onDismiss()
                    }
                }
                choices.hide?.let { go ->
                    RemoveChoiceRow(LauncherIcons.VisibilityOff, stringResource(R.string.popup_hide), stringResource(R.string.popup_hide_desc)) {
                        go(); onDismiss()
                    }
                }
                choices.uninstall?.let { go ->
                    RemoveChoiceRow(
                        LauncherIcons.Delete,
                        stringResource(R.string.popup_uninstall),
                        stringResource(R.string.popup_uninstall_desc),
                        danger = true,
                    ) { go(); onDismiss() }
                }
                return@ExpressivePopupCard
            }
            PopupPage.MAIN -> Unit
        }
        // The icon row: everyday actions first, then Customize (its own page) when there is one.
        val icons = quickActions.take(if (customizeRows.isNotEmpty()) 3 else 4)
        if (icons.isNotEmpty() || (grouped && customizeRows.isNotEmpty())) {
            QuickActionRow(
                icons.map { a -> QuickItem(a.label, a.icon ?: LauncherIcons.Info) { a.onClick(); onDismiss() } } +
                    if (customizeRows.isNotEmpty() && grouped) {
                        listOf(QuickItem(stringResource(R.string.popup_customize), LauncherIcons.Tune) { page = PopupPage.CUSTOMIZE })
                    } else {
                        emptyList()
                    },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = LocalExpressivePalette.current.trackOff)
        }
        notifications.forEach { notification ->
            PopupNotificationRow(notification, onOpened = onDismiss)
        }
        if (notifications.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = LocalExpressivePalette.current.trackOff,
            )
        }
        shortcuts.forEach { shortcut ->
            ShortcutPopupRow(
                text = shortcut.label,
                onClick = {
                    AppShortcuts.start(context, shortcut)
                    onDismiss()
                },
                onPin = onPinShortcut?.let {
                    {
                        it(shortcut)
                        onDismiss()
                    }
                },
            )
        }
        if (shortcuts.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = LocalExpressivePalette.current.trackOff,
            )
        }
        effectiveActions.forEach { action ->
            PopupRow(action.label, action.icon) {
                if (action.icon == LauncherIcons.Edit && onRename != null) {
                    showRenameDialog = true
                } else {
                    action.onClick()
                    onDismiss()
                }
            }
        }
        if (remove != null && remove.any) {
            PopupRow(stringResource(R.string.popup_remove), LauncherIcons.Delete) { page = PopupPage.REMOVE }
        }
    }
}

/**
 * A generic icon+label popup with the same Version C card/caret/positioning as [AppActionPopup].
 * Used by the home empty-area long-press menu (Launcher / Wallpaper / …).
 */
@Composable
fun IconMenuPopup(
    anchor: IntOffset,
    preferAbove: Boolean,
    items: List<IconMenuItem>,
    onDismiss: () -> Unit,
) {
    ExpressivePopupCard(anchor = anchor, preferAbove = preferAbove, onDismiss = onDismiss, showCaret = false) {
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        item.onClick()
                        onDismiss()
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(item.icon),
                    contentDescription = null,
                    tint = LocalAccent.current,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = LocalExpressivePalette.current.text,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
    }
}

/**
 * Shared Version C popup chrome: an anchored, dismissible rounded card (with a caret pointing at the
 * icon) wrapped in [ExpressiveTheme] so it resolves the warm-orange dark/light palette regardless of
 * the caller. [content] is the scrollable column of rows.
 */
@Composable
private fun ExpressivePopupCard(
    anchor: IntOffset,
    preferAbove: Boolean,
    onDismiss: () -> Unit,
    showCaret: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val windowWidthPx = LocalWindowInfo.current.containerSize.width.toFloat()
    val positionProvider = remember(anchor, preferAbove) {
        AnchoredPopupPositionProvider(anchor, preferAbove)
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        PopupThemed {
            val palette = LocalExpressivePalette.current
            val cardColor = palette.surfaceHi
            val cardWidthPx = with(density) { CARD_WIDTH.toPx() }
            val caretWidthPx = with(density) { 22.dp.toPx() }
            val cardLeft = (anchor.x - cardWidthPx / 2f).coerceIn(
                POPUP_MARGIN_PX.toFloat(),
                (windowWidthPx - cardWidthPx - POPUP_MARGIN_PX).coerceAtLeast(POPUP_MARGIN_PX.toFloat()),
            )
            val caretStart = with(density) {
                ((anchor.x - cardLeft) - caretWidthPx / 2f)
                    .coerceIn(14f, cardWidthPx - caretWidthPx - 14f)
                    .toDp()
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Left-aligned caret so [caretStart] is a true offset from the card's left edge.
                if (showCaret && !preferAbove) Caret(pointingUp = true, color = cardColor, modifier = Modifier.align(Alignment.Start).padding(start = caretStart))
                val glass = LocalPopupTheme.current == LauncherSettings.POPUP_THEME_GLASS
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (glass) Color.Transparent else cardColor,
                    tonalElevation = if (glass) 0.dp else 3.dp,
                    shadowElevation = if (glass) 0.dp else 10.dp,
                    modifier = Modifier
                        .width(CARD_WIDTH)
                        .then(if (glass) Modifier.liquidGlass(RoundedCornerShape(20.dp), cardColor) else Modifier),
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        content = content,
                    )
                }
                if (showCaret && preferAbove) Caret(pointingUp = false, color = cardColor, modifier = Modifier.align(Alignment.Start).padding(start = caretStart))
            }
        }
    }
}

/**
 * The look of the long-press menus under this subtree (Settings ▸ Appearance), one of
 * `LauncherSettings.POPUP_THEME_*`. Provided once at the launcher root.
 */
val LocalPopupTheme = compositionLocalOf { LauncherSettings.POPUP_THEME_CLASSIC }

/** Resolves [LocalPopupTheme] into the palette and accent the popup rows draw with. */
@Composable
private fun PopupThemed(content: @Composable () -> Unit) {
    when (LocalPopupTheme.current) {
        LauncherSettings.POPUP_THEME_MATERIAL -> {
            // The launcher's own Material colours (wallpaper-based where the system provides them).
            val scheme = MaterialTheme.colorScheme
            CompositionLocalProvider(
                LocalExpressivePalette provides ExpressivePalette(
                    bg = scheme.surface,
                    surfaceHi = scheme.surfaceContainerHigh,
                    text = scheme.onSurface,
                    dim = scheme.onSurfaceVariant,
                    faint = scheme.outline,
                    trackOff = scheme.outlineVariant,
                    thumbOff = scheme.outline,
                    btn = scheme.onSurface.copy(alpha = 0.07f),
                    shadow = 0.dp,
                ),
                LocalAccent provides scheme.primary,
                content = content,
            )
        }
        LauncherSettings.POPUP_THEME_DARK ->
            CompositionLocalProvider(LocalExpressivePalette provides DarkExpressivePalette, content = content)
        LauncherSettings.POPUP_THEME_LIGHT ->
            CompositionLocalProvider(LocalExpressivePalette provides LightExpressivePalette, content = content)
        // Liquid Glass: the dark palette over a see-through tint (how see-through: the glass level).
        LauncherSettings.POPUP_THEME_GLASS -> {
            val level = LocalGlassLevel.current
            CompositionLocalProvider(
                LocalExpressivePalette provides DarkExpressivePalette.copy(surfaceHi = Color.Black.copy(alpha = 0.62f - 0.38f * level)),
                content = content,
            )
        }
        // Classic: the warm-orange palette that follows the system dark/light mode.
        else -> ExpressiveTheme(content = content)
    }
}

/**
 * A shortcut row: tap the label launches it; tap the trailing **+** (shown when [onPin] != null) pins
 * it to the home screen — the + is the affordance that tells the user this shortcut can be pinned.
 */
@Composable
private fun ShortcutPopupRow(text: String, onClick: () -> Unit, onPin: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalExpressivePalette.current.text,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        )
        if (onPin != null) {
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPin),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(LauncherIcons.Add),
                    contentDescription = stringResource(R.string.shortcut_pin),
                    tint = LocalAccent.current,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun PopupRow(text: String, @DrawableRes icon: Int?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = LocalAccent.current,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalExpressivePalette.current.text,
            modifier = Modifier.padding(start = if (icon != null) 16.dp else 0.dp),
        )
    }
}

/** Small triangle that visually ties the popup to the icon it acts on. */
@Composable
private fun Caret(pointingUp: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 22.dp, height = 10.dp)) {
        val path = Path().apply {
            if (pointingUp) {
                moveTo(0f, size.height)
                lineTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
            } else {
                moveTo(0f, 0f)
                lineTo(size.width / 2f, size.height)
                lineTo(size.width, 0f)
            }
            close()
        }
        drawPath(path, color)
    }
}

/** Places the menu centered under (or above) the icon, clamped to the screen. */
private class AnchoredPopupPositionProvider(
    private val anchor: IntOffset,
    private val preferAbove: Boolean,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val margin = POPUP_MARGIN_PX
        val x = (anchor.x - popupContentSize.width / 2)
            .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        val y = if (preferAbove) {
            (anchor.y - popupContentSize.height - margin).coerceAtLeast(margin)
        } else {
            (anchor.y + margin)
                .coerceAtMost((windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin))
        }
        return IntOffset(x, y)
    }
}

/** One icon in the popup's top row. */
private class QuickItem(val label: String, @DrawableRes val icon: Int, val onClick: () -> Unit)

/** The popup's top row: up to four everyday actions as icons with a short label under each. */
@Composable
private fun QuickActionRow(items: List<QuickItem>) {
    val palette = LocalExpressivePalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEach { item ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = item.onClick)
                    .padding(vertical = 8.dp),
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(palette.btn),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(item.icon), contentDescription = null, tint = LocalAccent.current, modifier = Modifier.size(20.dp))
                }
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.dim,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** The top of the Customize and Remove pages: a back arrow and the page's title. */
@Composable
private fun PopupPageHeader(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onBack).padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Icon(
            painterResource(LauncherIcons.ArrowBack),
            contentDescription = stringResource(R.string.popup_back),
            tint = LocalExpressivePalette.current.text,
            modifier = Modifier.size(20.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = LocalExpressivePalette.current.text,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = LocalExpressivePalette.current.trackOff)
}

/** One choice on the Remove page: what it is, and in a line what it does. */
@Composable
private fun RemoveChoiceRow(@DrawableRes icon: Int, title: String, detail: String, danger: Boolean = false, onClick: () -> Unit) {
    val palette = LocalExpressivePalette.current
    val tint = if (danger) Color(0xFFE5534B) else LocalAccent.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (danger) tint else palette.text)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = palette.dim)
        }
    }
}
