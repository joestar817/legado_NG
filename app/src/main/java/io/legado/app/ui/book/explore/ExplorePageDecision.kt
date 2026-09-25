package io.legado.app.ui.book.explore

internal enum class ExplorePageDecision { ACCEPT, KEEP, END }

/** Compare stable book identities, not changing author/intro/cover metadata. */
internal fun evaluateExplorePage(
    knownUrls: Set<String>,
    incomingUrls: List<String>,
    targetPage: Int,
    lastAcceptedPage: Int
): ExplorePageDecision {
    if (knownUrls.isEmpty()) return ExplorePageDecision.ACCEPT
    val empty = incomingUrls.isEmpty()
    val repeatsForward = targetPage > lastAcceptedPage && incomingUrls.none { it !in knownUrls }
    if (!empty && !repeatsForward) return ExplorePageDecision.ACCEPT
    // A probe of page 999 cannot prove that page 2 does not exist.
    return if (targetPage.toLong() == lastAcceptedPage.toLong() + 1L) {
        ExplorePageDecision.END
    } else {
        ExplorePageDecision.KEEP
    }
}
