package com.jianke.schedule

import com.jianke.schedule.net.CasClient
import com.jianke.schedule.net.JwClient
import com.jianke.schedule.net.TtshituSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 无头登录里两个「差一点就错」的纯函数，用固定向量锁住：
 *
 * 1. 密码加密：**无填充、小端、不定长 hex**。写错成标准 PKCS#1 时，
 *    服务端只会回「密码错误」，人很难从现象反推到算法。
 *    期望值由《GZIST登录踩坑总结.md》里的 Python 参考实现算出。
 * 2. 算术题验证码归一化：识别器可能返回「14」「9+5=」「9+5=14」。
 */
class HeadlessNetTest {

    @Test
    fun rsaEncrypt_matchesReferenceImplementation() {
        assertEquals(
            "556386088bed8a24d6d61615f1d355cd79ea501e5ca52a4a8688fc2a587abeb030c7042cbbceb8daaf92facf658a6525" +
                "6e96e44dcc6a7871fdeb4554067a1a23265e9a4c2f768e69b6be2bc170014fcd6a5796f2d02198964a60a1f6b6087f7" +
                "0a9dfd10bcffa36a37c982abee41a9744f54a735353a79593b7e2fd61a4317c0f",
            CasClient.rsaEncrypt("test123"),
        )
        // 不以 0 开头的密文不会补前导零（长度 255 而不是 256）：
        // 这条专门盯住「format(x, 'x') 不补零」这个细节
        assertEquals(255, CasClient.rsaEncrypt("abc").length)
    }

    @Test
    fun captchaNormalize_handlesExpressionAndAnswer() {
        assertEquals("14", TtshituSolver.normalize("9+5="))
        assertEquals("14", TtshituSolver.normalize("9+5"))
        assertEquals("14", TtshituSolver.normalize("9+5=14"))
        assertEquals("14", TtshituSolver.normalize("14"))
        assertEquals("12", TtshituSolver.normalize("3×4="))
        assertEquals("2", TtshituSolver.normalize("7-5="))
        assertNull(TtshituSolver.normalize(""))
        assertNull(TtshituSolver.normalize("???"))
    }

    @Test
    fun academicTerm_followsSemesterBoundary() {
        val jw = JwClient(com.jianke.schedule.net.HttpLite())
        assertEquals("2025" to "3", jw.academicTerm(LocalDate.of(2025, 9, 1)))
        assertEquals("2025" to "3", jw.academicTerm(LocalDate.of(2026, 1, 10)))
        assertEquals("2025" to "12", jw.academicTerm(LocalDate.of(2026, 4, 1)))
        assertEquals("2026" to "3", jw.academicTerm(LocalDate.of(2026, 8, 20)))
    }
}
