package io.legado.themes.fluid

internal enum class ListeningFluidType(val storageValue: String) {
    SMOKE("smoke"), WATER("water"), EDGE("edge");
    companion object { fun fromStorage(value: String?) = entries.firstOrNull { it.storageValue == value } ?: SMOKE }
}
