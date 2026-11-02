package org.arkikeskus.launcher.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.ui.expressive.Accent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette

/**
 * Picks which display's profile the settings below edit. The outer screen and the inner screen keep
 * fully separate profiles; this switcher makes that explicit and lets either one be edited from
 * whichever display the settings are open on. [active] is the display currently in use.
 */
@Composable
fun DisplayProfileSwitcher(
    selected: ScreenType,
    active: ScreenType,
    onSelect: (ScreenType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalExpressivePalette.current
    Surface(
        color = palette.surfaceHi,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = palette.shadow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ScreenType.entries.forEach { type ->
                    val isSelected = type == selected
                    val background by animateColorAsState(
                        targetValue = if (isSelected) Accent else Color.Transparent,
                        animationSpec = tween(220),
                        label = "profileSegment",
                    )
                    val foreground = if (isSelected) Color.White else palette.text
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(background)
                            .clickable(role = Role.Tab) { onSelect(type) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = if (type == ScreenType.OUTER) Icons.Rounded.Smartphone else Icons.Rounded.TabletAndroid,
                            contentDescription = null,
                            tint = foreground,
                            modifier = Modifier.size(22.dp),
                        )
                        Column {
                            Text(
                                text = stringResource(
                                    if (type == ScreenType.OUTER) R.string.settings_profile_outer
                                    else R.string.settings_profile_inner,
                                ),
                                color = foreground,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (type == active) {
                                Text(
                                    text = stringResource(R.string.settings_profile_in_use),
                                    color = foreground.copy(alpha = 0.75f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = stringResource(
                    if (selected == ScreenType.OUTER) R.string.settings_profile_outer_hint
                    else R.string.settings_profile_inner_hint,
                ),
                color = palette.dim,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 6.dp),
            )
        }
    }
}

/** Small pill marking a setting that is shared by both displays (not part of a display profile). */
@Composable
fun ScopeBadge(modifier: Modifier = Modifier) {
    val palette = LocalExpressivePalette.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.btn)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_scope_both),
            color = palette.dim,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
