package io.legado.app.model.epub

internal enum class EpubSpreadSide { LEFT, RIGHT, CENTER }

/** A virtual page in one occurrence. Reusing the same resource never merges these identities. */
internal data class EpubSpreadPage(val occurrence: Int, val page: Int = 0) {
    init { require(occurrence >= 0 && page >= 0) }
}

internal data class EpubSpread(
    val left: EpubSpreadPage? = null,
    val right: EpubSpreadPage? = null,
    val center: EpubSpreadPage? = null,
) {
    init {
        require(if (center != null) left == null && right == null else left != null || right != null)
    }
    fun pagesInReadingOrder(rtl: Boolean): List<EpubSpreadPage> = center?.let(::listOf)
        ?: if (rtl) listOfNotNull(right, left) else listOfNotNull(left, right)
}

/**
 * Slot allocation only: the caller supplies actual virtual pages after layout.
 * It does not infer reflowable page counts from spine length or mutate source order.
 * EPUB RS 3.3 sections 8.1.1.3–4; EPUB 3.3 section 8.2.2.4.
 */
internal object EpubSpreadLayout {
    /** Pair against the complete fixed run, not the transient native chapter window. */
    fun fixedRun(publication: EpubPublication, occurrence: Int, landscape: Boolean): List<EpubSpread> {
        val current = publication.spine.getOrNull(occurrence) ?: return emptyList()
        if (publication.layoutFor(current) != EpubLayout.FIXED) return emptyList()
        fun joins(index: Int): Boolean = publication.spine.getOrNull(index)?.let {
            publication.layoutFor(it) == EpubLayout.FIXED
        } == true
        var first = occurrence
        var last = occurrence
        while (joins(first - 1)) first--
        while (joins(last + 1)) last++
        return build(publication, (first..last).map { EpubSpreadPage(it) }, landscape)
    }

    /** Incomplete edge spreads are not displayed as intentional blank pages. */
    fun loadedWindow(plan: List<EpubSpread>, available: Set<Int>): List<EpubSpread> =
        plan.filter { spread -> spread.pagesInReadingOrder(false).all { it.occurrence in available } }

    fun build(publication: EpubPublication, pages: List<EpubSpreadPage>, landscape: Boolean): List<EpubSpread> {
        val rtl = publication.pageProgressionDirection == EpubPageProgressionDirection.RTL
        val leading = if (rtl) EpubSpreadSide.RIGHT else EpubSpreadSide.LEFT
        val trailing = if (rtl) EpubSpreadSide.LEFT else EpubSpreadSide.RIGHT
        val result = ArrayList<EpubSpread>()
        var left: EpubSpreadPage? = null
        var right: EpubSpreadPage? = null
        var next = leading
        fun flush() {
            if (left != null || right != null) result += EpubSpread(left, right)
            left = null; right = null; next = leading
        }
        val seen = HashSet<EpubSpreadPage>()
        pages.forEach { page ->
            require(seen.add(page)) { "A virtual page must appear once in a spread plan" }
            val item = publication.spine.getOrNull(page.occurrence)
                ?: throw IllegalArgumentException("Unknown spine occurrence")
            val explicitSide = publication.pageSpreadSide(item)
            val spread = publication.renditionProperty(item, "spread", "auto")
            val enabled = explicitSide != EpubSpreadSide.CENTER && when (spread) {
                "none" -> false
                "both", "portrait" -> true // Deprecated portrait is treated as both by RS 3.3.
                else -> landscape // auto/landscape; unknown values retain the default policy.
            }
            if (!enabled) {
                flush(); result += EpubSpread(center = page)
            } else {
                // An itemref's placement controls its first virtual page, not every reflowed page.
                val side = explicitSide.takeIf { page.page == 0 } ?: next
                if (side == leading && (left != null || right != null)) flush()
                if (side == EpubSpreadSide.LEFT) left = page else right = page
                next = trailing
                if (side == trailing) flush()
            }
        }
        flush()
        return result
    }

    fun adjacent(spreads: List<EpubSpread>, current: EpubSpreadPage, delta: Int, rtl: Boolean): EpubSpreadPage? {
        require(delta == -1 || delta == 1)
        val index = spreads.indexOfFirst { current in it.pagesInReadingOrder(rtl) }
        if (index < 0) return null
        val target = spreads.getOrNull(index + delta)?.pagesInReadingOrder(rtl) ?: return null
        return if (delta > 0) target.firstOrNull() else target.lastOrNull()
    }
}

internal data class EpubPageSize(val width: Float, val height: Float) {
    init { require(width.isFinite() && height.isFinite() && width > 0 && height > 0) }
}

internal data class EpubPagePlacement(val x: Float, val y: Float, val width: Float, val height: Float)

/** One common scale and touching inner edges preserve true spreads without an injected gutter. */
internal object EpubSpreadGeometry {
    fun fit(spread: EpubSpread, sizes: Map<EpubSpreadPage, EpubPageSize>, viewport: EpubPageSize): Map<EpubSpreadPage, EpubPagePlacement> {
        spread.center?.let { page ->
            val size = sizes.getValue(page)
            val scale = minOf(viewport.width / size.width, viewport.height / size.height)
            val w = size.width * scale; val h = size.height * scale
            return mapOf(page to EpubPagePlacement((viewport.width - w) / 2, (viewport.height - h) / 2, w, h))
        }
        val left = spread.left?.let(sizes::getValue)
        val right = spread.right?.let(sizes::getValue)
        // A forced first/last side retains an empty opposite slot with the same page size.
        val leftWidth = (left ?: requireNotNull(right)).width
        val rightWidth = (right ?: requireNotNull(left)).width
        val maxHeight = maxOf(left?.height ?: 0f, right?.height ?: 0f)
        val scale = minOf(viewport.width / (leftWidth + rightWidth), viewport.height / maxHeight)
        val origin = (viewport.width - (leftWidth + rightWidth) * scale) / 2
        return buildMap {
            spread.left?.let { page ->
                val size = requireNotNull(left)
                put(page, EpubPagePlacement(origin, (viewport.height - size.height * scale) / 2, size.width * scale, size.height * scale))
            }
            spread.right?.let { page ->
                val size = requireNotNull(right)
                put(page, EpubPagePlacement(origin + leftWidth * scale, (viewport.height - size.height * scale) / 2, size.width * scale, size.height * scale))
            }
        }
    }
}
