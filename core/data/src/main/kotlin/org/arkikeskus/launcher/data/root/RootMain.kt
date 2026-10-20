package org.arkikeskus.launcher.data.root

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import java.io.File

/**
 * Runs as root through `app_process` (see [RootTasks]), outside the app, so it may ask Android's
 * task manager for what only the system may do. Kept small and fixed:
 * - `remove-task <id>`: closes one app, as swiping its Recents card away does.
 * - `snapshots <dir> <id>...`: Android's own screenshot of each task (the picture the system
 *   Recents shows, from memory or disk), saved as `<dir>/<id>.jpg` for Folark to read.
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
                        val removed = taskManager().javaClass
                            .getMethod("removeTask", Int::class.javaPrimitiveType)
                            .invoke(taskManager(), taskId)
                        if (removed == true) OK else "not removed"
                    }
                    "remove-tasks" -> {
                        val manager = taskManager()
                        val remove = manager.javaClass.getMethod("removeTask", Int::class.javaPrimitiveType)
                        val ids = args.drop(1).mapNotNull { it.toIntOrNull() }
                        val removed = ids.count { id -> runCatching { remove.invoke(manager, id) == true }.getOrDefault(false) }
                        "$OK $removed"
                    }
                    "snapshots" -> {
                        val dir = File(args.getOrNull(1) ?: error("no dir"))
                        dir.mkdirs()
                        var saved = 0
                        for (id in args.drop(2).mapNotNull { it.toIntOrNull() }) {
                            if (runCatching { saveSnapshot(id, File(dir, "$id.jpg")) }.getOrDefault(false)) saved++
                        }
                        "$OK $saved"
                    }
                    else -> "unknown"
                }
            }.getOrElse { "error: ${it.javaClass.simpleName}" }
            println(result)
            System.exit(0)
        }

        private fun taskManager(): Any = Class.forName("android.app.ActivityTaskManager")
            .getMethod("getService")
            .invoke(null) ?: error("no task manager")

        /**
         * `getTaskSnapshot(taskId, isLowResolution, ...)`: its parameters differ between Android
         * versions, so it is found by name and given the task, "small copy", and false for the rest.
         */
        private fun snapshot(taskId: Int, lowRes: Boolean): Any? {
            val manager = taskManager()
            val method = manager.javaClass.methods
                .filter { it.name == "getTaskSnapshot" && it.parameterTypes.firstOrNull() == Int::class.javaPrimitiveType }
                .minByOrNull { it.parameterTypes.size } ?: return null
            val params: List<Any?> = method.parameterTypes.mapIndexed { i, type ->
                when {
                    i == 0 -> taskId
                    type == Boolean::class.javaPrimitiveType -> if (i == 1) lowRes else false
                    type == Int::class.javaPrimitiveType -> 0
                    else -> null
                } as Any?
            }
            return method.invoke(manager, *params.toTypedArray())
        }

        private fun saveSnapshot(taskId: Int, out: File): Boolean {
            val snap = snapshot(taskId, lowRes = true) ?: snapshot(taskId, lowRes = false) ?: return false
            val buffer = snap.javaClass.getMethod("getHardwareBuffer").invoke(snap) as? HardwareBuffer ?: return false
            val space = runCatching { snap.javaClass.getMethod("getColorSpace").invoke(snap) as? ColorSpace }.getOrNull()
                ?: ColorSpace.get(ColorSpace.Named.SRGB)
            val hardware = Bitmap.wrapHardwareBuffer(buffer, space) ?: return false
            val bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false) ?: return false
            // Thumbnails: at most 720 px tall is plenty.
            val scaled = if (bitmap.height > MAX_EDGE) {
                Bitmap.createScaledBitmap(bitmap, bitmap.width * MAX_EDGE / bitmap.height, MAX_EDGE, true)
            } else bitmap
            out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            out.setReadable(true, false)
            return true
        }

        private const val MAX_EDGE = 720
    }
}
