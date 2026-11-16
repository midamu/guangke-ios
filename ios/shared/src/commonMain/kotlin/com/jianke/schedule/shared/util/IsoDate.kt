package com.jianke.schedule.shared.util

/**
 * ISO 日期（`yyyy-MM-dd`）的极简算术。
 *
 * 为什么要自己写：`java.time.LocalDate` 在 Kotlin/Native 上没有，而引擎只需要
 * 比较、加减天、找周一这几件事。算法用 Howard Hinnant 的 `days_from_civil` /
 * `civil_from_days`（与 `java.time.LocalDate` 同一套）。
 */
object IsoDate {

    private fun floorDiv(a: Long, b: Long): Long {
        var q = a / b
        if (a % b != 0L && ((a < 0) != (b < 0))) q--
        return q
    }

    private fun floorMod(a: Long, b: Long): Long {
        val r = a % b
        return if (r != 0L && ((r < 0) != (b < 0))) r + b else r
    }

    private fun isLeap(y: Int): Boolean = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

    private fun daysInMonth(y: Int, m: Int): Int = when (m) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        else -> if (isLeap(y)) 29 else 28
    }

    /** `2026-9-1` 与 `2026-09-01` 都认；非法返回 null。 */
    fun parse(raw: String): Triple<Int, Int, Int>? {
        val m = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").find(raw.trim()) ?: return null
        val y = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        val d = m.groupValues[3].toInt()
        if (mo !in 1..12) return null
        if (d !in 1..daysInMonth(y, mo)) return null
        return Triple(y, mo, d)
    }

    fun normalize(raw: String): String? = parse(raw)?.let { (y, m, d) -> fmt(y, m, d) }

    fun fmt(y: Int, m: Int, d: Int): String =
        y.toString() + "-" + m.toString().padStart(2, '0') + "-" + d.toString().padStart(2, '0')

    /** 自 1970-01-01 起的天数（可负）。 */
    fun toEpochDay(raw: String): Long? {
        val (y0, m, d) = parse(raw) ?: return null
        val y = if (m <= 2) y0 - 1 else y0
        val era = floorDiv(y.toLong(), 400L).toInt()
        val yoe = y - era * 400
        val mp = if (m > 2) m - 3 else m + 9
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era.toLong() * 146097L + doe.toLong() - 719468L
    }

    fun fromEpochDay(days: Long): String {
        val z = days + 719468L
        val era = floorDiv(z, 146097L)
        val doe = (z - era * 146097L).toInt()
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era.toInt() * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        return fmt(if (m <= 2) y + 1 else y, m, d)
    }

    fun plusDays(raw: String, delta: Int): String? =
        toEpochDay(raw)?.let { fromEpochDay(it + delta) }

    /** 该日期所在周的周一。 */
    fun mondayOf(raw: String): String? {
        val days = toEpochDay(raw) ?: return null
        // 1970-01-01 是周四：floorMod(days + 3, 7) 得到 0=周一 … 6=周日
        val weekday = floorMod(days + 3, 7L)
        return fromEpochDay(days - weekday)
    }

    /** 周几：1=周一 … 7=周日。 */
    fun weekdayIndex(raw: String): Int {
        val days = toEpochDay(raw) ?: return 1
        return (floorMod(days + 3, 7L) + 1).toInt()
    }

    /** 两个日期的天数差（a - b）。 */
    fun daysBetween(a: String, b: String): Long? {
        val da = toEpochDay(a) ?: return null
        val db = toEpochDay(b) ?: return null
        return da - db
    }
}
