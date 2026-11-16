package com.jianke.schedule.shared.parse

import com.jianke.schedule.shared.model.Course
import com.jianke.schedule.shared.model.Schedule
import com.jianke.schedule.shared.model.SemesterInfo
import com.jianke.schedule.shared.model.schoolPeriodTimes
import com.jianke.schedule.shared.util.IsoDate

/** 把抓到的课程整理成一份课表：反推学期开始日期，作息用学校那一套。 */
object ScheduleBuilder {

    fun build(
        bodyText: String,
        courses: List<Course>,
        allWeeks: List<Int> = emptyList(),
        todayIso: String = "",
        updatedAt: Long = 0L,
    ): Schedule {
        val header = WeekParser.pickHeader(bodyText, todayIso)
        val week = header?.week ?: WeekParser.currentWeekOf(bodyText, todayIso).takeIf { it > 0 }
        val startDate = startDateOf(header?.startDate, week)
        val maxWeek = maxOf(
            allWeeks.maxOrNull() ?: 0,
            courses.flatMap { it.weeks }.maxOrNull() ?: 0,
            18,
        )
        return Schedule(
            semester = SemesterInfo(
                label = if (week != null) "第 $week 周" else "本学期课表",
                startDate = startDate,
                totalWeeks = maxWeek,
            ),
            periodTimes = schoolPeriodTimes,
            courses = courses,
            updatedAt = updatedAt,
        )
    }

    /**
     * 表头日期按「本周一」对齐：个别学期从周二开学起算时，反推的学期起点
     * 仍落在周一，展示层才不会整体错位一天。
     */
    private fun startDateOf(dateText: String?, week: Int?): String {
        if (dateText == null || week == null) return ""
        val monday = IsoDate.mondayOf(dateText) ?: return ""
        return IsoDate.plusDays(monday, -(week - 1) * 7).orEmpty()
    }
}
