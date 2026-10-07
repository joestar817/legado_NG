package io.legado.app.ui.main.home

import androidx.annotation.StringRes
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import io.legado.app.R

/** Stable instance identity is independent of type, style and position. */
internal data class HomeWidgetInstance(
    @SerializedName("id") val id: String,
    @SerializedName("type") val typeId: String,
    @SerializedName("variant") val variantId: String,
)

internal enum class HomeWidgetSize(val columns: Int) {
    SMALL(1), LARGE(2),
}

internal data class HomeWidgetVariant(
    val id: String,
    val size: HomeWidgetSize,
    @param:StringRes val titleRes: Int,
    val styleId: String = "basic",
    @param:StringRes val styleTitleRes: Int = R.string.home_style_basic,
)

internal data class HomeWidgetType(
    val id: String,
    val variants: List<HomeWidgetVariant>,
)

/** Add future content types and variants here; the grid only consumes their column span. */
internal object HomeWidgetCatalog {
    val types = listOf("reading", "listening", "updates").map { id ->
        HomeWidgetType(
            id = id,
            variants = listOf(
                HomeWidgetVariant("small", HomeWidgetSize.SMALL, R.string.home_size_small),
                HomeWidgetVariant("large", HomeWidgetSize.LARGE, R.string.home_size_large),
            ) + if (id == "listening" || id == "reading" || id == "updates") listOf(
                HomeWidgetVariant(
                    "story-small", HomeWidgetSize.SMALL, R.string.home_variant_storybook_small,
                    styleId = "storybook", styleTitleRes = R.string.home_style_storybook,
                ),
                HomeWidgetVariant(
                    "story-large", HomeWidgetSize.LARGE, R.string.home_variant_storybook_large,
                    styleId = "storybook", styleTitleRes = R.string.home_style_storybook,
                ),
                HomeWidgetVariant(
                    "night-small", HomeWidgetSize.SMALL, R.string.home_variant_night_small,
                    styleId = "night", styleTitleRes = R.string.home_style_night,
                ),
                HomeWidgetVariant(
                    "night-large", HomeWidgetSize.LARGE, R.string.home_variant_night_large,
                    styleId = "night", styleTitleRes = R.string.home_style_night,
                ),
            ) else emptyList(),
        )
    } + HomeWidgetType(
        id = "calendar",
        variants = listOf(
            HomeWidgetVariant("large", HomeWidgetSize.LARGE, R.string.home_size_large),
            HomeWidgetVariant(
                "story-large", HomeWidgetSize.LARGE, R.string.home_variant_storybook_large,
                styleId = "storybook", styleTitleRes = R.string.home_style_storybook,
            ),
            HomeWidgetVariant(
                "night-large", HomeWidgetSize.LARGE, R.string.home_variant_night_large,
                styleId = "night", styleTitleRes = R.string.home_style_night,
            ),
        ),
    )

    fun type(id: String): HomeWidgetType? = types.firstOrNull { it.id == id }

    fun variant(widget: HomeWidgetInstance): HomeWidgetVariant? =
        type(widget.typeId)?.variants?.firstOrNull { it.id == widget.variantId }

    fun variantsForSize(typeId: String, size: HomeWidgetSize): List<HomeWidgetVariant> =
        type(typeId)?.variants?.filter { it.size == size }.orEmpty()

    fun variantForSize(typeId: String, selectedId: String, size: HomeWidgetSize): HomeWidgetVariant? {
        val type = type(typeId) ?: return null
        return selectHomeWidgetSize(type.variants, selectedId, size)
    }

    fun defaults(): List<HomeWidgetInstance> = listOf(
        HomeWidgetInstance("default_updates", "updates", "story-large"),
        HomeWidgetInstance("default_reading", "reading", "story-small"),
        HomeWidgetInstance("default_listening", "listening", "story-small"),
        HomeWidgetInstance("default_calendar", "calendar", "story-large"),
    )
}

internal fun selectHomeWidgetSize(
    variants: List<HomeWidgetVariant>,
    selectedId: String,
    size: HomeWidgetSize,
): HomeWidgetVariant? {
    val styleId = variants.firstOrNull { it.id == selectedId }?.styleId
    val choices = variants.filter { it.size == size }
    return choices.firstOrNull { it.styleId == styleId } ?: choices.firstOrNull()
}

internal fun moveHomeWidget(
    widgets: List<HomeWidgetInstance>,
    fromId: String,
    toId: String,
): List<HomeWidgetInstance> {
    val from = widgets.indexOfFirst { it.id == fromId }
    val to = widgets.indexOfFirst { it.id == toId }
    if (from < 0 || to < 0 || from == to) return widgets
    return widgets.toMutableList().apply { add(to, removeAt(from)) }
}

/** Null means a stale/invalid editor target; do not commit the rest of its draft. */
internal fun changeHomeWidgetVariant(
    widgets: List<HomeWidgetInstance>,
    id: String,
    variantId: String,
): List<HomeWidgetInstance>? {
    val widget = widgets.firstOrNull { it.id == id } ?: return null
    if (HomeWidgetCatalog.type(widget.typeId)?.variants?.none { it.id == variantId } != false) return null
    return widgets.map { if (it.id == id) it.copy(variantId = variantId) else it }
}

/** Explicit JSON keys avoid reflection/R8 field-name coupling in the new layout format. */
internal object HomeWidgetLayoutCodec {
    fun encode(widgets: List<HomeWidgetInstance>): String = JsonObject().apply {
        addProperty("version", 1)
        add("widgets", JsonArray().apply {
            widgets.forEach { widget ->
                add(JsonObject().apply {
                    addProperty("id", widget.id)
                    addProperty("type", widget.typeId)
                    addProperty("variant", widget.variantId)
                })
            }
        })
    }.toString()

    fun decode(json: String): List<HomeWidgetInstance> {
        val root = JsonParser.parseString(json)
        require(root.isJsonObject)
        val obj = root.asJsonObject
        require(obj.get("version")?.toString() == "1")
        val entries = obj.get("widgets")
        require(entries != null && entries.isJsonArray)
        val ids = hashSetOf<String>()
        return entries.asJsonArray.map { entry ->
            require(entry.isJsonObject)
            val fields = entry.asJsonObject
            val widget = HomeWidgetInstance(
                id = fields.requiredString("id"),
                typeId = fields.requiredString("type"),
                variantId = fields.requiredString("variant"),
            )
            require(ids.add(widget.id))
            require(HomeWidgetCatalog.variant(widget) != null)
            widget
        }
    }

    private fun JsonObject.requiredString(key: String): String {
        val value = get(key)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString.also { require(it.isNotBlank()) }
    }
}
