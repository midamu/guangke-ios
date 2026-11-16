package com.jianke.schedule.shared.parse

import com.jianke.schedule.shared.model.Schedule
import com.jianke.schedule.shared.util.IsoDate

/**
 * 展示层的周次重算（纯函数）。
 *
 * 与 [WeekParser] 的分工：那边从页面正文识别周次（导入时用一次），
 * 这边从已存的学期起点往前推（每次打开 App 都要算）。
 */
object ScheduleWeek {

    /**
     * 当前第几周 = (今天 - 学期起点) / 7 + 1，夹在 1..totalWeeks。
     *
     * 学期起点缺失时**不能退回第 1 周**（今日课程会显示错内容），
     * 退回导入时记录的「第 N 周」标签快照。
     */
    fun currentWeekOf(schedule: Schedule, todayIso: String): Int {
        val total = maxOf(1, schedule.semester.totalWeeks)
        val days = IsoDate.daysBetween(todayIso, schedule.semester.startDate)
        if (days != null) {
            if (days >= 0) return ((days / 7).toInt() + 1).coerceIn(1, total)
            return 1
        }
        return Regex("第\\s*([0-9]{1,2})\\s*周").find(schedule.semester.label)
            ?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, total) ?: 1
    }

    /**
     * 第 week 周周一 ~ 周日（ISO 字符串）。
     * 学期起点缺失时返回 7 个 null，表头退化成只显示星期几。
     */
    fun weekDatesOf(startDate: String, week: Int): List<String?> {
        val monday = IsoDate.mondayOf(startDate) ?: return List(7) { null }
        val first = IsoDate.plusDays(monday, (week - 1) * 7) ?: return List(7) { null }
        return (0..6).map { IsoDate.plusDays(first, it) }
    }
}
