package org.arkikeskus.launcher.recents

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import org.arkikeskus.launcher.data.SplitPicker
import org.arkikeskus.launcher.designsystem.theme.LauncherTheme
import org.arkikeskus.launcher.feature.appdrawer.SplitPickerDrawer

/**
 * The split-select picker: Folark's app drawer, shown full screen to choose the second app for a
 * split. Once an app is tapped, [SplitPickerViewModel.formSplit] opens it beside the first (dragged)
 * app — the first one on the side it was dropped — and this screen closes.
 */
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
                        viewModel.formSplit(firstPackage, right, app)
                        finish()
                    },
                )
            }
        }
    }
}
