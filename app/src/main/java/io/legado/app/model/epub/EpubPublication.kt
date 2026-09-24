package io.legado.app.model.epub

/** In-memory package description, never a persisted Book/Bookmark or a JavaScript host object. */
internal data class EpubPublication(
    val packagePath: String,
    val version: String,
    val uniqueIdentifier: String?,
    val title: String?,
    val language: String?,
    val metadata: List<EpubMetadata>,
    val manifest: List<EpubManifestItem>,
    val spine: List<EpubSpineItem>,
    val pageProgressionDirection: EpubPageProgressionDirection,
    val navigation: List<EpubNavigation>,
    val encryption: List<EpubEncryption>,
    val prefixes: Map<String, String> = emptyMap(),
    val warnings: List<String> = emptyList(),
) {
    val resourcesByPath: Map<String, EpubManifestItem> = manifest.mapNotNull { item ->
        item.location?.let { it.path to item }
    }.toMap()

    val resourcesById: Map<String, EpubManifestItem> = manifest.associateBy { it.id }

    /** Resolve a supported local document while book scripts remain disabled. */
    fun readableDocument(link: EpubResourceLink): EpubResourceLink {
        var item = resourcesByPath[link.path] ?: throw EpubFormatException("正文资源不存在")
        val visited = hashSetOf<String>()
        while (visited.add(item.id)) {
            val supported = item.location != null && item.mediaType in setOf("application/xhtml+xml", "text/html", "image/svg+xml")
            // Scripted XHTML may carry a manifest alternative; otherwise preserve its static content.
            if (supported && ("scripted" !in item.properties || item.fallback == null)) {
                return item.location!!.copy(query = link.query, fragment = if (item.location.path == link.path) link.fragment else null)
            }
            val next = item.fallback?.let(resourcesById::get)
                ?: if (supported) return item.location!! else throw EpubFormatException("此正文格式没有可显示的替代内容")
            item = next
        }
        throw EpubFormatException("正文替代内容循环引用")
    }

    /** A spine item can override the publication-wide layout; do not drop these tokens. */
    fun layoutFor(item: EpubSpineItem): EpubLayout {
        val properties = item.properties.map(::expandProperty)
        properties.firstOrNull { it == RENDITION + "layout-pre-paginated" || it == RENDITION + "layout-reflowable" }?.let {
            return if (it.endsWith("pre-paginated")) EpubLayout.FIXED else EpubLayout.REFLOWABLE
        }
        val fixed = metadata.any {
            it.namespace == "http://www.idpf.org/2007/opf" &&
                it.name.substringAfter(':') == "meta" &&
                it.attributes["property"]?.let(::expandProperty) == RENDITION + "layout" &&
                it.attributes["refines"].isNullOrEmpty() && it.value == "pre-paginated"
        }
        return if (fixed) EpubLayout.FIXED else EpubLayout.REFLOWABLE
    }

    fun renditionProperty(item: EpubSpineItem?, name: String, default: String): String {
        val prefix = RENDITION + name + "-"
        item?.properties?.map(::expandProperty)?.firstOrNull { it.startsWith(prefix) }
            ?.let { return it.removePrefix(prefix) }
        return metadata.firstOrNull { it.attributes["property"]?.let(::expandProperty) == RENDITION + name &&
            it.attributes["refines"].isNullOrEmpty() }?.value ?: default
    }

    /** EPUB 3 accepts both the unprefixed page-spread tokens and rendition aliases. */
    fun pageSpreadSide(item: EpubSpineItem): EpubSpreadSide? = item.properties.asSequence()
        .map(::expandProperty).mapNotNull {
            when (it) {
                "page-spread-left", RENDITION + "page-spread-left" -> EpubSpreadSide.LEFT
                "page-spread-right", RENDITION + "page-spread-right" -> EpubSpreadSide.RIGHT
                "spread-none", RENDITION + "page-spread-center" -> EpubSpreadSide.CENTER
                else -> null
            }
        }.firstOrNull()

    private fun expandProperty(property: String): String {
        if (':' !in property) return property
        val prefix = property.substringBefore(':')
        val vocabulary = prefixes[prefix] ?: if (prefix == "rendition") RENDITION else return property
        return vocabulary + property.substringAfter(':')
    }

    private companion object {
        const val RENDITION = "http://www.idpf.org/vocab/rendition/#"
    }
}

internal data class EpubMetadata(
    val name: String,
    val value: String,
    val attributes: Map<String, String>,
    val namespace: String = "",
)

internal data class EpubManifestItem(
    val id: String,
    val href: String,
    /** Null only for explicitly remote resources. Remote hrefs are described, never fetched. */
    val location: EpubResourceLink?,
    val mediaType: String,
    val properties: Set<String>,
    val fallback: String? = null,
    val mediaOverlay: String? = null,
)

internal data class EpubSpineItem(
    val idref: String,
    val linear: Boolean,
    val properties: Set<String>,
)

internal enum class EpubPageProgressionDirection { DEFAULT, LTR, RTL }
internal enum class EpubLayout { REFLOWABLE, FIXED }

/** Navigation entries are not documents: several entries may point into one XHTML resource. */
internal data class EpubNavigation(
    val types: Set<String>,
    val items: List<EpubNavigationItem>,
)

internal data class EpubNavigationItem(
    val title: String,
    val location: EpubResourceLink?,
    val children: List<EpubNavigationItem> = emptyList(),
    val externalHref: String? = null,
)

internal data class EpubEncryption(val path: String, val algorithm: String)
