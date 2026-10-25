package io.legado.app.help.bgm

import android.annotation.SuppressLint
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaMetadata
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.DiscardingTrackOutput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.MpegAudioUtil
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mp3.Mp3Extractor
import java.io.InputStream

/** Read complete ID3v2 MP3 headers with the same extractor used by playback, without media-service IPC. */
@SuppressLint("UnsafeOptInUsageError")
internal object LocalMp3Probe {
    // Keep eight simultaneous probes small; larger artwork uses the four-slot platform path.
    private const val MAX_TAG_BYTES = 512 * 1024

    data class Result(val format: Format, val tags: MediaMetadata, val durationMs: Long)

    fun read(stream: InputStream, length: Long, checkActive: () -> Unit): Result? {
        var bytesRead = 0
        val input = DefaultExtractorInput({ buffer, offset, count ->
            checkActive()
            if (bytesRead + count > MAX_TAG_BYTES + 128 * 1024) {
                throw java.io.IOException("MP3 header exceeds probe limit")
            }
            stream.read(buffer, offset, count).also { if (it > 0) bytesRead += it }
        }, 0, if (length > 0) length else C.LENGTH_UNSET.toLong())
        val header = ByteArray(10)
        if (!input.peekFully(header, 0, header.size, true)) return null
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte() ||
            header[3].toInt() !in 2..4 || (6..9).any { header[it].toInt() < 0 }) return null
        val tagSize = (6..9).fold(0) { size, index -> (size shl 7) or header[index].toInt() }
        if (tagSize !in 1..MAX_TAG_BYTES) return null
        input.advancePeekPosition(tagSize)
        val frame = ByteArray(4)
        if (!input.peekFully(frame, 0, frame.size, true)) return null
        val frameHeader = frame.fold(0) { value, byte -> (value shl 8) or (byte.toInt() and 0xff) }
        if (MpegAudioUtil.getFrameSize(frameHeader) == C.LENGTH_UNSET) return null
        input.resetPeekPosition()

        // Prefer a header/tag duration; unindexed streams without one keep the platform path.
        val extractor = Mp3Extractor(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)
        var format: Format? = null
        var seekMap: SeekMap? = null
        var sampleRead = false
        val track = object : TrackOutput by DiscardingTrackOutput() {
            override fun format(value: Format) { format = value }
            override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int,
                cryptoData: TrackOutput.CryptoData?) { sampleRead = true }
        }
        try {
            if (!extractor.sniff(input)) return null
            extractor.init(object : ExtractorOutput {
                override fun track(id: Int, type: Int): TrackOutput = track
                override fun endTracks() = Unit
                override fun seekMap(value: SeekMap) { seekMap = value }
            })
            val position = PositionHolder()
            while (!sampleRead) {
                checkActive()
                if (extractor.read(input, position) != Extractor.RESULT_CONTINUE) return null
            }
            val audio = format ?: return null
            if (audio.sampleMimeType != "audio/mpeg" || audio.sampleRate <= 0 || audio.channelCount <= 0) return null
            val duration = seekMap?.durationUs?.takeIf { it > 0 }?.div(1000) ?: return null
            val metadata = audio.metadata ?: return null
            val builder = MediaMetadata.Builder()
            for (index in 0 until metadata.length()) metadata[index].populateMediaMetadata(builder)
            val tags = builder.build()
            // Incomplete tags may need the platform's ID3v1 fallback. Keep that existing path.
            if (tags.title.isNullOrBlank() || tags.artist.isNullOrBlank() || tags.albumTitle.isNullOrBlank()) return null
            return Result(audio, tags, duration)
        } finally { extractor.release() }
    }
}
