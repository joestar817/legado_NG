package io.legado.app.help.config

import android.content.Context
import io.legado.app.constant.PreferKey
import io.legado.app.utils.defaultSharedPreferences

internal enum class PageKeyDirection { PREVIOUS, NEXT }

internal data class PageKeyBindings(
    val previous: String,
    val next: String,
) {
    fun bind(direction: PageKeyDirection, keyCode: Int): PageKeyBindings {
        require(keyCode > 0)
        val value = keyCode.toString()
        return when (direction) {
            PageKeyDirection.PREVIOUS -> copy(previous = value, next = if (next == value) "" else next)
            PageKeyDirection.NEXT -> copy(previous = if (previous == value) "" else previous, next = value)
        }
    }

    fun clear(direction: PageKeyDirection): PageKeyBindings = when (direction) {
        PageKeyDirection.PREVIOUS -> copy(previous = "")
        PageKeyDirection.NEXT -> copy(next = "")
    }
}

internal fun isValidPageKey(value: String): Boolean =
    value.toIntOrNull()?.let { it > 0 && it.toString() == value } == true

internal fun matchesPageKey(value: String?, keyCode: Int): Boolean =
    keyCode > 0 && value == keyCode.toString()

internal fun Context.savePageKeyBindings(bindings: PageKeyBindings) {
    defaultSharedPreferences.edit()
        .putString(PreferKey.prevKeys, bindings.previous)
        .putString(PreferKey.nextKeys, bindings.next)
        .apply()
}
