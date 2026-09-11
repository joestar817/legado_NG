package io.legado.app.model.jsSource

import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/** Retain cancellation and policy elements without re-entering a blocked caller's pool. */
internal fun CoroutineContext.withoutScriptDispatcher(): CoroutineContext =
    minusKey(ContinuationInterceptor)
