package io.legado.app.model.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.parser.Parser
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** Reads package/navigation metadata only; publication XHTML and CSS remain untouched. */
internal class EpubPackageParser(private val limits: EpubArchiveLimits = EpubArchiveLimits()) {

    fun parse(archive: EpubArchive): EpubPublication = parse(archive, true)

    /** NG owns its chapter list; a layout resource session does not need a second TOC tree. */
    fun parseLayout(archive: EpubArchive): EpubPublication = parse(archive, false)

    private fun parse(archive: EpubArchive, includeNavigation: Boolean): EpubPublication = try {
        parsePackage(archive, includeNavigation)
    } catch (error: IllegalArgumentException) {
        throw EpubFormatException("Invalid EPUB package path or value", error)
    }

    private fun parsePackage(archive: EpubArchive, includeNavigation: Boolean): EpubPublication {
        val container = readXml(archive, CONTAINER_PATH)
        requireName(container, CONTAINER_NS, "container")
        val rootfiles = container.requiredChild(CONTAINER_NS, "rootfiles")
            .childrenNamed(CONTAINER_NS, "rootfile")
        // Selecting another rendition is a reader decision, not a merge of multiple packages.
        val rootfile = rootfiles.firstOrNull {
            it.xmlAttribute("media-type").trim().equals("application/oebps-package+xml", true)
        } ?: fail("Container has no EPUB package rootfile")
        val packageLink = EpubPaths.resolve("mimetype", rootfile.requiredAttribute("full-path"))
        if (packageLink.query != null || packageLink.fragment != null) {
            fail("Package rootfile must name an archive entry")
        }
        val packagePath = packageLink.path
        val opf = readXml(archive, packagePath)
        requireName(opf, OPF_NS, "package")
        val version = opf.requiredAttribute("version")
        val metadataElement = opf.requiredChild(OPF_NS, "metadata")
        val metadata = metadataElement.children().map { element ->
            EpubMetadata(
                name = element.tagName(),
                value = element.wholeText().trim { it == ' ' || it == '\t' || it == '\r' || it == '\n' },
                attributes = element.attributes().associate { it.key to it.value },
                namespace = element.namespaceFor(element.tagName().substringBefore(':', "")),
            )
        }
        val identifierId = opf.xmlAttribute("unique-identifier").takeIf { it.isNotBlank() }
        val warnings = mutableListOf<String>()
        val uniqueIdentifier = identifierId?.let { id ->
            val matches = metadataElement.childrenNamed(DC_NS, "identifier").filter { it.xmlAttribute("id") == id }
            if (matches.size > 1) fail("Package unique-identifier is ambiguous")
            if (matches.isEmpty()) warnings.add("Package unique-identifier target is missing; obfuscated fonts cannot be decoded")
            // Malformed metadata must not prevent reading unencrypted documents. Never invent a font key.
            matches.singleOrNull()?.wholeText()
        }
        val manifest = parseManifest(archive, packagePath, opf.requiredChild(OPF_NS, "manifest"), warnings)
        val itemsById = manifest.associateBy { it.id }
        val spineElement = opf.requiredChild(OPF_NS, "spine")
        val spine = spineElement.childrenNamed(OPF_NS, "itemref").map { itemref ->
            val idref = itemref.requiredAttribute("idref")
            if (idref !in itemsById) fail("Spine references missing manifest item: $idref")
            val linear = when (itemref.xmlAttribute("linear")) {
                "", "yes" -> true
                "no" -> false
                else -> fail("Invalid spine linear value")
            }
            EpubSpineItem(idref, linear, tokens(itemref.xmlAttribute("properties")))
        }
        if (spine.isEmpty()) fail("Package has no spine items")
        val direction = when (spineElement.xmlAttribute("page-progression-direction")) {
            "", "default" -> EpubPageProgressionDirection.DEFAULT
            "ltr" -> EpubPageProgressionDirection.LTR
            "rtl" -> EpubPageProgressionDirection.RTL
            else -> fail("Invalid page-progression-direction")
        }
        val localPaths = manifest.mapNotNull { it.location?.path }.toSet()
        val navItems = manifest.filter { "nav" in it.properties }.distinctBy { it.location?.path ?: it.href }
        if (navItems.size > 1) fail("Package has multiple navigation documents")
        val navigation = if (navItems.isNotEmpty()) {
            val item = navItems.single()
            if (item.mediaType != "application/xhtml+xml") fail("Navigation must be XHTML")
            val path = item.location?.path ?: fail("Navigation document must be local")
            if (includeNavigation) parseNavigation(archive, path, localPaths) else emptyList()
        } else {
            val ncxId = spineElement.xmlAttribute("toc").takeIf { it.isNotBlank() }
            if (ncxId == null) {
                emptyList()
            } else {
                val ncx = itemsById[ncxId] ?: fail("Spine toc references missing NCX")
                if (ncx.mediaType != "application/x-dtbncx+xml") fail("Spine toc is not NCX")
                val path = ncx.location?.path ?: fail("NCX document must be local")
                if (includeNavigation) parseNcx(archive, path, localPaths) else emptyList()
            }
        }
        return EpubPublication(
            packagePath = packagePath,
            version = version,
            uniqueIdentifier = uniqueIdentifier,
            title = metadataElement.childrenNamed(DC_NS, "title").firstOrNull()?.text(),
            language = metadataElement.childrenNamed(DC_NS, "language").firstOrNull()?.text(),
            metadata = metadata,
            manifest = manifest,
            spine = spine,
            pageProgressionDirection = direction,
            navigation = navigation,
            encryption = parseEncryption(archive, localPaths),
            prefixes = parsePrefixes(opf.xmlAttribute("prefix")),
            warnings = warnings,
        )
    }

    private fun parseManifest(
        archive: EpubArchive,
        packagePath: String,
        manifest: Element,
        warnings: MutableList<String>,
    ): List<EpubManifestItem> {
        val ids = HashSet<String>()
        val items = manifest.childrenNamed(OPF_NS, "item").map { element ->
            val id = element.requiredAttribute("id")
            if (!ids.add(id)) fail("Duplicate manifest id: $id")
            val href = element.requiredAttribute("href")
            val location = if (isRemote(href)) null else EpubPaths.resolve(packagePath, href)
            if (location != null) {
                if (location.fragment != null) fail("Manifest href cannot include a fragment")
                // A stale image/font declaration must not reject otherwise readable chapters.
                // Required documents and actually requested assets are checked by openResource.
                if (location.path !in archive.entries) warnings.add("Missing manifest resource: ${location.path}")
            }
            EpubManifestItem(
                id = id,
                href = href,
                location = location,
                mediaType = parseMediaType(element.requiredAttribute("media-type")),
                properties = tokens(element.xmlAttribute("properties")),
                fallback = element.xmlAttribute("fallback").takeIf { it.isNotBlank() },
                mediaOverlay = element.xmlAttribute("media-overlay").takeIf { it.isNotBlank() },
            )
        }
        if (items.isEmpty()) fail("Package manifest is empty")
        items.filter { it.location != null }.groupBy { it.location!!.path }.forEach { (path, aliases) ->
            if (aliases.size > 1) {
                val descriptions = aliases.map {
                    listOf(it.mediaType, it.properties, it.fallback, it.mediaOverlay, it.location?.query)
                }.distinct()
                if (descriptions.size != 1) fail("Conflicting manifest declarations: $path")
                warnings.add("Equivalent manifest aliases retained: $path")
            }
        }
        items.forEach { item ->
            item.fallback?.let { if (it !in ids) fail("Missing fallback item: $it") }
            item.mediaOverlay?.let { if (it !in ids) fail("Missing media-overlay item: $it") }
        }
        // Fallback chains are later followed by capability selection and must terminate.
        val byId = items.associateBy { it.id }
        val checked = HashSet<String>()
        items.forEach { item ->
            val chain = HashSet<String>()
            var id: String? = item.id
            while (id != null && id !in checked) {
                if (!chain.add(id)) fail("Cyclic manifest fallback: $id")
                id = byId.getValue(id).fallback
            }
            checked.addAll(chain)
        }
        return items
    }

    private fun parseNavigation(
        archive: EpubArchive,
        path: String,
        localPaths: Set<String>,
    ): List<EpubNavigation> {
        val document = readXml(archive, path)
        requireName(document, XHTML_NS, "html")
        val navigation = document.getAllElements().filter { it.isName(XHTML_NS, "nav") }
            .mapNotNull { nav ->
                val types = tokens(nav.namespacedAttribute(EPUB_NS, "type"))
                if (types.none { it in NAV_TYPES }) return@mapNotNull null
                val list = nav.requiredChild(XHTML_NS, "ol")
                EpubNavigation(types, parseNavList(list, path, localPaths, 0))
            }
        if (navigation.none { "toc" in it.types }) fail("Navigation document has no toc")
        if (navigation.count { "toc" in it.types } > 1) fail("Navigation document has multiple tocs")
        return navigation
    }

    private fun parseNavList(
        list: Element,
        path: String,
        localPaths: Set<String>,
        depth: Int,
    ): List<EpubNavigationItem> {
        if (depth > MAX_NAV_DEPTH) fail("Navigation nesting exceeds limit")
        return list.childrenNamed(XHTML_NS, "li").map { item ->
            val labels = item.children().filter {
                it.isName(XHTML_NS, "a") || it.isName(XHTML_NS, "span")
            }
            val label = labels.singleOrNull() ?: fail("Navigation item needs one link or span")
            val href = if (label.isName(XHTML_NS, "a")) label.requiredAttribute("href") else null
            val target = href?.let { navigationTarget(path, it, localPaths) }
            val lists = item.childrenNamed(XHTML_NS, "ol")
            if (lists.size > 1) fail("Navigation item has multiple nested lists")
            EpubNavigationItem(
                title = label.clone().also { copy ->
                    copy.select("img").forEach { img -> img.after(org.jsoup.nodes.TextNode(img.attr("alt").ifBlank { img.attr("title") })); img.remove() }
                }.text(),
                location = target?.first,
                children = lists.firstOrNull()?.let {
                    parseNavList(it, path, localPaths, depth + 1)
                }.orEmpty(),
                externalHref = target?.second,
            )
        }
    }

    private fun parseNcx(
        archive: EpubArchive,
        path: String,
        localPaths: Set<String>,
    ): List<EpubNavigation> {
        val ncx = readXml(archive, path)
        requireName(ncx, NCX_NS, "ncx")
        val map = ncx.requiredChild(NCX_NS, "navMap")
        val navigation = mutableListOf(
            EpubNavigation(setOf("toc"), parseNcxItems(map, "navPoint", path, localPaths, 0))
        )
        ncx.childrenNamed(NCX_NS, "pageList").forEach { pageList ->
            navigation.add(
                EpubNavigation(setOf("page-list"), parseNcxItems(pageList, "pageTarget", path, localPaths, 0))
            )
        }
        return navigation
    }

    private fun parseNcxItems(
        parent: Element,
        itemName: String,
        path: String,
        localPaths: Set<String>,
        depth: Int,
    ): List<EpubNavigationItem> {
        if (depth > MAX_NAV_DEPTH) fail("NCX nesting exceeds limit")
        return parent.childrenNamed(NCX_NS, itemName).map { item ->
            val label = item.childrenNamed(NCX_NS, "navLabel").firstOrNull()
                ?: fail("NCX item has no label")
            val title = label.requiredChild(NCX_NS, "text").text()
            val href = item.requiredChild(NCX_NS, "content").requiredAttribute("src")
            val target = navigationTarget(path, href, localPaths)
            EpubNavigationItem(
                title = title,
                location = target.first,
                children = parseNcxItems(item, "navPoint", path, localPaths, depth + 1),
                externalHref = target.second,
            )
        }
    }

    private fun navigationTarget(
        basePath: String,
        href: String,
        localPaths: Set<String>,
    ): Pair<EpubResourceLink?, String?> {
        if (isRemote(href)) return null to href
        val location = EpubPaths.resolve(basePath, href)
        if (location.path !in localPaths) fail("Navigation references unlisted resource: ${location.path}")
        return location to null
    }

    private fun parseEncryption(archive: EpubArchive, localPaths: Set<String>): List<EpubEncryption> {
        if (ENCRYPTION_PATH !in archive.entries) return emptyList()
        val encryption = readXml(archive, ENCRYPTION_PATH)
        requireName(encryption, CONTAINER_NS, "encryption")
        val paths = HashSet<String>()
        return encryption.childrenNamed(ENCRYPTION_NS, "EncryptedData").map { data ->
            val algorithm = data.requiredChild(ENCRYPTION_NS, "EncryptionMethod")
                .requiredAttribute("Algorithm")
            val uri = data.requiredChild(ENCRYPTION_NS, "CipherData")
                .requiredChild(ENCRYPTION_NS, "CipherReference").requiredAttribute("URI")
            // OCF encryption URIs are rooted at the container, not beside encryption.xml.
            val location = EpubPaths.resolve("mimetype", uri)
            if (location.query != null || location.fragment != null) fail("Invalid encryption resource URI")
            if (location.path !in localPaths) fail("Encryption references unlisted resource: ${location.path}")
            if (!paths.add(location.path)) fail("Duplicate encryption resource: ${location.path}")
            EpubEncryption(location.path, algorithm)
        }
    }

    private fun readXml(archive: EpubArchive, path: String): Element {
        val entry = archive.entries[path] ?: fail("Missing EPUB XML resource: $path")
        if (entry.size > limits.maxXmlBytes) fail("EPUB XML exceeds limit: $path")
        val bytes = archive.open(path).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                if (total > limits.maxXmlBytes) fail("EPUB XML exceeds limit: $path")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        // Jsoup's XML parser does not resolve external entities or access a DOCTYPE URL.
        // A null charset honors XML declarations/BOMs instead of assuming UTF-8.
        val document = ByteArrayInputStream(bytes).use { input ->
            Jsoup.parse(input, null, "", Parser.xmlParser())
        }
        // Bound subsequent namespace walks and metadata/navigation traversal. This is a
        // post-parse work limit, not a claim that the initial XML DOM is memory-sandboxed.
        var nodeCount = 0
        NodeTraversor.traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                nodeCount++
                if (depth > MAX_XML_DEPTH || nodeCount > MAX_XML_NODES) {
                    fail("EPUB XML structure exceeds limit: $path")
                }
            }

            override fun tail(node: Node, depth: Int) = Unit
        }, document)
        return document.children().singleOrNull() ?: fail("EPUB XML needs one root element: $path")
    }

    private fun parseMediaType(value: String): String {
        val type = value.trim().lowercase(Locale.ROOT)
        if (!MEDIA_TYPE.matches(type)) fail("Invalid manifest media-type")
        return type
    }

    private fun parsePrefixes(value: String): Map<String, String> {
        val parts = value.split(XML_SPACE).filter { it.isNotEmpty() }
        if (parts.size % 2 != 0) fail("Invalid package prefix declaration")
        val prefixes = linkedMapOf<String, String>()
        for (index in parts.indices step 2) {
            val label = parts[index]
            if (!Regex("[A-Za-z_][A-Za-z0-9._-]*:").matches(label)) {
                fail("Invalid package vocabulary prefix")
            }
            val vocabulary = parts[index + 1]
            val uri = try { URI(vocabulary) } catch (error: URISyntaxException) {
                throw EpubFormatException("Invalid vocabulary URI", error)
            }
            if (!uri.isAbsolute || prefixes.put(label.dropLast(1), vocabulary) != null) {
                fail("Invalid or duplicate package vocabulary prefix")
            }
        }
        return prefixes
    }

    private fun isRemote(href: String): Boolean {
        if (!SCHEME.containsMatchIn(href)) return false
        val uri = try {
            URI(href)
        } catch (error: URISyntaxException) {
            throw EpubFormatException("Invalid resource URI", error)
        }
        if (!uri.scheme.equals("http", true) && !uri.scheme.equals("https", true)) {
            fail("Unsupported resource URI scheme")
        }
        if (uri.host.isNullOrEmpty()) fail("Remote resource URI must have a host")
        return true
    }

    private fun Element.isName(namespace: String, name: String): Boolean =
        tagName().substringAfter(':') == name && namespaceFor(tagName().substringBefore(':', "")) == namespace

    private fun Element.namespaceFor(prefix: String): String {
        val declaration = if (prefix.isEmpty()) "xmlns" else "xmlns:$prefix"
        var current: Element? = this
        while (current != null) {
            val attributes = current.attributes()
            if (attributes.hasKey(declaration)) return attributes.get(declaration)
            current = current.parent()
        }
        return ""
    }

    private fun Element.namespacedAttribute(namespace: String, name: String): String =
        attributes().firstOrNull {
            ':' in it.key && it.key.substringAfter(':') == name &&
                namespaceFor(it.key.substringBefore(':')) == namespace
        }?.value.orEmpty()

    private fun Element.childrenNamed(namespace: String, name: String): List<Element> =
        children().filter { it.isName(namespace, name) }

    private fun Element.requiredChild(namespace: String, name: String): Element =
        childrenNamed(namespace, name).singleOrNull() ?: fail("Expected one $name element")

    private fun requireName(element: Element, namespace: String, name: String) {
        if (!element.isName(namespace, name)) fail("Expected $name in namespace $namespace")
    }

    private fun Element.requiredAttribute(name: String): String =
        xmlAttribute(name).takeIf { it.isNotBlank() } ?: fail("Missing $name on ${tagName()}")

    /** Unlike HTML, both namespace declarations and ordinary XML attributes are case-sensitive. */
    private fun Element.xmlAttribute(name: String): String =
        attributes().get(name)

    private fun tokens(value: String): Set<String> =
        if (value.isEmpty()) emptySet() else value.split(XML_SPACE).filterTo(linkedSetOf()) { it.isNotEmpty() }

    private fun fail(message: String): Nothing = throw EpubFormatException(message)

    private companion object {
        const val CONTAINER_PATH = "META-INF/container.xml"
        const val ENCRYPTION_PATH = "META-INF/encryption.xml"
        const val CONTAINER_NS = "urn:oasis:names:tc:opendocument:xmlns:container"
        const val OPF_NS = "http://www.idpf.org/2007/opf"
        const val DC_NS = "http://purl.org/dc/elements/1.1/"
        const val XHTML_NS = "http://www.w3.org/1999/xhtml"
        const val EPUB_NS = "http://www.idpf.org/2007/ops"
        const val NCX_NS = "http://www.daisy.org/z3986/2005/ncx/"
        const val ENCRYPTION_NS = "http://www.w3.org/2001/04/xmlenc#"
        const val MAX_NAV_DEPTH = 128
        const val MAX_XML_DEPTH = 256
        const val MAX_XML_NODES = 100_000
        val NAV_TYPES = setOf("toc", "landmarks", "page-list")
        val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
        val XML_SPACE = Regex("[ \\t\\r\\n]+")
        val MEDIA_TYPE = Regex("[a-z0-9][a-z0-9!#$&^_.+-]{0,126}/[a-z0-9][a-z0-9!#$&^_.+-]{0,126}")
    }
}
