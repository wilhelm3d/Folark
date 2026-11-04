package org.arkikeskus.launcher.feature.appdrawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.ui.AppShortcuts

/** At most this many shortcut chips under the top search result. */
private const val MAX_CHIPS = 4

/**
 * The top search result's own shortcuts ("New message", "New tab", "Scan") as a row of chips right
 * under it; a tap starts the shortcut and closes the drawer. Nothing shows for an app without any
 * (or before Folark is the default home app, which Android requires for reading shortcuts).
 */
@Composable
internal fun TopAppShortcutChips(app: AppItem, onOpened: () -> Unit) {
    val context = LocalContext.current
    var items by remember(app.key) { mutableStateOf<List<AppShortcuts.Item>>(emptyList()) }
    LaunchedEffect(app.key) {
        items = withContext(Dispatchers.IO) { AppShortcuts.query(context, app).take(MAX_CHIPS) }
    }
    val tint = LocalContentColor.current
    AnimatedVisibility(visible = items.isNotEmpty(), enter = fadeIn() + expandVertically()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 56.dp, end = 12.dp, top = 2.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { item ->
                Text(
                    text = item.label,
                    color = tint,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(tint.copy(alpha = 0.12f))
                        .clickable {
                            AppShortcuts.start(context, item)
                            onOpened()
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}
