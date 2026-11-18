package io.legado.app.ui.source.edit

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.rule.ReviewRule

internal fun BookSource.hasSameEditorConfiguration(other: BookSource): Boolean {
    // equal() initializes missing rule groups. Compare copies to keep the stored shape intact.
    return copy(customOrder = other.customOrder).equal(other.copy()) &&
        eventListener == other.eventListener &&
        customButton == other.customButton &&
        exploreScreen.orEmpty() == other.exploreScreen.orEmpty() &&
        (ruleReview ?: ReviewRule()) == (other.ruleReview ?: ReviewRule())
}

internal fun RssSource.hasSameEditorConfiguration(other: RssSource): Boolean {
    return equal(other) &&
        jsLib.orEmpty() == other.jsLib.orEmpty() &&
        ruleImage.orEmpty() == other.ruleImage.orEmpty() &&
        contentWhitelist.orEmpty() == other.contentWhitelist.orEmpty() &&
        contentBlacklist.orEmpty() == other.contentBlacklist.orEmpty() &&
        shouldOverrideUrlLoading.orEmpty() == other.shouldOverrideUrlLoading.orEmpty()
}
