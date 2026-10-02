package org.arkikeskus.launcher.feature.home

import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.arkikeskus.launcher.launcher.system.BatteryMonitor
import org.arkikeskus.launcher.model.BatteryStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class NothingClockViewModel @Inject constructor(
    batteryMonitor: BatteryMonitor,
) : ViewModel() {
    val status: StateFlow<BatteryStatus?> = batteryMonitor.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * Dot Matrix Retro Style inspired Minimalist Clock Widget:
 * Features bold minimalist typography, digital time format, date, battery status,
 * and Nothing's signature red accent dot styling.
 */
@Composable
fun NothingClockWidget(
    modifier: Modifier = Modifier,
    viewModel: NothingClockViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val noIndication = remember { MutableInteractionSource() }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    val timeString = timeFormat.format(Date(now))
    val dateString = dateFormat.format(Date(now)).uppercase(Locale.getDefault())

    WidgetSurface(
        modifier = modifier.clickable(interactionSource = noIndication, indication = null) {
            runCatching {
                context.startActivity(
                    Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            // Nothing accent red dot in top-right corner
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDA291C))
                    .align(Alignment.TopEnd),
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top row: Date & Battery
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.labelMedium,
                        color = widgetContentColor().copy(alpha = 0.8f),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    status?.let { s ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (s.charging) Color(0xFF4CAF50) else Color(0xFFFFC107))
                            )
                            Text(
                                text = "${s.percent}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = widgetContentColor().copy(alpha = 0.9f),
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }

                // Center / Bottom: Bold Dot-Matrix / Monospace Time Display
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = timeString,
                        style = TextStyle(
                            fontSize = 54.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-2).sp,
                        ),
                        color = widgetContentColor(),
                    )
                }
            }
        }
    }
}
