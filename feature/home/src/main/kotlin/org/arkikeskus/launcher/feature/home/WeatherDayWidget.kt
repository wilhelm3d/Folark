package org.arkikeskus.launcher.feature.home

import org.arkikeskus.launcher.data.MotoLinks
import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.smartspace.CurrentWeather
import org.arkikeskus.launcher.data.smartspace.DayForecast
import org.arkikeskus.launcher.data.smartspace.HourForecast
import org.arkikeskus.launcher.data.smartspace.WeatherRepository
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.ui.component.LocalHomeCovered
import org.arkikeskus.launcher.ui.component.LocalBatterySaver
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@HiltViewModel
class WeatherDayViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val weather: StateFlow<CurrentWeather?> = weatherRepository.weather
    val hasPermission = MutableStateFlow(weatherRepository.hasPermission())
    val unavailable: StateFlow<Boolean> = weatherRepository.unavailable
    val settings: StateFlow<LauncherSettings> = settingsRepository.settings(ScreenType.OUTER)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), LauncherSettings())

    /** Re-checks the permission and asks for fresh weather (the repository rate-limits itself;
     *  [force] is for a tap, which tries again even right after a failed attempt). */
    fun refresh(force: Boolean = false) {
        hasPermission.value = weatherRepository.hasPermission()
        weatherRepository.refresh(force)
    }
}

/** What the sky is doing, which picks both the backdrop colours and the animation. */
private enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, STORM }

private fun skyOf(code: Int): Sky = when (code) {
    0 -> Sky.CLEAR
    1, 2 -> Sky.PARTLY
    45, 48 -> Sky.FOG
    in 51..67, in 80..82 -> Sky.RAIN
    in 71..77, 85, 86 -> Sky.SNOW
    in 95..99 -> Sky.STORM
    else -> Sky.CLOUDY
}

private fun glyph(code: Int, isDay: Boolean): String = when (skyOf(code)) {
    Sky.CLEAR -> if (isDay) "☀️" else "🌙"
    Sky.PARTLY -> if (isDay) "🌤️" else "☁️"
    Sky.CLOUDY -> "☁️"
    Sky.FOG -> "🌫️"
    Sky.RAIN -> "🌧️"
    Sky.SNOW -> "🌨️"
    Sky.STORM -> "⛈️"
}

/** Top and bottom colour of the backdrop; drawn translucent so the wallpaper still shows through. */
private fun skyColors(sky: Sky, isDay: Boolean): Pair<Color, Color> = when {
    sky == Sky.STORM -> Color(0xFF2B2F4A) to Color(0xFF14162A)
    sky == Sky.RAIN -> if (isDay) Color(0xFF4A6FA5) to Color(0xFF2C3E63) else Color(0xFF26324F) to Color(0xFF121A30)
    sky == Sky.SNOW -> if (isDay) Color(0xFF8FB3D9) to Color(0xFF5E7FA8) else Color(0xFF3A4C6E) to Color(0xFF1B2540)
    sky == Sky.FOG || sky == Sky.CLOUDY -> if (isDay) Color(0xFF7D93AD) to Color(0xFF4F6078) else Color(0xFF333D52) to Color(0xFF181D2B)
    !isDay -> Color(0xFF1C2452) to Color(0xFF0A0D24)
    sky == Sky.PARTLY -> Color(0xFF4F9BE8) to Color(0xFF7FB7EA)
    else -> Color(0xFF2E8FEF) to Color(0xFFF2A65A)
}

private val WeatherShape = RoundedCornerShape(24.dp)
private val TextShadow = Shadow(color = Color.Black.copy(alpha = 0.45f), blurRadius = 6f)

/**
 * The weather day widget. One widget for every size: a one-row strip shows the temperature, two
 * rows add the hours ahead, and the big size adds the detail chips, a temperature curve and the
 * coming days. Always in °C. The card itself is see-through: only the information and the animated
 * conditions are drawn over the wallpaper.
 */
@Composable
fun WeatherDayWidget(modifier: Modifier = Modifier, viewModel: WeatherDayViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val unavailable by viewModel.unavailable.collectAsStateWithLifecycle()
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        resumed = true
        viewModel.refresh()
        onPauseOrDispose { resumed = false }
    }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh(force = true) }

    val current = weather
    val forecast = current?.forecast
    val isDay = forecast?.isDay ?: (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 6..19)
    val sky = current?.let { skyOf(it.weatherCode) } ?: Sky.CLOUDY
    val (top, bottom) = skyColors(sky, isDay)
    // See-through by default: only the information and the animation are drawn. Settings ▸ Widget
    // background brings the weather-coloured card back, up to a translucent wash at 100%.
    val tint = 0.5f * LocalWidgetBackgroundAlpha.current
    fun deg(celsius: Double): String = celsius.roundToInt().toString() + "°"

    // Animation clock: about 30 frames a second, only while the home screen is in front and the
    // widget is actually inside the window (its page may be scrolled away, yet still composed).
    val time = remember { mutableFloatStateOf(0f) }
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // Not while the drawer covers the home screen, and not in battery saver (the sky then stands still).
    val animate = settings.widgets.weatherAnimated && resumed && current != null &&
        !LocalBatterySaver.current && !LocalHomeCovered.current
    // Rain, snow and storms move fast and get about 30 frames a second; sun, stars, clouds and fog
    // drift slowly and look the same at about 15.
    val frameMs = if (sky == Sky.RAIN || sky == Sky.SNOW || sky == Sky.STORM) 33L else 66L
    LaunchedEffect(animate, frameMs) {
        if (!animate) return@LaunchedEffect
        val start = System.nanoTime()
        while (true) {
            val where = placed[0]
            val onScreen = where != null && where.isAttached && !where.boundsInWindow().isEmpty
            if (onScreen) {
                time.floatValue = (System.nanoTime() - start) / 1_000_000_000f
                delay(frameMs)
            } else {
                delay(300)
            }
        }
    }

    val noIndication = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .onGloballyPositioned { placed[0] = it }
            .clip(WeatherShape)
            .background(Brush.verticalGradient(listOf(top.copy(alpha = tint), bottom.copy(alpha = tint))))
            .border(1.dp, Color.White.copy(alpha = 0.28f * tint), WeatherShape)
            .clickable(interactionSource = noIndication, indication = null) {
                if (!hasPermission) {
                    runCatching { askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                } else if (current == null) {
                    viewModel.refresh(force = true)
                } else if (!(settings.widgets.motoWeatherTap && MotoLinks.open(context, MotoLinks.WEATHER))) {
                    runCatching {
                        val query = Uri.encode("weather " + current?.city.orEmpty())
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            },
    ) {
        if (settings.widgets.weatherAnimated && current != null) {
            Canvas(Modifier.fillMaxSize().graphicsLayer()) { drawSky(sky, isDay, time.floatValue) }
        }
        if (current == null) {
            Text(
                text = stringResource(
                    when {
                        !hasPermission -> R.string.smartspace_allow_location
                        unavailable -> R.string.weather_unavailable
                        else -> R.string.weather_loading
                    },
                ),
                color = Color.White,
                style = TextStyle(fontSize = 13.sp, shadow = TextShadow),
                modifier = Modifier.align(Alignment.Center).padding(12.dp),
            )
            return@Box
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
            val wide = maxWidth >= 190.dp
            val showHours = maxHeight >= 120.dp && forecast != null && forecast.hours.isNotEmpty()
            val big = maxHeight >= 250.dp && forecast != null
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                // --- Now -------------------------------------------------------------------------
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = deg(current.temperatureC),
                        color = Color.White,
                        style = TextStyle(fontSize = if (big) 54.sp else if (showHours) 40.sp else 30.sp, fontWeight = FontWeight.Light, shadow = TextShadow),
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = glyph(current.weatherCode, isDay), fontSize = if (big) 30.sp else 22.sp)
                    if (wide) {
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            current.city?.let {
                                Text(
                                    text = it,
                                    color = Color.White,
                                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, shadow = TextShadow),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            val high = forecast?.highC
                            val low = forecast?.lowC
                            if (high != null && low != null) {
                                Text(
                                    text = "↑" + deg(high) + "  ↓" + deg(low),
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = TextStyle(fontSize = 12.sp, shadow = TextShadow),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                // --- Detail chips (big) ----------------------------------------------------------
                if (big && forecast != null) {
                    val chips = buildList {
                        forecast.feelsLikeC?.let { add(stringResource(R.string.weather_feels) + " " + deg(it)) }
                        forecast.rainChance?.let { add(stringResource(R.string.weather_rain) + " $it%") }
                        forecast.windKmh?.let { add(stringResource(R.string.weather_wind) + " ${it.roundToInt()} km/h") }
                        forecast.uvIndex?.let { add("UV ${it.roundToInt()}") }
                        forecast.humidity?.let { add(stringResource(R.string.weather_humidity) + " $it%") }
                        (if (isDay) forecast.sunset else forecast.sunrise)?.let {
                            add(stringResource(if (isDay) R.string.weather_sunset else R.string.weather_sunrise) + " $it")
                        }
                    }
                    val fit = (this@BoxWithConstraints.maxWidth / 104.dp).toInt().coerceAtLeast(1)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        chips.take(fit).forEach { chip ->
                            Text(
                                text = chip,
                                color = Color.White,
                                fontSize = 11.sp,
                                maxLines = 1,
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                // --- Hours ahead -----------------------------------------------------------------
                if (showHours && forecast != null) {
                    val count = (this@BoxWithConstraints.maxWidth / 46.dp).toInt().coerceIn(3, 12)
                    val hours = forecast.hours.take(count)
                    Column(Modifier.fillMaxWidth()) {
                        if (big) TemperatureCurve(hours, Modifier.fillMaxWidth().height(34.dp))
                        Row(Modifier.fillMaxWidth()) {
                            hours.forEachIndexed { index, hour ->
                                HourColumn(hour, if (index == 0) stringResource(R.string.weather_now) else "%02d".format(hour.hour), deg(hour.temperatureC), Modifier.weight(1f))
                            }
                        }
                    }
                }
                // --- Days ahead (big) ------------------------------------------------------------
                if (big && forecast != null) {
                    val rows = ((this@BoxWithConstraints.maxHeight - 230.dp) / 26.dp).toInt().coerceIn(0, 5)
                    val days = forecast.days.take(rows)
                    if (days.isNotEmpty()) {
                        val low = days.minOf { it.lowC }
                        val high = days.maxOf { it.highC }
                        Column(Modifier.fillMaxWidth()) {
                            days.forEachIndexed { index, day ->
                                DayRow(day, index == 0, low, high, deg(day.lowC), deg(day.highC))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HourColumn(hour: HourForecast, label: String, temperature: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1)
        Text(text = glyph(hour.weatherCode, hour.isDay), fontSize = 15.sp)
        Text(text = temperature, color = Color.White, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, shadow = TextShadow), maxLines = 1)
        if (hour.rainChance >= 20) {
            Text(text = "${hour.rainChance}%", color = Color(0xFFBFE0FF), fontSize = 9.sp, maxLines = 1)
        }
    }
}

/** A smooth line through the hourly temperatures, each point centred over its column. */
@Composable
private fun TemperatureCurve(hours: List<HourForecast>, modifier: Modifier) {
    Canvas(modifier) {
        if (hours.size < 2) return@Canvas
        val low = hours.minOf { it.temperatureC }
        val span = (hours.maxOf { it.temperatureC } - low).coerceAtLeast(1.0)
        val pad = 6.dp.toPx()
        val step = size.width / hours.size
        fun point(i: Int) = Offset(
            x = step * (i + 0.5f),
            y = pad + (size.height - 2 * pad) * (1f - ((hours[i].temperatureC - low) / span).toFloat()),
        )
        val path = Path().apply {
            val first = point(0)
            moveTo(first.x, first.y)
            for (i in 1 until hours.size) {
                val a = point(i - 1)
                val b = point(i)
                val mid = (a.x + b.x) / 2f
                cubicTo(mid, a.y, mid, b.y, b.x, b.y)
            }
        }
        drawPath(path, Color.White.copy(alpha = 0.85f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        for (i in hours.indices) drawCircle(Color.White, radius = 2.5.dp.toPx(), center = point(i))
    }
}

@Composable
private fun DayRow(day: DayForecast, today: Boolean, rangeLow: Double, rangeHigh: Double, low: String, high: String) {
    val name = if (today) stringResource(R.string.weather_today) else remember(day.date) {
        runCatching {
            LocalDate.parse(day.date).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
        }.getOrDefault(day.date.takeLast(5))
    }
    Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = name, color = Color.White, fontSize = 12.sp, maxLines = 1, modifier = Modifier.width(52.dp))
        Text(text = glyph(day.weatherCode, true), fontSize = 13.sp, modifier = Modifier.width(26.dp))
        Text(
            text = if (day.rainChance >= 20) "${day.rainChance}%" else "",
            color = Color(0xFFBFE0FF),
            fontSize = 10.sp,
            maxLines = 1,
            modifier = Modifier.width(32.dp),
        )
        Text(text = low, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1, modifier = Modifier.width(30.dp))
        // Where this day's range sits within the coldest and warmest of the days shown.
        Canvas(Modifier.weight(1f).height(5.dp)) {
            val span = (rangeHigh - rangeLow).coerceAtLeast(1.0)
            val from = ((day.lowC - rangeLow) / span).toFloat().coerceIn(0f, 1f) * size.width
            val to = ((day.highC - rangeLow) / span).toFloat().coerceIn(0f, 1f) * size.width
            val y = size.height / 2f
            drawLine(Color.White.copy(alpha = 0.2f), Offset(0f, y), Offset(size.width, y), size.height, StrokeCap.Round)
            drawLine(
                Brush.horizontalGradient(listOf(Color(0xFF8FD3FF), Color(0xFFFFC46B)), from, to.coerceAtLeast(from + 1f)),
                Offset(from, y), Offset(to.coerceAtLeast(from + 1f), y), size.height, StrokeCap.Round,
            )
        }
        Text(
            text = high,
            color = Color.White,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp).width(30.dp),
        )
    }
}

// --- The animated sky ----------------------------------------------------------------------------

/** A repeatable pseudo-random number in 0..1 for particle [i] (no allocation, same every frame). */
private fun rnd(i: Int, salt: Float): Float {
    val v = sin(i * 12.9898f + salt * 78.233f) * 43758.547f
    return v - kotlin.math.floor(v)
}

private fun DrawScope.drawSky(sky: Sky, isDay: Boolean, t: Float) {
    when (sky) {
        Sky.CLEAR -> if (isDay) drawSun(t) else { drawStars(t, 34); drawMoon() }
        Sky.PARTLY -> {
            if (isDay) drawSun(t) else { drawStars(t, 18); drawMoon() }
            drawClouds(t, 2, 0.20f)
        }
        Sky.CLOUDY -> drawClouds(t, 4, 0.17f)
        Sky.FOG -> drawFog(t)
        Sky.RAIN -> { drawClouds(t, 3, 0.13f); drawRain(t, 38) }
        Sky.SNOW -> { drawClouds(t, 2, 0.12f); drawSnow(t, 30) }
        Sky.STORM -> { drawClouds(t, 4, 0.14f); drawRain(t, 56); drawLightning(t) }
    }
}

private fun DrawScope.drawSun(t: Float) {
    val center = Offset(size.width * 0.84f, size.height * 0.22f)
    val radius = minOf(size.width, size.height) * 0.16f
    val pulse = 1f + 0.06f * sin(t * 1.3f)
    // The glow is three plain translucent discs: a gradient here would build a new shader every frame.
    drawCircle(Color(0x14FFE08A), radius * 3.2f * pulse, center)
    drawCircle(Color(0x1CFFE08A), radius * 2.3f * pulse, center)
    drawCircle(Color(0x26FFE08A), radius * 1.6f * pulse, center)
    val rays = 12
    for (i in 0 until rays) {
        val angle = (t * 0.18f + i * (2f * PI.toFloat() / rays))
        val inner = radius * 1.35f
        val outer = radius * (1.75f + 0.18f * sin(t * 2f + i))
        drawLine(
            Color(0x88FFE9A8),
            Offset(center.x + cos(angle) * inner, center.y + sin(angle) * inner),
            Offset(center.x + cos(angle) * outer, center.y + sin(angle) * outer),
            strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
        )
    }
    drawCircle(Color(0xCCFFD766), radius, center)
}

private fun DrawScope.drawMoon() {
    val center = Offset(size.width * 0.84f, size.height * 0.22f)
    val radius = minOf(size.width, size.height) * 0.12f
    drawCircle(Color(0x10DCE6FF), radius * 3f, center)
    drawCircle(Color(0x18DCE6FF), radius * 2f, center)
    drawCircle(Color(0xCCE8EEFF), radius, center)
}

private fun DrawScope.drawStars(t: Float, count: Int) {
    for (i in 0 until count) {
        val twinkle = 0.25f + 0.75f * abs(sin(t * (0.6f + rnd(i, 3f)) + rnd(i, 4f) * 6f))
        drawCircle(
            Color.White.copy(alpha = 0.75f * twinkle),
            radius = (0.7f + 1.3f * rnd(i, 5f)).dp.toPx(),
            center = Offset(rnd(i, 1f) * size.width, rnd(i, 2f) * size.height * 0.8f),
        )
    }
}

private fun DrawScope.drawClouds(t: Float, count: Int, alpha: Float) {
    for (i in 0 until count) {
        val scale = (0.7f + 0.7f * rnd(i, 6f)) * minOf(size.width, size.height * 2f) * 0.12f
        val travel = size.width + scale * 6f
        val x = ((rnd(i, 7f) * travel + t * (6f + 10f * rnd(i, 8f)).dp.toPx()) % travel) - scale * 3f
        val y = size.height * (0.12f + 0.5f * rnd(i, 9f))
        val color = Color.White.copy(alpha = alpha)
        drawCircle(color, scale, Offset(x, y))
        drawCircle(color, scale * 1.35f, Offset(x + scale * 1.2f, y - scale * 0.45f))
        drawCircle(color, scale * 1.05f, Offset(x + scale * 2.5f, y))
        drawRoundRect(color, Offset(x - scale * 0.4f, y), Size(scale * 3.3f, scale), androidx.compose.ui.geometry.CornerRadius(scale / 2f))
    }
}

private fun DrawScope.drawFog(t: Float) {
    for (i in 0 until 5) {
        val y = size.height * (0.15f + 0.17f * i)
        val shift = sin(t * (0.25f + 0.08f * i) + i) * size.width * 0.12f
        drawLine(
            Color.White.copy(alpha = 0.13f),
            Offset(-size.width * 0.2f + shift, y), Offset(size.width * 1.2f + shift, y),
            strokeWidth = size.height * 0.09f, cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawRain(t: Float, count: Int) {
    val length = 13.dp.toPx()
    val slant = length * 0.28f
    for (i in 0 until count) {
        val speed = 0.9f + 0.7f * rnd(i, 11f)
        val fall = (rnd(i, 12f) + t * speed) % 1f
        val x = rnd(i, 10f) * (size.width + slant * 4f) - fall * slant * 4f
        val y = fall * (size.height + length) - length
        drawLine(
            Color.White.copy(alpha = 0.22f + 0.25f * rnd(i, 13f)),
            Offset(x, y), Offset(x - slant, y + length),
            strokeWidth = 1.3.dp.toPx(), cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawSnow(t: Float, count: Int) {
    for (i in 0 until count) {
        val speed = 0.10f + 0.12f * rnd(i, 15f)
        val fall = (rnd(i, 16f) + t * speed) % 1f
        val sway = sin(t * (0.6f + rnd(i, 17f)) + i) * 8.dp.toPx()
        drawCircle(
            Color.White.copy(alpha = 0.45f + 0.4f * rnd(i, 18f)),
            radius = (1.2f + 2.2f * rnd(i, 19f)).dp.toPx(),
            center = Offset(rnd(i, 14f) * size.width + sway, fall * (size.height + 12f) - 6f),
        )
    }
}

/** Two quick flashes every few seconds. */
private fun DrawScope.drawLightning(t: Float) {
    val phase = t % 5.5f
    val flash = when {
        phase < 0.09f -> 0.26f
        phase in 0.17f..0.23f -> 0.16f
        else -> 0f
    }
    if (flash > 0f) drawRect(Color.White.copy(alpha = flash))
}
