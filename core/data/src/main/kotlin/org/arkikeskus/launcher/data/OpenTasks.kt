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
        return parse(text, context.packageName)
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
    private val INACTIVE = Regex("""inactive for (\d+)(ms|s|m|h|d)?""")
}
