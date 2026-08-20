package io.legado.app.help.source

import com.script.rhino.RhinoClassShutter
import io.legado.app.constant.SourceType
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.model.SharedJsScope
import org.mozilla.javascript.Scriptable
import kotlin.coroutines.CoroutineContext

fun BaseSource.getShareScope(coroutineContext: CoroutineContext? = null): Scriptable? {
    return SharedJsScope.getScope(
        jsLib = jsLib,
        coroutineContext = coroutineContext,
        bookSourceClassPolicy = this is BookSource
    )
}

fun <T> BaseSource?.withBookSourceClassPolicy(block: () -> T): T {
    return RhinoClassShutter.withBookSourceClassPolicy(this is BookSource, block)
}

fun BaseSource.getSourceType(): Int {
    return when (this) {
        is BookSource -> SourceType.book
        is RssSource -> SourceType.rss
        else -> error("unknown source type: ${this::class.simpleName}.")
    }
}
