package com.jianke.schedule.shared

import com.jianke.schedule.shared.crypto.Rsa
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 密码加密的固定向量。
 *
 * 期望值来自《GZIST登录踩坑总结.md》里的 Python 参考实现（`int.from_bytes(pwd,'little')`
 * → `pow(m, e, n)` → `format(x,'x')`），并用同算法的 limb 版对照 `pow()` 校验过。
 *
 * 这条测试的意义：**第一次跑就能确认 iOS 侧的加密和 Android 侧逐字节一致**，
 * 不用等到真机登录、对着一句「密码不正确」猜。
 */
class RsaTest {

    @Test
    fun matchesReferenceVector() {
        assertEquals(
            "556386088bed8a24d6d61615f1d355cd79ea501e5ca52a4a8688fc2a587abeb030c7042cbbceb8daaf92facf658a6525" +
                "6e96e44dcc6a7871fdeb4554067a1a23265e9a4c2f768e69b6be2bc170014fcd6a5796f2d02198964a60a1f6b6087f7" +
                "0a9dfd10bcffa36a37c982abee41a9744f54a735353a79593b7e2fd61a4317c0f",
            Rsa.encryptPassword("test123"),
        )
    }

    @Test
    fun doesNotPadLeadingZeros() {
        // "abc" 的密文正好 255 位：实现若把 hex 补齐到 512 就会立刻挂在这里
        assertEquals(255, Rsa.encryptPassword("abc").length)
    }

    @Test
    fun trimsWhitespaceInPassword() {
        assertEquals(Rsa.encryptPassword("test123"), Rsa.encryptPassword(" test 123 "))
    }

    @Test
    fun emptyPasswordIsZero() {
        assertEquals("0", Rsa.encryptPassword("   "))
    }
}
