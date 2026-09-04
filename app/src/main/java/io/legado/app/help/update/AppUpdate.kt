package io.legado.app.help.update

import io.legado.app.help.coroutine.Coroutine
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicBoolean

internal class AppUpdateProcessGate {

    private val started = AtomicBoolean(false)

    fun tryStart(): Boolean = started.compareAndSet(false, true)
}

object AppUpdate {

    private val processGate = AppUpdateProcessGate()

    val gitHubUpdate: AppUpdateInterface by lazy {
        AppUpdateGitHub
    }

    internal fun tryStartAutoCheck(): Boolean = processGate.tryStart()


    data class UpdateInfo(
        val tagName: String,
        val updateLog: String,
        val downloadUrls: List<String>,
        val fileName: String,
        val fileSize: Long,
        val sha256: String,
    )

    interface AppUpdateInterface {

        fun check(scope: CoroutineScope): Coroutine<UpdateInfo>

        fun latest(scope: CoroutineScope): Coroutine<UpdateInfo>

    }

}
