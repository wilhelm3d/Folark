package org.arkikeskus.launcher.feature.home

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Process
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.drawToBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.local.HomeItemEntity
import org.arkikeskus.launcher.ui.LauncherIcons

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

/** Searchable preview gallery with M3 Expressive layout, segmented tabs, and liquid glass cards. */
@Composable
fun WidgetPickerScreen(
    dragController: WidgetDragController,
    columns: Int,
    rows: Int,
    onPick: (WidgetChoice, Int, Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Built-in Expressive, 1 = Installed App Widgets

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

    val dragging = dragController.moving

    Surface(
        modifier = modifier.graphicsLayer { alpha = if (dragging) 0f else 1f },
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.widget_picker_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(painterResource(LauncherIcons.Close), stringResource(R.string.widget_picker_close))
                }
            }

            // M3 Search bar for instant filtering
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.widget_search)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                trailingIcon = if (query.isEmpty()) null else ({
                    IconButton(onClick = { query = "" }) {
                        Icon(painterResource(LauncherIcons.Close), stringResource(R.string.widget_search_clear))
                    }
                }),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            )

            // Segmented Category Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    onClick = { selectedTab = 0 },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedTab == 0) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Built-in Expressive",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(
                    onClick = { selectedTab = 1 },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedTab == 1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Installed Apps",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                stringResource(R.string.widget_picker_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                userScrollEnabled = !dragging,
            ) {
                if (selectedTab == 1 && groups == null) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
                if (failed && selectedTab == 1) {
                    item { Text(stringResource(R.string.widget_picker_load_failed)) }
                }

                if (selectedTab == 0) {
                    if (filteredBuiltins.isEmpty()) {
                        item { Text(stringResource(R.string.widget_search_none)) }
                    } else {
                        items(filteredBuiltins, key = {
                            when (val choice = it.choice) {
                                is WidgetChoice.Builtin -> "builtin:" + choice.type
                                else -> it.label
                            }
                        }) { option ->
                            WidgetCard(option, groupLabel = builtinTitle, dragController, columns, rows, onPick)
                        }
                    }
                } else {
                    if (filteredAppGroups.isEmpty() && groups != null) {
                        item { Text(stringResource(R.string.widget_search_none)) }
                    } else {
                        filteredAppGroups.forEach { group ->
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
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            items(group.widgets, key = {
                                when (val choice = it.choice) {
                                    is WidgetChoice.App -> choice.provider.provider.flattenToString()
                                    is WidgetChoice.Builtin -> "builtin:" + choice.type
                                }
                            }) { option ->
                                WidgetCard(option, groupLabel = group.label, dragController, columns, rows, onPick)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetCard(
    option: WidgetOption,
    groupLabel: String?,
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
            else -> columns to 2
        }
    }
    val sx = defaultX.coerceIn(1, columns.coerceAtLeast(1))
    val sy = defaultY.coerceIn(1, rows.coerceAtLeast(1))
    var snapshot by remember(option.choice) { mutableStateOf<(() -> Bitmap?)?>(null) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (option.appIcon != null) {
                    Image(
                        bitmap = option.appIcon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)),
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_widgets),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                Column(Modifier.weight(1f)) {
                    Text(option.label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    if (groupLabel != null && groupLabel != option.label) {
                        Text(
                            groupLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "${sx}×${sy}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Box(
                Modifier.fillMaxWidth()
                    .aspectRatio((sx * controller.cellWidthDp / (sy * controller.cellHeightDp)).coerceIn(1.5f, 3f))
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .widgetDragGesture(option.choice, true, controller) { root, fraction, owner ->
                        controller.start(WidgetDrag(null, option.choice, sx, sy, fraction, snapshot?.invoke()), root, owner)
                    },
                contentAlignment = Alignment.Center,
            ) {
                WidgetPreview(option.choice, Modifier.fillMaxSize().padding(12.dp),
                    sx * controller.cellWidthDp, sy * controller.cellHeightDp, onSnapshot = { snapshot = it })
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                FilledTonalButton(
                    onClick = { onPick(option.choice, sx, sy) },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        painter = painterResource(LauncherIcons.Add),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).padding(end = 4.dp),
                    )
                    Text(stringResource(R.string.widget_add))
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
                if (view.widgetWidth != w || view.widgetHeight != h) {
                    view.widgetWidth = w
                    view.widgetHeight = h
                    view.requestLayout()
                }
                if (view.tag !== preview.remote) {
                    view.removeAllViews()
                    view.resetRenderFailure()
                    val child = runCatching { preview.remote!!.apply(context, view) }.getOrNull()
                    if (child == null) failedRemote = true else {
                        view.addView(child, FrameLayout.LayoutParams(-1, -1))
                        view.tag = preview.remote
                    }
                }
                onSnapshot { runCatching { view.drawToBitmap() }.getOrNull() }
            },
            modifier = modifier.clearAndSetSemantics {},
        )
        preview.bitmap != null -> {
            Image(preview.bitmap!!.asImageBitmap(), null, modifier)
            SideEffect { onSnapshot { preview.bitmap } }
        }
        else -> Icon(painterResource(R.drawable.ic_widgets), null, modifier.padding(24.dp),
            tint = MaterialTheme.colorScheme.primary)
    }
}

/** Static samples share the real widget surface; previewing never starts data collectors or actions. */
@Composable
private fun BuiltinWidgetPreview(type: String, modifier: Modifier) {
    WidgetSurface(modifier) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            when (type) {
                HomeItemEntity.BUILTIN_BATTERY -> {
                    Text("75%", style = MaterialTheme.typography.displaySmall)
                    Text(stringResource(R.string.battery_widget_name), style = MaterialTheme.typography.labelMedium)
                }
                HomeItemEntity.BUILTIN_NOTIFICATIONS -> {
                    Icon(painterResource(R.drawable.ic_widgets), null, Modifier.size(32.dp))
                    Text(stringResource(R.string.notifications_widget_name), style = MaterialTheme.typography.labelMedium)
                }
                HomeItemEntity.BUILTIN_PEOPLE -> {
                    Icon(painterResource(LauncherIcons.Message), null, Modifier.size(32.dp))
                    Text(stringResource(R.string.people_widget_name), style = MaterialTheme.typography.labelMedium)
                    Text(stringResource(R.string.people_widget_desc), style = MaterialTheme.typography.bodySmall)
                }
                HomeItemEntity.BUILTIN_NOTHING_CLOCK -> {
                    Text("10:42", style = MaterialTheme.typography.displayMedium)
                    Text(stringResource(R.string.nothing_clock_widget_name), style = MaterialTheme.typography.labelMedium)
                }
                HomeItemEntity.BUILTIN_SAMSUNG_WEATHER -> {
                    Text("21°C", style = MaterialTheme.typography.displayMedium)
                    Text(stringResource(R.string.samsung_weather_widget_name), style = MaterialTheme.typography.labelMedium)
                }
                HomeItemEntity.BUILTIN_NOTIFICATION_WIDGET -> {
                    Text("Notifications", style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.samsung_notification_widget_name), style = MaterialTheme.typography.labelMedium)
                }
                HomeItemEntity.BUILTIN_INTERACTIVE_NOTIFICATIONS -> {
                    Text("Interactive Notifications", style = MaterialTheme.typography.titleMedium)
                    Text("Pro Interactive Notifications", style = MaterialTheme.typography.labelMedium)
                }
                else -> {
                    Text("9.41", style = MaterialTheme.typography.displayMedium)
                    Text(stringResource(R.string.smartspace_widget_name), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

internal class WidgetPreviewContainer(context: Context) : FrameLayout(context) {
    var onRenderFailed: (() -> Unit)? = null
    var widgetWidth = 1
    var widgetHeight = 1
    private var renderFailed = false

    fun resetRenderFailure() { renderFailed = false }

    private inline fun guarded(render: () -> Unit) {
        if (renderFailed) return
        try {
            render()
        } catch (e: Exception) {
            renderFailed = true
            onRenderFailed?.invoke()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?) = true

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        guarded {
            for (index in 0 until childCount) getChildAt(index).measure(
                MeasureSpec.makeMeasureSpec(widgetWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(widgetHeight, MeasureSpec.EXACTLY),
            )
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val scale = minOf(width.toFloat() / widgetWidth, height.toFloat() / widgetHeight)
        guarded {
            for (index in 0 until childCount) getChildAt(index).apply {
                layout(0, 0, widgetWidth, widgetHeight)
                pivotX = 0f
                pivotY = 0f
                scaleX = scale
                scaleY = scale
                translationX = (this@WidgetPreviewContainer.width - widgetWidth * scale) / 2f
                translationY = (this@WidgetPreviewContainer.height - widgetHeight * scale) / 2f
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val checkpoint = canvas.save()
        guarded { super.dispatchDraw(canvas) }
        canvas.restoreToCount(checkpoint)
    }
}
