package org.arkikeskus.launcher.xposed

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.animation.PathInterpolator
import org.arkikeskus.launcher.data.SplitThree
import org.arkikeskus.launcher.model.catching
import org.arkikeskus.launcher.xposed.ShellCalls.field
import org.arkikeskus.launcher.xposed.ShellCalls.methods
import org.arkikeskus.launcher.xposed.ShellCalls.mirrored
import org.arkikeskus.launcher.xposed.ShellCalls.nearest
import org.arkikeskus.launcher.xposed.ShellCalls.reason
import org.arkikeskus.launcher.xposed.ShellCalls.splitLayout
import org.arkikeskus.launcher.xposed.ShellCalls.splitManager
import org.arkikeskus.launcher.xposed.ShellCalls.splitPosition
import org.arkikeskus.launcher.xposed.SplitThreeHook.SNAP_MS
import org.arkikeskus.launcher.xposed.SplitThreeHook.SNAP_SETTLE_MS
import org.arkikeskus.launcher.xposed.SplitThreeHook.SNAP_SLACK_PX
import org.arkikeskus.launcher.xposed.SplitThreeHook.SPLIT_POSITION_BOTTOM_OR_RIGHT
import org.arkikeskus.launcher.xposed.SplitThreeHook.THREE_WAIT_TRIES
import org.arkikeskus.launcher.xposed.SplitThreeHook.WAIT_STEP_MS
import org.arkikeskus.launcher.xposed.SplitThreeHook.WAIT_TRIES
import org.arkikeskus.launcher.xposed.ToFolark.reply

/**
 * The divider of WM Shell's split screen, for SplitThreeHook (split out of SplitThreeHook.kt):
 * where it is and what state the split is in, and moving it to a stop — a third, peek, three
 * apps even — or to exact places, answering Folark once it has settled.
 */
internal object DividerSnaps {
    private fun log(message: String) = ToFolark.log(message)

    /** A split transition is under way (entering, resizing or animating): WM Shell's state is in flux. */
    fun splitBusy(c: Any): Boolean = catching {
        val stage = field(c, "mStageCoordinator") ?: return false
        val transitions = field(stage, "mSplitTransitions") ?: return false
        field(transitions, "mPendingEnter") != null || field(transitions, "mPendingResize") != null ||
            field(transitions, "mAnimatingTransition") != null ||
            activeStages(c).let { it in 0..1 }
    }.getOrDefault(false)

    /** Motorola's split state now (SplitState.mState), or null. */
    fun splitState(c: Any): Int? = catching {
        val split = field(splitManager(c), "mSplitState") ?: return@catching null
        field(split, "mState") as Int
    }.getOrNull()

    /** How many apps Motorola's split state holds (2 or 3; 0 for none), or -1 when unknown. */
    fun activeStages(c: Any): Int = catching {
        val state = field(splitManager(c), "mSplitState") ?: return@catching -1
        val m = methods(state.javaClass).first { it.name == "getActiveStageCountBySplitState" && it.parameterTypes.isEmpty() }
        m.isAccessible = true
        m.invoke(state) as Int
    }.getOrDefault(-1)

    /**
     * On WM Shell's thread: moves the split's divider to the snap target [snap], exactly where a
     * finger dragging the divider ends (SplitLayout.snapToTarget), so Android sets up the layout the
     * same way, peek strip included. Waits for the split to be showing first. Never picks a dismiss
     * target (the first and last ones), which would close the split instead. With [forTask] the snap
     * is meant for that task as if it sat left / on top: turned round when it sits on the other side.
     * A third or two thirds the divider has no stop for is refused (no silent half and half).
     */
    fun snapTo(context: Context, c: Any, request: String, snap: Int, tries: Int, forTask: Int = -1) {
        val visible = catching { c.javaClass.getMethod("isSplitScreenVisible").invoke(c) as Boolean }.getOrDefault(false)
        if (!visible) {
            if (tries < WAIT_TRIES) {
                Handler(Looper.myLooper() ?: Looper.getMainLooper()).postDelayed({ snapTo(context, c, request, snap, tries + 1, forTask) }, WAIT_STEP_MS)
            } else {
                reply(context, request, false)
            }
            return
        }
        var check: (() -> Unit)? = null
        val ok = catching {
            val wanted = if (forTask > 0 && splitPosition(c, forTask) == SPLIT_POSITION_BOTTOM_OR_RIGHT) mirrored(snap) else snap
            val layout = splitLayout(c)
            val algorithm = field(layout, "mDividerSnapAlgorithm") ?: error("no DividerSnapAlgorithm")
            val targets = (field(algorithm, "mTargets") as? List<*>).orEmpty().filterNotNull()

            // Never pick index 0 (dismiss start) or last index (dismiss end):
            // Match exact snapPosition first, then fall back to DividerSnapAlgorithm's non-dismiss split targets.
            val nonDismiss = if (targets.size >= 3) targets.subList(1, targets.size - 1) else targets
            val target = nonDismiss.firstOrNull { field(it, "snapPosition") == wanted }
                ?: when (wanted) {
                    SplitThree.SNAP_PEEK_LEFT -> field(algorithm, "mFirstSplitTarget") ?: nonDismiss.firstOrNull()
                    SplitThree.SNAP_PEEK_RIGHT -> field(algorithm, "mLastSplitTarget") ?: nonDismiss.lastOrNull()
                    SplitThree.SNAP_ONE_THIRD -> nearest(targets, nonDismiss, 1f / 3)
                    SplitThree.SNAP_TWO_THIRDS -> nearest(targets, nonDismiss, 2f / 3)
                    else -> field(algorithm, "mMiddleTarget") ?: nonDismiss.getOrNull(nonDismiss.size / 2)
                }
                ?: error("no valid split snap target for $wanted")

            val position = field(layout, "mDividerPosition") as? Int ?: error("no divider position")
            val method = methods(layout.javaClass).first { it.name == "snapToTarget" && it.parameterTypes.size == 4 }
            method.isAccessible = true
            method.invoke(layout, position, target, SNAP_MS, PathInterpolator(0.2f, 0f, 0f, 1f))
            // Once the divider has settled: did it stay there? (A system that only keeps 50/50
            // moves it back.) Answered then, so Folark learns whether sizes hold on this phone.
            val wantedAt = field(target, "position") as? Int
            check = {
                val now = field(layout, "mDividerPosition") as? Int
                val held = wantedAt == null || now == null || kotlin.math.abs(now - wantedAt) <= SNAP_SLACK_PX
                if (held) reply(context, request, true)
                else reply(context, request, false, detail = "the divider went back ($now, sent to $wantedAt)")
            }
            true
        }.getOrElse {
            log("split layout: can't snap to $snap: $it")
            false
        }
        val verify = check
        if (ok && verify != null) {
            Handler(Looper.myLooper() ?: Looper.getMainLooper()).postDelayed({ catching(verify) }, SNAP_MS + SNAP_SETTLE_MS)
            return
        }
        reply(context, request, ok)
    }

    /**
     * On WM Shell's thread: the three-app split showing now (or about to, after a third app joined)
     * to three equal thirds, both dividers gliding to Motorola's own even stops
     * (SplitLayoutCarousel3Apps.snapToTarget, as when dropped by a finger). Its end works out the
     * layout from the dividers, which [hookThreeEven] answers with the even one. Answered once it
     * settled: ok only when Motorola's split state is the even one.
     */
    fun snapThree(context: Context, c: Any, request: String, wanted: Int, tries: Int) {
        val manager = catching { splitManager(c) }.getOrNull()
        val layout = manager?.let { field(it, "mSplitLayout") }
        val three = layout != null && layout.javaClass.name.endsWith("Carousel3Apps") &&
            (field(layout, "mDividerPositions") as? IntArray)?.size == 2
        if (!three) {
            if (tries < THREE_WAIT_TRIES) {
                Handler(Looper.myLooper() ?: Looper.getMainLooper()).postDelayed({ snapThree(context, c, request, wanted, tries + 1) }, WAIT_STEP_MS)
            } else {
                reply(context, request, false, detail = "no three-app split showing")
            }
            return
        }
        val failure = catching {
            val algorithm = field(layout, "mDividerSnapAlgorithm") ?: error("no DividerSnapAlgorithm")
            val get = methods(algorithm.javaClass).first {
                it.name == "getDividerSnapTarget" && it.parameterTypes.size == 2 &&
                    it.parameterTypes.all { t -> t == Int::class.javaPrimitiveType }
            }
            get.isAccessible = true
            val first = get.invoke(algorithm, wanted, 0) ?: error("no stop for $wanted")
            val second = get.invoke(algorithm, wanted, 1) ?: error("no stop for $wanted")
            val positions = field(layout, "mDividerPositions") as IntArray
            val method = methods(layout.javaClass).first { it.name == "snapToTarget" && it.parameterTypes.size == 6 }
            method.isAccessible = true
            method.invoke(layout, positions[0], first, positions[1], second, SNAP_MS, PathInterpolator(0.2f, 0f, 0f, 1f))
        }.exceptionOrNull()
        if (failure != null) {
            log("split layout: can't make three apps $wanted: ${reason(failure)}")
            return reply(context, request, false, detail = reason(failure))
        }
        Handler(Looper.myLooper() ?: Looper.getMainLooper()).postDelayed({
            val state = catching {
                val split = field(manager, "mSplitState") ?: return@catching null
                field(split, "mState") as Int
            }.getOrNull()
            if (state == wanted) reply(context, request, true)
            else reply(context, request, false, detail = "the dividers went back (split state $state)")
        }, SNAP_MS + SNAP_SETTLE_MS)
    }

    /**
     * On WM Shell's thread: the split showing (or about to) to the divider places [fractions]
     * (thousandths): one for two apps, two for three. Waits for a layout with that many apps.
     */
    fun snapFractions(context: Context, c: Any, request: String, fractions: IntArray, tries: Int) {
        val layout = catching { field(splitManager(c), "mSplitLayout") }.getOrNull()
        val three = layout?.javaClass?.name?.endsWith("Carousel3Apps") == true
        val fits = layout != null && !splitBusy(c) && (if (fractions.size == 2) three else !three)
        if (!fits) {
            if (tries < THREE_WAIT_TRIES) {
                Handler(Looper.myLooper() ?: Looper.getMainLooper()).postDelayed({ snapFractions(context, c, request, fractions, tries + 1) }, WAIT_STEP_MS)
            } else {
                reply(context, request, false, detail = "no split with ${fractions.size + 1} apps showing")
            }
            return
        }
        // Known to be there: [fits] above needs a layout.
        val active = checkNotNull(layout) { "layout null despite fits check" }
        val ok = catching {
            val algorithm = field(active, "mDividerSnapAlgorithm") ?: error("no DividerSnapAlgorithm")
            val leftRight = field(algorithm, "mIsLeftRightSplit") as? Boolean ?: true
            val span = field(algorithm, if (leftRight) "mDisplayWidth" else "mDisplayHeight") as Int
            val at = fractions.map { (it.coerceIn(1, 999).toLong() * span / 1000).toInt() }.sorted()
            if (three) DividerStopsHook.placeThree(active, at[0], at[1]) else DividerStopsHook.placeTwo(active, at[0])
        }.getOrElse { log("split layout: can't place the divider: ${reason(it)}"); false }
        reply(context, request, ok)
    }

    /**
     * On WM Shell's thread: the snap position the divider is at now (the nearest stop), seen from
     * [forTask] as if it sat left / on top. Refused when [forTask] isn't in the split.
     */
    fun querySnap(context: Context, c: Any, request: String, forTask: Int) {
        val value = catching {
            val side = splitPosition(c, forTask)
            if (side != 0 && side != SPLIT_POSITION_BOTTOM_OR_RIGHT) error("task $forTask isn't in the split")
            val layout = splitLayout(c)
            val algorithm = field(layout, "mDividerSnapAlgorithm") ?: error("no DividerSnapAlgorithm")
            val targets = (field(algorithm, "mTargets") as? List<*>).orEmpty().filterNotNull()
            val position = field(layout, "mDividerPosition") as? Int ?: error("no divider position")
            val closest = targets.minByOrNull { kotlin.math.abs(((field(it, "position") as? Int) ?: Int.MAX_VALUE / 2) - position) }
                ?: error("no snap targets")
            val snap = field(closest, "snapPosition") as? Int ?: error("no snapPosition")
            if (side == SPLIT_POSITION_BOTTOM_OR_RIGHT) mirrored(snap) else snap
        }.getOrElse {
            log("split layout: can't read the divider: $it")
            null
        }
        reply(context, request, value != null, value)
    }

}
