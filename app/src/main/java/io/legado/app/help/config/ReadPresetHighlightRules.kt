package io.legado.app.help.config

import java.util.UUID

/** Rules remain shared; a preset selects which rules are active independently of global enablement. */
internal fun resolvePresetHighlightRules(
    rules: List<ReadHighlightRule>,
    linkedIds: List<String>?,
    useLinkedRules: Boolean,
): List<ReadHighlightRule> {
    val ids = linkedIds.orEmpty().filter(String::isNotBlank).toSet()
    val linked = if (useLinkedRules && ids.isNotEmpty()) rules.filter { it.id in ids } else emptyList()
    return (if (linked.isNotEmpty()) linked.map { it.copy(enabled = true) }
    else rules.filter(ReadHighlightRule::enabled)).sortedBy(ReadHighlightRule::position)
}

internal data class ReadPresetRuleMergeResult(
    val merge: ReadHighlightRuleMergeResult,
    val idMapping: Map<String, String>,
)

/** Importing a preset must not replace a shared rule used by another preset. */
internal fun mergePresetHighlightRules(
    existing: List<ReadHighlightRule>,
    incoming: List<ReadHighlightRule>,
    newId: () -> String = { UUID.randomUUID().toString() },
): ReadPresetRuleMergeResult {
    val merged = existing.map(ReadHighlightRule::normalized).toMutableList()
    val mapping = linkedMapOf<String, String>()
    var added = 0
    var skipped = 0
    incoming.forEach { source ->
        val sourceId = source.id.trim()
        val candidate = source.normalized().copy(id = sourceId.ifBlank(newId))
        val content = candidate.copy(id = "", position = 0, enabled = false)
        val equivalent = merged.firstOrNull { it.copy(id = "", position = 0, enabled = false) == content }
        val target = if (equivalent != null) {
            skipped++
            equivalent
        } else {
            val id = if (merged.any { it.id == candidate.id }) {
                generateSequence(newId).first { generated -> generated.isNotBlank() && merged.none { it.id == generated } }
            } else candidate.id
            candidate.copy(id = id, position = merged.size, enabled = false).also {
                merged += it
                added++
            }
        }
        if (sourceId.isNotBlank()) mapping[sourceId] = target.id
    }
    return ReadPresetRuleMergeResult(
        merge = ReadHighlightRuleMergeResult(
            rules = merged.mapIndexed { index, rule -> rule.copy(position = index) },
            addedCount = added,
            updatedCount = 0,
            skippedCount = skipped,
        ),
        idMapping = mapping,
    )
}
