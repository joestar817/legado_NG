package io.legado.app.help.bgm.qq

import io.legado.app.help.bgm.MusicProviderException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QqRequestCodecTest {
    @Test
    fun signatureMatchesQQMusicApi082Vectors() {
        assertEquals("zzcf0e03e5gx4qeiq5cfgdyqwu7sdqfsb5fro3aa45053", QqRequestCodec.sign(""))
        assertEquals("zzcf8e26805gyafigxmxjehoe02mvsjjgtwzw6f1a05f9", QqRequestCodec.sign("{}"))
        assertEquals("zzc3ed6ce50li1smzifrn34dp4l4cijdjigcsc9b25016", QqRequestCodec.sign("中文音乐"))
    }

    @Test
    fun qrAndOauthHashMatchesUpstream() {
        assertEquals(1341976211, QqRequestCodec.hash33("qrsig-test"))
        assertEquals(1404666077, QqRequestCodec.hash33("p_skey-test", 5381))
    }

    @Test
    fun deviceTokenUsesCbcPkcsPaddingAndFixedIv() {
        assertEquals("8uhW/EGGKPmsu9bIhaps7EnpK+BvzwDSeY2gquRkYvA=",
            QqRequestCodec.aesBase64("lvcwmSYVr2Axv1gn", "1234567890abcdef", "Zs0ntDqG2jyhKN0c"))
    }

    @Test
    fun filenameUsesMediaMidWithoutDuplicatingIt() {
        assertEquals("M500real-media.mp3", QqMusicProvider.fileName("song-mid", "real-media"))
        assertEquals("M500song-midsong-mid.mp3", QqMusicProvider.fileName("song-mid", ""))
    }

    @Test
    fun qqCallbackReadsOnlyQuotedArgumentsWithoutExecutingJavascript() {
        val values = QqMusicProvider.parseQrCallback("ptuiCB('67','0','','0','ok','nickname');")
        assertEquals("67", values[0])
        assertEquals("", values[2])
    }

    @Test(expected = MusicProviderException::class)
    fun malformedCallbackFailsClosed() {
        QqMusicProvider.parseQrCallback("location.href='https://example.com'")
    }

    @Test
    fun playbackUrlKeepsPlatformSchemeAndQqDomainWithoutUserInfo() {
        assertTrue(QqMusicProvider.validMediaUrl("https://isure.stream.qqmusic.qq.com/M500test.mp3?vkey=test"))
        assertTrue(QqMusicProvider.validMediaUrl("http://isure.stream.qqmusic.qq.com/M500test.mp3"))
        assertFalse(QqMusicProvider.validMediaUrl("https://qq.com.attacker.test/test.mp3"))
        assertFalse(QqMusicProvider.validMediaUrl("https://user:password@qq.com/test.mp3"))
        assertFalse(QqMusicProvider.validMediaUrl("https://qq.com:8443/test.mp3"))
    }
}
