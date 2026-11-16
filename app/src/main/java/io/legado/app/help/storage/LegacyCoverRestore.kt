package io.legado.app.help.storage

/** Normalize only local custom covers restored from an archive without an NG manifest. */
internal object LegacyCoverRestore {
    private val uriScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

    fun restoredReference(
        reference: String?,
        hasNativeManifest: Boolean,
        canRead: (String) -> Boolean,
    ): String? {
        if (hasNativeManifest || reference.isNullOrEmpty()) return reference
        if (reference.startsWith("file:///android_asset/", ignoreCase = true) ||
            reference.startsWith("file:/android_asset/", ignoreCase = true)) return reference
        if (uriScheme.containsMatchIn(reference) && !reference.startsWith("file:", ignoreCase = true) &&
            !reference.startsWith("content:", ignoreCase = true)) return reference
        return reference.takeIf(canRead)
    }
}
