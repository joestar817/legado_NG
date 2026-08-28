package io.legado.app.help.config

import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import splitties.init.appCtx

data class ReadAloudPlayerDisplaySettings(
    val showPageIndicator: Boolean = true,
    val showCover: Boolean = true,
    val showBookName: Boolean = true,
    val showSubtitle: Boolean = true,
)

object ReadAloudPlayerDisplayConfig {

    var showPageIndicator: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.readAloudPlayerShowPageIndicator, true)
        set(value) = appCtx.putPrefBoolean(PreferKey.readAloudPlayerShowPageIndicator, value)

    var showCover: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.readAloudPlayerShowCover, true)
        set(value) = appCtx.putPrefBoolean(PreferKey.readAloudPlayerShowCover, value)

    var showBookName: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.readAloudPlayerShowBookName, true)
        set(value) = appCtx.putPrefBoolean(PreferKey.readAloudPlayerShowBookName, value)

    var showSubtitle: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.readAloudPlayerShowSubtitle, true)
        set(value) = appCtx.putPrefBoolean(PreferKey.readAloudPlayerShowSubtitle, value)

    fun current() = ReadAloudPlayerDisplaySettings(
        showPageIndicator = showPageIndicator,
        showCover = showCover,
        showBookName = showBookName,
        showSubtitle = showSubtitle,
    )
}
