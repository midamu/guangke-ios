package com.jianke.schedule.net

import android.util.Base64
import org.json.JSONObject

/**
 * 验证码识别。
 *
 * 学校这道题是**算术题**（`9+5=` → `14`），偶尔减/乘。识别器可能返回
 * 「14」「9+5=」「9+5=14」三种形态，统一交给 [normalize] 收敛成答案。
 */
interface CaptchaSolver {
    /** @return 可直接填进输入框的答案；失败返回 null（调用方降级人工）。 */
    fun solve(png: ByteArray): String?
}

/** 图鉴（ttshitu.com）在线打码。typeid=11 是「计算题」。 */
class TtshituSolver(
    private val username: String,
    private val password: String,
    private val http: HttpLite = HttpLite(),
) : CaptchaSolver {

    override fun solve(png: ByteArray): String? {
        val image = Base64.encodeToString(png, Base64.NO_WRAP)
        val body = JSONObject()
            .put("username", username)
            .put("password", password)
            .put("typeid", "11")
            .put("image", image)
            .toString()

        val resp = runCatching { http.postJson("$BASE/predict", body) }.getOrNull() ?: return null
        if (!resp.ok) return null
        val json = runCatching { JSONObject(resp.body) }.getOrNull() ?: return null
        if (!json.optBoolean("success")) return null
        val result = json.optJSONObject("data")?.optString("result").orEmpty()
        return normalize(result)
    }

    /** 识别错了可以回传，平台会退积分（失败不抛异常）。 */
    fun report(id: String) {
        runCatching {
            http.postJson(
                "$BASE/reporter",
                JSONObject().put("username", username).put("password", password).put("id", id).toString(),
            )
        }
    }

    companion object {
        private const val BASE = "https://api.ttshitu.com"

        /**
         * 把识别结果收敛成答案：
         * - 纯数字 → 原样
         * - `a+b` / `a-b` / `a×b` → 算出来
         * - `a+b=` / `a+b=?` / `a+b=14` → 先去掉尾部符号，再按上面处理
         */
        fun normalize(rawIn: String): String? {
            var s = rawIn.trim()
                .replace(" ", "")
                .replace("？", "?")
                .replace("＝", "=")
            if (s.isEmpty()) return null
            // "a+b=14" 这种：等号右边已经是答案，直接取
            if (s.contains('=')) {
                val right = s.substringAfterLast('=').trim()
                val cleaned = right.replace("?", "").trim()
                if (cleaned.isNotEmpty() && cleaned.all { it.isDigit() }) return cleaned
                s = s.substringBeforeLast('=').replace("?", "")
            }
            s = s.replace("?", "").trim()
            if (s.isEmpty()) return null
            if (s.all { it.isDigit() }) return s

            val m = Regex("^(\\d{1,3})([+\\-×xX*÷/])(\\d{1,3})$").find(s) ?: return null
            val a = m.groupValues[1].toIntOrNull() ?: return null
            val b = m.groupValues[3].toIntOrNull() ?: return null
            val v = when (m.groupValues[2]) {
                "+" -> a + b
                "-" -> a - b
                "÷", "/" -> if (b == 0) return null else a / b
                else -> a * b
            }
            return if (v < 0) null else v.toString()
        }
    }
}
