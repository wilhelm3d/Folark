package org.arkikeskus.launcher.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.ui.expressive.Accent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette

// The two layouts of the settings (split out of SettingsScreen.kt): side by side on a wide window,
// one column (overview, then a page) on a narrow one. Both show a category's page through [page]:
// page(category, its scroll state, modifier, compact preview).

/** A settings page with its preview: the category, its scroll state, a modifier, a compact preview. */
internal typealias SettingsPageSlot = @Composable (SettingsCategory, ScrollState, Modifier, Boolean) -> Unit

/**
 * Master-detail on a wide window (an unfolded book foldable): the categories (or the search
 * results for [query]) in a sidebar, the [selected] page beside them.
 */
@Composable
internal fun SettingsTwoPane(
    profileSwitcher: @Composable () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    /** A setting picked in the search: the page it is on and its title. */
    onSearchPick: (SettingsCategory, String) -> Unit,
    detailScroll: ScrollState,
    page: SettingsPageSlot,
) {
    val palette = LocalExpressivePalette.current
    Row(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Left Master Sidebar Pane
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                color = palette.text,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 12.dp, end = 4.dp),
            )

            profileSwitcher()

            SearchFieldHeader(query = query, onQueryChange = onQueryChange, onClose = { onQueryChange("") })
            if (query.isNotBlank()) {
                val any = SettingsSearchResults(query, onSearchPick)
                if (!any) {
                    Text(
                        text = stringResource(R.string.settings_no_results),
                        color = palette.faint,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else SettingsCategory.entries.forEach { category ->
                // A heading above the first page of each group.
                val group = category.group
                if (group != null && SettingsCategory.entries.first { it.group == group } == category) {
                    CategoryGroupHeading(stringResource(group.titleRes))
                }
                SidebarCategoryRow(category, isSelected = selected == category, onClick = { onSelect(category) })
            }
        }

        // Right Detail Pane
        page(selected, detailScroll, Modifier.weight(1f).fillMaxHeight(), false)
    }
}

/** One category in the two-pane sidebar. */
@Composable
private fun SidebarCategoryRow(category: SettingsCategory, isSelected: Boolean, onClick: () -> Unit) {
    val palette = LocalExpressivePalette.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Accent.copy(alpha = 0.2f) else palette.surfaceHi,
        border = BorderStroke(1.dp, if (isSelected) Accent else Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = if (isSelected) Accent else palette.text,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(category.titleRes),
                color = if (isSelected) palette.text else palette.faint,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/**
 * One column on a narrow window (outer screens and flip-phone inner screens): the overview of the
 * categories, or the [selected] category's page with a back button. Both carry the search toggle.
 */
@Composable
internal fun SettingsOnePane(
    profileSwitcher: @Composable () -> Unit,
    selected: SettingsCategory?,
    onSelect: (SettingsCategory?) -> Unit,
    searchActive: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    /** A setting picked in the search: the page it is on and its title. */
    onSearchPick: (SettingsCategory, String) -> Unit,
    overviewScroll: ScrollState,
    pageScroll: ScrollState,
    page: SettingsPageSlot,
) {
    val palette = LocalExpressivePalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
    ) {
        if (selected == null) {
            // Main Category Overview List
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_title),
                    color = palette.text,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                )
                RoundIconButton(Icons.Rounded.Search, stringResource(R.string.settings_search), onToggleSearch)
            }

            AnimatedSearchField(searchActive, query, onQueryChange, onCloseSearch)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(overviewScroll)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                profileSwitcher()
                val queryLower = query.trim().lowercase()
                val filteredCategories = if (queryLower.isBlank()) {
                    SettingsCategory.entries
                } else {
                    SettingsCategory.entries.filter { cat ->
                        stringResource(cat.titleRes).lowercase().contains(queryLower) ||
                            stringResource(cat.subtitleRes).lowercase().contains(queryLower)
                    }
                }

                filteredCategories.forEach { category ->
                    // Grouped while not searching: a heading above each group's first page.
                    val group = category.group
                    if (queryLower.isBlank() && group != null && filteredCategories.first { it.group == group } == category) {
                        CategoryGroupHeading(stringResource(group.titleRes))
                    }
                    CategoryOverviewCard(
                        category = category,
                        onClick = { onSelect(category) },
                    )
                }
                // Single settings matching the search, below the matching pages.
                val anySetting = queryLower.isNotBlank() && SettingsSearchResults(query, onSearchPick)
                if (filteredCategories.isEmpty() && !anySetting) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_no_results),
                            color = palette.faint,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        } else {
            // Phone Category Subpage View
            BackHandler { onSelect(null) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.settings_close)) { onSelect(null) }
                    Text(
                        text = stringResource(selected.titleRes),
                        color = palette.text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                    )
                }
                RoundIconButton(Icons.Rounded.Search, stringResource(R.string.settings_search), onToggleSearch)
            }

            AnimatedSearchField(searchActive, query, onQueryChange, onCloseSearch)

            page(selected, pageScroll, Modifier.fillMaxSize(), true)
        }
    }
}

/** A round 42 dp button with one icon (back, search) in the one-column headers. */
@Composable
private fun RoundIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val palette = LocalExpressivePalette.current
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(palette.surfaceHi)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = palette.text,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** The search field under a one-column header, faded in while the search is open. */
@Composable
private fun ColumnScope.AnimatedSearchField(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        SearchFieldHeader(query = query, onQueryChange = onQueryChange, onClose = onClose)
    }
}

@Composable
private fun SearchFieldHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val palette = LocalExpressivePalette.current
    val shape = RoundedCornerShape(18.dp)
    // The field draws its own rounded outline (no clipping box around it), so no corner is cut off.
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.settings_search_hint), color = palette.faint) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Accent) },
        trailingIcon = {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .padding(8.dp),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = palette.dim)
            }
        },
        singleLine = true,
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = palette.surfaceHi,
            unfocusedContainerColor = palette.surfaceHi,
            focusedBorderColor = Accent.copy(alpha = 0.8f),
            unfocusedBorderColor = palette.faint.copy(alpha = 0.25f),
            focusedTextColor = palette.text,
            unfocusedTextColor = palette.text,
            cursorColor = Accent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    )
}

/** The name of a group of settings pages, above its first page in the list. */
@Composable
private fun CategoryGroupHeading(text: String) {
    Text(
        text = text.uppercase(),
        color = LocalExpressivePalette.current.dim,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun CategoryOverviewCard(
    category: SettingsCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalExpressivePalette.current

    Surface(
        onClick = onClick,
        color = palette.surfaceHi,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = palette.shadow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(24.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(category.titleRes),
                    color = palette.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(category.subtitleRes),
                    color = palette.dim,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                )
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = palette.faint,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
