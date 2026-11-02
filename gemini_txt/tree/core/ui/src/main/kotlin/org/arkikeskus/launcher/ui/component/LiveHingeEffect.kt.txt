package org.arkikeskus.launcher.ui.component

import android.graphics.Camera
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import org.arkikeskus.launcher.model.LauncherSettings
import kotlin.math.hypot
import kotlin.math.pow

/**
 * The live-hinge unfold effects (Settings ▸ Inner display ▸ Fold transition ▸ Live hinge), drawn
 * from [progress] (0 = the inner screen just came on, 1 = fully open; read while drawing).
 *
 * [style] is one of LauncherSettings.LIVE_HINGE_STYLE_*. On top of any style: [blurDp] of blur and a
 * [frost] veil (0..1) that both clear as the phone opens. Home draws normally once [progress] is 1.
 */
fun Modifier.liveHingeEffect(progress: () -> Float, style: String, blurDp: Float, frost: Float): Modifier = this
    .graphicsLayer {
        val t = progress()
        if (t >= 1f) return@graphicsLayer
        val e = 1f - t
        when (style) {
            LauncherSettings.LIVE_HINGE_STYLE_DROP -> {
                // Falls in from above, as if dropping from the outer screen onto the inner one.
                cameraDistance = 16f * density
                translationY = -e * e * size.height * 0.6f
                rotationX = e * 16f
                alpha = 0.25f + 0.75f * t
            }
            LauncherSettings.LIVE_HINGE_STYLE_ZOOM -> {
                scaleX = 1f + 0.25f * e
                scaleY = 1f + 0.25f * e
                alpha = 0.3f + 0.7f * t
            }
            LauncherSettings.LIVE_HINGE_STYLE_FADE -> {
                scaleX = 0.96f + 0.04f * t
                scaleY = 0.96f + 0.04f * t
            }
            LauncherSettings.LIVE_HINGE_STYLE_PAGES -> alpha = 0.35f + 0.65f * t
            else -> Unit
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            renderEffect = hingeRenderEffect(style, e, blurDp * density * e, size.width, size.height)
                ?.asComposeRenderEffect()
        }
    }
    .drawWithCache {
        val camera = Camera()
        val matrix = Matrix()
        val portal = Path()
        onDrawWithContent {
            val t = progress()
            if (t >= 1f) {
                drawContent()
                return@onDrawWithContent
            }
            val e = 1f - t
            val hingeX = size.width / 2f
            when (style) {
                LauncherSettings.LIVE_HINGE_STYLE_PAGES -> {
                    // Two pages folding flat around the hinge.
                    val tilt = e * MAX_TILT_DEG
                    clipRect(right = hingeX) { this@onDrawWithContent.page(camera, matrix, hingeX, tilt) }
                    clipRect(left = hingeX) { this@onDrawWithContent.page(camera, matrix, hingeX, -tilt) }
                }
                LauncherSettings.LIVE_HINGE_STYLE_DOORS -> {
                    // Two halves sliding in from the sides and meeting at the hinge.
                    val shift = e * hingeX
                    clipRect(right = hingeX) { translate(left = -shift) { this@onDrawWithContent.drawContent() } }
                    clipRect(left = hingeX) { translate(left = shift) { this@onDrawWithContent.drawContent() } }
                }
                LauncherSettings.LIVE_HINGE_STYLE_PORTAL -> {
                    // A circle opening from the hinge, with a glowing rim.
                    val center = Offset(hingeX, size.height / 2f)
                    val radius = t * hypot(size.width, size.height) / 2f
                    portal.reset()
                    portal.addOval(androidx.compose.ui.geometry.Rect(center, radius))
                    clipPath(portal) { this@onDrawWithContent.drawContent() }
                    drawCircle(Color.White.copy(alpha = 0.55f * e), radius, center, style = Stroke(width = 3f * density))
                }
                else -> drawContent()
            }
            if (style == LauncherSettings.LIVE_HINGE_STYLE_FADE) drawRect(Color.Black.copy(alpha = e.pow(1.4f)))
            if (frost > 0f) drawRect(FROST.copy(alpha = (frost * e).coerceIn(0f, 1f)))
        }
    }

/** How long the timed version (outer display, after closing) takes at [speed]. */
fun liveHingeTimedMs(speed: Float): Int = (TIMED_EFFECT_MS / speed.coerceIn(0.1f, 4f)).toInt().coerceIn(250, 6_500)

private const val TIMED_EFFECT_MS = 650f

private fun ContentDrawScope.page(camera: Camera, matrix: Matrix, hingeX: Float, degrees: Float) {
    drawIntoCanvas { canvas ->
        camera.save()
        camera.setLocation(0f, 0f, -CAMERA_DISTANCE_INCHES)
        camera.rotateY(degrees)
        camera.getMatrix(matrix)
        camera.restore()
        matrix.preTranslate(-hingeX, -size.height / 2f)
        matrix.postTranslate(hingeX, size.height / 2f)
        canvas.save()
        canvas.nativeCanvas.concat(matrix)
        drawContent()
        canvas.restore()
    }
}

/** The style's own effect (water ripple, black-and-white to colour) chained with the blur. */
@RequiresApi(Build.VERSION_CODES.S)
private fun hingeRenderEffect(style: String, e: Float, blurPx: Float, width: Float, height: Float): RenderEffect? {
    val own: RenderEffect? = when (style) {
        LauncherSettings.LIVE_HINGE_STYLE_WATER ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) water(e, width, height) else null
        LauncherSettings.LIVE_HINGE_STYLE_INK -> {
            // Starts black and white and dark, colour flows in as the phone opens.
            val m = inkMatrix.apply { setSaturation(1f - e) }
            val dim = 1f - 0.55f * e
            m.postConcat(inkDim.apply { setScale(dim, dim, dim, 1f) })
            RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(m))
        }
        else -> null
    }
    val blur = if (blurPx > 0.5f) RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.DECAL) else null
    return when {
        own != null && blur != null -> RenderEffect.createChainEffect(blur, own)
        else -> own ?: blur
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun water(e: Float, width: Float, height: Float): RenderEffect {
    val shader = waterShader
    shader.setFloatUniform("size", width, height)
    // Driven by the hinge, not a clock: the rings travel outwards as the phone opens.
    shader.setFloatUniform("phase", (1f - e) * 14f)
    shader.setFloatUniform("amp", e * 26f)
    return RenderEffect.createRuntimeShaderEffect(shader, "content")
}

/** Rings spreading from the hinge, bending the picture like water; flat once [amp] is 0. */
@delegate:RequiresApi(Build.VERSION_CODES.TIRAMISU)
private val waterShader: RuntimeShader by lazy(LazyThreadSafetyMode.NONE) {
    RuntimeShader(
        """
        uniform shader content;
        uniform float2 size;
        uniform float phase;
        uniform float amp;
        half4 main(float2 p) {
            float2 c = size * 0.5;
            float2 d = p - c;
            float r = length(d) + 0.001;
            float wave = sin(r * 0.045 - phase) * amp * exp(-r * 0.0011);
            return content.eval(p + (d / r) * wave);
        }
        """.trimIndent(),
    )
}

/** Reused every frame of the Ink style (only touched from the UI thread while drawing). */
private val inkMatrix = ColorMatrix()
private val inkDim = ColorMatrix()

private const val MAX_TILT_DEG = 32f
private const val CAMERA_DISTANCE_INCHES = 18f
private val FROST = Color(0xFFDDE4EC)
