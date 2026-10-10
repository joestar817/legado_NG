package io.legado.app.ui.browser

import java.net.URLEncoder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceLoginAppLinkTest {
    private fun link(ticket: String = "http://txz.qq.com/p?k=synthetic", callback: String = "legado://qq-login") =
        "wtloginmqq://ptlogin/qlogin?qrcode=${URLEncoder.encode(ticket, "UTF-8")}" +
            "&schemacallback=${URLEncoder.encode(callback, "UTF-8")}"

    @Test fun acceptsOfficialTicketsOnlyAfterTapInSourceVerificationPage() {
        assertTrue(isDirectQqLoginLink(link(), true, true, true))
        assertTrue(isDirectQqLoginLink(link("https://qm.qq.com/cgi-bin/qm/qr?k=x"), true, true, true))
        assertFalse(isDirectQqLoginLink(link(), false, true, true))
        assertFalse(isDirectQqLoginLink(link(), true, false, true))
        assertFalse(isDirectQqLoginLink(link(), true, true, false))
    }

    @Test fun rejectsOtherAppsAndUntrustedTicketAddresses() {
        listOf("https://txz.qq.com.evil.test/p?k=x", "https://user@txz.qq.com/p?k=x",
            "https://txz.qq.com:443/p?k=x", "https://txz.qq.com/other?k=x",
            "https://txz.qq.com/p?k=x#fragment", "javascript:alert(1)", "http://txz.qq.com/p"
        ).forEach { assertFalse(isDirectQqLoginLink(link(it), true, true, true)) }
        assertFalse(isDirectQqLoginLink(link(callback = "legado://import/bookSource?src=x"), true, true, true))
        assertFalse(isDirectQqLoginLink(link().replace("wtloginmqq", "otherapp"), true, true, true))
        assertFalse(isDirectQqLoginLink(link().replace("/qlogin", "/other"), true, true, true))
    }

    @Test fun rejectsDuplicateOrExtraParamsMalformedAndOversizedLinks() {
        listOf(link()+"&qrcode=x", link()+"&other=x", link()+"#x", link()+"%", "wtloginmqq://ptlogin/qlogin",
            link("http://txz.qq.com/p?k="+"a".repeat(9000))
        ).forEach { assertFalse(isDirectQqLoginLink(it, true, true, true)) }
    }
}
