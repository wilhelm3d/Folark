package org.arkikeskus.launcher.feature.home

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Process
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.core.view.drawToBitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.arkikeskus.launcher.ui.component.liquidGlass
import org.arkikeskus.launcher.ui.drawer.drawerStyleSpec
import org.arkikeskus.launcher.ui.expressive.ExpressiveTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.local.HomeItemEntity
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.ui.LauncherIcons
import org.arkikeskus.launcher.ui.component.LocalScreenType
import org.arkikeskus.launcher.ui.component.WindowBlurBehind
import org.arkikeskus.launcher.ui.drawer.DrawerColors
import org.arkikeskus.launcher.ui.drawer.rememberDrawerColors

private data class WidgetGroup(
    val packageName: String,
    val label: String,
    val appIcon: Bitmap? = null,
    val widgets: List<WidgetOption>,
)

private data class WidgetOption(
    val choice: WidgetChoice,
    val label: String,
    val appIcon: Bitmap? = null,
)

private data class WidgetPreviewData(val remote: RemoteViews? = null, val bitmap: Bitmap? = null)

/**
 * Searchable widget gallery. On the INNER SCREEN, renders a macOS Sonoma / iPadOS style 2-pane gallery
 * panel that automatically inherits the inner app drawer's size, positioning, theme colors, glass blur,
 * and animations. On the OUTER SCREEN, renders a compact 1-pane picker suited for narrow cover screens.
 */
@Composable
fun WidgetPickerScreen(
    dragController: WidgetDragController,
    columns: Int,
    rows: Int,
    screenType: ScreenType = LocalScreenType.current,
    settings: LauncherSettings = LauncherSettings(),
    onPick: (WidgetChoice, Int, Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }

    val groups by produceState<List<WidgetGroup>?>(null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                AppWidgetManager.getInstance(context).installedProviders
                    .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
                    .groupBy { it.provider.packageName }
                    .map { (pkg, providers) ->
                        val label = runCatching {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                        }.getOrDefault(pkg)
                        val icon = runCatching {
                            pm.getApplicationIcon(pkg).toBitmap(96, 96)
                        }.getOrNull()
                        WidgetGroup(
                            pkg,
                            label,
                            icon,
                            providers.map { provider ->
                                val widgetIcon = runCatching { provider.loadIcon(context, 0)?.toBitmap(96, 96) }.getOrNull() ?: icon
                                WidgetOption(
                                    WidgetChoice.App(provider),
                                    runCatching { provider.loadLabel(pm) }.getOrDefault(label),
                                    widgetIcon,
                                )
                            }.sortedBy { it.label.lowercase() },
                        )
                    }.sortedBy { it.label.lowercase() }
            }.getOrElse { failed = true; emptyList() }
        }
    }

    val builtinTitle = stringResource(R.string.widget_builtin_section)
    val builtins = listOf(
        WidgetOption(WidgetChoice.Builtin(HomeItemEntity.BUILTIN_SMARTSPACE), stringResource(R.string.smartspace_widget_name)),
        WidgetOption(WidgetChoice.Builtin(HomeItemEntity.BUILTIN_BATTERY), stringResource(R.string.battery_widget_name)),
        WidgetOption(WidgetChoice.Builtin(HomeItemEntity.BUILTIN_NOTIFICATIONS), stringResource(R.string.notifications_widget_name)),
        WidgetOption(WidgetChoice.Builtin(HomeItemEntity.BUILTIN_NOTIFICATION_WIDGET), stringResource(R.string.samsung_notification_widget_name)),
        WidgetOption(WidgetChoice.Builtin(HomeItemEntity.BUILTIN_INTERACTIVE_NOTIFICATIONS), "Pro Interactive Notifications"),
        WidgetOption(WidgetChoice.Builtin(HomeItemEntity.BUILTIN_PEOPLE), stringResource(R.string.people_widget_name)),
        WidgetOption(WidgetChoice.Builtin(HomeItemEntity.BUILTIN_NOTHING_CLOCK), stringResource(R.string.nothing_clock_widget_name)),
        WidgetOption(WidgetChoice.Builtin(HomeItemEntity.BUILTIN_SAMSUNG_WEATHER), stringResource(R.string.samsung_weather_widget_name)),
        WidgetOption(WidgetChoice.Builtin(HomeItemEntity.BUILTIN_MEDIA), stringResource(R.string.media_widget_name)),
        WidgetOption(WidgetChoice.Builtin(HomeItemEntity.BUILTIN_TOP_APPS), stringResource(R.string.top_apps_widget_name)),
        WidgetOption(WidgetChoice.Builtin(HomeItemEntity.BUILTIN_STATS), stringResource(R.string.stats_widget_name)),
        WidgetOption(WidgetChoice.Builtin(HomeItemEntity.BUILTIN_NOTE), stringResource(R.string.note_widget_name)),
        WidgetOption(WidgetChoice.Builtin(HomeItemEntity.BUILTIN_WEATHER_DAY), stringResource(R.string.weather_day_widget_name)),
        WidgetOption(WidgetChoice.Builtin(HomeItemEntity.BUILTIN_WEATHER_BIG), stringResource(R.string.weather_big_widget_name)),
    )

    val trimmedQuery = query.trim()
    val filteredBuiltins = if (trimmedQuery.isEmpty()) builtins else builtins.filter {
        it.label.contains(trimmedQuery, ignoreCase = true)
    }

    val filteredAppGroups = groups.orEmpty().mapNotNull { group ->
        val matchesGroup = group.label.contains(trimmedQuery, ignoreCase = true)
        val widgets = if (matchesGroup) group.widgets else group.widgets.filter {
            it.label.contains(trimmedQuery, ignoreCase = true)
        }
        group.copy(widgets = widgets).takeIf { widgets.isNotEmpty() }
    }

    // Resolve drawer colors from inner app drawer settings
    val drawerColors = rememberDrawerColors(
        theme = settings.drawerThemeTint,
        accentArgb = settings.drawerAccentColor,
        customArgb = settings.drawerCustomColor,
        glass = settings.drawerGlassEnabled,
        panelOpacity = settings.drawerPanelOpacity,
    )

    val configuration = LocalConfiguration.current
    val isInnerScreen = screenType == ScreenType.INNER || configuration.screenWidthDp >= 600

    if (isInnerScreen) {
        InnerWidgetGalleryPanel(
            dragController = dragController,
            columns = columns,
            rows = rows,
            settings = settings,
            drawerColors = drawerColors,
            query = query,
            onQueryChange = { query = it },
            builtins = filteredBuiltins,
            appGroups = filteredAppGroups,
            builtinTitle = builtinTitle,
            loading = groups == null,
            failed = failed,
            onPick = onPick,
            onDismiss = onDismiss,
            modifier = modifier,
        )
    } else {
        OuterWidgetPickerPanel(
            dragController = dragController,
            columns = columns,
            rows = rows,
            drawerColors = drawerColors,
            query = query,
            onQueryChange = { query = it },
            builtins = filteredBuiltins,
            appGroups = filteredAppGroups,
            builtinTitle = builtinTitle,
            loading = groups == null,
            failed = failed,
            onPick = onPick,
            onDismiss = onDismiss,
            modifier = modifier,
        )
    }
}

/**
 * macOS Sonoma / iPadOS 2-Pane Widget Gallery for the Inner Display. Sized and styled automatically
 * using the inner app drawer's settings (`drawerWidthFraction`, `drawerHeightFraction`, `drawerAlignment`,
 * `drawerGlassEnabled`, `drawerPanelOpacity`, `drawerBackgroundBlur`, `drawerThemeTint`, `drawerAccentColor`).
 */
@Composable
private fun InnerWidgetGalleryPanel(
    dragController: WidgetDragController,
    columns: Int,
    rows: Int,
    settings: LauncherSettings,
    drawerColors: DrawerColors,
    query: String,
    onQueryChange: (String) -> Unit,
    builtins: List<WidgetOption>,
    appGroups: List<WidgetGroup>,
    builtinTitle: String,
    loading: Boolean,
    failed: Boolean,
    onPick: (WidgetChoice, Int, Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sidebar = settings.innerSidebarAppDrawer
    val effectiveHeightFraction =
        if (sidebar) 1f else settings.drawerHeightFraction.coerceIn(0.4f, 1.0f)
    val effectiveWidthFraction = settings.drawerWidthFraction.coerceIn(0.4f, 1.0f)
        .let { if (sidebar) it.coerceAtMost(0.5f) else it }
    val effectiveAlignment = settings.drawerAlignment.let {
        if (sidebar && it != LauncherSettings.DRAWER_ALIGNMENT_LEFT) LauncherSettings.DRAWER_ALIGNMENT_RIGHT else it
    }

    val sheetAlignment = when (effectiveAlignment.lowercase()) {
        LauncherSettings.DRAWER_ALIGNMENT_LEFT -> Alignment.BottomStart
        LauncherSettings.DRAWER_ALIGNMENT_RIGHT -> Alignment.BottomEnd
        else -> Alignment.BottomCenter
    }

    val drawerStyle = remember(settings.drawerStyle) { drawerStyleSpec(settings.drawerStyle) }
    val systemDensity = LocalDensity.current
    val drawerDensity = remember(systemDensity, settings.dpiAppDrawer) {
        Density(
            density = systemDensity.density * settings.dpiAppDrawer,
            fontScale = systemDensity.fontScale * settings.dpiAppDrawer,
        )
    }

    val panelShape = when {
        !sidebar && effectiveHeightFraction >= 1.0f && effectiveWidthFraction >= 1.0f -> RoundedCornerShape(0.dp)
        !sidebar -> RoundedCornerShape(topStart = drawerStyle.corner, topEnd = drawerStyle.corner)
        effectiveAlignment == LauncherSettings.DRAWER_ALIGNMENT_LEFT ->
            RoundedCornerShape(topEnd = drawerStyle.corner, bottomEnd = drawerStyle.corner)
        else -> RoundedCornerShape(topStart = drawerStyle.corner, bottomStart = drawerStyle.corner)
    }

    var selectedCategoryIndex by remember { mutableIntStateOf(0) } // 0 = All, 1 = Expressive, 2+ = App groups

    // Glass blur behind the open gallery (matching inner app drawer)
    if (settings.drawerGlassEnabled) {
        WindowBlurBehind(maxRadiusDp = settings.drawerBackgroundBlur * 2f) { 1f }
    }

    val dragging = dragController.moving

    ExpressiveTheme {
    CompositionLocalProvider(LocalDensity provides drawerDensity) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (dragging) 0f else 1f },
        contentAlignment = sheetAlignment,
    ) {
        // Scrim backdrop (matching inner app drawer)
        if (settings.drawerScrimOpacity > 0f || effectiveHeightFraction < 1.0f || effectiveWidthFraction < 1.0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (settings.drawerScrimOpacity * 0.7f).coerceIn(0f, 0.8f)))
                    .pointerInput(Unit) {
                        detectTapGestures { onDismiss() }
                    }
            )
        }

        // Inner Widget Gallery Panel with Liquid Glass & Drawer Shape
        Box(
            modifier = Modifier
                .fillMaxWidth(effectiveWidthFraction)
                .fillMaxHeight(effectiveHeightFraction)
                .liquidGlass(
                    shape = panelShape,
                    tint = drawerColors.container,
                    highlight = if (drawerColors.glass) 1f else 0f,
                )
                .pointerInput(Unit) {},
        ) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                DragHandle(width = drawerStyle.handleWidth)
                // Header Bar (Title + Close Button)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_widgets),
                        contentDescription = null,
                        tint = drawerColors.accent,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.widget_picker_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = drawerColors.content,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(LauncherIcons.Close),
                            contentDescription = stringResource(R.string.widget_picker_close),
                            tint = drawerColors.content,
                        )
                    }
                }

                // Main 2-Pane Body (Left Master Sidebar + Right Detail Area)
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // LEFT MASTER SIDEBAR: Search + Categories List
                    Column(
                        modifier = Modifier
                            .width(230.dp)
                            .fillMaxHeight()
                            .background(drawerColors.field, RoundedCornerShape(20.dp))
                            .padding(10.dp),
                    ) {
                        // Search Widgets Field
                        OutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            placeholder = {
                                Text(
                                    stringResource(R.string.widget_search),
                                    color = drawerColors.contentDim,
                                    fontSize = 13.sp,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_search),
                                    contentDescription = null,
                                    tint = drawerColors.contentDim,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            trailingIcon = if (query.isEmpty()) null else ({
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(
                                        painter = painterResource(LauncherIcons.Close),
                                        contentDescription = stringResource(R.string.widget_search_clear),
                                        tint = drawerColors.contentDim,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }),
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = drawerColors.container,
                                unfocusedContainerColor = drawerColors.container,
                                focusedBorderColor = drawerColors.accent,
                                unfocusedBorderColor = drawerColors.content.copy(alpha = 0.12f),
                                focusedTextColor = drawerColors.content,
                                unfocusedTextColor = drawerColors.content,
                            ),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        )

                        // Category List
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // Category 0: All Widgets
                            item {
                                SidebarCategoryPill(
                                    title = stringResource(R.string.widget_category_all),
                                    icon = painterResource(R.drawable.ic_widgets),
                                    isSelected = selectedCategoryIndex == 0,
                                    colors = drawerColors,
                                    onClick = { selectedCategoryIndex = 0 },
                                )
                            }
                            // Category 1: Folark Expressive
                            item {
                                SidebarCategoryPill(
                                    title = builtinTitle,
                                    icon = painterResource(R.drawable.ic_home_settings),
                                    isSelected = selectedCategoryIndex == 1,
                                    colors = drawerColors,
                                    onClick = { selectedCategoryIndex = 1 },
                                )
                            }
                            // Installed App Categories
                            itemsIndexed(appGroups) { index, group ->
                                SidebarCategoryPill(
                                    title = group.label,
                                    bitmapIcon = group.appIcon,
                                    isSelected = selectedCategoryIndex == index + 2,
                                    colors = drawerColors,
                                    onClick = { selectedCategoryIndex = index + 2 },
                                )
                            }
                        }
                    }

                    // RIGHT DETAIL AREA: Hero Banner + Widget Options Carousel/Grid
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        // Determine options to display based on selected category
                        val currentCategoryLabel = when (selectedCategoryIndex) {
                            0 -> stringResource(R.string.widget_category_all)
                            1 -> builtinTitle
                            else -> appGroups.getOrNull(selectedCategoryIndex - 2)?.label ?: builtinTitle
                        }

                        val currentOptions = when (selectedCategoryIndex) {
                            0 -> builtins + appGroups.flatMap { it.widgets }
                            1 -> builtins
                            else -> appGroups.getOrNull(selectedCategoryIndex - 2)?.widgets.orEmpty()
                        }

                        // Hero Banner at top of detail area
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = drawerColors.field,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentCategoryLabel,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = drawerColors.accent,
                                    )
                                    Text(
                                        text = stringResource(R.string.widget_hero_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = drawerColors.contentDim,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }

                        // Scrollable List of Widget Cards
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            userScrollEnabled = !dragging,
                        ) {
                            if (loading && selectedCategoryIndex != 1) {
                                item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = drawerColors.accent) }
                            }
                            if (failed && selectedCategoryIndex != 1) {
                                item { Text(stringResource(R.string.widget_picker_load_failed), color = drawerColors.content) }
                            }
                            if (currentOptions.isEmpty() && !loading) {
                                item { Text(stringResource(R.string.widget_search_none), color = drawerColors.contentDim) }
                            } else {
                                items(currentOptions, key = { option ->
                                    when (val choice = option.choice) {
                                        is WidgetChoice.App -> choice.provider.provider.flattenToString()
                                        is WidgetChoice.Builtin -> "builtin:" + choice.type
                                    }
                                }) { option ->
                                    InnerGalleryWidgetCard(
                                        option = option,
                                        drawerColors = drawerColors,
                                        controller = dragController,
                                        columns = columns,
                                        rows = rows,
                                        onPick = onPick,
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Footer Bar (Hint + Done Button)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.widget_picker_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = drawerColors.contentDim,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = drawerColors.accent,
                            contentColor = drawerColors.onAccent,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.widget_gallery_done),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
    }
    }
}

/** Visual pull-down affordance: a small pill matching the app drawer. */
@Composable
private fun DragHandle(width: androidx.compose.ui.unit.Dp = 36.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = width, height = 4.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(2.dp),
                ),
        )
    }
}

/** Sidebar Category Item Pill with active selection state. */
@Composable
private fun SidebarCategoryPill(
    title: String,
    icon: androidx.compose.ui.graphics.painter.Painter? = null,
    bitmapIcon: Bitmap? = null,
    isSelected: Boolean,
    colors: DrawerColors,
    onClick: () -> Unit,
) {
    val bg = if (isSelected) colors.accent else Color.Transparent
    val textTint = if (isSelected) colors.onAccent else colors.content

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (bitmapIcon != null) {
            Image(
                bitmap = bitmapIcon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)),
            )
        } else if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = textTint,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
            color = textTint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Widget Option Card in the Inner Screen Gallery with live preview and + Add Widget button. */
@Composable
private fun InnerGalleryWidgetCard(
    option: WidgetOption,
    drawerColors: DrawerColors,
    controller: WidgetDragController,
    columns: Int,
    rows: Int,
    onPick: (WidgetChoice, Int, Int) -> Unit,
) {
    val context = LocalContext.current
    val (defaultX, defaultY) = when (val choice = option.choice) {
        is WidgetChoice.App -> defaultWidgetSpans(choice.provider, context, controller.cellWidthDp, controller.cellHeightDp)
        is WidgetChoice.Builtin -> when (choice.type) {
            HomeItemEntity.BUILTIN_BATTERY -> 1 to 1
            HomeItemEntity.BUILTIN_NOTIFICATIONS -> columns to 1
            HomeItemEntity.BUILTIN_NOTIFICATION_WIDGET -> columns to 2
            HomeItemEntity.BUILTIN_INTERACTIVE_NOTIFICATIONS -> columns to 4
            HomeItemEntity.BUILTIN_PEOPLE -> columns to PEOPLE_DEFAULT_SPAN_Y
            HomeItemEntity.BUILTIN_NOTHING_CLOCK -> columns to 2
            HomeItemEntity.BUILTIN_SAMSUNG_WEATHER -> columns to 2
            HomeItemEntity.BUILTIN_MEDIA -> columns to 1
            HomeItemEntity.BUILTIN_TOP_APPS -> columns to 1
            HomeItemEntity.BUILTIN_STATS -> minOf(columns, 3) to 1
            HomeItemEntity.BUILTIN_NOTE -> minOf(columns, 3) to 2
            HomeItemEntity.BUILTIN_WEATHER_DAY -> minOf(columns, 4) to 2
            HomeItemEntity.BUILTIN_WEATHER_BIG -> columns to 4
            else -> columns to 2
        }
    }
    val sx = defaultX.coerceIn(1, columns.coerceAtLeast(1))
    val sy = defaultY.coerceIn(1, rows.coerceAtLeast(1))
    var snapshot by remember(option.choice) { mutableStateOf<(() -> Bitmap?)?>(null) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = drawerColors.field,
        border = BorderStroke(1.dp, drawerColors.content.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            // Option Title + Dimension Tag
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (option.appIcon != null) {
                        Image(
                            bitmap = option.appIcon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)),
                        )
                    }
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = drawerColors.content,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = drawerColors.content.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "${sx} × ${sy}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = drawerColors.content,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            // Live Widget Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio((sx * controller.cellWidthDp / (sy * controller.cellHeightDp)).coerceIn(1.5f, 3.2f))
                    .clip(RoundedCornerShape(16.dp))
                    .background(drawerColors.base.copy(alpha = 0.5f))
                    .widgetDragGesture(option.choice, true, controller) { root, fraction, owner ->
                        controller.start(WidgetDrag(null, option.choice, sx, sy, fraction, snapshot?.invoke()), root, owner)
                    },
                contentAlignment = Alignment.Center,
            ) {
                WidgetPreview(
                    option.choice,
                    Modifier.fillMaxSize().padding(10.dp),
                    sx * controller.cellWidthDp,
                    sy * controller.cellHeightDp,
                    onSnapshot = { snapshot = it },
                )
            }

            // + Add Widget Button
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = { onPick(option.choice, sx, sy) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = drawerColors.accent,
                        contentColor = drawerColors.onAccent,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(
                        painter = painterResource(LauncherIcons.Add),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(end = 4.dp),
                    )
                    Text(
                        text = stringResource(R.string.widget_add),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

/** Single-pane compact Widget Picker for the Outer Cover Screen. */
@Composable
private fun OuterWidgetPickerPanel(
    dragController: WidgetDragController,
    columns: Int,
    rows: Int,
    drawerColors: DrawerColors,
    query: String,
    onQueryChange: (String) -> Unit,
    builtins: List<WidgetOption>,
    appGroups: List<WidgetGroup>,
    builtinTitle: String,
    loading: Boolean,
    failed: Boolean,
    onPick: (WidgetChoice, Int, Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val dragging = dragController.moving

    Surface(
        modifier = modifier.graphicsLayer { alpha = if (dragging) 0f else 1f },
        color = drawerColors.container,
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.widget_picker_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = drawerColors.content,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(painterResource(LauncherIcons.Close), stringResource(R.string.widget_picker_close), tint = drawerColors.content)
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.widget_search), color = drawerColors.contentDim) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null, tint = drawerColors.contentDim) },
                trailingIcon = if (query.isEmpty()) null else ({
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(painterResource(LauncherIcons.Close), stringResource(R.string.widget_search_clear), tint = drawerColors.contentDim)
                    }
                }),
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = drawerColors.field,
                    unfocusedContainerColor = drawerColors.field,
                    focusedBorderColor = drawerColors.accent,
                    unfocusedBorderColor = drawerColors.content.copy(alpha = 0.12f),
                    focusedTextColor = drawerColors.content,
                    unfocusedTextColor = drawerColors.content,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .background(drawerColors.field, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    onClick = { selectedTab = 0 },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedTab == 0) drawerColors.accent else Color.Transparent,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Built-in Expressive",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (selectedTab == 0) drawerColors.onAccent else drawerColors.contentDim,
                        )
                    }
                }
                Surface(
                    onClick = { selectedTab = 1 },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedTab == 1) drawerColors.accent else Color.Transparent,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Installed Apps",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (selectedTab == 1) drawerColors.onAccent else drawerColors.contentDim,
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                userScrollEnabled = !dragging,
            ) {
                if (selectedTab == 1 && loading) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = drawerColors.accent) }
                }
                if (failed && selectedTab == 1) {
                    item { Text(stringResource(R.string.widget_picker_load_failed), color = drawerColors.content) }
                }

                if (selectedTab == 0) {
                    if (builtins.isEmpty()) {
                        item { Text(stringResource(R.string.widget_search_none), color = drawerColors.contentDim) }
                    } else {
                        items(builtins, key = {
                            when (val choice = it.choice) {
                                is WidgetChoice.Builtin -> "builtin:" + choice.type
                                else -> it.label
                            }
                        }) { option ->
                            InnerGalleryWidgetCard(option, drawerColors, dragController, columns, rows, onPick)
                        }
                    }
                } else {
                    if (appGroups.isEmpty() && !loading) {
                        item { Text(stringResource(R.string.widget_search_none), color = drawerColors.contentDim) }
                    } else {
                        appGroups.forEach { group ->
                            item(key = "header:" + group.packageName) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 8.dp),
                                ) {
                                    if (group.appIcon != null) {
                                        Image(
                                            bitmap = group.appIcon.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)),
                                        )
                                    }
                                    Text(
                                        group.label,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = drawerColors.accent,
                                    )
                                }
                            }
                            items(group.widgets, key = {
                                when (val choice = it.choice) {
                                    is WidgetChoice.App -> choice.provider.provider.flattenToString()
                                    is WidgetChoice.Builtin -> "builtin:" + choice.type
                                }
                            }) { option ->
                                InnerGalleryWidgetCard(option, drawerColors, dragController, columns, rows, onPick)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WidgetPreview(
    choice: WidgetChoice, modifier: Modifier, widthDp: Float, heightDp: Float,
    onSnapshot: (() -> Bitmap?) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val provider = (choice as? WidgetChoice.App)?.provider
    val preview by produceState(WidgetPreviewData(), provider) {
        if (provider == null) return@produceState
        value = withContext(Dispatchers.IO) {
            val generated = if (Build.VERSION.SDK_INT >= 35) runCatching {
                AppWidgetManager.getInstance(context).getWidgetPreview(
                    provider.provider, provider.profile, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                )
            }.getOrNull() else null
            val remote = generated ?: if (Build.VERSION.SDK_INT >= 31 && provider.previewLayout != 0 &&
                provider.profile == Process.myUserHandle()
            ) runCatching { RemoteViews(provider.provider.packageName, provider.previewLayout) }.getOrNull() else null
            val bitmap = runCatching {
                val d = provider.loadPreviewImage(context, 0) ?: provider.loadIcon(context, 0)
                val w = d.intrinsicWidth.coerceAtLeast(1)
                val h = d.intrinsicHeight.coerceAtLeast(1)
                val scale = minOf(1f, 640f / maxOf(w, h))
                d.toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            }.getOrNull()
            WidgetPreviewData(remote, bitmap)
        }
    }
    var failedRemote by remember(provider, preview.remote) { mutableStateOf(false) }
    when {
        choice is WidgetChoice.Builtin -> {
            BuiltinWidgetPreview(choice.type, modifier)
            SideEffect { onSnapshot { null } }
        }
        preview.remote != null && !failedRemote -> AndroidView(
            factory = { c ->
                WidgetPreviewContainer(c).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                }
            },
            update = { view ->
                view.onRenderFailed = { failedRemote = true }
                val w = (widthDp * density).toInt().coerceAtLeast(1)
                val h = (heightDp * density).toInt().coerceAtLeast(1)
                view.render(preview.remote!!, provider!!, w, h)
                onSnapshot { view.capture() }
            },
            modifier = modifier,
        )
        preview.bitmap != null -> {
            Image(
                bitmap = preview.bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = modifier,
            )
            SideEffect { onSnapshot { preview.bitmap } }
        }
        else -> {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = modifier,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_widgets),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            SideEffect { onSnapshot { null } }
        }
    }
}

/** Host view for foreign RemoteViews that catches measure/layout/draw exceptions to avoid crashing home. */
internal class WidgetPreviewContainer(context: Context) : FrameLayout(context) {
    var widgetWidth: Int = 0
    var widgetHeight: Int = 0
    var onRenderFailed: (() -> Unit)? = null

    fun render(remote: RemoteViews, info: AppWidgetProviderInfo, widthPx: Int, heightPx: Int) {
        widgetWidth = widthPx
        widgetHeight = heightPx
        removeAllViews()
        try {
            val view = remote.apply(this.context, this)
            addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        } catch (_: Exception) {
            onRenderFailed?.invoke()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        try {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        } catch (_: Exception) {
            onRenderFailed?.invoke()
            setMeasuredDimension(0, 0)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        try {
            super.onLayout(changed, left, top, right, bottom)
        } catch (_: Exception) {
            onRenderFailed?.invoke()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        try {
            super.dispatchDraw(canvas)
        } catch (_: Exception) {
            onRenderFailed?.invoke()
        }
    }

    fun capture(): Bitmap? = runCatching {
        if (measuredWidth <= 0 || measuredHeight <= 0) null else drawToBitmap()
    }.getOrNull()
}

@Composable
private fun BuiltinWidgetPreview(type: String, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            when (type) {
                HomeItemEntity.BUILTIN_NOTIFICATIONS -> NotificationsWidget(Modifier.fillMaxSize())
                HomeItemEntity.BUILTIN_NOTIFICATION_WIDGET -> SamsungNotificationWidget(Modifier.fillMaxSize())
                HomeItemEntity.BUILTIN_BATTERY -> BatteryWidget(Modifier.fillMaxSize())
                HomeItemEntity.BUILTIN_PEOPLE -> PeopleWidget(Modifier.fillMaxSize())
                HomeItemEntity.BUILTIN_NOTHING_CLOCK -> NothingClockWidget(Modifier.fillMaxSize())
                HomeItemEntity.BUILTIN_SAMSUNG_WEATHER -> SamsungWeatherWidget(Modifier.fillMaxSize())
                HomeItemEntity.BUILTIN_INTERACTIVE_NOTIFICATIONS -> InteractiveNotificationWidget(Modifier.fillMaxSize())
                HomeItemEntity.BUILTIN_MEDIA, HomeItemEntity.BUILTIN_TOP_APPS,
                HomeItemEntity.BUILTIN_STATS, HomeItemEntity.BUILTIN_NOTE,
                HomeItemEntity.BUILTIN_WEATHER_DAY, HomeItemEntity.BUILTIN_WEATHER_BIG -> BuiltinWidget(type, Modifier.fillMaxSize())
                else -> SmartspaceWidget(Modifier.fillMaxSize())
            }
        }
    }
}
