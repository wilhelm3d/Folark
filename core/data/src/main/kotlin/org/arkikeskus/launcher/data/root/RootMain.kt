package org.arkikeskus.launcher.data.root

/**
 * Runs as root through `app_process` (see [RootTasks.removeTask]), outside the app, so it may ask
 * Android's task manager for what only the system may do. Kept small and fixed: one command, one
 * number.
 */
class RootMain {
    companion object {
        const val OK = "folark-ok"

        @JvmStatic
        fun main(args: Array<String>) {
            val result = runCatching {
                when (args.getOrNull(0)) {
                    "remove-task" -> {
                        val taskId = args.getOrNull(1)?.toIntOrNull() ?: error("no task")
                        val manager = Class.forName("android.app.ActivityTaskManager")
                            .getMethod("getService")
                            .invoke(null) ?: error("no task manager")
                        val removed = manager.javaClass
                            .getMethod("removeTask", Int::class.javaPrimitiveType)
                            .invoke(manager, taskId)
                        if (removed == true) OK else "not removed"
                    }
                    else -> "unknown"
                }
            }.getOrElse { "error: ${it.javaClass.simpleName}" }
            println(result)
        }
    }
}
