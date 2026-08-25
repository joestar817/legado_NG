package io.legado.app.help.tts

data class ReadAloudBufferProgress(
    val chapterIndex: Int,
    val chapterPosition: Int
)

internal data class ReadAloudMediaItemIdentity(
    val generation: Long,
    val chapterIndex: Int,
    val itemIndex: Int,
    val paragraphIndex: Int,
    val start: Int,
    val end: Int
) {
    fun toMediaId(): String = listOf(
        READ_ALOUD_MEDIA_ID_PREFIX,
        generation,
        chapterIndex,
        itemIndex,
        paragraphIndex,
        start,
        end
    ).joinToString(":")
}

private const val READ_ALOUD_MEDIA_ID_PREFIX = "read-aloud"

internal fun parseReadAloudMediaItemIdentity(mediaId: String): ReadAloudMediaItemIdentity? {
    val parts = mediaId.split(':')
    if (parts.size != 7 || parts[0] != READ_ALOUD_MEDIA_ID_PREFIX) return null
    val identity = ReadAloudMediaItemIdentity(
        generation = parts[1].toLongOrNull() ?: return null,
        chapterIndex = parts[2].toIntOrNull() ?: return null,
        itemIndex = parts[3].toIntOrNull() ?: return null,
        paragraphIndex = parts[4].toIntOrNull() ?: return null,
        start = parts[5].toIntOrNull() ?: return null,
        end = parts[6].toIntOrNull() ?: return null
    )
    return identity.takeIf {
        it.generation > 0L &&
                it.chapterIndex >= 0 &&
                it.itemIndex >= 0 &&
                it.paragraphIndex >= 0 &&
                it.start >= 0 &&
                it.end >= it.start
    }
}

internal fun shouldHandleReadAloudProgress(
    paragraphSeeking: Boolean,
    lastProgress: Int,
    progress: Int,
    restoreSubtitle: Boolean
): Boolean = !paragraphSeeking && (restoreSubtitle || progress != lastProgress)

internal fun shouldProjectReadAloudSubtitle(
    lastParagraphIndex: Int,
    currentSubtitle: String,
    nextParagraphIndex: Int,
    nextSubtitle: String
): Boolean = lastParagraphIndex != nextParagraphIndex || currentSubtitle != nextSubtitle

internal fun shouldSyncReadAloudMediaItemTransition(
    playlistChanged: Boolean,
    previousItemIndex: Int,
    currentItemIndex: Int
): Boolean = !playlistChanged || previousItemIndex != currentItemIndex

internal fun shouldHandoffReadAloudChapter(
    currentChapterIndex: Int,
    mediaChapterIndex: Int,
    stagedChapterIndex: Int?
): Boolean = mediaChapterIndex == currentChapterIndex + 1 &&
        stagedChapterIndex == mediaChapterIndex

internal fun previousReadAloudChapterMediaCount(
    currentMediaItemIndex: Int
): Int = currentMediaItemIndex.coerceAtLeast(0)

internal fun isReadAloudSeamlessPrefixReady(
    itemIndex: Int,
    preparedItemCount: Int
): Boolean = itemIndex in 0 until preparedItemCount

internal fun expectedReadAloudSeamlessMediaItemCount(
    sourceMediaItemCount: Int,
    preparedItemCount: Int,
    handedOff: Boolean
): Int = preparedItemCount + if (handedOff) 0 else sourceMediaItemCount

internal data class ReadAloudPreparedItemRange(
    val paragraphIndex: Int,
    val start: Int,
    val end: Int
)

internal data class ReadAloudPreparedPlaybackTarget(
    val itemIndex: Int,
    val itemOffset: Int
)

internal fun canReusePreparedReadAloudPlaylist(
    forceRebuild: Boolean,
    playlistChapterIndex: Int,
    currentChapterIndex: Int,
    hasSpeakItems: Boolean
): Boolean = !forceRebuild &&
        playlistChapterIndex == currentChapterIndex &&
        hasSpeakItems

internal fun preparedReadAloudChapterPosition(
    paragraphStarts: List<Int>,
    paragraphIndex: Int,
    preparedEnd: Int
): Int? = paragraphStarts.getOrNull(paragraphIndex)
    ?.plus(preparedEnd.coerceAtLeast(0))

internal fun preparedReadAloudPlaybackTarget(
    ranges: List<ReadAloudPreparedItemRange>,
    targetParagraphIndex: Int,
    targetParagraphOffset: Int,
    mediaItemCount: Int
): ReadAloudPreparedPlaybackTarget? {
    val firstRange = ranges.firstOrNull() ?: return null
    if (targetParagraphIndex < firstRange.paragraphIndex ||
        (targetParagraphIndex == firstRange.paragraphIndex &&
                targetParagraphOffset < firstRange.start)
    ) {
        return null
    }
    val index = ranges.indexOfFirst { range ->
        range.paragraphIndex > targetParagraphIndex ||
                (range.paragraphIndex == targetParagraphIndex &&
                        range.end > targetParagraphOffset)
    }
    if (index !in 0 until mediaItemCount) return null
    val range = ranges[index]
    val itemOffset = if (range.paragraphIndex == targetParagraphIndex) {
        (targetParagraphOffset - range.start).coerceIn(0, range.end - range.start)
    } else {
        0
    }
    return ReadAloudPreparedPlaybackTarget(index, itemOffset)
}

internal fun readAloudSeekPositionMs(
    durationMs: Long,
    itemLength: Int,
    itemOffset: Int
): Long {
    if (durationMs <= 0L || itemLength <= 0) return 0L
    val safeOffset = itemOffset.coerceIn(0, itemLength)
    return (durationMs.toDouble() * safeOffset / itemLength)
        .toLong()
        .coerceIn(0L, durationMs)
}
