package org.arkikeskus.launcher.recents

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.AppUsageRepository
import org.arkikeskus.launcher.data.OpenTasks
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.SplitApp
import org.arkikeskus.launcher.data.SplitCoordinator
import org.arkikeskus.launcher.data.SplitHistory
import org.arkikeskus.launcher.data.SplitThree
import org.arkikeskus.launcher.data.di.ApplicationScope
import org.arkikeskus.launcher.data.root.RootShell
import org.arkikeskus.launcher.data.root.RootTasks
import org.arkikeskus.launcher.feature.appdrawer.DrawerFolderUi
import org.arkikeskus.launcher.feature.appdrawer.SplitRecentItem
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.SplitPickerLook
import javax.inject.Inject

/** Backs [SplitPickerActivity]: what the drawer shows (as set in Settings) and building the split. */
@HiltViewModel
class SplitPickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val splitCoordinator: SplitCoordinator,
    appUsageRepository: AppUsageRepository,
) : ViewModel() {

    val look: StateFlow<SplitPickerLook> = settingsRepository.settings(ScreenType.INNER)
        .map { it.recents.picker }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SplitPickerLook())

    /**
     * Apps that can go into a split, in the order set (by name, or most used first). Only the
     * "most used" order follows the usage counts (they change with every launch); A–Z doesn't
     * re-sort for them.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val apps: StateFlow<List<AppItem>> = look.map { it.sort }.distinctUntilChanged().flatMapLatest { sort ->
        val usable = appRepository.apps.map { all -> all.filter { !it.isHidden && !it.privateProfile && !it.isArchived } }
        if (sort == SplitPickerLook.SORT_USAGE) {
            combine(usable, appUsageRepository.usage) { apps, usage ->
                val now = System.currentTimeMillis()
                apps.sortedWith(
                    compareByDescending<AppItem> { a -> usage[a.key]?.let { AppUsageRepository.currentScore(it, now) } ?: 0f }
                        .thenBy { it.label.lowercase() },
                )
            }
        } else {
            usable.map { apps -> apps.sortedBy { it.label.lowercase() } }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders: StateFlow<List<DrawerFolderUi>> =
        combine(apps, settingsRepository.drawerFolders) { apps, folders ->
            val byKey = apps.associateBy { it.key }
            folders.map { f -> DrawerFolderUi(f.id, f.name, f.appKeys.mapNotNull { byKey[it] }) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _recents = MutableStateFlow<List<SplitRecentItem>>(emptyList())
    val recents: StateFlow<List<SplitRecentItem>> = _recents.asStateFlow()

    /** The apps the first one was last put beside (newest first), for the "Paired before" row. */
    private val _partners = MutableStateFlow<List<AppItem>>(emptyList())
    val partners: StateFlow<List<AppItem>> = _partners.asStateFlow()

    /** Peek view needs Folark's module in System UI (Android's own peek layout). */
    val peekAvailable: Boolean get() = SplitThree.ready(context)

    /** 33 / 67 % can be offered: the module runs and System UI hasn't moved a size straight back. */
    val sizesAvailable: Boolean get() = SplitThree.ready(context) && SplitThree.sizesHold(context) != false

    /** 33/33/33 can be offered: Android's three-app split through the module. */
    val threeEvenAvailable: Boolean get() = SplitThree.ready(context)

    /**
     * The row of recent apps: the newest open apps but the first one, as many as set. Screenshots
     * only with previews on and root, and only for the apps shown (each is a root call).
     */
    fun loadRecents(firstPackage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val byPkg = appRepository.apps.first().groupBy { it.packageName }
            _partners.value = SplitHistory.partners(context, firstPackage).mapNotNull { byPkg[it]?.firstOrNull() }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val look = settingsRepository.settings(ScreenType.INNER).first().recents.picker
            if (look.recents == SplitPickerLook.RECENTS_OFF) {
                _recents.value = emptyList()
                return@launch
            }
            val byPackage = appRepository.apps.first()
                .filter { !it.isHidden && !it.privateProfile && !it.isArchived }
                .groupBy { it.packageName }
            val picked = OpenTasks.list(context).orEmpty()
                .asSequence()
                .filter { it.packageName != firstPackage && !it.packageName.startsWith(context.packageName) }
                .mapNotNull { task -> byPackage[task.packageName]?.firstOrNull()?.let { task to it } }
                .distinctBy { (_, app) -> app.packageName }
                .take(look.recentsCount)
                .toList()
            val withShots = look.recents == SplitPickerLook.RECENTS_PREVIEWS && RootShell.available()
            _recents.value = picked.map { (task, app) -> SplitRecentItem(task.taskId, app, task.inactiveMs) }
            if (withShots) {
                _recents.value = picked.map { (task, app) ->
                    SplitRecentItem(task.taskId, app, task.inactiveMs, RootTasks.snapshot(task.taskId, task.userId)?.let(::decodeSmall))
                }
            }
        }
    }

    private fun decodeSmall(bytes: ByteArray): ImageBitmap? = runCatching {
        val opts = BitmapFactory.Options().apply {
            inSampleSize = 4
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
    }.getOrNull()

    /** Narrows the split showing now so the first app only peeks in (once the picked app is up). */
    /**
     * Sets the divider so the picked app (opposite [firstOnRight]) gets [share] percent (33 or 66;
     * 50 leaves Android's even split alone), once the split is up. [afterFormingSplit]: the split
     * is still being built, so it waits longer.
     */
    fun snapShareLater(firstOnRight: Boolean, share: Int, afterFormingSplit: Boolean = false) {
        if (share == 50) return
        appScope.launch {
            delay(if (afterFormingSplit) SHARE_AFTER_FORM_MS else PEEK_AFTER_MS)
            // The SNAP_* stops are meant for the left (top) app: the pick is there when the first is on the right.
            val pickedLeft = firstOnRight
            val pickedBig = share > 50
            splitCoordinator.snapTo(if (pickedBig == pickedLeft) SplitThree.SNAP_TWO_THIRDS else SplitThree.SNAP_ONE_THIRD)
        }
    }

    fun snapPeekLater(firstOnRight: Boolean) {
        appScope.launch {
            delay(PEEK_AFTER_MS)
            splitCoordinator.snapPeek(peekOnLeft = !firstOnRight)
        }
    }

    /**
     * Builds the split once the picker has closed: the first app ([firstKey] / [firstPackage], its
     * task [firstTaskId] when known) on [firstOnRight]'s side and [second] on the other, the way the
     * Split engine setting says (see [SplitCoordinator]). When no split can be made, [second] just opens.
     */
    fun formSplit(
        firstPackage: String,
        firstKey: String?,
        firstTaskId: Int?,
        firstOnRight: Boolean,
        second: AppItem,
        peek: Boolean = false,
    ) {
        appScope.launch {
            val all = appRepository.apps.first()
            val first = all.firstOrNull { it.key == firstKey } ?: all.firstOrNull { it.packageName == firstPackage }
            // Let the picker go (its half of the split closes with it) before anything moves.
            delay(PICKER_CLOSE_MS)
            val firstApp = SplitApp(firstPackage, first, firstTaskId)
            val formed = splitCoordinator.pair(
                first = firstApp,
                second = SplitApp(second),
                firstOnRight = firstOnRight,
                peek = peek,
                keepOnUndo = firstApp,
            )
            if (!formed) withContext(Dispatchers.Main) { appRepository.launch(second) }
        }
    }

    /**
     * Three apps: the first left, [second] right, [third] as Android's third pane (Settings ▸
     * Recents ▸ Real three-app split) or else floating over the two.
     */
    fun formThree(firstPackage: String, firstKey: String?, firstTaskId: Int?, second: AppItem, third: AppItem, even: Boolean = false) {
        appScope.launch {
            val all = appRepository.apps.first()
            val first = all.firstOrNull { it.key == firstKey } ?: all.firstOrNull { it.packageName == firstPackage }
            val native = even || settingsRepository.settings(ScreenType.INNER).first().launch.threeAppSplit
            delay(PICKER_CLOSE_MS)
            val got = splitCoordinator.three(SplitApp(firstPackage, first, firstTaskId), SplitApp(second), SplitApp(third), native, even = even)
            withContext(Dispatchers.Main) {
                when (got) {
                    0 -> appRepository.launch(second)
                    2 -> appRepository.launchFloating(third)
                }
            }
        }
    }

    /** Long-press choice: [app] opens on its own (a pop-up when [floating], else full screen), no split. */
    fun popOut(firstPackage: String, firstTaskId: Int?, app: AppItem, floating: Boolean) {
        appScope.launch {
            splitCoordinator.cancelPicker(SplitApp(firstPackage, null, firstTaskId))
            withContext(Dispatchers.Main) { if (floating) appRepository.launchFloating(app) else appRepository.launch(app) }
        }
    }

    fun toggleFavorite(app: AppItem) {
        appScope.launch { settingsRepository.toggleSplitPickerFavorite(app.key) }
    }

    /** [app] was opened in the picker's half: remembered for "Paired before". */
    fun recordPair(firstPackage: String, firstTaskId: Int?, app: AppItem) {
        appScope.launch(Dispatchers.IO) {
            org.arkikeskus.launcher.data.Haptics.play(context, org.arkikeskus.launcher.data.Haptics.Event.SPLIT)
            SplitHistory.record(context, firstPackage, app.packageName)
            splitCoordinator.offerUndo(SplitApp(firstPackage, null, firstTaskId))
        }
    }

    /**
     * An app of another profile was opened from the picker's half; if it opened full screen
     * instead, the split is made again around it (then peek, when chosen).
     */
    fun ensureBeside(firstPackage: String, firstTaskId: Int?, firstOnRight: Boolean, app: AppItem, peek: Boolean) {
        appScope.launch {
            val ok = splitCoordinator.ensurePaired(SplitApp(firstPackage, null, firstTaskId), SplitApp(app), firstOnRight)
            if (ok && peek) {
                delay(PEEK_AFTER_MS)
                splitCoordinator.snapPeek(peekOnLeft = !firstOnRight)
            }
        }
    }

    /** The picker moves itself into the right half, beside the first app. */
    suspend fun dock(pickerTaskId: Int, firstPackage: String, firstTaskId: Int?, firstOnRight: Boolean): Boolean =
        splitCoordinator.dockPicker(pickerTaskId, SplitApp(firstPackage, null, firstTaskId), firstOnRight)

    /** Left without a pick: the half-made split closes again, keeping the first app. */
    fun cancel(firstPackage: String, firstTaskId: Int?) {
        appScope.launch { splitCoordinator.cancelPicker(SplitApp(firstPackage, null, firstTaskId)) }
    }

    private companion object {
        const val PICKER_CLOSE_MS = 300L
        const val PEEK_AFTER_MS = 450L
        /** A split built after the picker closed takes longer to be up before its divider can move. */
        const val SHARE_AFTER_FORM_MS = 1_400L
    }
}
