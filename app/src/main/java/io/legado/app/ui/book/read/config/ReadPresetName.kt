package io.legado.app.ui.book.read.config

internal fun defaultPresetName(existingNames: Iterable<String>): String {
    val names = existingNames.map { it.trim() }.toHashSet()
    val base = "新预设"
    var candidate = base
    var suffix = 2
    while (candidate in names) {
        candidate = "$base ${suffix++}"
    }
    return candidate
}
