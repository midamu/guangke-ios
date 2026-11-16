package com.jianke.schedule.shared

import com.jianke.schedule.shared.model.Course
import com.jianke.schedule.shared.model.Schedule
import com.jianke.schedule.shared.model.SemesterInfo
import com.jianke.schedule.shared.model.schoolPeriodTimes
import com.jianke.schedule.shared.storage.Credentials
import com.jianke.schedule.shared.storage.MemoryVault
import com.jianke.schedule.shared.storage.ScheduleRepository
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * 存储层：编解码、读写、「永不空白」的兜底。
 *
 * 用一个临时目录跑，不碰真实数据；JVM 上就能验证，不用模拟器。
 */
class StorageTest {

    private fun tempDir(): Path =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY / ("gxk-test-" + Random.nextLong())

    private fun sample(): Schedule = Schedule(
        semester = SemesterInfo(label = "第 3 周", startDate = "2026-08-31", totalWeeks = 18),
        periodTimes = schoolPeriodTimes,
        courses = listOf(
            Course(
                name = "高等数学",
                teacher = "张三",
                room = "A101",
                dayOfWeek = 2,
                startPeriod = 1,
                endPeriod = 2,
                weeks = (1..16).toList(),
                weeksText = "1-16周",
                color = 0xFF6F8AF5.toInt(),
            ),
        ),
        updatedAt = 1_700_000_000_000L,
    )

    @Test
    fun roundTrip() {
        val dir = tempDir()
        val repo = ScheduleRepository(dir)
        try {
            repo.save(sample())
            val loaded = assertNotNull(repo.load())
            assertEquals("第 3 周", loaded.semester.label)
            assertEquals("2026-08-31", loaded.semester.startDate)
            assertEquals(18, loaded.semester.totalWeeks)
            assertEquals(1_700_000_000_000L, loaded.updatedAt)
            assertEquals(1, loaded.courses.size)
            val c = loaded.courses[0]
            assertEquals("高等数学", c.name)
            assertEquals("A101", c.room)
            assertEquals((1..16).toList(), c.weeks)
            assertEquals(0xFF6F8AF5.toInt(), c.color)
            assertEquals(12, loaded.periodTimes.size)
        } finally {
            FileSystem.SYSTEM.deleteRecursively(dir, mustExist = false)
        }
    }

    @Test
    fun corruptMainFileFallsBackToLastGood() {
        val dir = tempDir()
        val repo = ScheduleRepository(dir)
        try {
            repo.save(sample())
            // 把主文件写坏，模拟断电/半截写
            FileSystem.SYSTEM.write(dir / "schedule.json") { writeUtf8("{ 坏掉的 json") }
            // last-good 还在，界面不会变空白
            val loaded = assertNotNull(repo.load())
            assertEquals(1, loaded.courses.size)
        } finally {
            FileSystem.SYSTEM.deleteRecursively(dir, mustExist = false)
        }
    }

    @Test
    fun emptyDirReturnsNull() {
        val dir = tempDir()
        try {
            assertNull(ScheduleRepository(dir).load())
        } finally {
            FileSystem.SYSTEM.deleteRecursively(dir, mustExist = false)
        }
    }

    @Test
    fun credentialsRoundTripAndClear() {
        val vault = MemoryVault()
        val creds = Credentials(vault)

        assertNull(creds.account())
        assertNull(creds.tgt())

        creds.saveAccount("2023123456", "p@ss")
        assertEquals("2023123456" to "p@ss", creds.account())
        creds.saveTgt("TGT-1")
        assertEquals("TGT-1", creds.tgt())

        // 换账号必须把旧 TGT 清掉，否则会拿别人的票去换
        creds.saveAccount("2023999999", "new")
        assertNull(creds.tgt())

        creds.clearAll()
        assertNull(creds.account())
    }

    @Test
    fun scheduleJsonIsCompatibleWithAndroidFormat() {
        // 与 Android 版 ScheduleCodec 的字段名对齐：source / semester / courses / weeks / color
        val dir = tempDir()
        val repo = ScheduleRepository(dir)
        try {
            repo.save(sample())
            val text = FileSystem.SYSTEM.read(dir / "schedule.json") { readUtf8() }
            assertEquals(true, text.contains("\"source\":\"scan\""))
            assertEquals(true, text.contains("\"weeksText\":\"1-16周\""))
            assertEquals(true, text.contains("\"startDate\":\"2026-08-31\""))
        } finally {
            FileSystem.SYSTEM.deleteRecursively(dir, mustExist = false)
        }
    }
}
