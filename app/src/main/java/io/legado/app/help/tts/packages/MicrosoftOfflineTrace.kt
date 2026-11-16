package io.legado.app.help.tts.packages

import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Logger

/** Only fixed stage names, elapsed time and allowlisted error codes reach the log. */
internal class MicrosoftOfflineTrace(
    private val emit: (String) -> Unit = { logger.info(it) },
    private val clock: () -> Long = System::nanoTime,
) {
    enum class Stage { GRAPH, ENGINE_LOAD, VOICE_PREPARE, SYNTHESIS, OUTPUT }
    enum class ClientEvent { TIMEOUT, CANCELLED, WORKER_DIED }

    private val request = sequence.incrementAndGet()
    private var stage: Stage? = null
    private var started = 0L

    fun begin(next: Stage) {
        stage = next
        started = clock()
        write("request=$request stage=${next.name} event=start")
    }

    fun success() {
        val current = stage ?: return
        write("request=$request stage=${current.name} event=success elapsed_ms=${elapsedMillis()}")
        stage = null
    }

    fun failure(error: Throwable) {
        val current = stage ?: return
        write("request=$request stage=${current.name} event=failure elapsed_ms=${elapsedMillis()} code=${safeCode(error)}")
        stage = null
    }

    private fun elapsedMillis() = ((clock() - started) / 1_000_000L).coerceAtLeast(0)

    private fun write(message: String) {
        runCatching { emit("MicrosoftOffline $message") }
    }

    companion object {
        private val logger = Logger.getLogger("Legado.MicrosoftOffline")
        private val sequence = AtomicLong()
        private val sdkCodes = listOf(
            "EMBEDDED_TTS_ERROR_UNSUPPORTED_VOICE",
            "EMBEDDED_TTS_ERROR_INVALID_LICENSE",
            "EMBEDDED_TTS_ERROR_WRONG_DECRYPTION_KEY",
            "WRONG_DECRYPTION_KEY",
        )

        internal fun safeCode(error: Throwable): String {
            var current: Throwable? = error
            repeat(8) {
                val item = current ?: return@repeat
                val message = runCatching { item.message }.getOrNull().orEmpty().take(4096)
                sdkCodes.firstOrNull { it in message }?.let { return it }
                if (item is TimeoutException) return "TIMEOUT"
                if (item is LinkageError) return "NATIVE_LINK"
                current = runCatching { item.cause }.getOrNull()?.takeUnless { it === item }
            }
            return "OTHER"
        }

        fun client(event: ClientEvent) {
            runCatching { logger.info("MicrosoftOffline stage=CLIENT event=${event.name}") }
        }
    }
}
