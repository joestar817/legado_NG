package io.legado.app.help.rhino

import com.script.rhino.RhinoClassShutter
import io.legado.app.constant.AppLog
import io.legado.app.help.config.AppConfig
import java.util.concurrent.ConcurrentHashMap

internal object BookSourceGuardLog {

    private val loggedEvents = ConcurrentHashMap.newKeySet<String>()

    fun noOp(owner: String, member: String, sourceHint: String? = null) {
        log(owner, member.substringBefore('('), "undefined/no-op", sourceHint)
    }

    fun ignoredWrite(owner: String, member: String, sourceHint: String? = null) {
        log(owner, member.substringBefore('('), "write ignored", sourceHint)
    }

    private fun log(owner: String, member: String, result: String, sourceHint: String?) {
        val source = RhinoClassShutter.currentBookSourceLabel()
            ?.takeIf { it.isNotBlank() }
            ?: sourceHint
            ?.takeIf { it.isNotBlank() }
            ?: return
        if (!runCatching { AppConfig.recordLog }.getOrDefault(false)) return
        val eventKey = "$source|$owner|$member|$result"
        if (!loggedEvents.add(eventKey)) return
        AppLog.putDebug("书源安全拦截 [$source] $owner.$member -> $result")
    }
}
