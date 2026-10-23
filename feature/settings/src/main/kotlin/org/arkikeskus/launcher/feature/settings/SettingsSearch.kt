package org.arkikeskus.launcher.feature.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.arkikeskus.launcher.ui.expressive.LocalAccent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette

/**
 * The title of the setting just picked in the search, while its page opens: that row scrolls into
 * view and glows for a moment ([searchFocus]), then this goes back to null.
 */
internal val LocalSettingsFocus = staticCompositionLocalOf<MutableState<String?>?> { null }

/** A settings row with [title]: brought into view and lit up when the search jumps to it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.searchFocus(title: String): Modifier {
    val focus = LocalSettingsFocus.current ?: return this
    if (focus.value != title) return this
    val accent = LocalAccent.current
    val requester = remember { BringIntoViewRequester() }
    val glow = remember { Animatable(0f) }
    LaunchedEffect(title) {
        delay(260) // the page is laid out first
        requester.bringIntoView()
        glow.animateTo(1f, tween(220))
        delay(1_100)
        glow.animateTo(0f, tween(700))
        if (focus.value == title) focus.value = null
    }
    return this
        .bringIntoViewRequester(requester)
        .drawBehind {
            drawRoundRect(accent.copy(alpha = 0.22f * glow.value), cornerRadius = CornerRadius(14.dp.toPx()))
        }
}

/**
 * Settings found for [query] (every word in the title or description, any order), with the page
 * each is on. Picking one calls [onPick] with its page and title. Returns whether anything matched.
 */
@Composable
internal fun SettingsSearchResults(query: String, onPick: (SettingsCategory, String) -> Unit): Boolean {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return false
    val context = LocalContext.current
    val palette = LocalExpressivePalette.current
    val found = remember(words) {
        SETTINGS_SEARCH_INDEX.mapNotNull { entry ->
            val title = runCatching { context.getString(entry.title) }.getOrNull() ?: return@mapNotNull null
            val sub = if (entry.subtitle != 0) runCatching { context.getString(entry.subtitle) }.getOrNull().orEmpty() else ""
            val hay = "$title $sub".lowercase()
            if (words.all { it in hay }) Triple(entry, title, sub) else null
        }
            // Title matches first, then by how early the first word shows up in the title.
            .sortedWith(compareBy({ !it.second.lowercase().contains(words.first()) }, { it.second.lowercase().indexOf(words.first()).let { i -> if (i < 0) 999 else i } }))
            .distinctBy { it.second to it.first.category }
            .take(MAX_RESULTS)
    }
    if (found.isEmpty()) return false
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            context.getString(R.string.settings_search_found),
            color = palette.faint, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp, top = 6.dp),
        )
        found.forEach { (entry, title, sub) ->
            Surface(
                onClick = { onPick(entry.category, title) },
                color = palette.surfaceHi,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (sub.isNotBlank()) {
                        Text(sub, color = palette.faint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        context.getString(entry.category.titleRes),
                        color = LocalAccent.current, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
    return true
}

private const val MAX_RESULTS = 40
