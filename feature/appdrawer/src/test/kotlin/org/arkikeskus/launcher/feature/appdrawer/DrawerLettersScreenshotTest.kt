package org.arkikeskus.launcher.feature.appdrawer

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.arkikeskus.launcher.model.AzPosition
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The app list's letters on both displays: Compact letters (each letter one cell before its apps,
 * the grid running on) and the A-Z bar with the letters that have apps drawn clearly.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class DrawerLettersScreenshotTest {
    @get:Rule val compose = createComposeRule()

    /** A, three apps, B, two apps, C, one app — grey discs stand in for the icons. */
    private val cells = listOf('A', null, null, null, 'B', null, null, 'C', null)
    private val icon = 52.dp

    private fun check(name: String) {
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(Modifier.testTag("drawer").background(Color(0xFF121212))) {
                    LazyVerticalGrid(GridCells.Adaptive(76.dp), Modifier.fillMaxWidth().height(200.dp)) {
                        items(cells.size) { i ->
                            val letter = cells[i]
                            if (letter != null) {
                                DrawerLetterTile(letter, icon)
                            } else {
                                Box(Modifier.height(icon), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(icon).background(Color(0xFF3C4043), CircleShape))
                                }
                            }
                        }
                    }
                    DrawerIndexBar(
                        position = AzPosition.HORIZONTAL_BOTTOM,
                        onLetterSelected = {},
                        available = setOf('A', 'B', 'C', 'M', 'W'),
                    )
                }
            }
        }
        Screenshots.verify(compose.onNodeWithTag("drawer"), "drawer_letters_$name")
    }

    @Test @Config(qualifiers = Screenshots.OUTER)
    fun `letters on the outer display`() = check("outer")

    @Test @Config(qualifiers = Screenshots.INNER)
    fun `letters on the inner display`() = check("inner")
}
