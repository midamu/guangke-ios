package com.jianke.schedule.shared.net

/**
 * 验证码答案是**算术题**（`9+5=` → `14`），识别器（图鉴 typeid=11）可能返回三种形态：
 * `14`、`9+5=`、`9+5=14`。这里统一收敛成可直接填进输入框的答案。
 */
object CaptchaAnswer {

    fun normalize(rawInput: String): String? {
        var s = rawInput.trim()
            .replace(" ", "")
            .replace("？", "?")
            .replace("＝", "=")
        if (s.isEmpty()) return null

        // "a+b=14"：等号右边已经是答案，直接取
        if (s.contains('=')) {
            val right = s.substringAfterLast('=').trim().replace("?", "").trim()
            if (right.isNotEmpty() && right.all { it.isDigit() }) return right
            s = s.substringBeforeLast('=').replace("?", "").trim()
        }
        if (s.isEmpty()) return null
        if (s.all { it.isDigit() }) return s

        val m = EXPRESSION.find(s) ?: return null
        val a = m.groupValues[1].toIntOrNull() ?: return null
        val b = m.groupValues[3].toIntOrNull() ?: return null
        val value = when (m.groupValues[2]) {
            "+" -> a + b
            "-" -> a - b
            "÷", "/" -> if (b == 0) return null else a / b
            else -> a * b
        }
        return if (value < 0) null else value.toString()
    }

    private val EXPRESSION = Regex("^(\\d{1,3})([+\\-×xX*÷/])(\\d{1,3})$")
}
