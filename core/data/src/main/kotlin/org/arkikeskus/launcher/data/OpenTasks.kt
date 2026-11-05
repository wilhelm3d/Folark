package org.arkikeskus.launcher.data

/** An app that is open now, as Android's list of recent tasks has it. */
data class OpenTask(
    val taskId: Int,
    val packageName: String,
    /** The task's main activity, when Android printed it. */
    val className: String?,
    val userId: Int,
    /** How long ago it was last in front, in ms (null when Android didn't say). */
    val inactiveMs: Long?,
    /** "fullscreen", "multi-window" (split), "freeform" (pop-up), or null. */
    val windowingMode: String?,
    /** Where the task sits on screen (left, top, right, bottom) as Android last laid it out, or null. */
    val bounds: List<Int>? = null,
)

/**
 * Reads Android's list of recent tasks (`dumpsys activity recents`, allowed by the one-time DUMP
 * grant) into [OpenTask]s, newest first. Only ordinary app tasks: home, Recents and Folark itself
 * are left out.
 */
object OpenTasks {

    /** The open apps, newest first; null without the DUMP grant (or when Android refused). */
    fun list(context: android.content.Context): List<OpenTask>? {
        val text = ShellSplit.dump(context, arrayOf("recents")) ?: return null
        val tasks = parse(text, context.packageName)
        val problem = formatProblem(text, tasks)
        if (problem != null && formatProblem == null) {
            android.util.Log.w("OpenTasks", "dumpsys activity recents not understood: $problem")
        }
        formatProblem = problem
        return tasks
    }

    /**
     * Why the last read of the task list looked unreadable (Android printed tasks in a form this
     * parser doesn't know, e.g. after a system update), or null when it read fine. An empty list
     * with a problem set means "can't tell", not "nothing open". Shown in Recents and in
     * Settings ▸ Recents ▸ Check split screen.
     */
    @Volatile
    var formatProblem: String? = null
        private set

    /** Pure check, for tests: tasks are in [text] but [parsed] found none of them. */
    fun formatProblem(text: String, parsed: List<OpenTask>): String? {
        if (parsed.isNotEmpty()) return null
        val blocks = text.lineSequence().count { it.contains("Recent #") }
        val taskLines = TASK_ID.findAll(text).count() + TASK_ID_FIELD.findAll(text).count()
        return when {
            // "Recent #" blocks whose task id, type or package can't be read. Blocks that are all
            // home/Recents (type≠standard) or Folark itself are a real empty list, not a problem.
            blocks > 0 && text.lineSequence().filter { it.contains("Recent #") }
                .none { TASK_ID.containsMatchIn(it) || TYPE.containsMatchIn(it) } ->
                "$blocks task entries without a readable id or type"
            blocks == 0 && taskLines > 0 -> "$taskLines tasks listed, but no \"Recent #\" entries"
            else -> null
        }
    }

    /**
     * The activity in front right now ("package/class"), from Android's activity list; null
     * without the DUMP grant or when it can't be told.
     */
    fun topResumed(context: android.content.Context): String? {
        val text = ShellSplit.dump(context, arrayOf("activities")) ?: return null
        val m = TOP_RESUMED.find(text) ?: return null
        val pkg = m.groupValues[1]
        val cls = m.groupValues[2].let { if (it.startsWith(".")) pkg + it else it }
        return "$pkg/$cls"
    }

    /** Pure parsing, for tests. */
    fun parse(text: String, ownPackage: String): List<OpenTask> {
        val blocks = mutableListOf<StringBuilder>()
        for (line in text.lineSequence()) {
            if (line.contains("Recent #")) blocks += StringBuilder()
            blocks.lastOrNull()?.appendLine(line)
        }
        val out = LinkedHashMap<Int, OpenTask>()
        for (block in blocks) {
            val body = block.toString()
            val header = body.lineSequence().first()
            // Only ordinary app tasks (not home, Recents, assistant or dream).
            val type = TYPE.find(header)?.groupValues?.get(1)
            if (type != null && type != "standard") continue
            val id = TASK_ID.find(header)?.groupValues?.get(1)?.toIntOrNull()
                ?: TASK_ID_FIELD.find(body)?.groupValues?.get(1)?.toIntOrNull()
                ?: continue
            val component = COMPONENT.find(body)?.groupValues?.get(1)
                ?: REAL_ACTIVITY.find(body)?.groupValues?.get(1)
                ?: INTENT_CMP.find(body)?.groupValues?.get(1)
            val pkg = component?.substringBefore('/')
                ?: AFFINITY.find(header)?.groupValues?.get(1)
                ?: continue
            if (pkg == ownPackage) continue
            val cls = component?.substringAfter('/', "")?.takeIf { it.isNotEmpty() }?.let {
                if (it.startsWith(".")) pkg + it else it
            }
            val user = USER.find(header)?.groupValues?.get(1)?.toIntOrNull()
                ?: USER_FIELD.find(body)?.groupValues?.get(1)?.toIntOrNull()
                ?: 0
            out.putIfAbsent(
                id,
                OpenTask(
                    taskId = id,
                    packageName = pkg,
                    className = cls,
                    userId = user,
                    inactiveMs = inactive(body),
                    windowingMode = MODE.find(header)?.groupValues?.get(1),
                    bounds = BOUNDS.find(body)?.groupValues?.drop(1)?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 4 },
                ),
            )
        }
        return out.values.toList()
    }

    /** "lastActiveTime=123 (inactive for 42s)" → 42 000 ms. */
    private fun inactive(body: String): Long? {
        val m = INACTIVE.find(body) ?: return null
        val value = m.groupValues[1].toLongOrNull() ?: return null
        return when (m.groupValues[2]) {
            "ms" -> value
            "s" -> value * 1_000
            "m" -> value * 60_000
            "h" -> value * 3_600_000
            "d" -> value * 86_400_000
            else -> value * 1_000
        }
    }

    private val TOP_RESUMED = Regex("""(?:topResumedActivity|ResumedActivity)\s*[=:]\s*ActivityRecord\{\S+ u\d+ ([\w.]+)/([\w.$]+)""")
    private val TASK_ID = Regex("""Task\{\S+ #(\d+)""")
    private val TASK_ID_FIELD = Regex("""\btaskId=(\d+)""")
    private val TYPE = Regex("""\btype=(\w+)""")
    private val MODE = Regex("""\bmode=([\w-]+)""")
    private val USER = Regex("""\bU=(\d+)""")
    private val USER_FIELD = Regex("""\buserId=(\d+)""")
    private val AFFINITY = Regex("""\bA=\d+:([\w.]+)""")
    private val COMPONENT = Regex("""mActivityComponent=([\w.]+/[\w.$]+)""")
    private val REAL_ACTIVITY = Regex("""realActivity=\{?([\w.]+/[\w.$]+)""")
    private val INTENT_CMP = Regex("""\bcmp=([\w.]+/[\w.$]+)""")
    /** A task's laid-out bounds in its configuration: `mBounds=Rect(0, 0 - 600, 2000)`. */
    private val BOUNDS = Regex("""mBounds=Rect\((-?\d+), (-?\d+) - (-?\d+), (-?\d+)\)""")
    private val INACTIVE = Regex("""inactive for (\d+)(ms|s|m|h|d)?""")
}
