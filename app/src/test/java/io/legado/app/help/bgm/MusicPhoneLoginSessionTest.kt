package io.legado.app.help.bgm

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicPhoneLoginSessionTest {
    @Test
    fun cancelledSessionCannotPublishLateLogin() {
        val session = MusicPhoneLoginSession("13000000000")
        session.bind(MusicPlatform.NETEASE)
        session.markCodeSent(true)
        session.cancel()
        var published = false
        assertFalse(session.commitIfActive { published = true })
        assertFalse(published)
    }

    @Test
    fun completedLoginCannotBePublishedTwice() {
        val session = MusicPhoneLoginSession("13000000000")
        assertTrue(session.commitIfActive { })
        session.cancel()
        assertFalse(session.commitIfActive { error("duplicate publication") })
    }

    @Test(expected = MusicProviderException::class)
    fun sessionCannotSwitchPlatforms() {
        val session = MusicPhoneLoginSession("13000000000")
        session.bind(MusicPlatform.NETEASE)
        session.bind(MusicPlatform.QQ)
    }

    @Test(expected = MusicProviderException::class)
    fun loginCannotFollowUnsentOrChallengedSms() {
        val session = MusicPhoneLoginSession("13000000000")
        session.markCodeSent(false)
        session.requireActive(requireCode = true)
    }

    @Test(expected = CancellationException::class)
    fun cancellationBeforeSendPreventsBinding() {
        val session = MusicPhoneLoginSession("13000000000")
        session.cancel()
        session.bind(MusicPlatform.QQ)
    }
}
