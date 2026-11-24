package io.legado.app.help.ai

import com.google.gson.JsonObject
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

internal data class AiCodexUsage(
    val plan: String?,
    val windows: List<Window>,
    val creditsBalance: String?,
    val unlimitedCredits: Boolean,
) {
    data class Window(val remainingPercent: Int, val durationSeconds: Int?, val resetsAt: Long?)

    val planDisplayName: String?
        get() = when (plan?.lowercase(Locale.ROOT)) {
            "prolite" -> "ChatGPT Pro 100"
            "pro" -> "ChatGPT Pro 200"
            "promax" -> "ChatGPT Pro 500"
            "plus" -> "ChatGPT Plus"
            "go" -> "ChatGPT Go"
            "free" -> "ChatGPT Free"
            else -> plan
        }

    fun formatCredits(locale: Locale): String? {
        val balance = creditsBalance?.toBigDecimalOrNull() ?: return null
        val integerDigits = balance.precision().toLong() - balance.scale()
        if (integerDigits > 40) return null
        val formatter = NumberFormat.getIntegerInstance(locale).apply { roundingMode = RoundingMode.HALF_UP }
        return if (integerDigits < 0) formatter.format(0) else formatter.format(balance)
    }
}

internal suspend fun AiProviderSetting.fetchCodexUsage(): AiCodexUsage =
    parseCodexUsage(getCodexAccountJson("https://chatgpt.com/backend-api/wham/usage"))

internal fun parseCodexUsage(json: JsonObject): AiCodexUsage {
    val limits = json.objectOrNull("rate_limit")
    val windows = listOfNotNull(limits?.objectOrNull("primary_window"), limits?.objectOrNull("secondary_window"))
        .mapNotNull { window ->
            val used = runCatching { window.get("used_percent")?.asDouble }.getOrNull()
                ?.takeIf { it in 0.0..100.0 } ?: return@mapNotNull null
            AiCodexUsage.Window(
                remainingPercent = (100 - used).roundToInt(),
                durationSeconds = window.positiveInt("limit_window_seconds"),
                resetsAt = runCatching { window.get("reset_at")?.asLong }.getOrNull()
                    ?.takeIf { it in 1..253402300799L },
            )
        }
    val credits = json.objectOrNull("credits")
    return AiCodexUsage(
        plan = json.stringOrNull("plan_type")?.trim()?.takeIf { it.isNotEmpty() && it.length <= 36 },
        windows = windows,
        creditsBalance = credits?.get("balance")?.takeIf { it.isJsonPrimitive }?.asString
            ?.takeIf { it.length <= 40 && it.toBigDecimalOrNull() != null },
        unlimitedCredits = credits?.get("unlimited")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
            ?.asBoolean == true,
    )
}
