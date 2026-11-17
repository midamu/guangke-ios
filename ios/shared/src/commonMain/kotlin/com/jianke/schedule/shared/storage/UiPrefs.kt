package com.jianke.schedule.shared.storage

/**
 * 首页可自定义的文案（标题 / 副标题）。
 *
 * 复用 [CredentialVault] 做键值存储：iOS 侧同一个实现（Keychain，失败降级 UserDefaults），
 * 桌面预览用内存实现，关闭即失效。
 */
class UiPrefs(private val vault: CredentialVault) {

    companion object {
        const val DEFAULT_TITLE = "理小广"
        const val DEFAULT_SUBTITLE = "天天开心"

        private const val K_TITLE = "ui.title"
        private const val K_SUBTITLE = "ui.subtitle"

        /** 输入框留空时的兜底：不允许把标题改成空。 */
        private fun orDefault(value: String?, fallback: String): String =
            if (value.isNullOrBlank()) fallback else value
    }

    fun title(): String = orDefault(vault.read(K_TITLE), DEFAULT_TITLE)

    fun subtitle(): String = orDefault(vault.read(K_SUBTITLE), DEFAULT_SUBTITLE)

    fun save(title: String, subtitle: String) {
        val t = title.trim()
        val s = subtitle.trim()
        if (t.isBlank() && s.isBlank()) {
            reset()
            return
        }
        if (t.isBlank()) vault.remove(K_TITLE) else vault.save(K_TITLE, t)
        if (s.isBlank()) vault.remove(K_SUBTITLE) else vault.save(K_SUBTITLE, s)
    }

    fun reset() {
        vault.remove(K_TITLE)
        vault.remove(K_SUBTITLE)
    }
}
