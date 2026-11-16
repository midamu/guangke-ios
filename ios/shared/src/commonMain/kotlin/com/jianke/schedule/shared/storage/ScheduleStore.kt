package com.jianke.schedule.shared.storage

import com.jianke.schedule.shared.model.Course
import com.jianke.schedule.shared.model.PeriodTime
import com.jianke.schedule.shared.model.Schedule
import com.jianke.schedule.shared.model.SemesterInfo
import com.jianke.schedule.shared.model.schoolPeriodTimes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * 课表 JSON 编解码。
 *
 * 字段与 Android 版**完全一致**，所以两端的 `schedule.json` 可以互相拷：
 * 用户在 Android 上导好课表，直接把文件放进 iOS 的 Documents 也能读。
 */
internal object ScheduleCodec {

    const val SOURCE_SCAN = "scan"
    private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

    fun encode(schedule: Schedule): String = buildJsonObject {
        put(
            "semester",
            buildJsonObject {
                put("label", schedule.semester.label)
                put("startDate", schedule.semester.startDate)
                put("totalWeeks", schedule.semester.totalWeeks)
            },
        )
        put("updatedAt", schedule.updatedAt)
        put(
            "periodTimes",
            buildJsonArray {
                schedule.periodTimes.forEach { p ->
                    add(
                        buildJsonObject {
                            put("index", p.index)
                            put("start", p.start)
                            put("end", p.end)
                        },
                    )
                }
            },
        )
        put(
            "courses",
            buildJsonArray {
                schedule.courses.forEach { c ->
                    add(
                        buildJsonObject {
                            put("name", c.name)
                            put("teacher", c.teacher)
                            put("room", c.room)
                            put("dayOfWeek", c.dayOfWeek)
                            put("startPeriod", c.startPeriod)
                            put("endPeriod", c.endPeriod)
                            put("weeksText", c.weeksText)
                            put("weeks", buildJsonArray { c.weeks.forEach { add(JsonPrimitive(it)) } })
                            put("color", c.color)
                        },
                    )
                }
            },
        )
        put("source", SOURCE_SCAN)
    }.toString()

    fun decode(text: String): Schedule? {
        val root = runCatching { JSON.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        if (root.str("source") != SOURCE_SCAN) return null
        val semObj = root["semester"] as? JsonObject ?: return null
        val semester = SemesterInfo(
            label = semObj.str("label").orEmpty(),
            startDate = semObj.str("startDate").orEmpty(),
            totalWeeks = (semObj["totalWeeks"]?.jsonPrimitive?.intOrNull ?: 20).coerceIn(1, 30),
        )
        val periods = (root["periodTimes"] as? JsonArray)?.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val index = o["index"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            if (index !in 1..20) return@mapNotNull null
            PeriodTime(index, o.str("start").orEmpty(), o.str("end").orEmpty())
        }.orEmpty()

        val courses = (root["courses"] as? JsonArray)?.mapNotNull { e ->
            (e as? JsonObject)?.toCourse()
        }.orEmpty()

        return Schedule(
            semester = semester,
            // 缺作息表时回退学校统一作息
            periodTimes = periods.ifEmpty { schoolPeriodTimes },
            courses = courses,
            updatedAt = root["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }

    private fun JsonObject.toCourse(): Course? {
        val name = str("name").orEmpty().trim()
        val day = this["dayOfWeek"]?.jsonPrimitive?.intOrNull ?: return null
        val start = this["startPeriod"]?.jsonPrimitive?.intOrNull ?: return null
        val end = this["endPeriod"]?.jsonPrimitive?.intOrNull ?: return null
        if (name.isEmpty() || day !in 1..7 || start !in 1..20 || end !in start..20) return null

        val weeks = (this["weeks"] as? JsonArray)?.map { it.jsonPrimitive.intOrNull ?: -1 }.orEmpty()
        if (weeks.any { it !in 1..30 }) return null

        return Course(
            name = name,
            teacher = str("teacher").orEmpty(),
            room = str("room").orEmpty(),
            dayOfWeek = day,
            startPeriod = start,
            endPeriod = end,
            weeks = weeks.distinct().sorted(),
            weeksText = str("weeksText").orEmpty(),
            // color 原样回读：0 = 页面没给颜色，用主题色
            color = this["color"]?.jsonPrimitive?.intOrNull ?: 0,
        )
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}

/**
 * 课表的本地读写（okio，同一套代码跑 iOS / JVM）。
 *
 * 「永不空白」的两道保险：
 * 1. 每次成功保存都**双写** `schedule.lastgood.json`
 * 2. 读主文件失败时依次退到 last-good、损坏留档
 */
class ScheduleRepository(private val dir: Path = defaultDir()) {

    companion object {
        private fun defaultDir(): Path =
            com.jianke.schedule.shared.platform.documentsDir().toPath()

        private const val MAIN = "schedule.json"
        private const val LAST_GOOD = "schedule.lastgood.json"
        private const val CORRUPT = "schedule.corrupt.json"
    }

    private val fs: FileSystem get() = FileSystem.SYSTEM

    private fun mainPath() = dir / MAIN
    private fun lastGoodPath() = dir / LAST_GOOD
    private fun corruptPath() = dir / CORRUPT

    fun load(): Schedule? {
        read(mainPath())?.let { return it }
        read(lastGoodPath())?.let { return it }
        return null
    }

    fun save(schedule: Schedule) {
        val text = ScheduleCodec.encode(schedule)
        fs.createDirectories(dir)
        write(mainPath(), text)
        // 主文件写得再稳也可能被后续写入搞坏，留一份上次的成品
        runCatching { write(lastGoodPath(), text) }
    }

    private fun read(path: Path): Schedule? {
        if (!fs.exists(path)) return null
        return runCatching { ScheduleCodec.decode(fs.read(path) { readUtf8() }) }
            .getOrNull()
            ?: run {
                // 解析不出来就改名留档，别让它下次继续盖住 last-good
                runCatching {
                    if (fs.exists(corruptPath())) fs.delete(corruptPath())
                    fs.atomicMove(path, corruptPath())
                }
                null
            }
    }

    private fun write(path: Path, text: String) {
        fs.write(path) { writeUtf8(text) }
    }
}
