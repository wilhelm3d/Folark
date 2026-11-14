package org.arkikeskus.launcher.feature.home

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.feature.home.widgets.LocalAppWidgetHost
import org.arkikeskus.launcher.feature.home.widgets.LocalOrphanWidgetConfigResult
import org.arkikeskus.launcher.feature.home.widgets.LocalWidgetConfigLauncher
import org.arkikeskus.launcher.feature.home.widgets.WidgetChoice
import org.arkikeskus.launcher.feature.home.workspace.containsScrollableCollection
import org.arkikeskus.launcher.model.WidgetPlacement
import org.arkikeskus.launcher.model.catching

// Getting a widget onto home (split out of HomeScreen.kt): the host views, the bind → configure → save
// pipeline, re-binding a restored placeholder and the widget picker's open state.

/**
 * What [HomeScreen] needs of the widget machinery. [views] and [scrollable] are what the workspace
 * draws; [showPicker] / [stackTarget] are the widget picker's state ([stackTarget] non-null: it is
 * choosing a widget FOR that stack, "Add to stack"); the rest start a bind, a restore or a
 * reconfigure, and [message] says something short.
 */
@Stable
internal class HomeWidgets(
    private val showPickerState: MutableState<Boolean>,
    private val stackTargetState: MutableState<Long?>,
    val views: Map<Int, AppWidgetHostView>,
    val scrollable: Map<Int, Boolean>,
    /** The app's AppWidgetHost (null before the activity provides it); its ids are freed with the widget. */
    val host: AppWidgetHost?,
    val message: (Int) -> Unit,
    val addWidget: (WidgetChoice, WidgetPlacement) -> Unit,
    val startBind: (provider: AppWidgetProviderInfo, restoreRowId: Long?, placement: WidgetPlacement?, stackId: Long?) -> Unit,
    val restore: (PendingWidget) -> Unit,
    val reconfigure: (appWidgetId: Int) -> Unit,
) {
    var showPicker: Boolean
        get() = showPickerState.value
        set(value) { showPickerState.value = value }
    var stackTarget: Long?
        get() = stackTargetState.value
        set(value) { stackTargetState.value = value }
}

@Composable
internal fun rememberHomeWidgets(
    viewModel: HomeViewModel,
    entries: List<HomeEntry>,
    widgetScope: CoroutineScope,
): HomeWidgets {
    val context = LocalContext.current
    val widgetHost = LocalAppWidgetHost.current
    val widgetConfigLauncher = LocalWidgetConfigLauncher.current
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val showPicker = remember { mutableStateOf(false) }
    // The widget stack the picker is choosing a widget FOR ("Add to stack"); null = a normal add.
    val stackToAddTo = remember { mutableStateOf<Long?>(null) }
    var liveBindId by remember { mutableStateOf<Int?>(null) }
    fun widgetMessage(message: Int) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    // Pending widget across the bind/configure result steps. [restoreRowId] non-null → this is a
    // RESTORE (re-bind an existing placeholder row) rather than adding a brand-new widget.
    // Saveable: while the system bind dialog or the widget's own config activity is in front, the
    // launcher is a killable background process — a plain remember lost the bind on process death
    // and the user's widget silently never appeared.
    var pendingWidget by rememberSaveable(stateSaver = PendingWidgetBindSaver) {
        mutableStateOf<PendingWidgetBind?>(null)
    }

    // Persistent, page-independent host views: created once per placed widget id and kept alive so the
    // AppWidgetHost listener is never lost when a pager page scrolls off-screen. A lost listener drops a
    // collection widget's deferred row update (notifyAppWidgetViewDataChanged) — the empty-WhatsApp bug.
    // Mirrors Launcher3's LauncherWidgetHolder keeping every host view registered for the app's lifetime.
    val widgetViews = remember { mutableStateMapOf<Int, AppWidgetHostView>() }
    val widgetScrollableById = remember { mutableStateMapOf<Int, Boolean>() }
    // Widgets inside a stack need a host view too — without one the stack shows empty tiles.
    val placedWidgetIds = remember(entries) {
        entries.flatMap { entry ->
            when (entry) {
                is PlacedWidget -> listOf(entry.appWidgetId)
                is PlacedWidgetStack -> entry.items.filterIsInstance<PlacedWidget>().map { it.appWidgetId }
                else -> emptyList()
            }
        }.toSet()
    }
    LaunchedEffect(placedWidgetIds, widgetHost) {
        val host = widgetHost ?: return@LaunchedEffect
        placedWidgetIds.forEach { id ->
            if (widgetViews[id] == null) {
                val info = appWidgetManager.getAppWidgetInfo(id)
                if (info != null) {
                    catching {
                        // Mark the widget as living on the HOME screen up front — some providers
                        // (WhatsApp's chat list) won't populate until the host category is set.
                        appWidgetManager.updateAppWidgetOptions(
                            id,
                            android.os.Bundle().apply {
                                putInt(
                                    AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                                )
                            },
                        )
                        val v = host.createView(context, id, info)
                        v.setPadding(0, 0, 0, 0)
                        v.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                            widgetScrollableById[id] = v.containsScrollableCollection()
                        }
                        widgetScrollableById[id] = v.containsScrollableCollection()
                        widgetViews[id] = v
                    }
                }
            }
        }
        (widgetViews.keys - placedWidgetIds).forEach {
            widgetViews.remove(it)
            widgetScrollableById.remove(it)
        }
    }

    // One-shot at startup: free any AppWidgetHost ids with no home_items row — e.g. the old bound ids
    // left over after a full backup restore replaced the layout — so the host doesn't leak widget ids.
    // The host ids are snapshotted BEFORE the (suspending) DB query: an id allocated while the query
    // runs is absent from the snapshot and can never be swept; an in-flight bind's id is excluded too.
    LaunchedEffect(widgetHost) {
        val host = widgetHost ?: return@LaunchedEffect
        val hostIds = catching { host.appWidgetIds }.getOrNull() ?: return@LaunchedEffect
        val dbIds = viewModel.boundWidgetIds()
        hostIds.forEach { id ->
            if (id !in dbIds && id != pendingWidget?.appWidgetId) catching { host.deleteAppWidgetId(id) }
        }
        // The reverse direction: DB rows bound to ids this host never allocated — what a Google Auto
        // Backup / device-transfer restore leaves behind (the Room DB carries the OLD device's ids).
        // Unbind them into tap-to-set-up placeholders instead of invisible dead zones on the grid.
        val validIds = hostIds.toMutableSet()
        pendingWidget?.appWidgetId?.let { validIds.add(it) }
        viewModel.unbindStaleWidgets(validIds)
    }

    fun finishWidget(bind: PendingWidgetBind) {
        widgetScope.launch {
            // The allocated id and the layout row must reach a consistent state even if the Activity
            // is destroyed while Room is writing. Process death is recovered by the saved bind state.
            withContext(NonCancellable) {
                val saved = catching {
                    if (bind.restoreRowId != null) {
                        viewModel.bindRestoredWidget(bind.restoreRowId, bind.appWidgetId)
                    } else if (bind.stackId != null) {
                        viewModel.addBoundWidgetToStack(
                            bind.stackId, bind.appWidgetId, bind.provider.provider.flattenToString(),
                        )
                    } else {
                        val target = bind.placement ?: return@catching false
                        viewModel.addWidgetAt(bind.appWidgetId, bind.provider.provider.flattenToString(), null, target)
                    }
                }.getOrDefault(false)
                if (!saved) {
                    catching { widgetHost?.deleteAppWidgetId(bind.appWidgetId) }
                    widgetMessage(R.string.widget_add_failed)
                }
                if (pendingWidget?.appWidgetId == bind.appWidgetId) pendingWidget = null
            }
        }
    }

    val reconfigureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* the widget reconfigures itself via RemoteViews; no DB change needed */ }

    fun configureOrFinish(bind: PendingWidgetBind) {
        // Launch the configuration activity through the SYSTEM (Activity-routed), not a raw
        // ACTION_APPWIDGET_CONFIGURE Intent, so the framework records the widget as configured.
        // Otherwise some providers (WhatsApp's auth-gated chat widget) stay "configuration pending" and
        // never populate. If there's no config activity, finish immediately.
        val launcher = widgetConfigLauncher
        if (bind.provider.configure != null && launcher != null) {
            pendingWidget = bind
            launcher(bind.appWidgetId) { ok ->
                val p = pendingWidget
                if (p != null) {
                    if (ok) finishWidget(p)
                    else { widgetHost?.deleteAppWidgetId(p.appWidgetId); pendingWidget = null }
                }
            }
        } else {
            finishWidget(bind)
        }
    }

    val bindLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val p = pendingWidget
        if (p != null) {
            if (result.resultCode == android.app.Activity.RESULT_OK) configureOrFinish(p)
            else { widgetHost?.deleteAppWidgetId(p.appWidgetId); pendingWidget = null }
        }
    }

    // Allocate an id and bind [provider] (silently if allowed, else via the system bind dialog), then
    // configure. [restoreRowId] != null re-binds an existing placeholder row instead of adding a new one;
    // [stackId] != null adds the new widget to that widget stack instead of a grid placement.
    fun startBind(
        provider: AppWidgetProviderInfo,
        restoreRowId: Long?,
        placement: WidgetPlacement? = null,
        stackId: Long? = null,
    ) {
        // Guards a double tap. Only a bind started by THIS process blocks: [pendingWidget] is saveable,
        // so one whose result never arrives would otherwise refuse every later add, silently, forever.
        if (pendingWidget != null && pendingWidget?.appWidgetId == liveBindId) return
        val host = widgetHost ?: return
        val id = catching { host.allocateAppWidgetId() }.getOrElse {
            widgetMessage(R.string.widget_add_failed)
            return
        }
        val bind = PendingWidgetBind(id, provider, restoreRowId, placement, stackId)
        liveBindId = id
        pendingWidget = bind
        val bound = catching { appWidgetManager.bindAppWidgetIdIfAllowed(id, provider.profile, provider.provider, null) }.getOrDefault(false)
        if (bound) {
            configureOrFinish(bind)
        } else {
            pendingWidget = bind
            val intent = android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile)
            }
            catching { bindLauncher.launch(intent) }
                .onFailure { host.deleteAppWidgetId(id); pendingWidget = null }
        }
    }

    fun addWidget(choice: WidgetChoice, placement: WidgetPlacement) {
        showPicker.value = false
        stackToAddTo.value = null
        when (choice) {
            is WidgetChoice.App -> startBind(choice.provider, restoreRowId = null, placement = placement)
            is WidgetChoice.Builtin -> widgetScope.launch {
                val ok = catching { viewModel.addWidgetAt(null, null, choice.type, placement) }.getOrDefault(false)
                if (!ok) widgetMessage(R.string.widget_add_failed)
            }
        }
    }

    // Re-bind a restored placeholder: resolve its provider info, then run the same bind/configure flow
    // targeting its existing row. If the provider's app is gone (uninstalled after restore), no-op.
    fun startRestoreWidget(pending: PendingWidget) {
        val provider = appWidgetManager.installedProviders.firstOrNull { it.provider == pending.provider } ?: return
        startBind(provider, restoreRowId = pending.rowId)
    }

    // A config result that arrived after process death: [pendingWidget] survived (saveable) but the
    // result callback died with the old process — LauncherActivity parks the outcome here instead.
    val orphanConfigResult = LocalOrphanWidgetConfigResult.current
    LaunchedEffect(orphanConfigResult) {
        orphanConfigResult?.collect { ok ->
            if (ok == null) return@collect
            orphanConfigResult.value = null
            val p = pendingWidget
            if (p != null) {
                if (ok) {
                    finishWidget(p)
                } else {
                    widgetHost?.deleteAppWidgetId(p.appWidgetId)
                    pendingWidget = null
                }
            }
        }
    }

    return HomeWidgets(
        showPickerState = showPicker,
        stackTargetState = stackToAddTo,
        views = widgetViews,
        scrollable = widgetScrollableById,
        host = widgetHost,
        message = ::widgetMessage,
        addWidget = ::addWidget,
        startBind = ::startBind,
        restore = ::startRestoreWidget,
        reconfigure = { id ->
            val info = appWidgetManager.getAppWidgetInfo(id)
            val configure = info?.configure
            if (configure != null) {
                catching {
                    reconfigureLauncher.launch(
                        android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                            .setComponent(configure)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                    )
                }
            }
        },
    )
}
