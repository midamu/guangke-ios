package com.jianke.schedule.shared

import com.jianke.schedule.shared.net.CaptchaAnswer
import com.jianke.schedule.shared.net.CasClient
import com.jianke.schedule.shared.net.JwClient
import com.jianke.schedule.shared.net.formBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * 无头引擎里所有**不依赖网络**的逻辑。
 *
 * 这些测试在 JVM 上跑（`./gradlew :shared:jvmTest`），Windows 也能跑，
 * 所以协议细节写错了不用等真机、不用等 Mac 就能发现。
 */
class NetLogicTest {

    // ---------- 验证码：算术题归一化 ----------

    @Test
    fun captchaHandlesThreeShapes() {
        assertEquals("14", CaptchaAnswer.normalize("9+5="))
        assertEquals("14", CaptchaAnswer.normalize("9+5"))
        assertEquals("14", CaptchaAnswer.normalize("9+5=14"))
        assertEquals("14", CaptchaAnswer.normalize("14"))
        assertEquals("14", CaptchaAnswer.normalize("9 + 5 = ？"))
        assertEquals("12", CaptchaAnswer.normalize("3×4="))
        assertEquals("2", CaptchaAnswer.normalize("7-5="))
        assertNull(CaptchaAnswer.normalize(""))
        assertNull(CaptchaAnswer.normalize("???!"))
    }

    // ---------- 学年 / 学期推算 ----------

    @Test
    fun academicTermBoundaries() {
        assertEquals("2025" to "3", JwClient.academicTerm(2025, 9))
        assertEquals("2025" to "3", JwClient.academicTerm(2026, 1))
        assertEquals("2025" to "12", JwClient.academicTerm(2026, 4))
        assertEquals("2026" to "3", JwClient.academicTerm(2026, 8))
    }

    // ---------- 表单编码 ----------

    @Test
    fun formBodyPercentEncodesNonAscii() {
        // 学号/密码里可能有中文，裸拼字符串会把请求发坏
        assertEquals(
            "u=%E5%BC%A0%E4%B8%89%201&p=a%2Bb%26c",
            formBody("u" to "张三 1", "p" to "a+b&c"),
        )
    }

    // ---------- 登录响应解析 ----------

    @Test
    fun parsesSuccessWithTopLevelFields() {
        val ok = assertIs<CasClient.LoginResult.Ok>(
            CasClient.parseLogin("""{"tgt":"TGT-1","ticket":"ST-2"}"""),
        )
        assertEquals("TGT-1", ok.tgt)
        assertEquals("ST-2", ok.ticket)
    }

    @Test
    fun parsesSuccessNestedUnderData() {
        // 有的版本把字段套在 data 里，两种都要认
        val ok = assertIs<CasClient.LoginResult.Ok>(
            CasClient.parseLogin("""{"data":{"tgt":"TGT-3","ticket":"ST-4"}}"""),
        )
        assertEquals("TGT-3", ok.tgt)
    }

    @Test
    fun detectsWrongCaptcha() {
        assertIs<CasClient.LoginResult.NeedCaptcha>(
            CasClient.parseLogin("""{"data":{"code":"CODEFALSE","uid":"abc"}}"""),
        )
    }

    @Test
    fun detectsPasswordErrorWithCounts() {
        // 密码错误会带回「已错次数,剩余次数」，必须当 Failed（不能重试）
        val failed = assertIs<CasClient.LoginResult.Failed>(
            CasClient.parseLogin("""{"data":{"code":"PWDERROR","message":"3,2"}}"""),
        )
        assertEquals("PWDERROR", failed.code)
    }

    @Test
    fun detectsTwoVerify() {
        val failed = assertIs<CasClient.LoginResult.Failed>(
            CasClient.parseLogin("""{"data":{"code":"TWOVERIFY","data":"138****1234"}}"""),
        )
        assertEquals("TWOVERIFY", failed.code)
        assertEquals(true, failed.message.contains("短信"))
    }

    @Test
    fun garbageIsParseFailureNotSuccess() {
        assertIs<CasClient.LoginResult.Failed>(CasClient.parseLogin("<html>login</html>"))
        assertIs<CasClient.LoginResult.Failed>(CasClient.parseLogin(""))
    }
}
