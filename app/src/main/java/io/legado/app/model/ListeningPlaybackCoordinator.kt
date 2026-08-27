package io.legado.app.model

import android.content.Context
import io.legado.app.service.AudioPlayService
import io.legado.app.service.BaseReadAloudService

internal enum class ListeningPlaybackTarget {
    READ_ALOUD,
    AUDIO,
}

internal enum class ListeningPlaybackStop {
    READ_ALOUD,
    AUDIO,
}

internal fun requiredListeningPlaybackStop(
    target: ListeningPlaybackTarget,
    readAloudRunning: Boolean,
    audioRunning: Boolean,
): ListeningPlaybackStop? {
    return when {
        target == ListeningPlaybackTarget.READ_ALOUD && audioRunning -> {
            ListeningPlaybackStop.AUDIO
        }
        target == ListeningPlaybackTarget.AUDIO && readAloudRunning -> {
            ListeningPlaybackStop.READ_ALOUD
        }
        else -> null
    }
}

object ListeningPlaybackCoordinator {

    fun beforeReadAloud() {
        if (requiredStop(ListeningPlaybackTarget.READ_ALOUD) == ListeningPlaybackStop.AUDIO) {
            AudioPlay.stop()
        }
    }

    fun beforeAudio(context: Context) {
        if (requiredStop(ListeningPlaybackTarget.AUDIO) == ListeningPlaybackStop.READ_ALOUD) {
            ReadAloud.stop(context)
        }
    }

    private fun requiredStop(target: ListeningPlaybackTarget): ListeningPlaybackStop? {
        return requiredListeningPlaybackStop(
            target = target,
            readAloudRunning = BaseReadAloudService.isRun,
            audioRunning = AudioPlayService.isRun,
        )
    }
}
