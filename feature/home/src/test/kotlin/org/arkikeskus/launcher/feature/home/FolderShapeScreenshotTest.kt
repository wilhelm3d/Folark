package org.arkikeskus.launcher.feature.home

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.arkikeskus.launcher.model.FolderShape
import org.arkikeskus.launcher.model.FolderTheme
import org.arkikeskus.launcher.model.IconShape
import org.arkikeskus.launcher.ui.component.LocalIconShape
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Closed home folders in each shape, "Like icons" with three icon shapes, on both displays: the
 * outline and how far the preview sits in must not change by accident.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class FolderShapeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private var iconShape by mutableStateOf(IconShape.CIRCLE)

    private fun check(name: String) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIconShape provides iconShape) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.testTag("folders").background(Color(0xFF202124)).padding(12.dp),
                    ) {
                        for (shape in FolderShape.entries) {
                            CompositionLocalProvider(LocalFolderStyle provides FolderStyle(shape = shape, theme = FolderTheme.LIGHT)) {
                                FolderIcon(name = shape.stored, apps = emptyList(), showLabel = true, badgeCount = 0, badgeShowCount = false)
                            }
                        }
                    }
                }
            }
        }
        for (shape in listOf(IconShape.CIRCLE, IconShape.COOKIE, IconShape.SQUARE)) {
            iconShape = shape
            compose.waitForIdle()
            Screenshots.verify(compose.onNodeWithTag("folders"), "folders_${name}_${shape.stored}")
        }
    }

    @Test @Config(qualifiers = Screenshots.OUTER)
    fun `folder shapes on the outer display`() = check("outer")

    @Test @Config(qualifiers = Screenshots.INNER)
    fun `folder shapes on the inner display`() = check("inner")
}
