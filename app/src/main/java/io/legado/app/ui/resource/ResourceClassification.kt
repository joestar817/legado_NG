package io.legado.app.ui.resource

import io.legado.app.help.resource.ResourceCategory
import io.legado.app.help.resource.ResourceEntry
import io.legado.app.help.resource.ResourceProtocol
import io.legado.app.help.resource.HOME_WIDGET_SKIN_TYPE

/** 用途只负责浏览；不改目录身份、安装类型或安装记录。 */
internal enum class ResourcePurpose(val title: String) {
    READING("阅读"), APPEARANCE("外观"), LISTENING("听书"), PETS("宠物")
}

internal data class ResourceTypeOption(val key: String, val title: String)

/** null为全部类型，空集合表示本地页/尚无对应远端类型，后台仍可补齐其它索引。 */
internal fun resourcePriorityTypes(purpose: ResourcePurpose?, type: String): Set<String>? {
    if (type.isNotEmpty() && ResourceProtocol.isBrowsableType(type)) return setOf(type)
    return when (purpose) {
        null -> null
        ResourcePurpose.READING -> setOf("read_preset")
        ResourcePurpose.APPEARANCE -> setOf("theme", HOME_WIDGET_SKIN_TYPE)
        ResourcePurpose.LISTENING -> setOf("tts")
        ResourcePurpose.PETS -> setOf("pet")
    }
}

internal fun ResourceCategory.resourcePurpose(): ResourcePurpose? = when (type) {
    "font", "background", "read_preset" -> ResourcePurpose.READING
    "theme", HOME_WIDGET_SKIN_TYPE -> ResourcePurpose.APPEARANCE
    "tts" -> ResourcePurpose.LISTENING
    "pet" -> ResourcePurpose.PETS
    else -> null
}

internal fun ResourceCategory.resourceTypeTitle(): String = when (type) {
    "font" -> "字体"
    "background" -> "背景"
    "read_preset" -> "阅读预设"
    "theme" -> "主题包"
    HOME_WIDGET_SKIN_TYPE -> "组件皮肤"
    "tts" -> "TTS 脚本"
    "pet" -> "宠物插件"
    else -> name
}

/** 同一安装类型可能存在多个目录分类，菜单合并展示而不丢失其中的资源。 */
internal fun resourceTypeOptions(
    categories: List<ResourceCategory>,
    purpose: ResourcePurpose?,
): List<ResourceTypeOption> = categories.asSequence()
    .filter {
        it.type.isNotBlank() && ResourceProtocol.isBrowsableType(it.type) &&
            (purpose == null || it.resourcePurpose() == purpose)
    }
    .distinctBy { it.type }
    .map { ResourceTypeOption(it.type, it.resourceTypeTitle()) }
    .toList()

internal fun filterResourceEntries(
    entries: List<ResourceEntry>,
    purpose: ResourcePurpose?,
    type: String,
    query: String,
): List<ResourceEntry> {
    val search = query.trim()
    return entries.filter { entry ->
        ResourceProtocol.isBrowsableType(entry.category.type) &&
            (purpose == null || entry.category.resourcePurpose() == purpose) &&
            (type.isEmpty() || entry.category.type == type) &&
            (search.isEmpty() || listOf(entry.item.name, entry.item.author, entry.item.description)
                .plus(entry.item.tags).any { it.contains(search, ignoreCase = true) })
    }
}
