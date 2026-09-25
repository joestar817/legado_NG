package io.legado.app.ui.book.read

internal enum class ReadCatalogStyle(val value: String) {
    CARD("card"),
    COMPACT_SIDE("compactSide");

    companion object {
        fun fromValue(value: String?): ReadCatalogStyle =
            entries.firstOrNull { it.value == value } ?: CARD
    }
}
