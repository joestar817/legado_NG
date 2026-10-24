package io.legado.app.help.bgm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BgmPlaybackPolicyTest {
    @Test
    fun preparationNeverStartsMusicAndShortGapsKeepItRunning() {
        val gate = BgmSpeechGate()
        gate.waiting(0)
        assertFalse(gate.canPlay(0))
        gate.started()
        gate.waiting(1_000)
        assertTrue(gate.canPlay(8_999))
        assertFalse(gate.canPlay(9_000))
        gate.started()
        assertTrue(gate.canPlay(9_001))
    }

    @Test
    fun repeatedBufferingDoesNotExtendGraceAndPauseRequiresActualSpeechToResume() {
        val gate = BgmSpeechGate()
        gate.started()
        gate.waiting(100)
        gate.waiting(7_000)
        assertFalse(gate.canPlay(8_100))
        gate.started()
        gate.pause()
        gate.waiting(9_000)
        assertFalse(gate.canPlay(9_001))
        gate.started()
        assertTrue(gate.canPlay(9_002))
    }

    @Test
    fun failuresAreBoundedByQueueOrThreeAndActualPlaybackResetsBudget() {
        val one = BgmFailureBudget()
        assertTrue(one.failed(1))
        val two = BgmFailureBudget()
        assertFalse(two.failed(2))
        assertTrue(two.failed(2))
        val many = BgmFailureBudget()
        assertFalse(many.failed(10))
        assertFalse(many.failed(10))
        assertTrue(many.failed(10))
        many.reset()
        assertFalse(many.failed(10))
    }

    @Test
    fun mediaRequestsRejectCrossPlatformRedirectsAndCredentialUrls() {
        assertTrue(isAllowedBgmMediaAddress(MusicPlatform.QQ, "https://dl.stream.qqmusic.qq.com/test.mp3"))
        assertTrue(isAllowedBgmMediaAddress(MusicPlatform.NETEASE, "http://m10.music.126.net/test.mp3"))
        assertFalse(isAllowedBgmMediaAddress(MusicPlatform.QQ, "https://qq.com.evil.test/test.mp3"))
        assertFalse(isAllowedBgmMediaAddress(MusicPlatform.QQ, "https://user@dl.stream.qqmusic.qq.com/test.mp3"))
        assertFalse(isAllowedBgmMediaAddress(MusicPlatform.QQ, "https://dl.stream.qqmusic.qq.com:8443/test.mp3"))
        assertFalse(isAllowedBgmMediaAddress(MusicPlatform.QQ, "https://m10.music.126.net/test.mp3"))
        assertFalse(isAllowedBgmMediaAddress(MusicPlatform.LOCAL, "https://dl.stream.qqmusic.qq.com/test.mp3"))
    }
}
