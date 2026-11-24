package io.legado.app.ui.config

internal inline fun <T> List<T>.prioritizeDrawerGroup(match: (T) -> Boolean): List<T> {
    val index = indexOfFirst(match)
    if (index <= 0) return this
    return toMutableList().apply { add(0, removeAt(index)) }
}
