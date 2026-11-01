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
import kotlinx.coroutines.withTimeoutOrNull
import org.arkikeskus.launcher.data.ShellSplit
import org.arkikeskus.launcher.data.SplitPicker
import org.arkikeskus.launcher.designsystem.theme.LauncherTheme
import org.arkikeskus.launcher.feature.appdrawer.SplitPickerDrawer
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.SplitPickerLook

/**
 * The split picker: Folark's drawer in the half of the screen beside the app that was dragged to an
 * edge. Picking an app opens it in this half (or builds the split, when the picker couldn't dock);
 * leaving without a pick closes the half-made split again.
 */
@AndroidEntryPoint
class SplitPickerActivity : ComponentActivity() {

    private val viewModel: SplitPickerViewModel by viewModels()
    private var docked by mutableStateOf(false)
    private var appPicked = false

    private val firstPackage get() = intent.getStringExtra(SplitPicker.EXTRA_FIRST_PACKAGE).orEmpty()
    private val firstKey get() = intent.getStringExtra(SplitPicker.EXTRA_FIRST_KEY)
    private val firstTaskId get() = intent.getIntExtra(SplitPicker.EXTRA_FIRST_TASK, -1).takeIf { it > 0 }
    private val firstOnRight get() = intent.getBooleanExtra(SplitPicker.EXTRA_RIGHT, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))

        val selfDockRight = intent.getBooleanExtra(SplitPicker.EXTRA_SELF_DOCK_RIGHT, false)
        val peek = intent.getBooleanExtra(SplitPicker.EXTRA_PEEK, false)
        // Shown at once unless it still has to move itself into its half (no full-screen flash then).
        docked = !selfDockRight || isInMultiWindowMode

        setContent {
            val apps by viewModel.apps.collectAsStateWithLifecycle()
            val folders by viewModel.folders.collectAsStateWithLifecycle()
            val recents by viewModel.recents.collectAsStateWithLifecycle()
            val look by viewModel.look.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                viewModel.loadRecents(firstPackage)
                if (selfDockRight && !isInMultiWindowMode) {
                    withContext(Dispatchers.IO) {
                        val root = ShellSplit.preferRoot(this@SplitPickerActivity)
                        repeat(DOCK_TRIES) {
                            if (ShellSplit.moveToSide(this@SplitPickerActivity, taskId, right = true, root = root)) return@withContext
                            delay(DOCK_RETRY_MS)
                        }
                    }
                    // onMultiWindowModeChanged shows it; this is the fallback.
                    withTimeoutOrNull(DOCK_WAIT_MS) { while (!isInMultiWindowMode) delay(40L) }
                }
                docked = true
            }

            LauncherTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    AnimatedVisibility(visible = docked, enter = fadeIn(animationSpec = tween(110))) {
                        SplitPickerDrawer(
                            apps = apps,
                            folders = folders,
                            look = look,
                            recents = recents,
                            firstPackageToHide = firstPackage,
                            onPick = { app -> pick(app, peek, look.openAnim) },
                        )
                    }
                }
            }
        }
    }

    private fun pick(app: AppItem, peek: Boolean, openAnim: String) {
        if (appPicked) return
        appPicked = true
        // Docked beside the first app: the pick opens right here, in this half of the split.
        if (isInMultiWindowMode && app.user == Process.myUserHandle() && launchInThisStage(app, openAnim)) {
            if (peek) viewModel.snapPeekLater(firstOnRight)
            // Zoom: the grown icon stays under the app until its window is up, then the picker goes.
            if (openAnim == SplitPickerLook.OPEN_ZOOM) {
                window.decorView.postDelayed({ if (!isFinishing) finishAndRemoveTask() }, ZOOM_FINISH_MS)
                return
            }
        } else {
            viewModel.formSplit(firstPackage, firstKey, firstTaskId, firstOnRight, app, peek = peek)
        }
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Left without a pick (back, or swiped away): close the half-made split, keeping the first app.
        // Not when only recreated (a fold or display change) — the split stays as it is then.
        if (!appPicked && isFinishing && !isChangingConfigurations) {
            val app = applicationContext
            val keep = firstTaskId
            val pkg = firstPackage
            Thread {
                runCatching {
                    val root = ShellSplit.preferRoot(app)
                    val id = keep ?: ShellSplit.taskIdOf(app, pkg, root) ?: -1
                    ShellSplit.exitSplit(app, id, root)
                }
            }.start()
        }
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (isInMultiWindowMode) docked = true
    }

    /**
     * Opens [app] from this activity, so WM Shell puts it in this half of the split. Zoom: the app
     * fades in over the grown icon (which stays put); Fade: it fades in as the picker fades out;
     * Off: Android's own opening animation.
     */
    private fun launchInThisStage(app: AppItem, openAnim: String): Boolean = runCatching {
        val launchIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.componentName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val opts = when (openAnim) {
            SplitPickerLook.OPEN_ZOOM -> ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, 0).toBundle()
            SplitPickerLook.OPEN_FADE -> ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out).toBundle()
            else -> null
        }
        startActivity(launchIntent, opts)
        true
    }.getOrDefault(false)

    private companion object {
        const val DOCK_TRIES = 3
        const val DOCK_RETRY_MS = 150L
        const val DOCK_WAIT_MS = 450L
        /** Zoom: how long the picker stays under the opening app (its window is up by then). */
        const val ZOOM_FINISH_MS = 700L
    }
}
