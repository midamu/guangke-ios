package com.jianke.schedule.shared.net

import com.jianke.schedule.shared.crypto.Rsa
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 统一身份认证（联奕 lyuapServer）的无头登录。与 Android 版协议逐字段一致。
 *
 *   GET  /lyuapServer/kaptcha            → {uid, content:base64 png}
 *   POST /lyuapServer/v1/tickets  (form) → {tgt, ticket}
 *   POST /lyuapServer/v1/tickets/{tgt}   → 纯文本 ST-...
 *   GET  {service}?ticket=ST-...         → 教务域 Set-Cookie
 */
class CasClient(private val http: Http) {

    data class Captcha(val uid: String, val png: ByteArray) {
        // ByteArray 在 data class 里要手写 equals/hashCode，避免 lint 报错
        override fun equals(other: Any?): Boolean =
            this === other || (other is Captcha && uid == other.uid && png.contentEquals(other.png))

        override fun hashCode(): Int = 31 * uid.hashCode() + png.contentHashCode()
    }

    sealed interface LoginResult {
        data class Ok(val tgt: String, val ticket: String) : LoginResult
        data class NeedCaptcha(val code: String, val message: String) : LoginResult
        data class Failed(val code: String, val message: String) : LoginResult
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun fetchCaptcha(): Captcha? {
        val resp = http.get("$CAS/kaptcha?uid=")
        if (!resp.ok) return null
        val root = runCatching { JSON.parseToJsonElement(resp.body).jsonObject }.getOrNull() ?: return null
        val uid = root.str("uid")
        var content = root.str("content")
        if (uid.isBlank() || content.isBlank()) return null
        if (content.startsWith("data:")) content = content.substringAfter(',')
        val png = runCatching { Base64.decode(content) }.getOrNull() ?: return null
        return if (png.isEmpty()) null else Captcha(uid, png)
    }

    /**
     * @param captcha 首次尝试可传 null：服务端不一定每次都要求验证码
     */
    suspend fun login(
        username: String,
        password: String,
        captcha: Captcha? = null,
        answer: String? = null,
    ): LoginResult {
        val body = formBody(
            "username" to username.trim(),
            "password" to Rsa.encryptPassword(password),
            "service" to SERVICE,
            "loginType" to "",
            "id" to captcha?.uid.orEmpty(),
            "code" to answer.orEmpty(),
        )
        val resp = http.postForm(
            "$CAS/v1/tickets",
            body,
            mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Origin" to HOST,
                "Referer" to "$HOST/lyuapServer/login?service=" + urlEncode(SERVICE),
            ),
        )
        if (!resp.ok) {
            return LoginResult.Failed("HTTP_${resp.status}", "登录接口返回 HTTP ${resp.status}")
        }
        return parseLogin(resp.body)
    }

    /** 用 TGT 换新的 Service Ticket —— 这条路径**不需要验证码**，是免打码的关键。 */
    suspend fun ticketFor(tgt: String): String? {
        val resp = http.postForm(
            "$CAS/v1/tickets/$tgt",
            formBody("service" to SERVICE, "loginToken" to "loginToken"),
        )
        if (!resp.ok) return null
        val text = resp.body.trim()
        // 成功是纯文本 ST-...；失败会回 JSON 或报错文案
        return if (text.startsWith("ST-")) text.substringBefore('\n').trim() else null
    }

    /** 拿 ST 建教务会话。成功后教务域的 Cookie 由 Ktor 的 HttpCookies 保存。 */
    suspend fun openJwSession(ticket: String): Boolean {
        val resp = http.get(SERVICE + "?ticket=" + urlEncode(ticket))
        return resp.status in 200..399
    }

    companion object {
        const val HOST = "https://ids.gzist.edu.cn"
        const val CAS = "$HOST/lyuapServer"
        const val SERVICE = "http://jw.gzist.edu.cn/sso/lyiotlogin"

        private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

        /** 纯函数，方便用样例 JSON 单测（不走网络）。 */
        internal fun parseLogin(body: String): LoginResult {
            val raw = body.trim()
            if (raw.isEmpty()) return LoginResult.Failed("EMPTY", "登录接口返回空响应")
            val root = runCatching { JSON.parseToJsonElement(raw).jsonObject }.getOrNull()
                ?: return LoginResult.Failed("PARSE", "登录响应解析失败：" + raw.take(200))

            val data = root.obj("data")
            val tgt = root.str("tgt").ifBlank { data?.str("tgt").orEmpty() }
            val ticket = root.str("ticket").ifBlank { data?.str("ticket").orEmpty() }
            if (tgt.isNotBlank()) return LoginResult.Ok(tgt, ticket)

            val code = data?.str("code").orEmpty().ifBlank { root.str("code") }
            val message = data?.str("message").orEmpty()
                .ifBlank { root.str("message") }
                .ifBlank { data?.str("data").orEmpty() }

            return when {
                code == "CODEFALSE" -> LoginResult.NeedCaptcha(code, "验证码错误")
                code.isBlank() -> LoginResult.Failed("UNKNOWN", "登录失败：" + raw.take(200))
                else -> LoginResult.Failed(code, friendly(code, message))
            }
        }

        private fun friendly(code: String, message: String): String = when (code) {
            "NOREGISTER" -> "账号不存在或未开通"
            "NOAUTHORIZATION" -> "该账号没有此应用的权限"
            "ISMODIFYPASS" -> "需要先修改密码"
            "TWOVERIFY" -> "触发短信二次验证，请用网页手动登录"
            "NETWORKCOMMITMENT" -> "需要先签署网络使用承诺书"
            else -> message.ifBlank { "登录失败（$code）" }
        }
    }
}

private fun JsonObject.str(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
