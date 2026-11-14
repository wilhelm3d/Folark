package org.arkikeskus.launcher.model

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * A scope for work that must never take the launcher down: its jobs are independent (one failing
 * does not cancel the others), and a failure is logged instead of reaching the thread's uncaught
 * exception handler, which would end the process. Folark is the home screen, so an uncaught
 * exception in any of its scopes restarts home.
 */
fun safeScope(dispatcher: CoroutineDispatcher): CoroutineScope =
    CoroutineScope(SupervisorJob() + dispatcher + LogFailure)

/** Logs a coroutine's uncaught failure instead of ending the process; see [safeScope]. */
val LogFailure = CoroutineExceptionHandler { _, error -> Log.e("Folark", "background task failed", error) }
