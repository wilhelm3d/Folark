package org.arkikeskus.launcher.pairs

import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.model.catching

/**
 * A translucent trampoline: a scheduled pair's notification taps here, the pair opens
 * (a notification content intent is user interaction, so the background-activity-start
 * restriction does not apply), and this activity is gone before the split appears.
 */
@AndroidEntryPoint
class PairLaunchActivity : ComponentActivity() {

    @Inject lateinit var appRepository: AppRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pairId = intent?.getStringExtra(PairScheduleReceiver.EXTRA_PAIR_ID) ?: return finish()
        scope.launch {
            val pair = appRepository.appPairs.first().firstOrNull { it.id == pairId }
                ?: return@launch finish()
            catching { appRepository.launchAppPair(pair) }
            finish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
