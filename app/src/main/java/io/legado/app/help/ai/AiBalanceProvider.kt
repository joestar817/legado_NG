package io.legado.app.help.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Request
import java.util.Locale

data class AiBalanceInfo(
    val name: String,
    val remaining: Double? = null,
    val total: Double? = null,
    val used: Double? = null,
    val unit: String? = null,
    val isValid: Boolean? = null,
    val invalidMessage: String? = null
)

data class AiBalanceResult(
    val providerName: String,
    val items: List<AiBalanceInfo>
)

object AiBalanceProvider {

    suspend fun query(setting: AiProviderSetting): AiBalanceResult {
        check(setting.apiKey.isNotBlank()) { "API key is empty" }
        val detected = BalanceProvider.detect(setting.baseUrl)
            ?: error("Current provider does not support balance query")
        val client = aiHttpClient(setting.timeoutSeconds)
        val request = Request.Builder()
            .url(detected.endpoint)
            .addHeader("Authorization", "Bearer ${setting.apiKey}")
            .addHeader("Accept", "application/json")
            .get()
            .build()
        val (response, body) = client.executeJsonOrThrow(request)
        if (response.code == 401 || response.code == 403) {
            error("Authentication failed (HTTP ${response.code})")
        }
        if (!response.isSuccessful) {
            error("HTTP ${response.code}: ${body.take(500)}")
        }
        val json = JsonParser.parseString(body).asJsonObject
        val items = detected.parse(json)
        check(items.isNotEmpty()) { "Balance response is empty" }
        return AiBalanceResult(detected.displayName, items)
    }

    private enum class BalanceProvider(
        val displayName: String,
        val endpoint: String
    ) {
        DEEPSEEK("DeepSeek", "https://api.deepseek.com/user/balance") {
            override fun parse(json: JsonObject): List<AiBalanceInfo> {
                val available = json.boolOrNull("is_available") ?: true
                return json.arrayOrNull("balance_infos")
                    ?.mapNotNull { item ->
                        val obj = item.asObjectOrNull() ?: return@mapNotNull null
                        val currency = obj.stringOrNull("currency") ?: "CNY"
                        val total = obj.doubleOrNull("total_balance")
                        AiBalanceInfo(
                            name = currency,
                            remaining = total,
                            unit = currency,
                            isValid = available,
                            invalidMessage = if (available) null else "Insufficient balance"
                        )
                    }
                    ?: emptyList()
            }
        },
        STEPFUN("StepFun", "https://api.stepfun.com/v1/accounts") {
            override fun parse(json: JsonObject): List<AiBalanceInfo> {
                return listOf(
                    AiBalanceInfo(
                        name = "StepFun",
                        remaining = json.doubleOrNull("balance"),
                        unit = "CNY",
                        isValid = true
                    )
                )
            }
        },
        SILICON_FLOW_CN("SiliconFlow", "https://api.siliconflow.cn/v1/user/info") {
            override fun parse(json: JsonObject): List<AiBalanceInfo> {
                return parseSiliconFlow(json, "SiliconFlow", "CNY")
            }
        },
        SILICON_FLOW_EN("SiliconFlow (EN)", "https://api.siliconflow.com/v1/user/info") {
            override fun parse(json: JsonObject): List<AiBalanceInfo> {
                return parseSiliconFlow(json, "SiliconFlow (EN)", "USD")
            }
        },
        OPEN_ROUTER("OpenRouter", "https://openrouter.ai/api/v1/credits") {
            override fun parse(json: JsonObject): List<AiBalanceInfo> {
                val data = json.objectOrNull("data") ?: json
                val total = data.doubleOrNull("total_credits") ?: 0.0
                val used = data.doubleOrNull("total_usage") ?: 0.0
                val remaining = total - used
                return listOf(
                    AiBalanceInfo(
                        name = "OpenRouter",
                        remaining = remaining,
                        total = total,
                        used = used,
                        unit = "USD",
                        isValid = remaining > 0.0,
                        invalidMessage = if (remaining > 0.0) null else "No credits remaining"
                    )
                )
            }
        },
        NOVITA("Novita AI", "https://api.novita.ai/v3/user/balance") {
            override fun parse(json: JsonObject): List<AiBalanceInfo> {
                val available = (json.doubleOrNull("availableBalance") ?: 0.0) / 10000.0
                return listOf(
                    AiBalanceInfo(
                        name = "Novita AI",
                        remaining = available,
                        unit = "USD",
                        isValid = available > 0.0,
                        invalidMessage = if (available > 0.0) null else "No balance remaining"
                    )
                )
            }
        };

        abstract fun parse(json: JsonObject): List<AiBalanceInfo>

        companion object {
            fun detect(baseUrl: String): BalanceProvider? {
                val url = baseUrl.lowercase(Locale.ROOT)
                return when {
                    "api.deepseek.com" in url -> DEEPSEEK
                    "api.stepfun.ai" in url || "api.stepfun.com" in url -> STEPFUN
                    "api.siliconflow.cn" in url -> SILICON_FLOW_CN
                    "api.siliconflow.com" in url -> SILICON_FLOW_EN
                    "openrouter.ai" in url -> OPEN_ROUTER
                    "api.novita.ai" in url -> NOVITA
                    else -> null
                }
            }

            private fun parseSiliconFlow(
                json: JsonObject,
                name: String,
                unit: String
            ): List<AiBalanceInfo> {
                val data = json.objectOrNull("data") ?: return emptyList()
                return listOf(
                    AiBalanceInfo(
                        name = name,
                        remaining = data.doubleOrNull("totalBalance"),
                        unit = unit,
                        isValid = true
                    )
                )
            }
        }
    }

    private fun JsonObject.boolOrNull(key: String): Boolean? {
        return get(key)?.takeIf { !it.isJsonNull }?.asBoolean
    }

    private fun JsonObject.doubleOrNull(key: String): Double? {
        return get(key)?.doubleOrNull()
    }

    private fun JsonElement.doubleOrNull(): Double? {
        return runCatching {
            when {
                isJsonNull -> null
                isJsonPrimitive && asJsonPrimitive.isNumber -> asDouble
                isJsonPrimitive -> asString.toDoubleOrNull()
                else -> null
            }
        }.getOrNull()
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? {
        return takeIf { it.isJsonObject }?.asJsonObject
    }
}
