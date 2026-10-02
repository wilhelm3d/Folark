package org.arkikeskus.launcher.feature.home

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.arkikeskus.launcher.data.smartspace.WeatherCodes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Category Card Interface inspired Weather & Briefing Card Widget:
 * Features a clean rounded card design, daily greeting, weather condition summary,
 * temperature, and date.
 */
@Composable
fun SamsungWeatherWidget(
    modifier: Modifier = Modifier,
    viewModel: SmartspaceViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val noIndication = remember { MutableInteractionSource() }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000)
        }
    }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> stringResource(R.string.samsung_greeting_morning)
        in 12..17 -> stringResource(R.string.samsung_greeting_afternoon)
        else -> stringResource(R.string.samsung_greeting_evening)
    }

    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }
    val dateString = dateFormat.format(Date(now))

    WidgetSurface(
        modifier = modifier.clickable(interactionSource = noIndication, indication = null) {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW).setData(CalendarContract.CONTENT_URI)
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top row: Greeting & Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = widgetContentColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = dateString,
                            style = MaterialTheme.typography.bodySmall,
                            color = widgetContentColor().copy(alpha = 0.75f),
                        )
                    }
                }

                // Center/Bottom row: Weather Summary & Temperature
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val tempText = weather?.let { "${it.temperatureC.roundToInt()}°C" } ?: "--°C"
                        val conditionText = weather?.let { "${WeatherCodes.emoji(it.weatherCode)} ${it.city.orEmpty()}" }
                            ?: stringResource(R.string.smartspace_allow_location)

                        Text(
                            text = tempText,
                            style = TextStyle(
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = widgetContentColor(),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = conditionText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = widgetContentColor().copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
