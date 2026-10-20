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
                            val ok = runCatching { saveSnapshot(id, File(dir, "$id.jpg")) }
                                .onFailure { System.err.println("task $id: ${it.javaClass.name}: ${it.message} ${(it.cause ?: it).stackTrace.take(3).joinToString(" | ")}") }
                                .getOrDefault(false)
                            if (ok) saved++ else System.err.println("task $id: no picture")
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
        /**
         * Where Android hands out task screenshots: the task manager itself (up to Android 16), or
         * the separate snapshot manager it gives out (`getTaskSnapshotManager()`, Android 17).
         */
        private fun snapshotSources(): List<Any> {
            val manager = taskManager()
            val sources = mutableListOf(manager)
            val getter = manager.javaClass.methods.firstOrNull { it.name == "getTaskSnapshotManager" && it.parameterTypes.isEmpty() }
            if (getter != null) {
                val got = runCatching { getter.invoke(manager) }.getOrNull()
                val service = if (got is android.os.IBinder) asInterface(got) else got
                if (service != null) sources.add(0, service)
            }
            return sources
        }

        /** An AIDL binder as its interface (`<descriptor>$Stub.asInterface`). */
        private fun asInterface(binder: android.os.IBinder): Any? = runCatching {
            val descriptor = binder.interfaceDescriptor ?: return null
            Class.forName("$descriptor\$Stub").getMethod("asInterface", android.os.IBinder::class.java).invoke(null, binder)
        }.getOrNull()

        /** What the last snapshot call threw, for helper.log. */
        private var lastError: Throwable? = null

        /**
         * `getTaskSnapshot(taskId, isLowResolution, ...)` on whichever source has it: its parameters
         * differ between Android versions, so it is found by name and given the task, "small copy",
         * false for any further switch, 0 for any further number.
         */
        private fun snapshot(taskId: Int, lowRes: Boolean): Any? {
            for (source in snapshotSources()) {
                val methods = source.javaClass.methods
                    .filter {
                        it.name.startsWith("getTaskSnapshot") && it.name != "getTaskSnapshotManager" &&
                            it.parameterTypes.firstOrNull() == Int::class.javaPrimitiveType
                    }
                    .sortedBy { it.parameterTypes.size }
                for (method in methods) {
                    var firstBoolean = true
                    val params: List<Any?> = method.parameterTypes.mapIndexed { i, type ->
                        when {
                            i == 0 -> taskId
                            type == Boolean::class.javaPrimitiveType -> (if (firstBoolean) lowRes else false).also { firstBoolean = false }
                            type == Int::class.javaPrimitiveType -> 0
                            type == Long::class.javaPrimitiveType -> 0L
                            else -> null
                        } as Any?
                    }
                    val result = runCatching { method.invoke(source, *params.toTypedArray()) }
                        .onFailure { lastError = (it as? java.lang.reflect.InvocationTargetException)?.targetException ?: it }
                        .getOrNull()
                    if (result != null) return result
                }
            }
            return null
        }

        /** The snapshot methods each source offers, for helper.log. */
        private fun describeSources(): String = snapshotSources().joinToString(" ; ") { source ->
            source.javaClass.name + ": " + source.javaClass.methods
                .filter { it.name.contains("Snapshot") }
                .joinToString { m -> m.name + m.parameterTypes.joinToString(",", "(", ")") { it.simpleName } }
        }

        private fun saveSnapshot(taskId: Int, out: File): Boolean {
            val snap = snapshot(taskId, lowRes = true) ?: snapshot(taskId, lowRes = false)
            if (snap == null) {
                System.err.println("task $taskId: Android has no snapshot. Last error: $lastError. Sources: " + describeSources())
                return false
            }
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
