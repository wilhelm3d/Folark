package org.arkikeskus.launcher.data.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.arkikeskus.launcher.model.SearchResult
import org.arkikeskus.launcher.model.SearchResults
import javax.inject.Inject

/**
 * Runs all enabled [SearchProvider]s concurrently for a query and groups their results into
 * [SearchResults]. A blank query short-circuits to empty (no provider runs); a provider that
 * throws or is disabled contributes nothing (never breaks the whole search).
 *
 * [results] hands over what is found so far each time a provider answers, so apps and the
 * calculator show at once even while a slow one (contacts, through Android's ContentResolver) is
 * still looking. [search] waits for all of them.
 */
class SearchAggregator @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards SearchProvider>,
) {
    fun results(query: String): Flow<SearchResults> {
        val q = query.trim()
        if (q.isBlank()) return flowOf(SearchResults())
        return channelFlow {
            val found = ArrayList<SearchResult>()
            val lock = Mutex()
            var left = providers.size
            // The first answer waits until every provider is in or [FIRST_ANSWER_MS] passed, so
            // the list doesn't jump around as the quick ones come in a few ms apart. After that
            // each late provider (contacts on a slow day) adds its results the moment it answers.
            var released = false
            suspend fun publishLocked() = send(group(found))
            val timer = launch {
                delay(FIRST_ANSWER_MS)
                lock.withLock { if (!released) { released = true; publishLocked() } }
            }
            providers.forEach { p ->
                // IO: several providers block on ContentResolver / PackageManager calls.
                launch(Dispatchers.IO) {
                    val list = try {
                        if (p.isEnabled()) p.query(q) else emptyList()
                    } catch (e: CancellationException) {
                        throw e // the query changed: stop, don't count it as "no results"
                    } catch (_: Throwable) {
                        emptyList()
                    }
                    lock.withLock {
                        found.addAll(list)
                        left--
                        // Sent while holding the lock, so answers can't overtake each other.
                        if (released) {
                            if (list.isNotEmpty()) publishLocked()
                        } else if (left == 0) {
                            released = true
                            timer.cancel()
                            publishLocked()
                        }
                    }
                }
            }
        }.distinctUntilChanged()
    }

    suspend fun search(query: String): SearchResults = results(query).last()

    private fun group(all: List<SearchResult>) = SearchResults(
        calc = all.filterIsInstance<SearchResult.Calculation>().firstOrNull(),
        apps = all.filterIsInstance<SearchResult.App>().map { it.app },
        settings = all.filterIsInstance<SearchResult.Setting>(),
        contacts = all.filterIsInstance<SearchResult.Contact>(),
        shortcuts = all.filterIsInstance<SearchResult.Shortcut>(),
        actions = all.filterIsInstance<SearchResult.Action>(),
    )

    private companion object {
        const val FIRST_ANSWER_MS = 120L
    }
}
