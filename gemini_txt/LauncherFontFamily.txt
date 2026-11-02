package org.arkikeskus.launcher.ui.theme

import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.R
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 5 Distinct Launcher Font Families:
 * 1. Default Sans-Serif
 * 2. Nothing NDot 57 (Dot Matrix)
 * 3. Nothing NType 82 (Headline)
 * 4. Space Grotesk (Modern Tech)
 * 5. Monospace & Serif
 */
object LauncherFontFamily {
    val Default = FontFamily.Default
    val Serif = FontFamily.Serif
    val Monospace = FontFamily.Monospace

    val NDot = FontFamily(
        Font(R.font.ndot_57, FontWeight.Normal),
        Font(R.font.ndot_57, FontWeight.Bold),
    )

    val NType = FontFamily(
        Font(R.font.ntype_82, FontWeight.Normal),
        Font(R.font.ntype_82, FontWeight.Bold),
    )

    val SpaceGrotesk = FontFamily(
        Font(R.font.space_grotesk, FontWeight.Normal),
        Font(R.font.space_grotesk, FontWeight.Bold),
    )

    fun resolve(key: String): FontFamily = when (key.lowercase()) {
        "ndot", "nothing_dot", "dot_matrix" -> NDot
        "ntype", "nothing_type" -> NType
        "space_grotesk", "grotesk" -> SpaceGrotesk
        "serif" -> Serif
        "monospace", "mono" -> Monospace
        else -> phoneFamily(key) ?: Default
    }

    /** Fonts that ship on Motorola phones: file and weight per style. */
    private val PHONE_FONTS: Map<String, List<Pair<String, Int>>> = mapOf(
        LauncherSettings.FONT_FAMILY_MOTO_ROOKERY to listOf(
            "RookeryNew-Regular.ttf" to 400, "RookeryNew-Medium.ttf" to 500, "RookeryNew-Bold.ttf" to 700,
        ),
        LauncherSettings.FONT_FAMILY_MOTO_WORK_SANS to listOf(
            "WorkSans-Regular.ttf" to 400, "WorkSans-Medium.ttf" to 500, "WorkSans-Bold.ttf" to 700,
        ),
        LauncherSettings.FONT_FAMILY_MOTO_EXO2 to listOf("Exo2-Regular.ttf" to 400, "Exo2-Medium.ttf" to 500),
        LauncherSettings.FONT_FAMILY_MOTO_ROBOTO_SLAB to listOf(
            "RobotoSlab-Regular.ttf" to 400, "RobotoSlab-Medium.ttf" to 500,
        ),
    )
    private const val PHONE_FONT_DIR = "/product/fonts/"
    private val phoneFamilies = ConcurrentHashMap<String, FontFamily>()

    /** Whether [key] can be used here: Folark's own fonts always, the phone's only where they exist. */
    fun isAvailable(key: String): Boolean =
        PHONE_FONTS[key]?.any { File(PHONE_FONT_DIR + it.first).canRead() } ?: true

    /** One of the phone's font families, read from its files once; null if the phone lacks it. */
    private fun phoneFamily(key: String): FontFamily? {
        phoneFamilies[key]?.let { return it }
        val files = PHONE_FONTS[key] ?: return null
        val typeface = runCatching {
            val fonts = files.mapNotNull { (name, weight) ->
                val file = File(PHONE_FONT_DIR + name)
                if (!file.canRead()) return@mapNotNull null
                runCatching { android.graphics.fonts.Font.Builder(file).setWeight(weight).build() }.getOrNull()
            }
            if (fonts.isEmpty()) return null
            val family = android.graphics.fonts.FontFamily.Builder(fonts.first())
            fonts.drop(1).forEach { family.addFont(it) }
            Typeface.CustomFallbackBuilder(family.build()).setSystemFallback("sans-serif").build()
        }.getOrNull() ?: return null
        return FontFamily(typeface).also { phoneFamilies[key] = it }
    }
}
