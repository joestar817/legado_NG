package io.legado.app.ui.book.read.epub

import io.legado.app.model.epub.EpubContentProjection
import org.json.JSONArray
import org.json.JSONObject

/** Compact renderer instructions. Linear runs avoid sending two integers for every character. */
internal fun EpubContentProjection.ProjectedDocument.toReaderJson(key: String): JSONObject {
    val nodeValues = JSONArray()
    nodes.forEach { node ->
        val runs = JSONArray()
        var index = 0
        while (index < node.starts.size) {
            if (node.starts[index] < 0) { index++; continue }
            val from = index
            val start = node.starts[index]
            val end = node.ends[index]
            val linear = end == start + 1
            index++
            while (index < node.starts.size && if (linear)
                node.starts[index] == start + index - from && node.ends[index] == node.starts[index] + 1
                else node.starts[index] == start && node.ends[index] == end) index++
            runs.put(JSONArray().put(from).put(index).put(start)
                .put(if (linear) start + index - from else end).put(if (linear) 1 else 0))
        }
        nodeValues.put(JSONObject().put("element", node.element).put("ordinal", node.ordinal)
            .put("original", node.original).put("text", node.text).put("runs", runs)
            .put("preserveBreaks", node.preserveBreaks)
            .put("beforeMedia", node.beforeMedia ?: JSONObject.NULL))
    }
    return JSONObject().put("version", 1).put("key", key).put("nodes", nodeValues)
        .put("excluded", JSONArray(source.excludedElements.toList()))
        .put("media", JSONArray().also { result -> media.forEach { item ->
            result.put(JSONObject().put("element", item.element).put("visible", item.visible)
                .put("start", item.start).put("end", item.end))
        } })
}
