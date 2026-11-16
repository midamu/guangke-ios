package com.jianke.schedule.shared.net

import com.jianke.schedule.shared.model.Course
import com.jianke.schedule.shared.model.Schedule
import com.jianke.schedule.shared.parse.ScheduleBuilder
import com.jianke.schedule.shared.parse.ZfsoftParser
import com.jianke.schedule.shared.platform.nowMillis
import com.jianke.schedule.shared.storage.Credentials
import com.jianke.schedule.shared.storage.ScheduleRepository
import com.jianke.schedule.shared.util.IsoDate

/**
 * 无头更新的编排：不开 WebView，直接走 CAS + 教务接口把整学期课表拉下来。
 * 与 Android 版 `HeadlessImport` 同一套流程与判定。
 *
 * 三条结果：
 * - [Result.Ok]       存盘成功
 * - [Result.Fallback] 这条路走不通（没配打码、触发二次验证、接口空），**旧课表不动**
 * - [Result.Failed]   本机问题（没账号、写盘失败）
 *
 * 安全边界由「只有解析出非空且合法的课程才 save」保证。
 */
class HeadlessImport(
    private val http: Http,
    private val repo: ScheduleRepository,
    private val credentials: Credentials,
    private val solver: CaptchaSolver?,
) {

    sealed interface Result {
        data class Ok(val schedule: Schedule, val via: String, val courseCount: Int) : Result
        data class Fallback(val reason: String) : Result
        data class Failed(val reason: String) : Result
    }

    companion object {
        private const val MAX_CAPTCHA_TRIES = 3
    }

    suspend fun run(todayYear: Int, todayMonth: Int, todayDay: Int): Result {
        val account = credentials.account() ?: return Result.Failed("还没设置学号和密码")
        val todayIso = IsoDate.fmt(todayYear, todayMonth, todayDay)
        val cas = CasClient(http)
        val jw = JwClient(http)

        // ① TGT 优先：不需要验证码
        var ticket: String? = null
        var via = ""
        val savedTgt = credentials.tgt()
        if (!savedTgt.isNullOrBlank()) {
            ticket = cas.ticketFor(savedTgt)
            if (ticket != null) via = "TGT 续期" else credentials.clearTgt()
        }

        // ② TGT 失效（或没有）→ 完整登录
        if (ticket == null) {
            var result = cas.login(account.first, account.second)
            if (result is CasClient.LoginResult.NeedCaptcha) {
                val s = solver ?: return Result.Fallback("需要图形验证码，但还没配置打码平台")
                var answer: String? = null
                var lastCode = ""
                for (i in 1..MAX_CAPTCHA_TRIES) {
                    val captcha = cas.fetchCaptcha() ?: return Result.Fallback("取验证码失败")
                    answer = s.solve(captcha.png) ?: return Result.Fallback("验证码没识别出来")
                    val r = cas.login(account.first, account.second, captcha, answer)
                    if (r is CasClient.LoginResult.Ok) {
                        result = r
                        break
                    }
                    if (r is CasClient.LoginResult.NeedCaptcha) {
                        lastCode = r.code
                        continue
                    }
                    return Result.Fallback((r as? CasClient.LoginResult.Failed)?.message ?: "登录失败")
                }
                if (result !is CasClient.LoginResult.Ok) {
                    return Result.Fallback("验证码连续 $MAX_CAPTCHA_TRIES 次没通过（$lastCode）")
                }
            }
            val ok = result as? CasClient.LoginResult.Ok
                ?: return Result.Fallback((result as? CasClient.LoginResult.Failed)?.message ?: "登录失败")
            credentials.saveTgt(ok.tgt)
            ticket = ok.ticket.ifBlank { cas.ticketFor(ok.tgt) }
            via = "账号登录"
        }

        if (ticket.isNullOrBlank()) return Result.Fallback("没换到教务票据")

        // ③ 建会话 → 取 kbList
        if (!cas.openJwSession(ticket)) return Result.Fallback("教务会话建立失败")
        jw.warmUp()

        val (xnm, xqm) = JwClient.academicTerm(todayYear, todayMonth)
        val raw = jw.fetchKbList(xnm, xqm)
            ?: return Result.Fallback("教务接口没返回课表（$xnm 学年 xqm=$xqm）")

        val parsed = ZfsoftParser.parseCourses(raw) ?: return Result.Fallback("课表数据解析失败")
        if (parsed.isEmpty()) return Result.Fallback("接口返回 0 门课")

        // ④ 结构自检（替代 WebView 那条「页面 vs 接口」的双向校验）
        val sane = parsed.filter { isSane(it) }
        if (sane.size * 10 < parsed.size * 7) {
            return Result.Fallback("课表数据异常（${sane.size}/${parsed.size} 条字段不合法）")
        }

        // ⑤ 无头模式没有页面表头：学期起始日期沿用上一份课表，避免日期整体错位
        val built = ScheduleBuilder.build("", sane, todayIso = todayIso, updatedAt = nowMillis())
        val old = runCatching { repo.load() }.getOrNull()
        val fixed = if (built.semester.startDate.isBlank() && old != null) {
            built.copy(semester = built.semester.copy(startDate = old.semester.startDate))
        } else {
            built
        }

        val saved = runCatching { repo.save(fixed) }.isSuccess
        if (!saved) return Result.Failed("课表已解析但写不进本地存储")

        return Result.Ok(fixed, via, sane.size)
    }

    /** 基本合法性：课程名/星期/节次/周次不能自相矛盾。 */
    private fun isSane(c: Course): Boolean {
        if (c.name.isBlank()) return false
        if (c.dayOfWeek !in 1..7) return false
        if (c.startPeriod !in 1..20) return false
        if (c.endPeriod !in c.startPeriod..20) return false
        if (c.weeks.any { it !in 1..30 }) return false
        return true
    }
}
