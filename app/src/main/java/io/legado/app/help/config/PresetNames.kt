package io.legado.app.help.config

/**
 * 预设名字是稳定身份。确认时去空白、拒绝空名；重名才加 `-1`、`-2`。
 * 补后缀时整段不超过 [MAX_CODE_POINTS]，和名称框大约能放下的字数一致。
 * 没有重名时保留用户原文，不按这个上限截断。
 */
internal object PresetNames {
    const val MAX_CODE_POINTS = 8

    fun allocate(
        requested: String,
        taken: Collection<String>,
        current: String? = null,
    ): String? {
        val base = requested.trim()
        if (base.isEmpty()) return null
        val own = current?.trim()?.takeIf { it.isNotEmpty() }
        val occupied = taken
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != own }
            .toSet()
        if (base !in occupied) return base
        var number = 1
        while (number <= 99) {
            val suffix = "-$number"
            val suffixCount = suffix.codePointCount(0, suffix.length)
            if (suffixCount >= MAX_CODE_POINTS) return null
            val stem = base.codePointPrefix(MAX_CODE_POINTS - suffixCount).trimEnd()
            if (stem.isEmpty()) return null
            val candidate = stem + suffix
            if (candidate !in occupied) return candidate
            number++
        }
        return null
    }
}

internal fun String.codePointPrefix(count: Int): String {
    if (count <= 0 || isEmpty()) return ""
    var end = 0
    var seen = 0
    while (end < length && seen < count) {
        end += Character.charCount(Character.codePointAt(this, end))
        seen++
    }
    return substring(0, end)
}
