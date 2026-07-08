package io.legado.app.ui.book.character

data class ChapterStoryboard(
    val chapterTitle: String,
    val scenes: List<StoryboardScene>
) {
    val segmentCount: Int get() = scenes.sumOf { it.segments.size }
    val dialogueCount: Int get() = scenes.sumOf { scene ->
        scene.segments.count { it.type == StoryboardSegmentType.DIALOGUE }
    }
    val thoughtCount: Int get() = scenes.sumOf { scene ->
        scene.segments.count { it.type == StoryboardSegmentType.THOUGHT }
    }
}

data class StoryboardScene(
    val index: Int,
    val title: String,
    val summary: String,
    val characters: List<String>,
    val segments: List<StoryboardSegment>
) {
    val narrationCount: Int get() = segments.count { it.type == StoryboardSegmentType.NARRATION }
    val dialogueCount: Int get() = segments.count { it.type == StoryboardSegmentType.DIALOGUE }
    val thoughtCount: Int get() = segments.count { it.type == StoryboardSegmentType.THOUGHT }
}

data class StoryboardSegment(
    val type: StoryboardSegmentType,
    val paragraphIndex: Int,
    val text: String,
    val speakerName: String?,
    val evidence: String,
    val speakerId: Long? = null,
    val speakerGender: String = SpeakerGender.UNKNOWN,
    val start: Int = 0,
    val end: Int = start + text.length
) {
    object SpeakerGender {
        const val MALE = "male"
        const val FEMALE = "female"
        const val UNKNOWN = "unknown"
    }
}

enum class StoryboardSegmentType {
    NARRATION,
    DIALOGUE,
    THOUGHT
}
