package com.jianke.schedule.shared.model

/** 一门课在课表中的一次出现（同一门课不同时间段算多条）。与 Android 版 Course 完全一致。 */
data class Course(
    val name: String,
    val teacher: String,
    val room: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: List<Int>,
    val weeksText: String,
    val color: Int = 0,
) {
    val periodText: String
        get() = if (startPeriod == endPeriod) startPeriod.toString() else "$startPeriod-$endPeriod"

    fun isInWeek(week: Int): Boolean = weeks.isEmpty() || weeks.contains(week)
}

data class PeriodTime(val index: Int, val start: String, val end: String)

data class SemesterInfo(val label: String, val startDate: String, val totalWeeks: Int)

data class Schedule(
    val semester: SemesterInfo,
    val periodTimes: List<PeriodTime>,
    val courses: List<Course>,
    val updatedAt: Long,
) {
    fun coursesOfWeek(week: Int): List<Course> = courses.filter { it.isInWeek(week) }
}

/** 学校统一作息（与 Android 的 ClassTimes 同一份数据）。M1 先留空，M2 从 ClassTimes.kt 搬过来。 */
val SCHOOL_PERIOD_TIMES: List<PeriodTime> = emptyList()
