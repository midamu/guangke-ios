package com.jianke.schedule.shared.parse

import com.jianke.schedule.shared.model.Course
import com.jianke.schedule.shared.model.CourseColors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 正方教务（jwglxt）课表 JSON 解析。与 Android 版 `ZfsoftParser` 同逻辑，
 * 只是把 `org.json` 换成了 `kotlinx.serialization` 的 `JsonElement`。
 *
 * 字段名做了容错：不同学校/版本的正方字段不一样，能拿到 kbList 就尽量解析出来。
 */
object ZfsoftParser {

    private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * @param bigPeriodMode 接口里的 `jcs` 若是「大节」编号（1~5），换算成小节（第 n 大节 = 2n-1 ~ 2n）
     */
    fun parseCourses(raw: String, bigPeriodMode: Boolean = false): List<Course>? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val root = runCatching { JSON.parseToJsonElement(text) }.getOrNull() ?: return null
        return when (root) {
            is JsonArray -> parseCourseArray(root, bigPeriodMode).ifEmpty { null }
            is JsonObject -> parseCourseObject(root, bigPeriodMode)
            else -> null
        }
    }

    private fun parseCourseObject(obj: JsonObject, bigPeriodMode: Boolean): List<Course>? {
        val preferred = listOf("kbList", "kbListXq", "courses", "list", "rows")
        val out = ArrayList<Course>()
        val seen = HashSet<String>()

        fun take(arr: JsonArray?) {
            if (arr == null) return
            for (c in parseCourseArray(arr, bigPeriodMode)) {
                val key = c.name + "|" + c.dayOfWeek + "|" + c.startPeriod + "|" + c.endPeriod +
                    "|" + c.weeks.joinToString(",")
                if (seen.add(key)) out.add(c)
            }
        }

        for (k in preferred) take(obj[k] as? JsonArray)
        for (k in obj.keys) {
            if (k in preferred) continue
            val arr = obj[k] as? JsonArray ?: continue
            if (!looksLikeCourseList(arr)) continue
            take(arr)
        }
        return out.ifEmpty { null }
    }

    /** 数组里有没有「带课名 + 星期 + 节次」的对象——用来识别哪个数组是课表。 */
    private fun looksLikeCourseList(arr: JsonArray): Boolean {
        for (i in 0 until minOf(arr.size, 5)) {
            val o = arr[i] as? JsonObject ?: continue
            val hasName = o.str("kcmc") != null || o.str("courseName") != null
            val hasDay = o.containsKey("xqj") || o.containsKey("xingqi")
            val hasPeriod = o.containsKey("jcs") || o.containsKey("jc") || o.containsKey("periods")
            if (hasName && hasDay && hasPeriod) return true
        }
        return false
    }

    private fun parseCourseArray(arr: JsonArray, bigPeriodMode: Boolean): List<Course> {
        val out = ArrayList<Course>(arr.size)
        for (i in 0 until arr.size) {
            val o = arr[i] as? JsonObject ?: continue
            val name = o.str("kcmc", "courseName", "kcname", "kcmcBz") ?: continue
            val day = o.intInRange("xqj", "xingqi", "dayOfWeek", "xq") ?: continue
            val periods = parsePeriods(o.str("jcs", "jcsText", "jc", "periods")) ?: continue

            val firstPeriod = if (bigPeriodMode) periods.first * 2 - 1 else periods.first
            val lastPeriod = if (bigPeriodMode) periods.second * 2 else periods.second

            // 周次字段各版本不一样：正方标准是 zcd，这个学校的 sjkList 用 qsjsz，
            // 必须都试一遍；否则会走「空 -> 每周都有」的兜底，把整张课表搞乱。
            val weeksText = o.str("zcd", "zcmc", "qsjsz", "zc", "zcdText", "zcstr", "weeks").orEmpty()

            out.add(
                Course(
                    name = name,
                    teacher = o.str("xm", "teacher", "jsxm").orEmpty(),
                    room = o.str("cdmc", "room", "jsmc", "cdmcId").orEmpty(),
                    dayOfWeek = day,
                    startPeriod = firstPeriod,
                    endPeriod = lastPeriod,
                    weeks = parseWeeks(weeksText),
                    weeksText = weeksText,
                    color = CourseColors.of(name),
                ),
            )
        }
        return out
    }

    /** "1-2" / "3" / "1-2节" -> 1..2 / 3..3 */
    fun parsePeriods(text: String?): Pair<Int, Int>? {
        if (text.isNullOrBlank()) return null
        val nums = Regex("[0-9]+").findAll(text)
            .mapNotNull { it.value.toIntOrNull() }
            .filter { it > 0 }
            .toList()
        if (nums.isEmpty()) return null
        val start = nums.first()
        val end = if (nums.size > 1) nums[1] else start
        if (start !in 1..20 || end !in 1..20) return null
        return if (start <= end) start to end else end to start
    }

    /**
     * 周次文本："1-16周" "1-16周(单)" "3-5,7-9周" "1,2,3周" "1-16"
     *
     * `(单)/(双)` 只作用于它所在那一段；两标并存时整段全收（否则会静默丢掉半个学期）。
     */
    fun parseWeeks(text: String): List<Int> {
        if (text.isBlank()) return (1..20).toList()
        // 用 HashSet + 末尾 sorted()：sortedSetOf() 是 JVM 专有的 stdlib 函数，
        // Kotlin/Native（iOS）上编译不过 —— JVM 单测发现不了这类问题。
        val result = HashSet<Int>()
        val parts = text.replace("第", "").split(",", "，", "、")
        for (raw in parts) {
            val part = raw.trim()
            if (part.isEmpty()) continue
            val odd = part.contains("单") || part.contains("奇")
            val even = part.contains("双") || part.contains("偶")
            val nums = Regex("[0-9]+").findAll(part)
                .mapNotNull { it.value.toIntOrNull() }
                .filter { it > 0 }
                .toList()
            if (nums.isEmpty()) return emptyList()
            val from = nums.first()
            val to = if (nums.size > 1) nums[1] else from
            val lo = minOf(from, to)
            val hi = maxOf(from, to)
            for (w in lo..hi) {
                if (w !in 1..30) continue
                when {
                    odd && even -> result.add(w)
                    odd -> if (w % 2 == 1) result.add(w)
                    even -> if (w % 2 == 0) result.add(w)
                    else -> result.add(w)
                }
            }
        }
        return result.sorted()
    }
}

// ---------- JsonElement 的小工具 ----------

private fun JsonObject.str(vararg keys: String): String? {
    for (k in keys) {
        val p = this[k] as? JsonPrimitive ?: continue
        val v = p.content
        if (v.isNotEmpty() && v != "null") return v
    }
    return null
}

private fun JsonObject.intInRange(vararg keys: String): Int? {
    for (k in keys) {
        val p = this[k] as? JsonPrimitive ?: continue
        val n = p.content.toIntOrNull() ?: continue
        if (n in 1..7) return n
    }
    return null
}
