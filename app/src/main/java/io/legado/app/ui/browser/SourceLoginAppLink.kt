package io.legado.app.ui.browser

import java.net.URI
import java.net.URLDecoder

/** Only a tap in the source's own verification HTML may bypass the second prompt. */
internal fun isDirectQqLoginLink(
    url: String,
    hasGesture: Boolean,
    isMainFrame: Boolean,
    sourceOwnedPage: Boolean,
): Boolean {
    if (!hasGesture || !isMainFrame || !sourceOwnedPage || url.length > 8192) return false
    return runCatching {
        val uri = URI(url)
        if (uri.scheme != "wtloginmqq" || uri.rawAuthority != "ptlogin" ||
            uri.rawPath != "/qlogin" || uri.rawFragment != null
        ) return false
        val pairs = uri.rawQuery.orEmpty().split('&').map {
            val pair = it.split('=', limit = 2)
            require(pair.size == 2)
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
        }
        if (pairs.size != 2 || pairs.map { it.first }.toSet() != setOf("qrcode", "schemacallback")) return false
        val values = pairs.toMap()
        if (values["schemacallback"] != "legado://qq-login") return false
        val ticket = URI(values.getValue("qrcode"))
        ticket.scheme in setOf("http", "https") && ticket.rawFragment == null &&
            ((ticket.rawAuthority == "txz.qq.com" && ticket.rawPath == "/p") ||
                (ticket.rawAuthority == "qm.qq.com" && ticket.rawPath == "/cgi-bin/qm/qr")) &&
            !ticket.rawQuery.isNullOrBlank()
    }.getOrDefault(false)
}
