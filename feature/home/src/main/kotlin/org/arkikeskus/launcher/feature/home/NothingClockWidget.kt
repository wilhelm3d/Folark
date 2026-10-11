package org.arkikeskus.launcher.feature.home

import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import org.arkikeskus.launcher.ui.component.liquidGlass
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.launcher.system.BatteryMonitor
import org.arkikeskus.launcher.model.BatteryStatus
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class NothingClockViewModel @Inject constructor(
    batteryMonitor: BatteryMonitor,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val status: StateFlow<BatteryStatus?> = batteryMonitor.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Settings ▸ Folark widgets ▸ clock style and text colour (both displays). */
    val settings: StateFlow<LauncherSettings> = settingsRepository.settings(ScreenType.OUTER)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherSettings())
}

/** What sets one clock style apart: its type, where the date goes and how things line up. */
private class ClockLook(
    val family: FontFamily? = null,
    val hourWeight: FontWeight = FontWeight.Medium,
    val minuteWeight: FontWeight = FontWeight.Medium,
    /** The minutes take the accent colour. */
    val accentMinutes: Boolean = false,
    val infoWeight: FontWeight = FontWeight.Medium,
    val upperCaseInfo: Boolean = false,
    /** Date and battery sit above the time instead of below it, and never beside it. */
    val infoAbove: Boolean = false,
    val centred: Boolean = true,
    val redDot: Boolean = false,
    /** Width of one character relative to the font size, for fitting the time to the widget. */
    val charWidth: Float = 0.6f,
    val isGlassPill: Boolean = false,
    val isOutline: Boolean = false,
    val isVerticalStack: Boolean = false,
    val isNeumorphic: Boolean = false,
)

private fun clockLook(style: String): ClockLook = when (style) {
    // Bold hours against thin, accent-coloured minutes, set to the left.
    LauncherSettings.CLOCK_STYLE_DUO -> ClockLook(
        hourWeight = FontWeight.Bold, minuteWeight = FontWeight.ExtraLight, accentMinutes = true,
        infoWeight = FontWeight.Normal, centred = false,
    )
    // One light weight throughout, set to the left.
    LauncherSettings.CLOCK_STYLE_LIGHT -> ClockLook(
        hourWeight = FontWeight.Light, minuteWeight = FontWeight.Light, infoWeight = FontWeight.Normal,
        centred = false, charWidth = 0.56f,
    )
    // Heavy monospaced digits, capitals and a red dot.
    LauncherSettings.CLOCK_STYLE_DOT -> ClockLook(
        family = FontFamily.Monospace, hourWeight = FontWeight.ExtraBold, minuteWeight = FontWeight.ExtraBold,
        infoWeight = FontWeight.Bold, upperCaseInfo = true, redDot = true, charWidth = 0.62f,
    )
    // The date above a large, semi-bold time, centred.
    LauncherSettings.CLOCK_STYLE_STACKED -> ClockLook(
        hourWeight = FontWeight.SemiBold, minuteWeight = FontWeight.SemiBold, infoAbove = true,
    )
    LauncherSettings.CLOCK_STYLE_GLASS_PILL -> ClockLook(
        hourWeight = FontWeight.Bold, minuteWeight = FontWeight.Medium, accentMinutes = true,
        isGlassPill = true, centred = true,
    )
    LauncherSettings.CLOCK_STYLE_OUTLINE -> ClockLook(
        hourWeight = FontWeight.Bold, minuteWeight = FontWeight.Light,
        isOutline = true, centred = true,
    )
    LauncherSettings.CLOCK_STYLE_VERTICAL_STACK -> ClockLook(
        hourWeight = FontWeight.Black, minuteWeight = FontWeight.Black,
        isVerticalStack = true, centred = true, charWidth = 0.50f,
    )
    LauncherSettings.CLOCK_STYLE_NEUMORPHIC -> ClockLook(
        hourWeight = FontWeight.Bold, minuteWeight = FontWeight.Bold,
        isNeumorphic = true, centred = true,
    )
    LauncherSettings.CLOCK_STYLE_NOTHING_NDOT -> ClockLook(
        family = org.arkikeskus.launcher.ui.theme.LauncherFontFamily.NDot,
        hourWeight = FontWeight.Bold, minuteWeight = FontWeight.Bold,
        accentMinutes = true, redDot = true, upperCaseInfo = true, charWidth = 0.65f,
    )
    else -> ClockLook()
}

/**
 * The digital clock widget: the time as large as its cells allow, with the date and the phone's
 * battery. It has no card of its own, only text on the wallpaper, in white or dark, and comes in
 * several styles (Settings ▸ Folark widgets). Resize the widget and the text resizes with it: a
 * wide widget puts the date and battery beside the time so the whole width is used, a tall one
 * puts them above or below it. A tap opens the alarms.
 *
 * (It keeps the name and widget type of the dot-matrix clock it replaced, so a clock that was
 * already on a home screen simply becomes this one.)
 */
@Composable
fun NothingClockWidget(
    modifier: Modifier = Modifier,
    viewModel: NothingClockViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val darkText = settings.clockWidgetDarkText
    val look = remember(settings.clockWidgetStyle) { clockLook(settings.clockWidgetStyle) }
    val noIndication = remember { MutableInteractionSource() }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Only hours and minutes are shown: one tick a minute, and none while the launcher is hidden.
    MinuteTicker { now = it }
    // Rebuilt each minute, so a changed time zone, language or 12/24-hour setting shows at once.
    // [time, weekday, day and month, the short one-line date]
    val texts = remember(now, look) {
        val locale = Locale.getDefault()
        val date = Date(now)
        fun format(pattern: String) = SimpleDateFormat(pattern, locale).format(date)
        fun best(skeleton: String) = format(DateFormat.getBestDateTimePattern(locale, skeleton))
        listOf(
            format(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"),
            format("EEEE"),
            best("dMMMM"),
            best("EEEdMMM"),
        ).map { if (look.upperCaseInfo) it.uppercase(locale) else it }
    }
    val timeString = texts[0]

    val ink = if (darkText) Color(0xFF16181C) else Color.White
    val useGlassCard = settings.clockWidgetGlassEffect || look.isGlassPill || look.isNeumorphic
    // Enhanced glass text halo shadow for glass depth
    val halo = Shadow(
        color = (if (darkText) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.5f)),
        blurRadius = if (useGlassCard) 14f else 8f,
        offset = androidx.compose.ui.geometry.Offset(0f, 2f),
    )
    // Settings ▸ Folark widgets ▸ Clock minutes: a fixed shade from white to black, or what the
    // style uses; and how opaque they are.
    val minuteBase = when (settings.clockMinuteColor) {
        LauncherSettings.CLOCK_MINUTE_WHITE -> Color.White
        LauncherSettings.CLOCK_MINUTE_LIGHT_GREY -> Color(0xFFC4C7CC)
        LauncherSettings.CLOCK_MINUTE_GREY -> Color(0xFF8A8E95)
        LauncherSettings.CLOCK_MINUTE_DARK_GREY -> Color(0xFF4A4D52)
        LauncherSettings.CLOCK_MINUTE_BLACK -> Color(0xFF0E0F11)
        else -> if (look.accentMinutes) MaterialTheme.colorScheme.primary else ink
    }
    val minuteColor = minuteBase.copy(alpha = minuteBase.alpha * settings.clockMinuteOpacity.coerceIn(0.1f, 1f))
    val density = LocalDensity.current
    val timeText = buildAnnotatedString {
        val parts = timeString.split(':')
        withStyle(SpanStyle(fontWeight = look.hourWeight)) { append(parts.first()) }
        if (parts.size > 1) {
            withStyle(SpanStyle(fontWeight = look.minuteWeight, color = minuteColor)) { append(":" + parts[1]) }
        }
    }

    val containerShape = RoundedCornerShape(28.dp)
    val clockModifier = modifier
        .then(
            if (useGlassCard) {
                Modifier.liquidGlass(
                    shape = containerShape,
                    tint = Color.Black.copy(alpha = 0.35f),
                    highlight = 0.85f,
                    rimWidth = 1.5.dp,
                    rimBottom = 0.55f,
                )
            } else Modifier
        )
        .clickable(interactionSource = noIndication, indication = null) {
            runCatching {
                context.startActivity(
                    Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        .padding(horizontal = 12.dp, vertical = 6.dp)

    BoxWithConstraints(
        modifier = clockModifier,
        contentAlignment = Alignment.Center,
    ) {
        val battery = status
        val boxW = maxWidth.value
        val boxH = maxHeight.value
        // Much wider than tall (an 8×3 on the unfolded screen): the time takes the full height on one
        // side and the date and battery fill the other, so the whole widget is used.
        val beside = !look.infoAbove && boxW > boxH * 2.3f && boxH >= 60f

        @Composable
        fun Line(text: String, sizeDp: Float, alpha: Float = 0.9f) {
            Text(
                text = text,
                color = ink.copy(alpha = alpha),
                maxLines = 1,
                softWrap = false,
                style = TextStyle(
                    fontSize = with(density) { sizeDp.dp.toSp() },
                    fontFamily = look.family,
                    fontWeight = look.infoWeight,
                    shadow = halo,
                ),
            )
        }

        @Composable
        fun Battery(sizeDp: Float) {
            if (battery == null) return
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (look.redDot) {
                    Box(Modifier.size((sizeDp * 0.5f).dp).background(Color(0xFFDA291C), CircleShape))
                    Spacer(Modifier.width((sizeDp * 0.35f).dp))
                } else {
                    Icon(
                        painter = painterResource(if (battery.charging) R.drawable.ic_status_charging else R.drawable.ic_stat_battery),
                        contentDescription = stringResource(R.string.battery_widget_name),
                        tint = ink.copy(alpha = 0.9f),
                        modifier = Modifier.size((sizeDp * 1.05f).dp),
                    )
                }
                Line("${battery.percent}%", sizeDp)
            }
        }

        @Composable
        fun Time(sizeDp: Float) {
            val size = with(density) { sizeDp.dp.toSp() }
            Text(
                text = timeText,
                color = ink,
                maxLines = 1,
                softWrap = false,
                style = TextStyle(
                    fontSize = size,
                    lineHeight = size * 1.04f,
                    fontFamily = look.family,
                    fontFeatureSettings = "tnum",
                    shadow = halo,
                ),
            )
        }

        if (beside) {
            // Time: as tall as the widget, within about two thirds of its width.
            val timeDp = minOf(boxH / 1.1f, boxW * 0.64f / (timeString.length * look.charWidth)).coerceAtLeast(14f)
            val longest = maxOf(texts[1].length, texts[2].length, 5)
            val infoDp = minOf(boxH / 3f / 1.25f, boxW * 0.32f / (longest * 0.56f), timeDp * 0.36f).coerceAtLeast(10f)
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Time(timeDp)
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                    Line(texts[1], infoDp, alpha = 1f)
                    Line(texts[2], infoDp)
                    Battery(infoDp)
                }
            }
        } else {
            val showInfo = boxH >= 54f
            val timeDp = minOf(
                boxW / (timeString.length * look.charWidth),
                boxH * (if (showInfo) 0.72f else 0.96f) / 1.1f,
            ).coerceAtLeast(14f)
            val infoChars = texts[3].length + (if (battery != null) 7 else 0)
            val infoDp = minOf(timeDp * 0.27f, boxW / (infoChars * 0.56f), 30f).coerceAtLeast(10f)

            @Composable
            fun Info() {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Line(texts[3], infoDp)
                    if (battery != null) {
                        Spacer(Modifier.width((infoDp * 0.8f).dp))
                        Battery(infoDp)
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = if (look.centred) Alignment.CenterHorizontally else Alignment.Start,
                verticalArrangement = Arrangement.Center,
            ) {
                if (showInfo && look.infoAbove) Info()
                Time(timeDp)
                if (showInfo && !look.infoAbove) Info()
            }
        }
    }
}
