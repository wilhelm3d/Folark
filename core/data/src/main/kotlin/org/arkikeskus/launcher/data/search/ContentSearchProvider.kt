package org.arkikeskus.launcher.data.search

import kotlinx.coroutines.flow.first
import org.arkikeskus.launcher.data.ClipboardHistoryRepository
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.SearchResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device content search: the quick-note widget's text and the clipboard history. A match shows
 * as an action — the note line opens in a notes app (the existing NOTE action), a clipboard entry
 * is copied back with one tap ([SearchResult.Action.CLIPBOARD]). Off with the other search
 * sources (Settings ▸ App drawer ▸ Search).
 */
@Singleton
class ContentSearchProvider @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val clipboardHistoryRepository: ClipboardHistoryRepository,
) : SearchProvider {

    override suspend fun isEnabled(): Boolean =
        settingsRepository.settings(ScreenType.OUTER).first().search.actions

    override suspend fun query(query: String): List<SearchResult> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        val found = ArrayList<SearchResult>()
        // The note, one matching line per result (newest first is meaningless; keep the note's order).
        val note = settingsRepository.settings(ScreenType.OUTER).first().widgets.noteText
        note.lineSequence()
            .filter { it.isNotBlank() && it.lowercase().contains(q) }
            .take(3)
            .forEach { line ->
                found += SearchResult.Action(
                    kind = SearchResult.Action.NOTE,
                    label = line.trim().take(64),
                    detail = NOTE_DETAIL,
                    args = mapOf("text" to line.trim()),
                )
            }
        // The clipboard history, newest first (loaded on demand: no clipboard widget may have
        // started the repository's own listening yet).
        clipboardHistoryRepository.ensureLoaded()
        clipboardHistoryRepository.clips.value
            .filter { it.text.isNotBlank() && it.text.lowercase().contains(q) }
            .take(3)
            .forEach { clip ->
                found += SearchResult.Action(
                    kind = SearchResult.Action.CLIPBOARD,
                    label = clip.text.trim().take(64),
                    detail = CLIP_DETAIL,
                    args = mapOf("text" to clip.text),
                )
            }
        return found
    }

    companion object {
        const val NOTE_DETAIL = "From your note"
        const val CLIP_DETAIL = "From the clipboard — tap to copy again"
    }
}
