package org.arkikeskus.launcher.recents

import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.LauncherApps
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.arkikeskus.launcher.data.SplitPicker
import org.arkikeskus.launcher.designsystem.theme.LauncherTheme
import org.arkikeskus.launcher.feature.appdrawer.SplitPickAction
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
    /** Open the pick as peek view (the Peek switch at the top, or how the picker was opened). */
    private var peek by mutableStateOf(false)
    /** The picked app's share of the split in percent (Size row: 30, 50, 70). */
    private var share by mutableStateOf(50)
    private val three get() = intent.getBooleanExtra(SplitPicker.EXTRA_THREE, false)

    private val firstPackage get() = intent.getStringExtra(SplitPicker.EXTRA_FIRST_PACKAGE).orEmpty()
    private val firstKey get() = intent.getStringExtra(SplitPicker.EXTRA_FIRST_KEY)
    private val firstTaskId get() = intent.getIntExtra(SplitPicker.EXTRA_FIRST_TASK, -1).takeIf { it > 0 }
    private val firstOnRight get() = intent.getBooleanExtra(SplitPicker.EXTRA_RIGHT, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))

        val selfDockRight = intent.getBooleanExtra(SplitPicker.EXTRA_SELF_DOCK_RIGHT, false)
        peek = intent.getBooleanExtra(SplitPicker.EXTRA_PEEK, false)
        // Shown at once unless it still has to move itself into its half (no full-screen flash then).
        docked = !selfDockRight || isInMultiWindowMode

        setContent {
            val apps by viewModel.apps.collectAsStateWithLifecycle()
            val folders by viewModel.folders.collectAsStateWithLifecycle()
            val recents by viewModel.recents.collectAsStateWithLifecycle()
            val look by viewModel.look.collectAsStateWithLifecycle()
            val partners by viewModel.partners.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                viewModel.loadRecents(firstPackage)
                if (selfDockRight && !isInMultiWindowMode) {
                    viewModel.dock(taskId, firstPackage, firstTaskId, firstOnRight)
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
                            partners = partners,
                            pickCount = if (three) 2 else 1,
                            onPickAll = ::pickThree,
                            peek = if (!three && look.peekToggle && viewModel.peekAvailable) peek else null,
                            onPeekChange = { peek = it },
                            onAction = ::onAction,
                            onPick = { app -> pick(app, peek, look.openAnim) },
                            // The divider sizes need Folark's Vector module (as peek does).
                            share = if (!three && viewModel.peekAvailable) share else null,
                            pickedOnLeft = firstOnRight,
                            // A size is a plain split: it ends peek.
                            onShareChange = { share = it; peek = false },
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
        if (isInMultiWindowMode) {
            val mine = app.user == Process.myUserHandle()
            val opened = if (mine) launchInThisStage(app, openAnim) else launchOtherProfileHere(app, openAnim)
            if (opened) {
                if (mine) {
                    viewModel.recordPair(firstPackage, firstTaskId, app)
                    if (peek) viewModel.snapPeekLater(firstOnRight) else viewModel.snapShareLater(firstOnRight, share)
                } else {
                    // Another profile's app opens through Android's launcher service, which may put
                    // it full screen instead: checked, and the split made again around it if so.
                    viewModel.ensureBeside(firstPackage, firstTaskId, firstOnRight, app, peek)
                }
                // Zoom: the grown icon stays under the app until its window is up, then the picker goes.
                if (openAnim == SplitPickerLook.OPEN_ZOOM) {
                    window.decorView.postDelayed({ if (!isFinishing) finishAndRemoveTask() }, ZOOM_FINISH_MS)
                    return
                }
                finishAndRemoveTask()
                return
            }
        }
        viewModel.formSplit(firstPackage, firstKey, firstTaskId, firstOnRight, app, peek = peek)
        if (!peek) viewModel.snapShareLater(firstOnRight, share, afterFormingSplit = true)
        finishAndRemoveTask()
    }

    /** Three-app split: the two picked apps join the first (the picker was full screen). */
    private fun pickThree(apps: List<AppItem>) {
        if (appPicked || apps.size < 2) return
        appPicked = true
        viewModel.formThree(firstPackage, firstKey, firstTaskId, apps[0], apps[1])
        finishAndRemoveTask()
    }

    /** Long-press choices: open the app on its own instead of in the split, or (un)mark it a favorite. */
    private fun onAction(app: AppItem, action: SplitPickAction) {
        when (action) {
            SplitPickAction.FAVORITE -> viewModel.toggleFavorite(app)
            SplitPickAction.POPUP, SplitPickAction.FULL_SCREEN -> {
                if (appPicked) return
                appPicked = true
                viewModel.popOut(firstPackage, firstTaskId, app, floating = action == SplitPickAction.POPUP)
                finishAndRemoveTask()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Left without a pick (back, or swiped away): close the half-made split, keeping the first app.
        // Not when only recreated (a fold or display change) — the split stays as it is then.
        // A three-app picker opened full screen, without a half-made split to close.
        if (!appPicked && !three && isFinishing && !isChangingConfigurations) {
            viewModel.cancel(firstPackage, firstTaskId)
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
        startActivity(launchIntent, openOptions(openAnim))
        true
    }.getOrDefault(false)

    /** An app of another profile (work, Private Space) can only be opened through [LauncherApps]. */
    private fun launchOtherProfileHere(app: AppItem, openAnim: String): Boolean = runCatching {
        getSystemService(LauncherApps::class.java).startMainActivity(app.componentName, app.user, null, openOptions(openAnim))
        true
    }.getOrDefault(false)

    private fun openOptions(openAnim: String): Bundle? = when (openAnim) {
        SplitPickerLook.OPEN_ZOOM -> ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, 0).toBundle()
        SplitPickerLook.OPEN_FADE -> ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out).toBundle()
        else -> null
    }

    private companion object {
        const val DOCK_WAIT_MS = 2_000L
        /** Zoom: how long the picker stays under the opening app (its window is up by then). */
        const val ZOOM_FINISH_MS = 700L
    }
}
