package io.legado.app.ui.book.read.aloud

internal enum class ListeningCapsuleHost {
    READER,
    MAIN,
    OTHER,
}

internal enum class ListeningPlayback {
    READ_ALOUD,
    AUDIO,
}

internal object ListeningCapsulePolicy {

    fun shouldAttach(host: ListeningCapsuleHost, showOnMain: Boolean): Boolean {
        return host == ListeningCapsuleHost.READER ||
            host == ListeningCapsuleHost.MAIN && showOnMain
    }

    fun resolvePlayback(
        host: ListeningCapsuleHost,
        readAloudRunning: Boolean,
        readAloudPlaying: Boolean,
        audioRunning: Boolean,
        audioPlaying: Boolean,
        preferred: ListeningPlayback?,
    ): ListeningPlayback? {
        if (host == ListeningCapsuleHost.OTHER) return null
        if (host == ListeningCapsuleHost.READER) {
            return ListeningPlayback.READ_ALOUD.takeIf { readAloudRunning }
        }
        val readAloudIsPlaying = readAloudRunning && readAloudPlaying
        val audioIsPlaying = audioRunning && audioPlaying
        if (audioIsPlaying && !readAloudIsPlaying) return ListeningPlayback.AUDIO
        if (readAloudIsPlaying && !audioIsPlaying) return ListeningPlayback.READ_ALOUD
        if (preferred == ListeningPlayback.AUDIO && audioRunning) {
            return ListeningPlayback.AUDIO
        }
        if (preferred == ListeningPlayback.READ_ALOUD && readAloudRunning) {
            return ListeningPlayback.READ_ALOUD
        }
        return when {
            audioRunning -> ListeningPlayback.AUDIO
            readAloudRunning -> ListeningPlayback.READ_ALOUD
            else -> null
        }
    }
}
