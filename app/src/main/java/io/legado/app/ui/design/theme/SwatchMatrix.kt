package io.legado.app.ui.design.theme

import androidx.annotation.StringRes
import io.legado.app.R
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 13×8 swatch lattice. Spectrum is the classic HSV rainbow; named paper
 * seeds rebuild the same grid from Oklch L × analogous hue around the seed.
 */
internal object SwatchMatrix {

    const val ROWS = 8
    const val COLS = 13
    const val HUE_COLUMNS = 12
    const val SPECTRUM_ID = "spectrum"

    val optionIds: List<String> = listOf(SPECTRUM_ID) + ReadingPaletteCatalog.seeds.map { it.id }

    @StringRes
    fun labelRes(id: String): Int = when (id) {
        SPECTRUM_ID -> R.string.ng_picker_swatch_spectrum
        else -> ReadingPaletteCatalog.labelRes(id) ?: R.string.ng_picker_swatch_spectrum
    }

    fun cells(optionId: String, alpha: Int = 255): IntArray {
        val a = alpha.coerceIn(0, 255)
        val seed = ReadingPaletteCatalog.seed(optionId)
        return if (seed == null) spectrum(a) else fromSeed(seed, a)
    }

    private fun spectrum(alpha: Int): IntArray {
        val out = IntArray(ROWS * COLS)
        var i = 0
        repeat(ROWS) { row ->
            repeat(COLS) { column ->
                out[i++] = spectrumCell(row, column, alpha)
            }
        }
        return out
    }

    private fun spectrumCell(row: Int, column: Int, alpha: Int): Int {
        if (column == HUE_COLUMNS) {
            val value = 1f - row.toFloat() / (ROWS - 1)
            val channel = (value * 255).roundToInt().coerceIn(0, 255)
            return packArgb(alpha, channel, channel, channel)
        }
        val hue = column * (360f / HUE_COLUMNS)
        val saturation = when (row) {
            0 -> 0.12f
            1 -> 0.36f
            2 -> 0.62f
            else -> 0.92f
        }
        val value = when (row) {
            0, 1, 2 -> 1f
            else -> 1f - (row - 2) * 0.145f
        }.coerceAtLeast(0.20f)
        return hsvArgb(hue, saturation, value, alpha)
    }

    /**
     * Rows are Oklch L from paper (0.96) to ink (0.16). Columns 0–11 are
     * analogous hues around [ReadingPaletteSeed.targetHue]; column 12 is
     * the matching-L gray. Chroma peaks in mid-L so paper and ink stay quiet.
     */
    private fun fromSeed(seed: ReadingPaletteSeed, alpha: Int): IntArray {
        val out = IntArray(ROWS * COLS)
        var i = 0
        repeat(ROWS) { row ->
            val t = row.toFloat() / (ROWS - 1)
            val l = 0.96f - t * 0.80f
            val midBoost = sin(Math.PI.toFloat() * t).coerceAtLeast(0.18f)
            repeat(COLS) { column ->
                if (column == HUE_COLUMNS) {
                    out[i++] = withAlpha(oklch(l, 0f, seed.targetHue), alpha)
                } else {
                    val hue = seed.targetHue + (column - 5.5f) * 8f
                    val chroma = (seed.chromaScale * (1.4f + column / 11f) * (0.35f + 0.90f * midBoost))
                        .coerceIn(0.004f, 0.09f)
                    out[i++] = withAlpha(oklch(l, chroma, ((hue % 360f) + 360f) % 360f), alpha)
                }
            }
        }
        return out
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}

private fun hsvArgb(hue: Float, saturation: Float, value: Float, alpha: Int): Int {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val c = v * s
    val hp = h / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val m = v - c
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return packArgb(
        alpha,
        ((r1 + m) * 255f).roundToInt(),
        ((g1 + m) * 255f).roundToInt(),
        ((b1 + m) * 255f).roundToInt(),
    )
}
