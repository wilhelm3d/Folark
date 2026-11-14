package org.arkikeskus.launcher.feature.appdrawer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Canvas
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import java.io.File
import kotlin.math.abs
import org.junit.Assert.fail

/**
 * Screenshot checks without an extra library: a node is drawn by Robolectric's native graphics,
 * captured, and compared with its reference PNG in `src/test/screenshots`. A missing reference is
 * written (and the check passes); `-Pfolark.recordScreenshots` rewrites all of them after an
 * intended change of look. The outer and inner display sizes are the qualifiers [OUTER] and [INNER].
 */
internal object Screenshots {
    /** The razr fold's cover screen and its inner screen, in dp at its 420 dpi. */
    const val OUTER = "en-w411dp-h960dp-420dpi"
    const val INNER = "en-w850dp-h946dp-420dpi"

    private val dir = File("src/test/screenshots")
    private val record = System.getProperty("folark.recordScreenshots") == "true"

    /** Pixels may differ by this much per channel (anti-aliasing), and this share of them at all. */
    private const val CHANNEL_TOLERANCE = 12
    private const val MAX_DIFFERING_SHARE = 0.002

    fun verify(node: SemanticsNodeInteraction, name: String) {
        val actual = capture(node)
        val file = File(dir, "$name.png")
        if (record || !file.exists()) {
            dir.mkdirs()
            file.outputStream().use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return
        }
        val expected = BitmapFactory.decodeFile(file.path) ?: return fail("$name: unreadable reference $file")
        if (expected.width != actual.width || expected.height != actual.height) {
            save(actual, name)
            fail("$name: ${actual.width}×${actual.height}, reference ${expected.width}×${expected.height}")
        }
        var differing = 0
        for (y in 0 until actual.height) for (x in 0 until actual.width) {
            val a = actual.getPixel(x, y)
            val e = expected.getPixel(x, y)
            if (abs(Color.red(a) - Color.red(e)) > CHANNEL_TOLERANCE ||
                abs(Color.green(a) - Color.green(e)) > CHANNEL_TOLERANCE ||
                abs(Color.blue(a) - Color.blue(e)) > CHANNEL_TOLERANCE ||
                abs(Color.alpha(a) - Color.alpha(e)) > CHANNEL_TOLERANCE
            ) differing++
        }
        val share = differing.toDouble() / (actual.width * actual.height)
        if (share > MAX_DIFFERING_SHARE) {
            save(actual, name)
            fail("$name: ${"%.2f".format(share * 100)} % of the pixels differ from $file (actual in build/screenshots)")
        }
    }

    /**
     * The node drawn in software: its window's views onto a canvas, cut to the node's bounds.
     * (captureToImage waits for a hardware redraw that Robolectric never reports.)
     */
    private fun capture(node: SemanticsNodeInteraction): Bitmap {
        val semantics = node.fetchSemanticsNode()
        val view = (semantics.root as ViewRootForTest).view.rootView
        val whole = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(whole))
        val b = semantics.boundsInWindow
        val left = b.left.toInt().coerceIn(0, whole.width - 1)
        val top = b.top.toInt().coerceIn(0, whole.height - 1)
        val width = b.width.toInt().coerceIn(1, whole.width - left)
        val height = b.height.toInt().coerceIn(1, whole.height - top)
        return Bitmap.createBitmap(whole, left, top, width, height)
    }

    /** The new picture beside the build output, to compare by eye with the reference. */
    private fun save(bitmap: Bitmap, name: String) {
        val out = File("build/screenshots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
