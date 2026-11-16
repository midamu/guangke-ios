package com.jianke.schedule.net

import android.content.Context
import com.jianke.schedule.data.Course
import com.jianke.schedule.data.CredentialStore
import com.jianke.schedule.data.Schedule
import com.jianke.schedule.data.ScheduleBuilder
import com.jianke.schedule.data.ScheduleStore
import com.jianke.schedule.data.ZfsoftParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 无头更新：不开 WebView，直接走 CAS + 教务接口把整学期课表拉下来。
 *
 * 三条结果，UI 分别对待：
 * - [Result.Ok]      存盘成功，课表已换新
 * - [Result.Fallback] 这条路走不通（要验证码但没配打码、触发二次验证、接口空），
 *                     转回原来的 WebView 流程，**旧课表原样保留**
 * - [Result.Failed]  本机问题（没配账号、存储写失败），提示用户，旧课表同样保留
 *
 * 安全性由「只有解析出非空课程才 save」保证：任何一步失败都不会动本地那份课表。
 */
object HeadlessImport {

    sealed interface Result {
        data class Ok(val schedule: Schedule, val via: String) : Result
        data class Fallback(val reason: String) : Result
        data class Failed(val reason: String) : Result
    }

    private const val MAX_CAPTCHA_TRIES = 3

    suspend fun run(context: Context): Result = withContext(Dispatchers.IO) {
        val account = CredentialStore.account(context)
            ?: return@withContext Result.Failed("还没设置学号和密码")

        val http = HttpLite()
        val cas = CasClient(http)
        val jw = JwClient(http)

        // ① 优先用 TGT 换票：这条路径不需要验证码
        var ticket: String? = null
        var via = ""
        val savedTgt = CredentialStore.tgt(context)
        if (!savedTgt.isNullOrBlank()) {
            ticket = cas.ticketFor(savedTgt)
            if (ticket != null) via = "TGT 续期" else CredentialStore.clearTgt(context)
        }

        // ② TGT 失效（或没有）→ 走完整登录
        if (ticket == null) {
            val solver = CredentialStore.solver(context)?.let { TtshituSolver(it.first, it.second) }
            var result = cas.login(account.username, account.password)
            if (result is CasClient.LoginResult.NeedCaptcha) {
                if (solver == null) {
                    return@withContext Result.Fallback("需要图形验证码，但还没配置打码平台")
                }
                var answer: String? = null
                var lastCode = ""
                for (i in 1..MAX_CAPTCHA_TRIES) {
                    val captcha = cas.fetchCaptcha()
                        ?: return@withContext Result.Fallback("取验证码失败")
                    answer = solver.solve(captcha.png)
                        ?: return@withContext Result.Fallback("验证码没识别出来")
                    val r = cas.login(account.username, account.password, captcha, answer)
                    if (r is CasClient.LoginResult.Ok) {
                        result = r
                        break
                    }
                    if (r is CasClient.LoginResult.NeedCaptcha) {
                        lastCode = r.code
                        continue
                    }
                    // 密码错 / 锁定 / 二次验证：不能再试，直接降级人工
                    val msg = (r as? CasClient.LoginResult.Failed)?.message ?: "登录失败"
                    return@withContext Result.Fallback(msg)
                }
                if (result !is CasClient.LoginResult.Ok) {
                    return@withContext Result.Fallback("验证码连续 " + MAX_CAPTCHA_TRIES + " 次没通过（" + lastCode + "）")
                }
            }
            if (result !is CasClient.LoginResult.Ok) {
                val msg = (result as? CasClient.LoginResult.Failed)?.message ?: "登录失败"
                return@withContext Result.Fallback(msg)
            }
            val ok = result as CasClient.LoginResult.Ok
            CredentialStore.saveTgt(context, ok.tgt)
            ticket = ok.ticket.ifBlank { cas.ticketFor(ok.tgt) }
            via = "账号登录"
        }

        if (ticket.isNullOrBlank()) return@withContext Result.Fallback("没换到教务票据")

        // ③ 建教务会话 → 取 kbList
        if (!cas.openJwSession(ticket)) return@withContext Result.Fallback("教务会话建立失败")
        jw.warmUp()

        val (xnm, xqm) = jw.academicTerm()
        val raw = jw.fetchKbList(xnm, xqm)
            ?: return@withContext Result.Fallback("教务接口没返回课表（" + xnm + " 学年 xqm=" + xqm + "）")

        val parsed = ZfsoftParser.parseCourses(raw)
            ?: return@withContext Result.Fallback("课表数据解析失败")
        if (parsed.isEmpty()) return@withContext Result.Fallback("接口返回 0 门课")

        // ④ 替代「双向校验」的结构自检：字段明显错乱的比例过高就判为不可信
        val sane = parsed.filter { isSane(it) }
        if (sane.size * 10 < parsed.size * 7) {
            return@withContext Result.Fallback("课表数据异常（" + sane.size + "/" + parsed.size + " 条字段不合法）")
        }

        // ⑤ 组课表：无头模式拿不到页面表头，学期起始日期沿用上一份课表，避免日期整体错位
        val built = ScheduleBuilder.build("", sane)
        val old = runCatching { ScheduleStore.load(context) }.getOrNull()
        val fixed = if (built.semester.startDate.isBlank() && old != null) {
            built.copy(
                semester = built.semester.copy(
                    startDate = old.semester.startDate,
                    label = built.semester.label,
                ),
            )
        } else {
            built
        }

        val saved = runCatching { ScheduleStore.save(context, fixed) }.isSuccess
        if (!saved) return@withContext Result.Failed("课表已解析但写不进本地存储，请检查剩余空间")

        Result.Ok(fixed, via + "，" + sane.size + " 门课")
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
