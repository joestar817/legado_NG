package io.legado.app.help.bgm

import io.legado.app.help.bgm.netease.NeteaseMusicProvider
import io.legado.app.help.bgm.qq.QqMusicProvider

object MusicProviders {
    private val netease by lazy { NeteaseMusicProvider() }
    private val qq by lazy { QqMusicProvider() }

    fun get(platform: MusicPlatform): MusicProvider = when (platform) {
        MusicPlatform.NETEASE -> netease
        MusicPlatform.QQ -> qq
        MusicPlatform.LOCAL -> throw MusicProviderException("本地音乐无需在线账号")
    }
}
