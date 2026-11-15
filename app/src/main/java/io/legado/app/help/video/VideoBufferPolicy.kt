package io.legado.app.help.video

/** Shared, validated buffer presets; no third-party wrapper defaults. */
internal enum class VideoBufferPolicy(val title: String, val minMs: Int, val maxMs: Int, val startMs: Int, val resumeMs: Int) {
    BALANCED("均衡", 30000, 60000, 1000, 5000),
    STABLE("弱网优先", 50000, 90000, 2000, 8000),
    LIVE("直播", 5000, 30000, 2000, 5000);

    companion object {
        fun fromId(id: Int) = entries.getOrElse(id) { BALANCED }
    }
}
