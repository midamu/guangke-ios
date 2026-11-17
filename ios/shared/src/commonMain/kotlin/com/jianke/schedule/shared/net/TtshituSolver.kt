package com.jianke.schedule.shared.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** 打码结果。失败时带上**平台原话**，不再统一压成一句「没识别出来」。 */
sealed interface SolveResult {
    data class Ok(val answer: String) : SolveResult
    data class Failed(val reason: String) : SolveResult
}

/** 验证码识别。失败返回 [SolveResult.Failed]，调用方负责降级。 */
interface CaptchaSolver {
    suspend fun solve(png: ByteArray): SolveResult
}

/**
 * 图鉴（ttshitu.com）在线打码。
 *
 * `typeid=11` 是「计算题」——学校的验证码是算术题（`9+5=`），不是普通字符题。
 * 只上传验证码图片本身（100×25 PNG，约 700 字节），不传账号、不传页面。
 *
 * 接口约定（实测）：
 *   成功 → {"success":true,"data":{"result":"14","id":"..."}}
 *   失败 → {"success":false,"code":"-1","message":"用户名或密码错误,检查后重试"}
 * 注意是 `message` 而不是常见的 `msg`。
 */
class TtshituSolver(
    private val username: String,
    private val password: String,
    private val http: Http,
) : CaptchaSolver {

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun solve(png: ByteArray): SolveResult {
        val body = buildJsonObject {
            put("username", username)
            put("password", password)
            put("typeid", "11")
            put("image", Base64.encode(png))
        }.toString()

        val resp = try {
            http.postJson("$BASE/predict", body)
        } catch (t: Throwable) {
            return SolveResult.Failed("打码接口请求失败：${t.message}")
        }
        if (!resp.ok) return SolveResult.Failed("打码接口返回 HTTP ${resp.status}")

        val root = runCatching { JSON.parseToJsonElement(resp.body).jsonObject }.getOrNull()
            ?: return SolveResult.Failed("打码接口返回的不是 JSON：${resp.body.take(120)}")

        val success = (root["success"] as? JsonPrimitive)?.booleanOrNull ?: false
        if (!success) {
            val code = root.str("code")
            val message = root.str("message").ifBlank { root.str("msg") }
            return SolveResult.Failed(
                "打码平台拒绝" + (if (message.isNotBlank()) "：$message" else "") +
                    (if (code.isNotBlank() && code != "-1") "（code=$code）" else ""),
            )
        }

        val data = root["data"] as? JsonObject
        val raw = (data?.get("result") as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (raw.isBlank()) return SolveResult.Failed("打码平台返回了空结果")

        val answer = CaptchaAnswer.normalize(raw)
            ?: return SolveResult.Failed("识别结果「$raw」不像算术题答案（学校可能换了题型）")

        return SolveResult.Ok(answer)
    }

    /** 识别错了可以回传，平台退积分（失败不抛异常）。 */
    suspend fun report(id: String) {
        runCatching {
            http.postJson(
                "$BASE/reporter",
                buildJsonObject {
                    put("username", username)
                    put("password", password)
                    put("id", id)
                }.toString(),
            )
        }
    }

    companion object {
        private const val BASE = "https://api.ttshitu.com"
        private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}

private fun JsonObject.str(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
