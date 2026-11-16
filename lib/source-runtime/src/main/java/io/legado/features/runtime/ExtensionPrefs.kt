package io.legado.features.runtime

import android.os.Build
import androidx.annotation.RequiresApi
import io.legado.features.runtime.RuntimeServices
import io.legado.features.runtime.model.MangaExtensionRepo
import io.legado.features.runtime.RuntimeJson as GSON
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

object ExtensionPrefs {

    private const val KEY_REPOS = "mangaExtensionRepos"
    private const val KEY_TRUSTED_SIGNATURES = "mangaExtensionTrustedSignatures"
    private const val KEY_SOURCE_ENABLED = "mangaExtensionSourceEnabled"
    private const val KEY_LANGUAGES = "mangaExtensionLanguages"
    private const val KEY_KIND_FILTER = "mangaExtensionKindFilter"
    private const val KEY_CONTENT_FILTER = "mangaExtensionContentFilter"

    private val repoListSerializer = ListSerializer(MangaExtensionRepo.serializer())
    private val stringSetSerializer = ListSerializer(String.serializer())
    private val sourceEnabledSerializer = MapSerializer(String.serializer(), Boolean.serializer())

    private fun getPrefString(key: String): String? =
        RuntimeServices.getPreference(key)

    private fun putPrefString(key: String, value: String) {
        RuntimeServices.putPreference(key, value)
    }

    fun getRepos(): List<MangaExtensionRepo> =
        decodeUserRepos().map { it.copy(isDefault = false) }

    fun addUserRepo(repo: MangaExtensionRepo) {
        val repos = decodeUserRepos().toMutableList()
        repos.removeAll { it.indexUrl == repo.indexUrl }
        repos.add(repo)
        putPrefString(KEY_REPOS, GSON.encodeToString(repoListSerializer, repos))
    }

    fun removeRepo(indexUrl: String) {
        val repos = decodeUserRepos().filterNot { it.indexUrl == indexUrl }
        putPrefString(KEY_REPOS, GSON.encodeToString(repoListSerializer, repos))
    }

    private fun decodeUserRepos(): List<MangaExtensionRepo> {
        val json = getPrefString(KEY_REPOS) ?: return emptyList()
        return runCatching { GSON.decodeFromString(repoListSerializer, json) }.getOrNull().orEmpty()
    }

    fun getTrustedSignatures(): Set<String> {
        val json = getPrefString(KEY_TRUSTED_SIGNATURES) ?: return emptySet()
        return runCatching {
            GSON.decodeFromString(stringSetSerializer, json).toSet()
        }.getOrNull().orEmpty()
    }

    fun setTrustedSignatures(signatures: Set<String>) {
        putPrefString(KEY_TRUSTED_SIGNATURES, GSON.encodeToString(stringSetSerializer, signatures.toList()))
    }

    fun getSelectedLanguages(): Set<String> {
        val json = getPrefString(KEY_LANGUAGES) ?: return emptySet()
        return runCatching {
            GSON.decodeFromString(stringSetSerializer, json).toSet()
        }.getOrNull().orEmpty()
    }

    fun setSelectedLanguages(languages: Set<String>) {
        putPrefString(
            KEY_LANGUAGES,
            GSON.encodeToString(stringSetSerializer, languages.toList()),
        )
    }

    fun getKindFilter(): String? = getPrefString(KEY_KIND_FILTER)?.takeIf { it.isNotBlank() }

    fun setKindFilter(name: String?) {
        putPrefString(KEY_KIND_FILTER, name.orEmpty())
    }

    fun getContentFilter(): String? = getPrefString(KEY_CONTENT_FILTER)?.takeIf { it.isNotBlank() }

    fun setContentFilter(name: String?) {
        putPrefString(KEY_CONTENT_FILTER, name.orEmpty())
    }

    fun getSourceEnabled(): Map<Long, Boolean> {
        val json = getPrefString(KEY_SOURCE_ENABLED) ?: return emptyMap()
        val decoded = runCatching {
            GSON.decodeFromString(sourceEnabledSerializer, json)
        }.getOrNull().orEmpty()
        return decoded.mapNotNull { (key, value) -> key.toLongOrNull()?.let { it to value } }.toMap()
    }

    fun isSourceEnabled(sourceId: Long, kind: String = "mihon"): Boolean {
        val json = getPrefString(KEY_SOURCE_ENABLED) ?: return true
        val values = runCatching { GSON.decodeFromString(sourceEnabledSerializer, json) }.getOrDefault(emptyMap())
        return values["$kind:$sourceId"] ?: values[sourceId.toString()] ?: true
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @Synchronized fun setSourceEnabled(sourceId: Long, enabled: Boolean, kind: String = "mihon") {
        val map = getPrefString(KEY_SOURCE_ENABLED)?.let {
            runCatching { GSON.decodeFromString(sourceEnabledSerializer, it) }.getOrDefault(emptyMap())
        }.orEmpty().toMutableMap()
        // Materialize a legacy numeric setting for both kinds before updating just one.
        map.remove(sourceId.toString())?.let { old ->
            map.putIfAbsent("mihon:$sourceId", old)
            map.putIfAbsent("aniyomi:$sourceId", old)
        }
        val key = "$kind:$sourceId"
        if (enabled) {
            map.remove(key)
        } else {
            map[key] = false
        }
        val json = GSON.encodeToString(sourceEnabledSerializer, map)
        putPrefString(KEY_SOURCE_ENABLED, json)
    }
}

