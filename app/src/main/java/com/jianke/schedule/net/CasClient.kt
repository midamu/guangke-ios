package com.jianke.schedule.net

import android.util.Base64
import org.json.JSONObject
import java.math.BigInteger

/**
 * 统一身份认证（联奕 lyuapServer）的无头登录。
 *
 * 链路（全部实测确认，见 docs/headless-cas-plan.md）：
 *   GET  /lyuapServer/kaptcha            → {uid, content:base64 png}
 *   POST /lyuapServer/v1/tickets  (form) → {tgt, ticket}
 *   POST /lyuapServer/v1/tickets/{tgt}   → 纯文本 ST-...
 *   GET  {service}?ticket=ST-...         → 教务域 Set-Cookie
 *
 * 密码不是标准 RSA/PKCS#1：**无填充、小端字节序、输出不定长小写 hex**。
 * 用 Cipher.getInstance("RSA/ECB/PKCS1Padding") 会一直「密码错误」，别踩。
 */
class CasClient(private val http: HttpLite = HttpLite()) {

    data class Captcha(val uid: String, val png: ByteArray)

    sealed interface LoginResult {
        data class Ok(val tgt: String, val ticket: String) : LoginResult
        /** 服务端要求验证码，但这次没带或带错了。 */
        data class NeedCaptcha(val code: String, val message: String) : LoginResult
        /** 密码错 / 账号锁定 / 需要改密 / 二次验证等，**不该重试**。 */
        data class Failed(val code: String, val message: String) : LoginResult
    }

    fun fetchCaptcha(): Captcha? {
        val resp = http.get("$CAS/kaptcha?uid=")
        if (!resp.ok) return null
        return runCatching {
            val o = JSONObject(resp.body)
            val uid = o.optString("uid")
            var content = o.optString("content")
            if (uid.isBlank() || content.isBlank()) return null
            // content 带 "data:image/png;base64," 前缀，按第一个逗号切
            if (content.startsWith("data:")) content = content.substringAfter(',')
            val png = Base64.decode(content, Base64.DEFAULT)
            if (png.isEmpty()) null else Captcha(uid, png)
        }.getOrNull()
    }

    /**
     * @param captcha 首次尝试可传 null（部分时段服务端不要求验证码）
     */
    fun login(
        username: String,
        password: String,
        captcha: Captcha? = null,
        answer: String? = null,
    ): LoginResult {
        val params = LinkedHashMap<String, String>()
        params["username"] = username.trim()
        params["password"] = rsaEncrypt(password)
        params["service"] = SERVICE
        params["loginType"] = "" // 空 = 密码为密文
        params["id"] = captcha?.uid.orEmpty()
        params["code"] = answer.orEmpty()

        val resp = http.postForm(
            "$CAS/v1/tickets",
            params,
            mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Origin" to HOST,
                "Referer" to "$HOST/lyuapServer/login?service=" + java.net.URLEncoder.encode(SERVICE, "UTF-8"),
            ),
        )
        if (!resp.ok) {
            return LoginResult.Failed("HTTP_" + resp.code, "登录接口返回 HTTP " + resp.code)
        }
        return parseLogin(resp.body)
    }

    private fun parseLogin(body: String): LoginResult {
        val raw = body.trim()
        if (raw.isEmpty()) return LoginResult.Failed("EMPTY", "登录接口返回空响应")
        return runCatching {
            val root = JSONObject(raw)
            // 成功：顶层或 data 下给 tgt / ticket
            val data = root.optJSONObject("data")
            val tgt = root.optString("tgt").ifBlank { data?.optString("tgt").orEmpty() }
            val ticket = root.optString("ticket").ifBlank { data?.optString("ticket").orEmpty() }
            if (tgt.isNotBlank()) return LoginResult.Ok(tgt, ticket)

            val code = data?.optString("code").orEmpty().ifBlank { root.optString("code") }
            val message = data?.optString("message").orEmpty()
                .ifBlank { root.optString("message") }
                .ifBlank { data?.optString("data").orEmpty() }

            when {
                code == "CODEFALSE" -> LoginResult.NeedCaptcha(code, "验证码错误")
                code.isBlank() -> LoginResult.Failed("UNKNOWN", "登录失败：" + raw.take(200))
                // 密码错误会带「已错次数,剩余次数」或锁定时间
                else -> LoginResult.Failed(code, friendly(code, message))
            }
        }.getOrElse {
            LoginResult.Failed("PARSE", "登录响应解析失败：" + raw.take(200))
        }
    }

    private fun friendly(code: String, message: String): String = when (code) {
        "NOREGISTER" -> "账号不存在或未开通"
        "NOAUTHORIZATION" -> "该账号没有此应用的权限"
        "ISMODIFYPASS" -> "需要先修改密码"
        "TWOVERIFY" -> "触发短信二次验证，请用网页手动登录"
        "NETWORKCOMMITMENT" -> "需要先签署网络使用承诺书"
        else -> message.ifBlank { "登录失败（" + code + "）" }
    }

    /** 用 TGT 换新的 Service Ticket —— 这条路径**不需要验证码**，是免打码的关键。 */
    fun ticketFor(tgt: String): String? {
        val resp = http.postForm("$CAS/v1/tickets/" + tgt, mapOf("service" to SERVICE, "loginToken" to "loginToken"))
        if (!resp.ok) return null
        val text = resp.body.trim()
        // 成功是纯文本 ST-...，失败是 JSON / 报错文案
        return if (text.startsWith("ST-")) text.substringBefore('\n').trim() else null
    }

    /** 拿 ST 建教务会话。成功后教务域的 Cookie 会存在 [http] 里。 */
    fun openJwSession(ticket: String): Boolean {
        val resp = http.get(SERVICE + "?ticket=" + ticket)
        // 落地页可能是 302 → 课表页；能拿到任何 2xx/3xx 且已有 Cookie 就算成功
        return resp.code in 200..399
    }

    companion object {
        const val HOST = "https://ids.gzist.edu.cn"
        const val CAS = HOST + "/lyuapServer"
        const val SERVICE = "http://jw.gzist.edu.cn/sso/lyiotlogin"

        /** 登录页 bundle 里硬编码的公钥（module 15），学校换密钥时才需要更新。 */
        private const val RSA_E = "010001"
        private const val RSA_N =
            "00b5eeb166e069920e80bebd1fea4829d3d1f3216f2aabe79b6c47a3c18dcee5fd22c2e7ac519" +
                "cab59198ece036dcf289ea8201e2a0b9ded307f8fb704136eaeb670286f5ad44e691005ba9ea5" +
                "af04ada5367cd724b5a26fdb5120cc95b6431604bd219c6b7d83a6f8f24b43918ea988a76f93" +
                "c333aa5a20991493d4eb1117e7b1"

        /**
         * 无填充裸模幂：m = 小端字节序的密码整数，c = m^e mod n，输出小写 hex（不补前导零）。
         * 与登录页 JS 完全一致。
         */
        fun rsaEncrypt(password: String): String {
            val clean = password.filterNot { it.isWhitespace() }
            val bytes = clean.toByteArray(Charsets.UTF_8).reversedArray() // 小端
            val m = BigInteger(1, bytes)
            val e = BigInteger(RSA_E, 16)
            val n = BigInteger(RSA_N, 16)
            return m.modPow(e, n).toString(16)
        }
    }
}
