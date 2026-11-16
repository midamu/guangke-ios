package com.jianke.schedule.shared

import com.jianke.schedule.shared.parse.ScheduleBuilder
import com.jianke.schedule.shared.parse.WeekParser
import com.jianke.schedule.shared.parse.ZfsoftParser
import com.jianke.schedule.shared.util.IsoDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 解析层：正方 JSON、周次文本、周次表头、日期算术。全部纯函数，JVM 上直接跑。 */
class ParserTest {

    // ---------- 正方 kbList ----------

    private val kbListJson = """
        {"kbList":[
          {"kcmc":"高等数学","xm":"张三","cdmc":"A101","xqj":"2","jcs":"1-2","zcd":"1-16周"},
          {"kcmc":"大学英语","xm":"李四","cdmc":"B203","xqj":"4","jcs":"3-4","qsjsz":"4-5周,7-9周(单)"}
        ],"xsxx":{"XH_ID":"2026"}}
    """.trimIndent()

    @Test
    fun parsesKbList() {
        val courses = assertNotNull(ZfsoftParser.parseCourses(kbListJson))
        assertEquals(2, courses.size)

        val math = courses[0]
        assertEquals("高等数学", math.name)
        assertEquals("张三", math.teacher)
        assertEquals("A101", math.room)
        assertEquals(2, math.dayOfWeek)
        assertEquals(1, math.startPeriod)
        assertEquals(2, math.endPeriod)
        assertEquals((1..16).toList(), math.weeks)

        // 这门课的周次在 qsjsz 而不是 zcd —— 只认 zcd 会退化成「每周都有」
        val english = courses[1]
        assertEquals("大学英语", english.name)
        assertEquals(listOf(4, 5, 7, 9), english.weeks)
    }

    @Test
    fun bigPeriodModeDoublesPeriodNumbers() {
        val courses = assertNotNull(ZfsoftParser.parseCourses(kbListJson, bigPeriodMode = true))
        // 大节 1-2 = 小节 1~4；大节 3-4 = 小节 5~8
        assertEquals(1, courses[0].startPeriod)
        assertEquals(4, courses[0].endPeriod)
        assertEquals(5, courses[1].startPeriod)
        assertEquals(8, courses[1].endPeriod)
    }

    @Test
    fun rejectsGarbageAndEmpty() {
        assertNull(ZfsoftParser.parseCourses(""))
        assertNull(ZfsoftParser.parseCourses("<html>login</html>"))
        assertNull(ZfsoftParser.parseCourses("""{"kbList":[]}"""))
    }

    @Test
    fun weeksTextVariants() {
        assertEquals((1..16).toList(), ZfsoftParser.parseWeeks("1-16周"))
        assertEquals(listOf(4, 5, 7, 9), ZfsoftParser.parseWeeks("4-5周,7-9周(单)"))
        // 单双两标并存 = 整段全收（只认单周会静默丢掉半个学期）
        assertEquals((1..16).toList(), ZfsoftParser.parseWeeks("1-16周(单双)"))
        assertEquals(listOf(1, 2, 3), ZfsoftParser.parseWeeks("1,2,3周"))
        // 空文本 = 每周都有（页面只给一周数据时的兜底）
        assertEquals((1..20).toList(), ZfsoftParser.parseWeeks(""))
    }

    @Test
    fun periodsTextVariants() {
        assertEquals(1 to 2, ZfsoftParser.parsePeriods("1-2"))
        assertEquals(3 to 3, ZfsoftParser.parsePeriods("3"))
        assertEquals(1 to 2, ZfsoftParser.parsePeriods("1-2节"))
        assertEquals(9 to 10, ZfsoftParser.parsePeriods("9-10节"))
        assertNull(ZfsoftParser.parsePeriods(""))
        assertNull(ZfsoftParser.parsePeriods("上午"))
    }

    // ---------- 周次表头 ----------

    private val body = "……表头 第3周 2026-09-14/2026-09-20 ……"

    @Test
    fun picksWeekByDateRange() {
        val header = assertNotNull(WeekParser.pickHeader(body, "2026-09-16"))
        assertEquals(3, header.week)
        assertEquals("2026-09-14", header.startDate)
    }

    @Test
    fun fallsBackToFirstHeaderWhenTodayIsOutside() {
        val header = assertNotNull(WeekParser.pickHeader(body, "2027-01-01"))
        assertEquals(3, header.week)
    }

    @Test
    fun semesterStartAlignsToMonday() {
        val courses = assertNotNull(ZfsoftParser.parseCourses(kbListJson))
        val schedule = ScheduleBuilder.build(body, courses, todayIso = "2026-09-16")
        // 第 3 周 → 周一(2026-09-14) 往前推两周 = 2026-08-31
        assertEquals("2026-08-31", schedule.semester.startDate)
        assertEquals("第 3 周", schedule.semester.label)
    }

    // ---------- 日期算术（LocalDate 的替代品） ----------

    @Test
    fun epochDayMatchesKnownValues() {
        assertEquals(0L, IsoDate.toEpochDay("1970-01-01"))
        assertEquals(20712L, IsoDate.toEpochDay("2026-09-16"))
        assertEquals("2026-09-16", IsoDate.fromEpochDay(20712L))
    }

    @Test
    fun mondayAndPlusDays() {
        assertEquals("2026-09-14", IsoDate.mondayOf("2026-09-16"))
        assertEquals("2026-09-14", IsoDate.mondayOf("2026-09-14"))
        // 2026-09-20 是周日，仍属于 14 号那一周
        assertEquals("2026-09-14", IsoDate.mondayOf("2026-09-20"))
        assertEquals("2026-08-31", IsoDate.plusDays("2026-09-14", -14))
        assertEquals(31L, IsoDate.daysBetween("2026-09-14", "2026-08-14"))
    }

    @Test
    fun epochDayRoundTripAcrossYears() {
        // 覆盖闰年 2 月与跨年，防止 days_from_civil 的常数写错
        var day = IsoDate.toEpochDay("2020-01-01")!!
        val end = IsoDate.toEpochDay("2028-12-31")!!
        while (day <= end) {
            val iso = IsoDate.fromEpochDay(day)
            assertEquals(day, IsoDate.toEpochDay(iso), "round trip failed at $iso")
            day += 1
        }
    }

    @Test
    fun normalizesUnpaddedDates() {
        assertEquals("2026-09-01", IsoDate.normalize("2026-9-1"))
        assertNull(IsoDate.normalize("2026-13-01"))
        assertNull(IsoDate.normalize("不是日期"))
    }
}
