package org.arkikeskus.launcher.data

/**
 * Reads `dumpsys activity activities` (Android's window hierarchy, top to bottom) into a
 * [TaskSnapshot]: every app task with its package, whether it shows, and which split stage it
 * sits in. Plain text in, data out (no Android calls), so it is unit-tested with real dumps.
 */
internal object ShellSplitParser {

    // Built once: a dump is thousands of lines.
    private val TASK_ID = Regex("""Task\{\S+ #(\d+)""")
    private val STAGE = Regex("""stage_[abc]""")
    private val PKG_INTENT = Regex("""\bI=([\w.]+)/""")
    private val PKG_AFFINITY = Regex("""\bA=\d+:([\w.]+)""")
    private val TYPE = Regex("""\btype=(\w+)""")
    private val MODE = Regex("""\bmode=([\w-]+)""")
    /** `ActivityRecord{9f8e7d u0 com.android.chrome/.Main t15520}`: the package of an activity in task 15520. */
    private val ACTIVITY = Regex("""ActivityRecord\{\S+ u\d+ ([\w.]+)/\S+ t(\d+)""")

    fun parse(text: String, ownPackage: String): TaskSnapshot {
        // The bottom (root) activity of each task names its app; lines list activities top first,
        // so the last one seen for a task is its root.
        val rootPackage = HashMap<Int, String>()
        for (m in ACTIVITY.findAll(text)) {
            val id = m.groupValues[2].toIntOrNull() ?: continue
            rootPackage[id] = m.groupValues[1]
        }

        val tasks = ArrayList<TaskSnapshot.Task>()
        // Some ROMs (Motorola's) print the hierarchy twice in one dump: each task counts once.
        val seen = HashSet<Int>()
        val stagedIds = LinkedHashSet<Int>()
        val perStage = LinkedHashMap<String, TaskSnapshot.Task>()
        val visiblePerStage = LinkedHashMap<String, TaskSnapshot.Task>()
        var stageIndent = -1
        var currentStage = ""
        for (line in text.lineSequence()) {
            if (line.startsWith("Display ") || line.contains("TaskDisplayArea") || line.startsWith("RootHomeTask")) {
                stageIndent = -1
                currentStage = ""
            }
            val star = line.indexOf("* Task{")
            if (star < 0) continue
            val id = TASK_ID.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val stage = STAGE.find(line)?.value
            if (stage != null) {
                stageIndent = star
                currentStage = stage
                continue
            }
            val intentPkg = PKG_INTENT.find(line)?.groupValues?.get(1)
            val affinityPkg = PKG_AFFINITY.find(line)?.groupValues?.get(1)
            val packages = listOfNotNull(rootPackage[id], intentPkg, affinityPkg).distinct()
            val type = TYPE.find(line)?.groupValues?.get(1).orEmpty()
            val visible = (line.contains("visible=true") || line.contains("visibleRequested=true")) &&
                !line.contains("visible=false")

            if (stageIndent >= 0 && star <= stageIndent) {
                stageIndent = -1
                currentStage = ""
            }
            val inStage = stageIndent >= 0 && star <= stageIndent + 4 &&
                type != "home" && type != "recents" && !line.contains("mode=fullscreen")
            val task = TaskSnapshot.Task(
                id = id,
                packages = packages,
                type = type,
                mode = MODE.find(line)?.groupValues?.get(1).orEmpty(),
                visible = visible,
                stage = if (inStage) currentStage else null,
                own = packages.isNotEmpty() && packages.all { it.startsWith(ownPackage) },
            )
            // Only tasks that belong to an app (container tasks name none).
            if (packages.isNotEmpty() && seen.add(id)) tasks += task
            if (inStage) {
                stagedIds += id
                if (packages.isNotEmpty() && !task.own) {
                    perStage.putIfAbsent(currentStage, task)
                    if (visible) visiblePerStage.putIfAbsent(currentStage, task)
                }
            }
        }
        return TaskSnapshot(tasks, stagedIds, perStage.values.toList(), visiblePerStage.values.toList())
    }
}

/**
 * One read of Android's window hierarchy (see [ShellSplit.snapshot]). [tasks] are the app tasks,
 * top (front) first; [stagedIds] every task in a split stage, any app; [stagedApps] /
 * [visibleStagedApps] one app per stage (Folark's own tasks, such as its picker, left out).
 */
class TaskSnapshot internal constructor(
    val tasks: List<Task>,
    val stagedIds: Set<Int>,
    val stagedApps: List<Task>,
    val visibleStagedApps: List<Task>,
) {
    data class Task(
        val id: Int,
        /** The app's package as Android names it (root activity, launch intent, affinity). */
        val packages: List<String>,
        val type: String,
        val mode: String,
        val visible: Boolean,
        /** The split stage it sits in (stage_a / stage_b / stage_c), or null. */
        val stage: String?,
        /** One of Folark's own tasks (its home screen, its split picker). */
        val own: Boolean,
    ) {
        val packageName: String get() = packages.firstOrNull().orEmpty()
        fun isApp(packageName: String): Boolean = packageName in packages
    }

    fun isRunning(taskId: Int): Boolean = tasks.any { it.id == taskId }

    fun isStaged(taskId: Int): Boolean = taskId in stagedIds

    /** The front-most task of [packageName] (the name has to match exactly: `com.foo` isn't `com.foo.bar`). */
    fun taskOf(packageName: String): Task? =
        if (packageName.isEmpty()) null else tasks.firstOrNull { it.type != "home" && it.isApp(packageName) }

    /** The app in front right now: the top visible app task that isn't Folark's or Home. */
    fun frontApp(): Task? = tasks.firstOrNull { it.visible && !it.own && it.type == "standard" }

    /**
     * The app used last, showing or not (behind Home, say): the top app task that isn't Folark's
     * or Home, and isn't [except]'s.
     */
    fun lastApp(except: String? = null): Task? =
        tasks.firstOrNull { !it.own && it.type == "standard" && (except == null || !it.isApp(except)) }

    /** The apps of the split showing now (two or three), or empty. */
    val splitShowing: List<Task> get() = if (visibleStagedApps.size >= 2) visibleStagedApps else emptyList()

    /** The apps of a split, also while it is hidden behind Recents or Home, or empty. */
    val splitStaged: List<Task> get() = if (stagedApps.size >= 2) stagedApps else emptyList()
}
