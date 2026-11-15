package io.legado.app.ui.book.read.config

import io.legado.app.help.config.ReadHighlightRule
import io.legado.app.help.config.resolvePresetHighlightRules

internal data class ReadHighlightOverview(
    val linkedRuleIds: Set<String>,
    val usesLinkedRules: Boolean,
    val usedRuleIds: Set<String>,
)

/** Mirrors the reader's fallback to global rules when a preset has no valid links. */
internal fun readHighlightOverview(
    rules: List<ReadHighlightRule>,
    linkedRuleIds: List<String>,
    useLinkedRules: Boolean,
): ReadHighlightOverview {
    val requested = linkedRuleIds.asSequence().filter(String::isNotBlank).toSet()
    val availableLinks = rules.asSequence().map(ReadHighlightRule::id).filter { it in requested }.toSet()
    return ReadHighlightOverview(
        linkedRuleIds = availableLinks,
        usesLinkedRules = useLinkedRules && availableLinks.isNotEmpty(),
        usedRuleIds = resolvePresetHighlightRules(rules, linkedRuleIds, useLinkedRules)
            .mapTo(hashSetOf(), ReadHighlightRule::id),
    )
}
