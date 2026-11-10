package io.legado.themes.fire

internal enum class ListeningFireStyle(val storageValue: String) {
    GODFIRE("godfire"), INFERNO("inferno"), RIFT("rift");
    companion object { fun fromStorage(value: String?) = entries.firstOrNull { it.storageValue == value } ?: GODFIRE }
}
