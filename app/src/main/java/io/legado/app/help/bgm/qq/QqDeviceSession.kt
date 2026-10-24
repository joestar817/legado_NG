package io.legado.app.help.bgm.qq

import io.legado.app.help.bgm.MusicHttpClient
import io.legado.app.help.bgm.MusicProviderException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** QQMusicApi 0.8.2 Android identity/QIMEI/session subset, GPL-3.0.
 * Uses a private, generated identity; it does not read the phone's hardware identifiers.
 */
internal class QqDeviceSession(
    private val http: MusicHttpClient,
    private val saved: JSONObject,
    private val persist: () -> Unit
) {
    private val device = saved.optJSONObject("device") ?: qJson(
        "androidId" to QqRequestCodec.randomHex(16),
        "openUdid" to UUID.randomUUID().toString().replace("-", ""),
        "openUdid2" to UUID.randomUUID().toString().replace("-", ""),
        "imei" to randomImei()
    ).also { saved.put("device", it); persist() }

    val userAgent = "QQMusic 20090008(android 15)"

    suspend fun comm(credential: JSONObject, includeSession: Boolean = true): JSONObject {
        val qimei = ensureQimei()
        val session = if (includeSession) ensureSession() else null
        val musicId = credential.optString("musicid").takeUnless { it == "0" }.orEmpty()
        return qJson(
            "ct" to "11", "cv" to "20090008", "v" to "20090008", "chid" to "10003505",
            "tmeAppID" to "qqmusic", "traceid" to "10002_${musicId.ifEmpty { device.getString("openUdid") }}_${System.currentTimeMillis() / 1000}",
            "QIMEI36" to qimei.getString("q36"), "OpenUDID" to device.getString("openUdid"),
            "udid" to device.getString("openUdid"), "OpenUDID2" to device.getString("openUdid2"),
            "aid" to device.getString("androidId"), "os_ver" to "15", "phonetype" to "V2408A"
        ).apply {
            if (musicId.isNotEmpty()) put("qq", musicId)
            credential.optString("musickey").takeIf(String::isNotEmpty)?.let { put("authst", it) }
            credential.optInt("loginType").takeIf { it != 0 }?.let { put("tmeLoginType", it.toString()) }
            session?.let { put("uid", it.getString("uid")); put("sid", it.getString("sid")) }
        }
    }

    private suspend fun ensureQimei(): JSONObject {
        saved.optJSONObject("qimei")?.takeIf {
            it.optString("q16").isNotEmpty() && it.optString("q36").isNotEmpty() &&
                System.currentTimeMillis() - it.optLong("savedAt") in 0L until 86_400_000L
        }?.let { return it }

        val cryptKey = QqRequestCodec.randomHex(16)
        val nonce = QqRequestCodec.randomHex(16)
        val seconds = System.currentTimeMillis() / 1000
        val extra = "{\"appKey\":\"0AND0HD6FE4HY80F\"}"
        val key = QqRequestCodec.rsaBase64(cryptKey)
        val params = QqRequestCodec.aesBase64(cryptKey, qimeiPayload().toString())
        val request = qJson("app" to 0, "os" to 1, "qimeiParams" to qJson(
            "key" to key, "params" to params, "time" to seconds.toString(), "nonce" to nonce,
            "sign" to QqRequestCodec.md5(key, params, (seconds * 1000).toString(), nonce,
                "ZdJqM15EeO2zWc08", extra), "extra" to extra))
        val response = http.postJson("https://api.tencentmusic.com/tme/trpc/proxy", request,
            mapOf("method" to "GetQimei", "service" to "trpc.tme_datasvr.qimeiproxy.QimeiProxy",
                "appid" to "qimei_qq_android", "user-agent" to "QQMusic", "timestamp" to seconds.toString(),
                "sign" to QqRequestCodec.md5("qimei_qq_androidpzAuCmaFAaFaHrdakPjLIEqKrGnSOOvH", seconds.toString())))
        if (response.status != 200) throw MusicProviderException("QQ 音乐设备验证失败")
        val outer = response.json()
        val envelope = outer.optJSONObject("data") ?: outer.optString("data").takeIf(String::isNotEmpty)
            ?.let(::JSONObject) ?: throw MusicProviderException("QQ 音乐设备验证响应异常")
        val result = envelope.optJSONObject("data") ?: throw MusicProviderException("QQ 音乐设备验证响应异常")
        if (result.optString("q16").isEmpty() || result.optString("q36").isEmpty()) {
            throw MusicProviderException("QQ 音乐设备验证响应异常")
        }
        return qJson("q16" to result.getString("q16"), "q36" to result.getString("q36"),
            "savedAt" to System.currentTimeMillis()).also { saved.put("qimei", it); persist() }
    }

    private suspend fun ensureSession(): JSONObject {
        val stale = saved.optJSONObject("session")
        if (stale != null && stale.optString("uid").isNotEmpty() && stale.optString("sid").isNotEmpty() &&
            sameDay(stale.optLong("savedAt"), System.currentTimeMillis())) return stale
        val payload = qJson("comm" to comm(JSONObject(), false), "req_0" to qJson(
            "module" to "music.getSession.session", "method" to "GetSession",
            "param" to qJson("uid" to stale?.optString("uid").orEmpty(), "vkey" to 0,
                "caller" to if (stale == null) 2 else 1)))
        val response = http.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", payload,
            mapOf("User-Agent" to userAgent))
        val data = qqCgiData(response.json()).optJSONObject("session")
            ?: throw MusicProviderException("QQ 音乐设备会话响应异常")
        if (data.optString("uid").isEmpty() || data.optString("sid").isEmpty()) {
            throw MusicProviderException("QQ 音乐设备会话响应异常")
        }
        return qJson("uid" to data.getString("uid"), "sid" to data.getString("sid"),
            "savedAt" to System.currentTimeMillis()).also { saved.put("session", it); persist() }
    }

    private fun qimeiPayload(): JSONObject {
        val now = System.currentTimeMillis()
        val month = utcFormat("yyyy-MM-'01'", now)
        val rand1 = 100_000 + QqRequestCodec.randomInt(900_000)
        val rand2 = 100_000_000 + QqRequestCodec.randomInt(900_000_000)
        val timeKeys = setOf(1, 2, 13, 14, 17, 18, 21, 22, 25, 26, 29, 30, 33, 34, 37, 38)
        val beacon = (1..40).joinToString("") { index ->
            val value = when {
                index in timeKeys -> "$month$rand1.$rand2"
                index == 3 -> "0000000000000000"
                index == 4 -> (1..16).joinToString("") { "123456789abcdef"[QqRequestCodec.randomInt(15)].toString() }
                else -> QqRequestCodec.randomInt(10_000).toString()
            }
            "k$index:$value;"
        }
        val androidId = device.getString("androidId")
        val md5 = QqRequestCodec.md5(androidId)
        val reserved = qJson("harmony" to "0", "clone" to "0", "containe" to "",
            "oz" to QqRequestCodec.aesBase64("lvcwmSYVr2Axv1gn", androidId, "Zs0ntDqG2jyhKN0c"),
            "oo" to QqRequestCodec.aesBase64("lvcwmSYVr2Axv1gn", "V2408A", "Zs0ntDqG2jyhKN0c"),
            "kelong" to "0", "ip" to "192.168.${md5.take(2).toInt(16)}.${md5.substring(2, 4).toInt(16) % 253 + 2}",
            "uptimes" to utcFormat("yyyy-MM-dd HH:mm:ss", now - QqRequestCodec.randomInt(14401) * 1000L),
            "multiUser" to "0", "bod" to "sun", "brd" to "vivo", "dv" to "PD2408",
            "firstLevel" to "35", "manufact" to "vivo", "name" to "PD2408", "host" to "comdg01150014",
            "kernel" to "Linux localhost 6.6.89-android15-8-g1f71897ac249-abogki467805059-4k #1 SMP PREEMPT Thu Dec 11 01:56:00 UTC 2025 aarch64",
            "pre" to "0", "av" to "20.9.0.8", "ch" to "")
        return qJson("androidId" to androidId, "platformId" to 1, "appKey" to "0AND0HD6FE4HY80F",
            "appVersion" to "20.9.0.8", "beaconIdSrc" to beacon, "brand" to "vivo", "channelId" to "10003505",
            "cid" to "", "imei" to device.getString("imei"), "imsi" to "", "mac" to "", "model" to "V2408A",
            "networkType" to "wifi", "oaid" to "", "osVersion" to "Android 15,level 35", "qimei" to "",
            "qimei36" to "", "sdkVersion" to "1.2.13.6", "targetSdkVersion" to "30", "audit" to "",
            "userId" to "{}", "packageId" to "com.tencent.qqmusic", "deviceType" to "Phone", "sdkName" to "",
            "reserved" to reserved.toString())
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        if (a <= 0 || a > b) return false
        val first = Calendar.getInstance().apply { timeInMillis = a }
        val second = Calendar.getInstance().apply { timeInMillis = b }
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }

    private fun utcFormat(pattern: String, millis: Long): String =
        SimpleDateFormat(pattern, Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))

    private fun randomImei(): String {
        val digits = (1..14).map { QqRequestCodec.randomInt(10) }
        val sum = digits.mapIndexed { i, d -> if (i % 2 == 1) (d * 2).let { if (it > 9) it - 9 else it } else d }.sum()
        return digits.joinToString("") + (10 - sum % 10) % 10
    }
}

internal fun qJson(vararg values: Pair<String, Any?>): JSONObject = JSONObject().apply {
    values.forEach { (key, value) -> put(key, value) }
}

internal fun qqCgiData(response: JSONObject): JSONObject {
    val outerCode = response.optInt("code", -1)
    if (outerCode != 0) throw MusicProviderException("QQ 音乐请求失败（$outerCode）", outerCode)
    val item = response.optJSONObject("req_0") ?: throw MusicProviderException("QQ 音乐响应异常")
    val code = item.optInt("code", -1)
    if (code != 0) throw MusicProviderException("QQ 音乐请求失败（$code）", code)
    return item.optJSONObject("data") ?: throw MusicProviderException("QQ 音乐响应异常")
}
