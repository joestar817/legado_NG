package io.legado.app.ui.resource

import io.legado.app.help.resource.ResourceEntry

/** 纯展示行；组标题不占用资源身份，切换视图以资源key而非旧像素/行号定位。 */
internal data class ResourceDisplayRow(
    val key: String,
    val entry: ResourceEntry? = null,
    val groupName: String = "",
    val count: Int = 0,
)

internal fun resourceDisplayRows(entries: List<ResourceEntry>, grouped: Boolean, keepSingleHeader: Boolean = false): List<ResourceDisplayRow> {
    fun row(entry: ResourceEntry) = ResourceDisplayRow(key = "resource:${entry.item.id}", entry = entry)
    val groups = entries.groupBy { it.category.id }
    if (!grouped || (groups.size < 2 && !keepSingleHeader)) return entries.map(::row)
    return groups.flatMap { (id, items) ->
        listOf(ResourceDisplayRow(key = "group:$id", groupName = items.first().category.resourceTypeTitle(), count = items.size)) +
            items.map(::row)
    }
}

internal fun resourceAnchorIndex(rows: List<ResourceDisplayRow>, key: String?): Int =
    rows.indexOfFirst { it.key == key }.coerceAtLeast(0)

