package org.arkikeskus.launcher.feature.home

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.IconPackInfo
import org.arkikeskus.launcher.data.IconPackRepository
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.catching
import javax.inject.Inject

/**
 * The "Change icon" picker for one app (from a menu on home, the dock or the drawer; split out of
 * HomeViewModel): the installed icon packs and their icons, the app's own shape, and saving the
 * choice, which then shows wherever the app does.
 */
@HiltViewModel
class IconPickerViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val iconPackRepository: IconPackRepository,
) : ViewModel() {

    /** The installed icon packs the picker can choose from (queried off the main thread). */
    val iconPacks: StateFlow<List<IconPackInfo>> =
        flow { emit(withContext(Dispatchers.Default) { iconPackRepository.installedPacks() }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /** Hand-picked icons, app key → "pack|drawable" — the picker shows "use default" for these. */
    val customIcons: StateFlow<Map<String, String>> = settingsRepository.customIcons
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    /** Per-app icon shapes, app key → shape, for the picker's shape row. */
    val iconShapes: StateFlow<Map<String, String>> = settingsRepository.iconShapes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    /** Decoded picker icons, "pack|drawable" → bitmap (about 50 KB each at [ICON_PICKER_PX]). */
    private val iconPickerCache = android.util.LruCache<String, ImageBitmap>(ICON_PICKER_CACHE_SIZE)

    /** The names of the icons [pack] offers (parsing the pack is slow the first time). */
    suspend fun iconPackDrawableNames(pack: String): List<String> = withContext(Dispatchers.Default) {
        iconPackRepository.get(pack)?.drawableNames().orEmpty()
    }

    /** One of [pack]'s icons as a small bitmap for the picker grid; null if it cannot be drawn. */
    suspend fun iconPackBitmap(pack: String, drawable: String): ImageBitmap? = withContext(Dispatchers.Default) {
        val cacheKey = "$pack|$drawable"
        // Scrolling the grid back and forth re-asks for the same icons; decode each one once.
        iconPickerCache.get(cacheKey)?.let { return@withContext it }
        catching {
            iconPackRepository.get(pack)?.getDrawable(drawable)?.let { icon ->
                val bitmap = android.graphics.Bitmap.createBitmap(
                    ICON_PICKER_PX, ICON_PICKER_PX, android.graphics.Bitmap.Config.ARGB_8888,
                )
                icon.setBounds(0, 0, ICON_PICKER_PX, ICON_PICKER_PX)
                icon.draw(android.graphics.Canvas(bitmap))
                bitmap.asImageBitmap().also { iconPickerCache.put(cacheKey, it) }
            }
        }.getOrNull()
    }

    /** Gives [appItem] the icon [drawable] of [pack], everywhere it is shown. */
    fun setCustomIcon(appItem: AppItem, pack: String, drawable: String) = viewModelScope.launch {
        settingsRepository.setCustomIcon(appItem.key, "$pack|$drawable")
    }

    /** Gives [appItem] its own icon [shape]; null follows the global shape again. */
    fun setIconShape(appItem: AppItem, shape: String?) = viewModelScope.launch {
        settingsRepository.setIconShape(appItem.key, shape)
    }

    /** Returns [appItem] to its normal icon. */
    fun clearCustomIcon(appItem: AppItem) = viewModelScope.launch {
        settingsRepository.setCustomIcon(appItem.key, null)
    }

    private companion object {
        /** Rasterization size (px) of an icon in the picker grid. */
        const val ICON_PICKER_PX = 112

        /** How many decoded picker icons are kept (a few screens of the grid). */
        const val ICON_PICKER_CACHE_SIZE = 240
    }
}
