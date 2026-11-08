package org.arkikeskus.launcher.model

import kotlin.coroutines.cancellation.CancellationException

/**
 * [catching] that never swallows coroutine cancellation.
 *
 * Kotlin's [catching] catches [Throwable], so a coroutine cancelled while inside the block
 * keeps running with a failed [Result] instead of stopping: a cancelled scope then leaks work
 * past its lifetime (a refresh loop that keeps rebuilding state until the process dies). Every
 * call site in this project uses [catching] so a cancelled coroutine really ends. Outside a
 * coroutine the behaviour is identical to [catching].
 */
inline fun <T> catching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error)
    }
