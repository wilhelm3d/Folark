package org.arkikeskus.launcher.ui.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import org.arkikeskus.launcher.model.FolderShape
import org.arkikeskus.launcher.model.IconShape
import org.arkikeskus.launcher.model.catching

/** A shape from an outline in a 100×100 box (SVG path syntax, like an icon mask), stretched to fill. */
class OutlineShape(private val pathData: String) : Shape {
    private val unit: Path? = catching { PathParser().parsePathString(pathData).toPath() }.getOrNull()

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = unit ?: return Outline.Rectangle(size.toRect())
        val scaled = Path().apply {
            addPath(path)
            transform(Matrix().apply { scale(size.width / 100f, size.height / 100f) })
        }
        return Outline.Generic(scaled)
    }

    override fun equals(other: Any?): Boolean = other is OutlineShape && other.pathData == pathData
    override fun hashCode(): Int = pathData.hashCode()
}

/**
 * The outline of a closed folder [size] big (home and the settings preview draw the same one).
 * [FolderShape.ICON] takes the app icons' outline [iconOutline] (100×100 path data, see
 * IconShapes.pathData); without one it is the squircle.
 */
fun folderTileShape(shape: FolderShape, iconOutline: String?, size: Dp): Shape = when (shape) {
    FolderShape.CIRCLE -> CircleShape
    FolderShape.SQUARE -> RoundedCornerShape(size * (6f / 52f))
    FolderShape.ICON -> iconOutline?.let(::OutlineShape) ?: RoundedCornerShape(size * (15f / 52f))
    FolderShape.SQUIRCLE -> RoundedCornerShape(size * (15f / 52f))
}

/**
 * How far the preview of the apps sits in from the edge of a folder [size] big: further in where
 * the outline cuts the corners off (a circle, a diamond), so no app peeks out of it.
 */
fun folderTileInset(shape: FolderShape, iconShape: IconShape, size: Dp): Dp {
    val per52 = when (shape) {
        FolderShape.CIRCLE -> 10f
        FolderShape.SQUARE, FolderShape.SQUIRCLE -> 6f
        FolderShape.ICON -> when (iconShape) {
            IconShape.SQUARE, IconShape.ROUNDED, IconShape.SQUIRCLE -> 6f
            IconShape.SYSTEM -> 8f
            IconShape.DIAMOND -> 13f
            IconShape.CIRCLE, IconShape.TEARDROP, IconShape.PEBBLE, IconShape.ARCH,
            IconShape.HEXAGON, IconShape.COOKIE -> 10f
        }
    }
    return size * (per52 / 52f)
}

/** The gap between the apps of the preview, scaled with the tile like everything inside it. */
fun folderTileGap(size: Dp): Dp = size * (2f / 52f)
