package com.jianke.schedule.shared.net

/**
 * 正方教务（jw.gzist.edu.cn）侧的取数。与 Android 版同一套接口与参数。
 *
 * 注意：教务域是**明文 HTTP**，iOS 上必须靠 Info.plist 的 ATS 例外放行
 * （仓库里 `ios/iosApp/Info.plist` 已经配好 `jw.gzist.edu.cn`）。
 */
class JwClient(private val http: Http) {

    companion object {
        const val JW = "http://jw.gzist.edu.cn"
        const val TABLE_PAGE =
            "$JW/jwglxt/kbcx/xskbcxMobile_cxTimeTableIndex.html?gnmkdm=Y253511&hasHeader=true"

        /** 接口候选，顺序与 Android 版页面里的探测脚本一致。 */
        val API_TARGETS = listOf(
            "$JW/jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N253508",
            "$JW/jwglxt/kbcx/xskbcx_cxXsKb.html",
            "$JW/jwglxt/kbcx/xskbcxMobile_cxXsKb.html?gnmkdm=Y253511",
        )

        /**
         * 学年/学期推算：8 月起算新学年，第一学期 `xqm=3`、第二学期 `xqm=12`。
         *
         * 无头模式拿不到页面表头，只能按日期推——这是整个移植里**唯一必错一次**的地方，
         * 需要一学期校准一次（1 月 / 8 月的边界最可疑）。
         */
        fun academicTerm(year: Int, month: Int): Pair<String, String> = when {
            month >= 8 -> year.toString() to "3"
            month == 1 -> (year - 1).toString() to "3"
            else -> (year - 1).toString() to "12"
        }
    }

    /**
     * 先打开课表页：个别学期要页面初始化才会把 Cookie 写齐。
     *
     * **顺便把页面正文带回去**——里面就有「第N周 起止日期」的表头，
     * 是推算学期起始日和当前周次的唯一可靠来源（无头模式没有渲染页面，但 HTML 有）。
     */
    suspend fun warmUp(): String =
        runCatching { http.get(TABLE_PAGE).body }.getOrDefault("")

    /** @return 接口原始 JSON；候选全失败返回 null */
    suspend fun fetchKbList(xnm: String, xqm: String): String? {
        val body = formBody("xnm" to xnm, "xqm" to xqm)
        // 正方对 Referer / X-Requested-With 敏感：少了会 403
        val headers = mapOf(
            "Referer" to TABLE_PAGE,
            "X-Requested-With" to "XMLHttpRequest",
        )
        for (url in API_TARGETS) {
            val resp = runCatching { http.postForm(url, body, headers) }.getOrNull() ?: continue
            val text = resp.body.trim()
            if (!resp.ok || text.isEmpty()) continue
            // 成功是 {"kbList":[...]}；被踢回登录页会是一段 HTML
            if (text.startsWith("{") && (text.contains("kbList") || text.contains("kcb"))) {
                return text
            }
        }
        return null
    }
}
