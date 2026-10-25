package io.legado.app.help.bgm

import java.util.Locale

/** Discovery and codec policy are separate: a matching suffix is not proof of playable audio. */
internal object LocalAudioFormat {
    val musicExtensions = setOf("mp3", "aac", "m4a", "flac", "wav", "ogg", "oga", "opus")
    val recordingExtensions = setOf("amr", "3gp")
    val audioMimes = setOf("audio/mpeg", "audio/mp4a-latm", "audio/flac", "audio/raw",
        "audio/vorbis", "audio/opus", "audio/3gpp", "audio/amr-wb")
    private val excludedExtensions = setOf("ncm", "qmc", "qmc0", "qmc3", "qmcflac", "mgg", "mflac",
        "kgm", "kgma", "vpr", "ape", "wma", "dsf", "dff", "zip", "7z", "cue", "m3u", "m3u8", "pls",
        "mp4", "m4b", "webm", "mka", "mkv", "part", "tmp")

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    fun isRecording(name: String): Boolean = extension(name) in recordingExtensions
    fun isCandidate(name: String, mime: String? = null): Boolean {
        val extension = extension(name)
        if (extension in excludedExtensions) return false
        return extension in musicExtensions || extension in recordingExtensions ||
            mime?.lowercase(Locale.ROOT)?.startsWith("audio/") == true
    }
}
