package io.legado.app.help.ai

import com.google.gson.annotations.SerializedName
import io.legado.app.constant.PreferKey
import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import splitties.init.appCtx

internal enum class AiWebRoutingMode {
    @SerializedName("auto") AUTO,
    @SerializedName("external") EXTERNAL,
}

internal enum class AiDoubaoSearchMode(val id: String, val resultCountRange: IntRange) {
    @SerializedName("global") GLOBAL("global", 1..20),
    @SerializedName("custom") CUSTOM("custom", 1..50),
}

internal enum class AiTavilySearchDepth(val apiValue: String) {
    @SerializedName("basic") BASIC("basic"),
    @SerializedName("advanced") ADVANCED("advanced"),
}

internal enum class AiExternalSearchProvider(
    val id: String,
    val defaultName: String,
    val host: String,
    val resultCountRange: IntRange,
    val maxQueryLength: Int,
) {
    @SerializedName("deepseek") DEEPSEEK("deepseek", "DeepSeek", "api.deepseek.com", 1..50, 128),
    @SerializedName("doubao") DOUBAO("doubao", "豆包搜索", "open.feedcoopapi.com", AiDoubaoSearchMode.CUSTOM.resultCountRange, 100),
    @SerializedName("tavily") TAVILY("tavily", "Tavily", "api.tavily.com", 1..20, 400),
    @SerializedName("tinyfish") TINYFISH("tinyfish", "TinyFish", "api.search.tinyfish.ai", 1..10, 400),
    @SerializedName("exa") EXA("exa", "Exa", "api.exa.ai", 1..100, 400);

    val baseUrl: String get() = "https://$host"
}

internal data class AiExternalSearchService(
    @SerializedName("provider") val provider: AiExternalSearchProvider = AiExternalSearchProvider.DEEPSEEK,
    @SerializedName("name") val name: String = provider.defaultName,
    @SerializedName("api_key") val apiKey: String = "",
    @SerializedName("result_count") val resultCount: Int = 10,
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("doubao_mode") val doubaoMode: AiDoubaoSearchMode = AiDoubaoSearchMode.CUSTOM,
    @SerializedName("tavily_search_depth") val tavilySearchDepth: AiTavilySearchDepth = AiTavilySearchDepth.BASIC,
) {
    val displayName: String get() = name.trim().ifEmpty { provider.defaultName }
    val available: Boolean get() = enabled && apiKey.isNotBlank()
    val resultCountRange: IntRange
        get() = if (provider == AiExternalSearchProvider.DOUBAO) doubaoMode.resultCountRange else provider.resultCountRange
    val chatResultCountRange: IntRange
        get() = resultCountRange.let { it.first..minOf(it.last, MAX_CHAT_RESULTS) }
    val chatResultCount: Int
        get() = resultCount.coerceIn(chatResultCountRange)

    private companion object {
        const val MAX_CHAT_RESULTS = 10
    }
}

internal data class AiExternalSearchSettings(
    @SerializedName("routing") val routing: AiWebRoutingMode = AiWebRoutingMode.AUTO,
    @SerializedName("selected_service") val selectedService: String = "",
    @SerializedName("deepseek") val deepSeek: AiExternalSearchService = AiExternalSearchService(),
    @SerializedName("doubao") private val doubao: AiExternalSearchService = AiExternalSearchService(AiExternalSearchProvider.DOUBAO),
    @SerializedName("tavily") private val tavily: AiExternalSearchService = AiExternalSearchService(AiExternalSearchProvider.TAVILY),
    @SerializedName("tinyfish") private val tinyFish: AiExternalSearchService = AiExternalSearchService(AiExternalSearchProvider.TINYFISH),
    @SerializedName("exa") private val exa: AiExternalSearchService = AiExternalSearchService(AiExternalSearchProvider.EXA),
    @SerializedName("service_order") private val serviceOrder: List<AiExternalSearchProvider?>? = null,
    @SerializedName("web_enabled") val webEnabled: Boolean = true,
    @SerializedName("backup_enabled") val backupEnabled: Boolean = false,
) {
    val usesExternalService: Boolean
        get() = webEnabled && (routing == AiWebRoutingMode.EXTERNAL || backupEnabled)

    val services: List<AiExternalSearchService>
        get() = (serviceOrder.orEmpty().filterNotNull() + AiExternalSearchProvider.entries)
            .distinct()
            .map(::service)

    fun service(provider: AiExternalSearchProvider): AiExternalSearchService = when (provider) {
        AiExternalSearchProvider.DEEPSEEK -> deepSeek
        AiExternalSearchProvider.DOUBAO -> doubao
        AiExternalSearchProvider.TAVILY -> tavily
        AiExternalSearchProvider.TINYFISH -> tinyFish
        AiExternalSearchProvider.EXA -> exa
    }

    fun withService(service: AiExternalSearchService): AiExternalSearchSettings = when (service.provider) {
        AiExternalSearchProvider.DEEPSEEK -> copy(deepSeek = service)
        AiExternalSearchProvider.DOUBAO -> copy(doubao = service)
        AiExternalSearchProvider.TAVILY -> copy(tavily = service)
        AiExternalSearchProvider.TINYFISH -> copy(tinyFish = service)
        AiExternalSearchProvider.EXA -> copy(exa = service)
    }

    fun withServiceOrder(order: List<AiExternalSearchProvider>): AiExternalSearchSettings {
        val providers = AiExternalSearchProvider.entries
        if (order.size != providers.size || order.toSet() != providers.toSet()) return this
        if (order == services.map(AiExternalSearchService::provider)) return this
        return copy(serviceOrder = order.toList())
    }

    fun activeService(): AiExternalSearchService? = availableService(selectedService)

    fun selectService(provider: AiExternalSearchProvider): AiExternalSearchSettings =
        if (service(provider).available) copy(selectedService = provider.id) else this

    private fun availableService(serviceId: String): AiExternalSearchService? =
        AiExternalSearchProvider.entries.firstOrNull { it.id == serviceId }
            ?.let(::service)
            ?.takeIf { it.available }
}

internal object AiExternalSearchStore {
    private val settings by lazy {
        val saved = appCtx.getPrefString(PreferKey.aiExternalSearchJson).orEmpty()
        val decoded = runCatching {
            GSON.fromJson(saved, AiExternalSearchSettings::class.java)
        }.getOrNull()
        MutableStateFlow(decoded ?: AiExternalSearchSettings())
    }

    val changes by lazy { settings.asStateFlow() }

    fun current(): AiExternalSearchSettings = settings.value

    @Synchronized
    fun update(transform: (AiExternalSearchSettings) -> AiExternalSearchSettings): AiExternalSearchSettings {
        val previous = settings.value
        val next = transform(previous)
        if (next != previous) {
            // AI preferences are excluded from portable backups by BackupModules.
            appCtx.putPrefString(PreferKey.aiExternalSearchJson, GSON.toJson(next))
            settings.value = next
        }
        return next
    }
}
