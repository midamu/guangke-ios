package com.jianke.schedule.shared.model

/** 一门课在课表格子里的摆放位置。 */
data class PlacedCourse(
    val course: Course,
    val startRow: Int,
    val endRow: Int,
    val lane: Int,
    val lanes: Int,
)

/**
 * 第 n 小节落在第几大节（0 起）。
 *
 * 越界脏数据不能落回第 0 行（会压在 1-2 节的课上）：小于 1 归第 0 行，
 * 大于 10 归最后一行。
 */
fun rowIndexOf(period: Int): Int {
    schoolBigPeriods.firstOrNull { period in it.startPeriod..it.endPeriod }?.let { return it.row }
    return if (period <= 1) 0 else schoolBigPeriods.last().row
}

/**
 * 计算某一天所有课程的摆放位置。
 *
 * 不重叠时每门课独占整列；重叠（选课冲突、重修撞课）时按车道并排，
 * 否则两个格子叠在一起、文字糊成一团。
 */
fun layoutDay(dayCourses: List<Course>): List<PlacedCourse> {
    if (dayCourses.isEmpty()) return emptyList()
    val sorted = dayCourses.sortedWith(
        compareBy({ rowIndexOf(it.startPeriod) }, { rowIndexOf(it.endPeriod) }),
    )
    val out = ArrayList<PlacedCourse>()
    var i = 0
    while (i < sorted.size) {
        val cluster = ArrayList<Course>()
        cluster.add(sorted[i])
        var maxEnd = rowIndexOf(sorted[i].endPeriod)
        var j = i + 1
        while (j < sorted.size && rowIndexOf(sorted[j].startPeriod) <= maxEnd) {
            cluster.add(sorted[j])
            maxEnd = maxOf(maxEnd, rowIndexOf(sorted[j].endPeriod))
            j++
        }
        val laneEnds = ArrayList<Int>()
        val laneOf = HashMap<Course, Int>()
        for (c in cluster) {
            val s = rowIndexOf(c.startPeriod)
            val e = rowIndexOf(c.endPeriod)
            val free = laneEnds.indexOfFirst { it < s }
            if (free >= 0) {
                laneEnds[free] = e
                laneOf[c] = free
            } else {
                laneEnds.add(e)
                laneOf[c] = laneEnds.size - 1
            }
        }
        val lanes = laneEnds.size
        for (c in cluster) {
            out.add(
                PlacedCourse(
                    course = c,
                    startRow = rowIndexOf(c.startPeriod),
                    endRow = rowIndexOf(c.endPeriod),
                    lane = laneOf[c] ?: 0,
                    lanes = lanes,
                ),
            )
        }
        i = j
    }
    return out
}
