package io.legado.app.model.epub

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.select.NodeFilter
import org.jsoup.select.NodeTraversor

/** Keep source order without consuming the JVM stack for publisher-controlled nesting. */
internal fun forEachEpubBodyNode(root: Node, action: (Node) -> Unit) {
    NodeTraversor.filter(object : NodeFilter {
        override fun head(node: Node, depth: Int): NodeFilter.FilterResult {
            if (node is Element && node.normalName() in setOf("head", "style", "script")) {
                return NodeFilter.FilterResult.SKIP_CHILDREN
            }
            action(node)
            return NodeFilter.FilterResult.CONTINUE
        }

        override fun tail(node: Node, depth: Int) = NodeFilter.FilterResult.CONTINUE
    }, root)
}
