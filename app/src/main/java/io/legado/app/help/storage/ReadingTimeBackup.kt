package io.legado.app.help.storage

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/** Fixed JSON keys keep cumulative timing portable without relying on reflected field names. */
internal object ReadingTimeBackup {
    const val fileName = "readingTime.json"
    private const val VERSION = 1L
    private val integer = Regex("0|[1-9][0-9]*")

    data class Snapshot(val totalReadTime: Long, val days: Map<Long, Long>)

    fun encode(snapshot: Snapshot): String {
        require(snapshot.totalReadTime >= 0L) { "阅读总时长不能为负数" }
        val days = JsonArray()
        snapshot.days.toSortedMap().forEach { (epochDay, readTime) ->
            require(epochDay >= 0L && readTime >= 0L) { "每日阅读时长无效" }
            days.add(JsonObject().apply {
                addProperty("epochDay", epochDay)
                addProperty("readTime", readTime)
            })
        }
        return JsonObject().apply {
            addProperty("version", VERSION)
            addProperty("totalReadTime", snapshot.totalReadTime)
            add("days", days)
        }.toString()
    }

    fun decode(json: String): Snapshot {
        val root = JsonParser.parseString(json)
        require(root.isJsonObject) { "阅读时长备份格式无效" }
        val value = root.asJsonObject
        require(value.keySet() == setOf("version", "totalReadTime", "days")) {
            "阅读时长备份字段无效"
        }
        require(nonNegativeLong(value.get("version")) == VERSION) { "不支持的阅读时长备份版本" }
        val total = nonNegativeLong(value.get("totalReadTime"))
        val entries = value.get("days")
        require(entries.isJsonArray) { "每日阅读时长备份格式无效" }
        val days = linkedMapOf<Long, Long>()
        entries.asJsonArray.forEach { entry ->
            require(entry.isJsonObject) { "每日阅读时长备份格式无效" }
            val day = entry.asJsonObject
            require(day.keySet() == setOf("epochDay", "readTime")) { "每日阅读时长备份字段无效" }
            val epochDay = nonNegativeLong(day.get("epochDay"))
            val readTime = nonNegativeLong(day.get("readTime"))
            require(days.put(epochDay, readTime) == null) { "每日阅读时长备份日期重复" }
        }
        return Snapshot(total, days)
    }

    /** Missing files remain distinguishable from an explicit zero counter. */
    fun read(root: File): Snapshot? {
        val file = File(root, fileName)
        if (!file.exists()) return null
        require(file.isFile) { "阅读时长备份不是文件" }
        return decode(file.readText())
    }

    /**
     * The old archive's total was the sum of its own book counters. Preserve that historical
     * value only on explicit import; never rebuild it from already merged local records.
     * The archive has no daily intervals, so the calendar must remain unchanged.
     */
    fun snapshotForRestore(snapshot: Snapshot?, legacyReadTimes: List<Long>?): Snapshot? {
        if (snapshot != null) return snapshot
        if (legacyReadTimes == null) return null
        val total = legacyReadTimes.fold(0L) { accumulated, readTime ->
            require(readTime >= 0L) { "旧备份阅读时长不能为负数" }
            Math.addExact(accumulated, readTime)
        }
        return Snapshot(total, emptyMap())
    }

    private fun nonNegativeLong(value: JsonElement?): Long {
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
            "阅读时长备份必须使用非负整数"
        }
        val text = value.asString
        require(integer.matches(text)) { "阅读时长备份必须使用非负整数" }
        return requireNotNull(text.toLongOrNull()) { "阅读时长备份整数超出范围" }
    }
}
