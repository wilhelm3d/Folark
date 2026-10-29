package org.arkikeskus.launcher.recents

import android.app.ActivityOptions
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.ShellSplit
import org.arkikeskus.launcher.data.SplitPicker
import org.arkikeskus.launcher.designsystem.theme.LauncherTheme
import org.arkikeskus.launcher.feature.appdrawer.SplitPickerDrawer
import org.arkikeskus.launcher.model.AppItem

@AndroidEntryPoint
class SplitPickerActivity : ComponentActivity() {

    private val viewModel: SplitPickerViewModel by viewModels()
    private var docked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make window completely transparent while full-screen so it never flashes over the screen
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))

        val firstPackage = intent.getStringExtra(SplitPicker.EXTRA_FIRST_PACKAGE).orEmpty()
        val right = intent.getBooleanExtra(SplitPicker.EXTRA_RIGHT, false)
        val selfDockRight = intent.getBooleanExtra(SplitPicker.EXTRA_SELF_DOCK_RIGHT, false)

        docked = isInMultiWindowMode

        setContent {
            val apps by viewModel.apps.collectAsStateWithLifecycle()
            val folders by viewModel.folders.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                if (selfDockRight && !isInMultiWindowMode) {
                    // We already know our exact taskId (`this@SplitPickerActivity.taskId`) — dock immediately!
                    withContext(Dispatchers.IO) {
                        ShellSplit.moveToSideFast(this@SplitPickerActivity, taskId, right = true)
                    }
                }
                // Fallback: if WMShell takes longer than 260ms or isn't in multi-window, show anyway
                delay(260L)
                docked = true
            }

            LauncherTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = docked,
                        enter = fadeIn(animationSpec = tween(110)),
                    ) {
                        SplitPickerDrawer(
                            apps = apps,
                            folders = folders,
                            onPick = { app ->
                                if (isInMultiWindowMode && app.user == Process.myUserHandle() && launchInThisStage(app)) {
                                    finishAndRemoveTask()
                                } else {
                                    viewModel.formSplit(firstPackage, right, app)
                                    finishAndRemoveTask()
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (isInMultiWindowMode) {
            docked = true
        }
    }

    private fun launchInThisStage(app: AppItem): Boolean = runCatching {
        val launchIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.componentName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val opts = ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out).toBundle()
        startActivity(launchIntent, opts)
        true
    }.getOrDefault(false)
}
