package org.arkikeskus.launcher.recents

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import org.arkikeskus.launcher.designsystem.theme.LauncherTheme
import org.arkikeskus.launcher.feature.appdrawer.SplitPickerDrawer

/**
 * The second pane of a split-select: Folark opens the dragged app on one side and this picker on
 * the other (paired through the normal split mechanism). It shows [SplitPickerDrawer]; tapping an
 * app launches it, which replaces this picker in its pane with Android's own slide-in animation.
 */
@AndroidEntryPoint
class SplitPickerActivity : ComponentActivity() {

    private val viewModel: SplitPickerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val apps by viewModel.apps.collectAsStateWithLifecycle()
            val folders by viewModel.folders.collectAsStateWithLifecycle()
            LauncherTheme(darkTheme = true) {
                SplitPickerDrawer(
                    apps = apps,
                    folders = folders,
                    onPick = { app ->
                        viewModel.launch(app)
                        finish()
                    },
                )
            }
        }
    }
}
