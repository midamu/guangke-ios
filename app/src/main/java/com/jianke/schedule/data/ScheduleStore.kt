package com.jianke.schedule.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** 课表的本地存储：只存在 App 私有目录，不联网、不上传。 */
object ScheduleStore {

    private const val FILE_NAME = "schedule.json"
    /** 上一次**成功保存**的副本：正式文件损坏时用它兜底，保证界面上永远有课表。 */
    private const val LAST_GOOD = "schedule.lastgood.json"
    private const val LEGACY = "schedule.legacy.json"
    private const val DEBUG_DIR = "import_debug"

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun save(context: Context, schedule: Schedule) {
        val payload = ScheduleCodec.encode(schedule)
        writeAtomically(file(context), payload)
        // 双写一份 last-good：正式文件写成功但之后被写坏（断电/半截写）时还有得救
        runCatching { writeAtomically(File(context.filesDir, LAST_GOOD), payload) }
    }

    fun load(context: Context): Schedule? {
        val f = file(context)
        // AtomicFile 写入时会把 base 改名为 .bak 再写 .new：进程若死在写入中途，
        // base 不在但 .bak 里是完整的旧课表。这里不能因 base 缺失就提前返回——
        // 要交给 AtomicFile.openRead 走它的 .bak 还原逻辑。
        val bakExists = File(context.filesDir, "$FILE_NAME.bak").exists()
        if (f.exists() || bakExists) {
            readAtomic(context, f)?.let { return it }
        }
        // 兜底 1：上次成功保存的副本（解析异常 / 文件损坏时的第一条救命绳）
        readPlain(File(context.filesDir, LAST_GOOD))?.let { return it }
        // 兜底 2：损坏留档（留档是给排查用的，能读出来也先用上）
        readPlain(File(context.filesDir, "$FILE_NAME.corrupt"))?.let { return it }
        return null
    }

    private fun readAtomic(context: Context, f: File): Schedule? {
        val atomic = AtomicFile(f)
        return try {
            val text = atomic.openRead().bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            // 早期接口导入的数据不作页面课表使用。旧行为是直接 delete()，
            // 结果升级后的第一次启动会变成空白课表——改成改名归档再放弃。
            if (root.optString("source") != ScheduleCodec.SOURCE_SCAN) {
                runCatching { File(context.filesDir, LEGACY).writeText(text) }
                atomic.delete()
                null
            } else {
                ScheduleCodec.decode(root)
            }
        } catch (e: Exception) {
            // 解析失败（文件损坏 / 写入中断）时把坏文件改名留档：
            // 课表「消失」时还能拷出来看是哪里坏了，而不是无痕丢失
            runCatching { f.renameTo(File(context.filesDir, FILE_NAME + ".corrupt")) }
            null
        }
    }

    private fun readPlain(f: File): Schedule? {
        if (!f.exists()) return null
        return runCatching {
            ScheduleCodec.decode(JSONObject(f.readText()))
        }.getOrNull()
    }

    /** 把抓取到的原始内容落盘，方便排查解析问题。 */
    fun saveDebugDump(context: Context, name: String, content: String): File {
        require(name.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid debug file name" }
        val f = File(debugDir(context), name)
        writeAtomically(f, content)
        return f
    }

    private fun debugDir(context: Context): File {
        val dir = File(context.filesDir, DEBUG_DIR)
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw IOException("Cannot create debug directory")
        }
        return dir
    }

    /** 先写临时文件并同步，再替换正式文件，避免中途失败留下半份 JSON。 */
    private fun writeAtomically(target: File, content: String) {
        target.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs() && !parent.exists()) {
                throw IOException("Cannot create data directory")
            }
        }
        val atomic = AtomicFile(target)
        val stream = atomic.startWrite()
        try {
            stream.writer().apply {
                write(content)
                flush()
            }
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
    }
}
