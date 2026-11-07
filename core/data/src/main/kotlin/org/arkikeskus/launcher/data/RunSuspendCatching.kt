package org.arkikeskus.launcher.data

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] for a suspend call: turns a failure into a [Result] the same way, but lets a
 * [CancellationException] through — a coroutine that is being cancelled stays cancelled, instead
 * of coming back as a failed result (or a wrong default from [Result.getOrDefault]) and carrying
 * on with work that was already asked to stop.
 *
 * The block is inlined, so a suspend call inside it works wherever this is called from a suspend
 * context, exactly as with [runCatching].
 */
inline fun <T> runSuspendCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
