package com.jianke.schedule.shared.parse

/**
 * 「当前是第几周」的统一识别。与 Android 版同逻辑，只是把 `LocalDate`
 * 换成了 ISO 字符串比较（`yyyy-MM-dd` 的字典序就是时间序）。
 *
 * 页面正文里表头和周次下拉框都可能出现「第3周 2026-09-14/2026-09-20」，
 * 谁先谁后没保证，所以：
 * 1. 最强判据：今天落在哪一行「第N周 起/止」的范围内；
 * 2. 兜底：一行都没罩住今天时，退回第一个带日期的行。
 */
object WeekParser {

    private val WEEK_HEADER_REGEX = Regex(
        "第\\s*([0-9]{1,2})\\s*周\\s*([0-9]{4})-([0-9]{1,2})-([0-9]{1,2})" +
            "(?:\\s*/\\s*([0-9]{4})-([0-9]{1,2})-([0-9]{1,2}))?",
    )
    private val WEEK_REGEX = Regex("第\\s*([0-9]+)\\s*周")

    data class HeaderPair(val week: Int, val startDate: String, val endDate: String?)

    fun headerPairs(bodyText: String): List<HeaderPair> {
        val out = ArrayList<HeaderPair>()
        for (m in WEEK_HEADER_REGEX.findAll(bodyText)) {
            val week = m.groupValues[1].toIntOrNull() ?: continue
            if (week !in 1..30) continue
            val start = normalizeDate(m.groupValues[2], m.groupValues[3], m.groupValues[4])
            val end = if (m.groupValues[5].isEmpty()) {
                null
            } else {
                normalizeDate(m.groupValues[5], m.groupValues[6], m.groupValues[7])
            }
            out.add(HeaderPair(week, start, end))
        }
        return out
    }

    private fun normalizeDate(year: String, month: String, day: String): String =
        year + "-" + month.padStart(2, '0') + "-" + day.padStart(2, '0')

    fun pickHeader(bodyText: String, todayIso: String): HeaderPair? {
        val pairs = headerPairs(bodyText)
        if (pairs.isEmpty()) return null
        for (p in pairs) {
            val end = p.endDate ?: p.startDate
            // 两个都是补零后的 ISO，字典序比较等价于日期比较
            if (todayIso >= p.startDate && todayIso <= end) return p
        }
        return pairs.first()
    }

    fun currentWeekOf(bodyText: String, todayIso: String): Int {
        pickHeader(bodyText, todayIso)?.let { return it.week }
        for (m in WEEK_REGEX.findAll(bodyText)) {
            val week = m.groupValues[1].toIntOrNull() ?: continue
            if (week in 1..30) return week
        }
        return 0
    }

    /**
     * 学期总周数：只有带日期的行把 1..W **连续列全**才返回 W。
     * 半截列表、跳号列表一律返回 null —— 拿不完整的列表当整学期会误杀合法周次。
     */
    fun semesterWeekCount(bodyText: String): Int? {
        val weeks = headerPairs(bodyText).map { it.week }.distinct().sorted()
        if (weeks.size < 8) return null
        val w = weeks.last()
        return if ((1..w).all { it in weeks }) w else null
    }
}
