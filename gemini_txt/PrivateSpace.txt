package org.arkikeskus.launcher.feature.appdrawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.drawer.DrawerColors

/** The drawer's Private Space: locked or not, and its apps (empty while locked). */
@Immutable
data class PrivateSpaceUi(val locked: Boolean, val apps: List<AppItem>)

/** [ui] plus what the section's buttons do (provided by AppDrawerScreen). */
@Immutable
internal class PrivateSpaceDrawer(
    val ui: PrivateSpaceUi,
    val onToggleLock: () -> Unit,
    val onInstall: (() -> Unit)?,
    val onOpen: (AppItem) -> Unit,
)

/** The Private Space section at the end of the "All" list; null hides it. */
internal val LocalPrivateSpace = staticCompositionLocalOf<PrivateSpaceDrawer?> { null }

/**
 * Grid drawer: the Private Space header across the row, then its apps while unlocked. They only
 * open on a tap: no long-press menu or drag, so a private app is never copied onto home, the dock
 * or a folder (Android asks launchers to keep them in the drawer).
 */
internal fun LazyGridScope.privateSpaceCells(space: PrivateSpaceDrawer, colors: DrawerColors, showLabels: Boolean, iconSize: Dp) {
    item(key = "private-space", span = { GridItemSpan(maxLineSpan) }, contentType = "private-header") {
        PrivateSpaceHeader(space, colors)
    }
    items(space.ui.apps.size, key = { "private/" + space.ui.apps[it].key }, contentType = { "private-app" }) { index ->
        val app = space.ui.apps[index]
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { space.onOpen(app) },
        ) {
            AppIcon(
                appItem = app,
                labelColor = colors.content,
                iconSize = iconSize,
                showLabel = showLabels,
                aquamorphicTouchEnabled = false,
            )
        }
    }
}

/** List drawer: the same section as rows. */
internal fun LazyListScope.privateSpaceRows(space: PrivateSpaceDrawer, colors: DrawerColors) {
    item(key = "private-space", contentType = "private-header") { PrivateSpaceHeader(space, colors) }
    items(space.ui.apps.size, key = { "private/" + space.ui.apps[it].key }, contentType = { "private-app" }) { index ->
        val app = space.ui.apps[index]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { space.onOpen(app) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            AppIcon(appItem = app, labelColor = colors.content, iconSize = 40.dp, showLabel = false, aquamorphicTouchEnabled = false)
            Text(app.label, color = colors.content, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PrivateSpaceHeader(space: PrivateSpaceDrawer, colors: DrawerColors) {
    val locked = space.ui.locked
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.field),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = space.onToggleLock)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.18f)),
            ) {
                Icon(
                    imageVector = if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.drawer_private_space),
                    color = colors.content,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(if (locked) R.string.drawer_private_space_locked else R.string.drawer_private_space_unlocked),
                    color = colors.contentDim,
                    fontSize = 12.5.sp,
                )
            }
            Text(
                stringResource(if (locked) R.string.drawer_private_space_unlock else R.string.drawer_private_space_lock),
                color = colors.onAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.accent)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
        val install = space.onInstall
        if (!locked && install != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = install)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.drawer_private_space_install), color = colors.accent, fontSize = 13.5.sp)
            }
        }
        if (!locked && space.ui.apps.isEmpty()) {
            Text(
                stringResource(R.string.drawer_private_space_empty),
                color = colors.contentDim,
                fontSize = 12.5.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
    }
}
