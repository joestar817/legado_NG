package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val WORK_BUDDY_CREDITS_URL = "https://www.codebuddy.cn/v2/billing/meter/get-user-resource"
internal const val WORK_BUDDY_CHECK_IN_STATUS_PATH = "/v2/billing/meter/checkin-activity-status"
internal const val WORK_BUDDY_CHECK_IN_PATH = "/v2/billing/meter/daily-checkin"
private const val WORK_BUDDY_CREDITS_PAGE_SIZE = 100
private val creditsZone = ZoneId.of("Asia/Shanghai")
private val creditDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

internal data class AiWorkBuddyCredits(val remaining: Long, val total: Long)

internal enum class AiWorkBuddyCheckInStatus { UNAVAILABLE, AVAILABLE, CLAIMED }

internal data class AiWorkBuddyCheckIn(
    val status: AiWorkBuddyCheckInStatus,
    val dailyCredits: Long? = null,
)

internal data class AiWorkBuddyCheckInResult(
    val checkIn: AiWorkBuddyCheckIn,
    val awardedCredits: Long? = null,
)

internal fun parseWorkBuddyCheckIn(data: JsonObject): AiWorkBuddyCheckIn {
    val active = data.checkInFlag("active") ?: throw invalidWorkBuddyResponse()
    val status = if (active) when (data.checkInFlag("today_checked_in")) {
        true -> AiWorkBuddyCheckInStatus.CLAIMED
        false -> if (data.workBuddyLong("today_credit")?.let { it <= 0 } == true) {
            AiWorkBuddyCheckInStatus.UNAVAILABLE
        } else AiWorkBuddyCheckInStatus.AVAILABLE
        null -> throw invalidWorkBuddyResponse()
    } else AiWorkBuddyCheckInStatus.UNAVAILABLE
    return AiWorkBuddyCheckIn(
        status = status,
        dailyCredits = data.workBuddyLong("daily_credit")?.takeIf { it > 0 },
    )
}

private fun JsonObject.checkInFlag(key: String): Boolean? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean

internal fun workBuddyCreditsPayload(nowSeconds: Long): JsonObject {
    val now = LocalDateTime.ofInstant(Instant.ofEpochSecond(nowSeconds), creditsZone)
    return JsonObject().apply {
        addProperty("PageNumber", 1)
        addProperty("PageSize", WORK_BUDDY_CREDITS_PAGE_SIZE)
        addProperty("ProductCode", "p_tcaca")
        add("Status", JsonArray().apply {
            add(0)
            add(3)
        })
        addProperty("PackageEndTimeRangeBegin", now.format(creditDateFormat))
        addProperty("PackageEndTimeRangeEnd", now.plusYears(101).format(creditDateFormat))
    }
}

internal fun parseWorkBuddyCredits(data: JsonObject): AiWorkBuddyCredits {
    val response = data.objectOrNull("Response") ?: throw invalidWorkBuddyResponse()
    if (response.objectOrNull("Error") != null) throw invalidWorkBuddyResponse()
    val payload = response.objectOrNull("Data") ?: throw invalidWorkBuddyResponse()
    val accounts = payload.arrayOrNull("Accounts") ?: throw invalidWorkBuddyResponse()
    if (accounts.size() >= WORK_BUDDY_CREDITS_PAGE_SIZE ||
        (payload.workBuddyLong("TotalCount") ?: 0L) > accounts.size()
    ) throw invalidWorkBuddyResponse()
    var remaining = 0L
    var total = 0L
    accounts.forEach { item ->
        val pack = item.takeIf { it.isJsonObject }?.asJsonObject ?: throw invalidWorkBuddyResponse()
        val cycleSize = pack.workBuddyLong("CycleCapacitySize")?.takeIf { it > 0 }
        val size = cycleSize ?: pack.workBuddyLong("CapacitySize")?.takeIf { it >= 0 }
            ?: throw invalidWorkBuddyResponse()
        val available = if (cycleSize != null) {
            val cycleRemaining = pack.workBuddyLong("CycleCapacityRemain")
            val cycleUsed = pack.workBuddyLong("CycleCapacityUsed")?.takeIf { it >= 0 }
            val afterUse = cycleUsed?.let { size - it.coerceAtMost(size) }
            listOfNotNull(cycleRemaining, afterUse).minOrNull()
        } else {
            pack.workBuddyLong("CapacityRemain")
        } ?: throw invalidWorkBuddyResponse()
        val balance = available.coerceIn(0L, size)
        if (Long.MAX_VALUE - total < size) throw invalidWorkBuddyResponse()
        remaining += balance
        total += size
    }
    return AiWorkBuddyCredits(remaining, total)
}
