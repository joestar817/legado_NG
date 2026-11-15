package io.legado.features.aniyomi

import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.serialization.json.*

/** Keep source memo intact while persisting the ABI fields needed to choose the directory API. */
internal object AnimeCatalogMetadata {
    private const val FORMAT = "legado:aniyomi:catalog:1"

    fun encode(anime: SAnime): String = buildJsonObject {
        put("format", FORMAT)
        put("memo", anime.memo)
        put("fetchType", anime.fetch_type.name)
        put("seasonNumber", anime.season_number)
    }.toString()

    fun restore(anime: SAnime, metadata: String?) {
        val value = metadata?.let { Json.parseToJsonElement(it) as JsonObject } ?: JsonObject(emptyMap())
        if ((value["format"] as? JsonPrimitive)?.content != FORMAT || value["memo"] !is JsonObject) {
            anime.memo = value // Previously saved metadata was the raw extension memo.
            return
        }
        anime.memo = value.getValue("memo") as JsonObject
        anime.fetch_type = if ((value["fetchType"] as? JsonPrimitive)?.content == FetchType.Seasons.name)
            FetchType.Seasons else FetchType.Episodes
        anime.season_number = (value["seasonNumber"] as? JsonPrimitive)?.doubleOrNull ?: -1.0
    }
}
