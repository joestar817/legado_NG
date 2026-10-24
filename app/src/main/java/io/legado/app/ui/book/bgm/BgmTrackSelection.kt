package io.legado.app.ui.book.bgm

import io.legado.app.help.bgm.MusicTrack

internal enum class BgmTrackSelectionAction { TOGGLE_ALL, INVERT }

internal data class BgmTrackSelectionResult(
    val tracks: Map<String, MusicTrack>,
    val limitReached: Boolean,
)

/** Changes only loaded candidates; selections hidden by another search keep their order. */
internal fun selectBgmTracks(
    selected: Map<String, MusicTrack>,
    visible: List<MusicTrack>,
    action: BgmTrackSelectionAction,
    excludedIds: Set<String> = emptySet(),
    limit: Int? = null,
): BgmTrackSelectionResult {
    val result = selected.filterValues { it.id !in excludedIds }.toMap(LinkedHashMap())
    val candidates = visible.filterNot { it.id in excludedIds }.distinctBy(MusicTrack::key)
    val additions = when (action) {
        BgmTrackSelectionAction.TOGGLE_ALL -> {
            if (candidates.all { it.key in result }) {
                candidates.forEach { result.remove(it.key) }
                emptyList()
            } else candidates.filterNot { it.key in result }
        }
        BgmTrackSelectionAction.INVERT -> {
            val unselected = candidates.filterNot { it.key in result }
            // Release the visible selection before applying the cross-search size limit.
            candidates.forEach { result.remove(it.key) }
            unselected
        }
    }
    var limitReached = false
    additions.forEach { track ->
        if (limit == null || result.size < limit) result[track.key] = track
        else limitReached = true
    }
    return BgmTrackSelectionResult(result, limitReached)
}
