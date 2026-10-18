package org.arkikeskus.launcher.data.search

import kotlinx.coroutines.flow.first
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.SearchResult
import javax.inject.Inject

/** Quick actions from the search text ([SearchActions]); off with Settings ▸ App drawer ▸ Search. */
class ActionSearchProvider @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : SearchProvider {
    override suspend fun isEnabled(): Boolean = settingsRepository.settings(ScreenType.OUTER).first().searchActions

    override suspend fun query(query: String): List<SearchResult> = SearchActions.parse(query)
}
