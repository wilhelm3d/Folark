package org.arkikeskus.launcher.recents

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import org.arkikeskus.launcher.data.SplitPicker
import org.arkikeskus.launcher.designsystem.theme.LauncherTheme
import org.arkikeskus.launcher.feature.appdrawer.SplitPickerDrawer
import org.arkikeskus.launcher.model.AppItem

@AndroidEntryPoint
class SplitPickerActivity : ComponentActivity() {

    private val viewModel: SplitPickerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val firstPackage = intent.getStringExtra(SplitPicker.EXTRA_FIRST_PACKAGE).orEmpty()
        val right = intent.getBooleanExtra(SplitPicker.EXTRA_RIGHT, false)
        setContent {
            val apps by viewModel.apps.collectAsStateWithLifecycle()
            val folders by viewModel.folders.collectAsStateWithLifecycle()
            LauncherTheme(darkTheme = true) {
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
