package io.legado.app.help.config

/**
 * 中文书西文光学比例。字号仍是一个用户设置；这里只决定 Latin run 的内部乘数。
 *
 * 自动把拉丁光学高度校准到汉字墨高的一个比例（见 [INK_PROPORTION]），不是把两者的墨高拉齐。
 */
object LatinOpticalScale {
    const val MIN = 0.80f
    const val MAX = 1.20f

    /**
     * 拉丁光学高度相对汉字墨高的校准系数，不是用户设置。
     * 初值 0.72。目的是让西文略小于汉字、看起来协调，而不是把两者的墨高拉齐。
     * 实页对比后可在约 0.70–0.75 内改这个常数。
     */
    const val INK_PROPORTION = 0.72f

    fun clamp(value: Float): Float {
        if (!value.isFinite()) return 1f
        return value.coerceIn(MIN, MAX)
    }

    /**
     * 同一 em 下测得的墨高。
     * [cjkBody] 是汉字墨高，[latinCap] 是大写墨高，[latinX] 是 x 高。
     * 拉丁光学尺寸以大写为主（0.75 cap + 0.25 x）。目标是 [INK_PROPORTION] × 汉字墨高，不是墨高相等。
     */
    fun estimate(cjkBody: Float, latinCap: Float, latinX: Float): Float {
        if (cjkBody <= 0f || latinCap <= 0f) return 1f
        val xHeight = if (latinX > 0f) latinX.coerceAtMost(latinCap) else latinCap
        val latinOptical = latinCap * 0.75f + xHeight * 0.25f
        if (latinOptical <= 0f) return 1f
        return clamp(INK_PROPORTION * cjkBody / latinOptical)
    }

    /**
     * @param manual null 表示自动
     * @param cjkFace / [latinFace] 是 run 实际绑定的字体文件。相同则自动为 1
     */
    fun effective(
        scriptClass: String?,
        manual: Float?,
        cjkFace: String?,
        latinFace: String?,
        estimateFaces: (cjk: String, latin: String) -> Float,
    ): Float {
        if (scriptClass != "cjk") return 1f
        manual?.let { return clamp(it) }
        val cjk = cjkFace?.takeIf { it.isNotBlank() }
        val latin = latinFace?.takeIf { it.isNotBlank() }
        if (cjk == null || latin == null || cjk == latin) return 1f
        return clamp(estimateFaces(cjk, latin))
    }

    /**
     * EPUB 只能给已经单独加载的 Latin FontFace 加 size-adjust。
     * 没有独立 Latin 面时返回 null，不改正文 font-size，也不写 ascent/descent。
     */
    fun epubSizeAdjust(
        scriptClass: String?,
        manual: Float?,
        cjkFace: String?,
        latinFace: String?,
        distinctLatinPath: String?,
        estimateFaces: (cjk: String, latin: String) -> Float,
    ): Float? {
        if (distinctLatinPath.isNullOrBlank()) return null
        val scale = effective(scriptClass, manual, cjkFace, latinFace, estimateFaces)
        return scale.takeIf { it != 1f }
    }
}
