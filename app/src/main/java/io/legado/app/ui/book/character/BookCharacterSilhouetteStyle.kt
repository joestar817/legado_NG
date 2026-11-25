package io.legado.app.ui.book.character

internal enum class BookCharacterSilhouetteStyle(val key: String) {
    AUTO("auto"),
    GENERIC("generic"),
    ANCIENT("ancient"),
    MODERN("modern"),
    FANTASY("fantasy");

    fun resolve(automatic: BookCharacterSilhouetteStyle): BookCharacterSilhouetteStyle =
        if (this == AUTO) automatic else this

    companion object {
        fun fromKey(key: String?): BookCharacterSilhouetteStyle =
            entries.firstOrNull { it.key == key } ?: AUTO
    }
}

private val categorySeparator = Regex("""[,，、·•|/；;\s]+""")

private val categoryStyles = mapOf(
    BookCharacterSilhouetteStyle.ANCIENT to setOf(
        "古风", "古代", "古言", "古代言情", "历史", "历史架空", "架空历史", "武侠", "wuxia",
    ),
    BookCharacterSilhouetteStyle.MODERN to setOf(
        "现代", "都市", "都市小说", "都市生活", "现代言情", "现言", "青春校园", "校园",
        "modern", "contemporary",
    ),
    BookCharacterSilhouetteStyle.FANTASY to setOf(
        "玄幻", "东方玄幻", "异世大陆", "奇幻", "西方奇幻", "魔法", "仙侠", "修真",
        "fantasy", "xianxia", "xuanhuan",
    ),
)

private val descriptionClues = mapOf(
    BookCharacterSilhouetteStyle.ANCIENT to setOf(
        "朝堂", "科举", "县衙", "皇宫", "王朝", "江湖", "武林", "宫斗", "官府", "侯府",
    ),
    BookCharacterSilhouetteStyle.MODERN to setOf(
        "现代都市", "写字楼", "地铁", "上下班", "高考", "大学校园", "高中同学",
        "职场", "出租车", "直播间", "手机",
    ),
    BookCharacterSilhouetteStyle.FANTASY to setOf(
        "斗罗", "武魂", "魂师", "魂环", "魂力", "斗气", "斗帝", "魔法学院", "魔法师",
        "魔力", "元婴", "灵根", "渡劫",
    ),
)

internal fun detectBookCharacterSilhouetteStyle(
    title: String,
    intro: String?,
    sourceCategories: String?,
    userCategories: String? = null,
): BookCharacterSilhouetteStyle {
    styleFromCategories(userCategories)?.let { return it }
    styleFromCategories(sourceCategories)?.let { return it }

    val description = title.take(256) + "\n" + intro.orEmpty().take(2048)
    val supportedStyles = descriptionClues.filterValues { clues ->
        clues.count { description.contains(it) } >= 2
    }.keys
    return supportedStyles.singleOrNull() ?: BookCharacterSilhouetteStyle.GENERIC
}

private fun styleFromCategories(categories: String?): BookCharacterSilhouetteStyle? {
    if (categories.isNullOrBlank()) return null
    val tags = categories.lowercase().split(categorySeparator).toSet()
    val styles = categoryStyles.filterValues { names -> names.any(tags::contains) }.keys
    if (styles.isEmpty()) return null
    return styles.singleOrNull() ?: BookCharacterSilhouetteStyle.GENERIC
}
