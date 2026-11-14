package org.arkikeskus.launcher.xposed

import android.content.Context
import org.arkikeskus.launcher.data.SplitThree
import java.lang.reflect.Field
import java.util.concurrent.Executor
import org.arkikeskus.launcher.model.catching

/**
 * The reflection SplitThreeHook does on WM Shell (split out of SplitThreeHook.kt): finding the
 * split layout and its stops, calling methods whose newer versions add parameters at the end,
 * neutral values for those, readable failures, and the controller's own executor. Nothing here
 * keeps state; every call reads what WM Shell has now.
 */
internal object ShellCalls {
    /** How far (of the divider's whole travel) a stop may be from a third and still count as one. */
    private const val THIRD_REACH = 0.08f

    /** WM Shell's SplitLayoutManager (Motorola), which holds the current split layout and state. */
    fun splitManager(c: Any): Any {
        val stage = field(c, "mStageCoordinator") ?: error("no StageCoordinator")
        return field(stage, "mSplitLayoutManager") ?: error("no SplitLayoutManager")
    }

    /** The SplitLayout of the split screen (the divider and its stops). */
    fun splitLayout(c: Any): Any {
        val stage = field(c, "mStageCoordinator") ?: error("no StageCoordinator")
        field(stage, "mSplitLayout")?.let { return it }
        val manager = field(stage, "mSplitLayoutManager") ?: error("no SplitLayoutManager")
        return field(manager, "mSplitLayout") ?: error("no SplitLayout")
    }

    /** Which side [taskId] sits on: 0 left / top, 1 right / bottom, -1 not in the split (or unknown). */
    fun splitPosition(c: Any, taskId: Int): Int = catching {
        val m = c.javaClass.methods.firstOrNull {
            it.name == "getSplitPosition" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType
        } ?: return@catching -1
        m.invoke(c, taskId) as Int
    }.getOrDefault(-1)

    /** The same layout seen from the other side (a third ↔ two thirds, peek left ↔ peek right). */
    fun mirrored(snap: Int): Int = when (snap) {
        SplitThree.SNAP_ONE_THIRD -> SplitThree.SNAP_TWO_THIRDS
        SplitThree.SNAP_TWO_THIRDS -> SplitThree.SNAP_ONE_THIRD
        SplitThree.SNAP_PEEK_LEFT -> SplitThree.SNAP_PEEK_RIGHT
        SplitThree.SNAP_PEEK_RIGHT -> SplitThree.SNAP_PEEK_LEFT
        else -> snap
    }

    /**
     * The stop among [candidates] nearest to [fraction] of the way across (from the dismiss stops at
     * both ends of [all]), or null when none is within reach of it: then the phone has no such stop
     * (the middle one or a peek strip is not a third).
     */
    fun nearest(all: List<Any>, candidates: List<Any>, fraction: Float): Any? {
        val start = all.firstOrNull()?.let { field(it, "position") as? Int } ?: return null
        val end = all.lastOrNull()?.let { field(it, "position") as? Int } ?: return null
        val span = (end - start).toFloat()
        if (span <= 0f) return null
        val goal = start + span * fraction
        val best = candidates.minByOrNull { kotlin.math.abs(((field(it, "position") as? Int) ?: return@minByOrNull Float.MAX_VALUE) - goal) }
            ?: return null
        val at = field(best, "position") as? Int ?: return null
        return best.takeIf { kotlin.math.abs(at - goal) <= span * THIRD_REACH }
    }

    /**
     * Calls WM Shell's [name] whose parameters START with [leading] (type to value). Android builds
     * add parameters at the end (a RemoteTransition, an InstanceId, flags for flexible split…), so
     * an exact count breaks on the next release; the rest are filled with neutral values: null,
     * 0 / false, and a real InstanceId (the split logger reads it). The shortest match wins. When
     * none matches, the error lists what the class does have, for Check split screen.
     */
    fun invokeLeading(c: Any, name: String, leading: List<Pair<Class<*>, Any?>>) {
        val candidates = methods(c.javaClass).filter { it.name == name }
        val m = candidates.filter { method ->
            val p = method.parameterTypes
            p.size >= leading.size && leading.indices.all { i -> p[i] == leading[i].first || p[i].isAssignableFrom(leading[i].first) }
        }.minByOrNull { it.parameterTypes.size }
            ?: error("no $name(${leading.joinToString { it.first.simpleName }}, …); found: " +
                candidates.joinToString(" | ") { method -> method.parameterTypes.joinToString(",") { it.simpleName } }.ifEmpty { "none" })
        val args = m.parameterTypes.mapIndexed { i, type -> if (i < leading.size) leading[i].second else neutral(type) }
        m.isAccessible = true
        m.invoke(c, *args.toTypedArray())
    }

    /**
     * Like [invokeLeading], with the split index (which pane: 2 = the third) in the first number
     * after [leading]; newer builds may add parameters after it.
     */
    fun invokeWithIndex(c: Any, name: String, leading: List<Pair<Class<*>, Any?>>, index: Int) {
        val int = Int::class.javaPrimitiveType
        val candidates = methods(c.javaClass).filter { it.name == name }
        val m = candidates.filter { method ->
            val p = method.parameterTypes
            p.size > leading.size && leading.indices.all { i -> p[i] == leading[i].first || p[i].isAssignableFrom(leading[i].first) } &&
                (leading.size until p.size).any { p[it] == int }
        }.minByOrNull { it.parameterTypes.size }
            ?: error("no $name(${leading.joinToString { it.first.simpleName }}, …, int index); found: " +
                candidates.joinToString(" | ") { method -> method.parameterTypes.joinToString(",") { it.simpleName } }.ifEmpty { "none" })
        val p = m.parameterTypes
        val indexAt = (leading.size until p.size).first { p[it] == int }
        val args = p.mapIndexed { i, type ->
            when {
                i < leading.size -> leading[i].second
                i == indexAt -> index
                else -> neutral(type)
            }
        }
        m.isAccessible = true
        m.invoke(c, *args.toTypedArray())
    }

    /** A harmless value for a trailing parameter of [type]. */
    fun neutral(type: Class<*>): Any? = when {
        type == Int::class.javaPrimitiveType -> 0
        type == Long::class.javaPrimitiveType -> 0L
        type == Boolean::class.javaPrimitiveType -> false
        type == Float::class.javaPrimitiveType -> 0f
        type.name == "com.android.internal.logging.InstanceId" -> catching {
            type.getDeclaredConstructor(Int::class.javaPrimitiveType).apply { isAccessible = true }
                .newInstance((System.nanoTime() and 0xFFFFF).toInt())
        }.getOrNull()
        else -> null
    }

    /** What went wrong, without reflection's wrapper. */
    fun reason(t: Throwable): String {
        val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
        // The first frame is often a JDK check (Preconditions, Objects); WM Shell's own frames say
        // where it happened, so up to three of those follow it.
        val frames = cause.stackTrace
        val shell = frames.filter { it.className.startsWith("com.android.wm.shell") }.take(3)
        val shown = (listOfNotNull(frames.firstOrNull()) + shell).distinct()
        val at = shown.joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
        return (cause.javaClass.simpleName + ": " + (cause.message ?: "")).take(200) + if (at.isEmpty()) "" else " ($at)"
    }

    /** WM Shell knows [taskId] as a running task (unknown ones make it throw); true when it can't tell. */
    fun isRunningTask(c: Any, taskId: Int): Boolean = catching {
        val organizer = field(c, "mTaskOrganizer") ?: return true
        val get = methods(organizer.javaClass).firstOrNull {
            it.name == "getRunningTaskInfo" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType
        } ?: return true
        get.isAccessible = true
        get.invoke(organizer, taskId) != null
    }.getOrDefault(true)

    /** What a listener method gives back: nothing for the void ones (all today), a zero otherwise. */
    fun defaultFor(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        else -> null
    }

    fun methods(cls: Class<*>): List<java.lang.reflect.Method> = Reflect.methods(cls)

    /** WM Shell's main executor: the controller's `mMainExecutor`, or any Executor field it has. */
    fun mainExecutor(c: Any): Executor? =
        (field(c, "mMainExecutor") as? Executor)
            ?: allFields(c.javaClass).firstOrNull { Executor::class.java.isAssignableFrom(it.type) }
                ?.let { catching { it.isAccessible = true; it.get(c) as? Executor }.getOrNull() }

    fun field(target: Any, name: String): Any? = Reflect.get(target, name)

    fun allFields(cls: Class<*>): List<Field> = generateSequence(cls) { it.superclass }
        .flatMap { it.declaredFields.asSequence() }.toList()

    fun appContext(): Context? = catching {
        Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Context
    }.getOrNull()
}
