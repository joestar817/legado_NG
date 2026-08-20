package io.legado.app.help.source

import java.security.MessageDigest

internal object BookSourceStorageScope {

    private const val identityPrefix = "book\u0000"

    fun namespace(sourceUrl: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest((identityPrefix + sourceUrl).toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
