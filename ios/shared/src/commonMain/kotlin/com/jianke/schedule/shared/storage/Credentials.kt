package com.jianke.schedule.shared.storage

/**
 * 凭据仓库。
 *
 * **iOS 上由 Swift 层实现成 Keychain**（`SecItemAdd` / `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`），
 * 也就是 M2 的 `iosApp` 里传一个实现进 `MainViewController`。
 * 这样做的原因：Keychain 的 CFDictionary 互操作放在 Swift 里又短又稳，比在 Kotlin/Native 里
 * 手搓 `CFDictionaryCreateMutable` 靠谱得多。
 *
 * 注意 Keychain 的特性：**卸载 App 不会清掉条目**，所以「清除账号」必须显式调用 [clear]。
 */
interface CredentialVault {
    fun read(key: String): String?
    fun save(key: String, value: String)
    fun remove(key: String)
    fun clear()
}

/** 内存实现：单测与预览用，绝不用于真实凭据。 */
class MemoryVault : CredentialVault {
    private val map = HashMap<String, String>()
    override fun read(key: String): String? = map[key]
    override fun save(key: String, value: String) {
        map[key] = value
    }

    override fun remove(key: String) {
        map.remove(key)
    }

    override fun clear() = map.clear()
}

/** 账号 / TGT / 打码平台凭据的读写门面。 */
class Credentials(private val vault: CredentialVault) {

    companion object {
        private const val K_USER = "username"
        private const val K_PASS = "password"
        private const val K_TGT = "tgt"
        private const val K_SOLVER_USER = "solver_user"
        private const val K_SOLVER_PASS = "solver_pass"
    }

    fun account(): Pair<String, String>? {
        val u = vault.read(K_USER).orEmpty()
        val p = vault.read(K_PASS).orEmpty()
        return if (u.isBlank() || p.isBlank()) null else u to p
    }

    fun hasAccount(): Boolean = account() != null

    fun saveAccount(username: String, password: String) {
        vault.save(K_USER, username.trim())
        vault.save(K_PASS, password)
        // 换了账号，旧 TGT 必然无效
        vault.remove(K_TGT)
    }

    fun tgt(): String? = vault.read(K_TGT)?.ifBlank { null }

    fun saveTgt(tgt: String) = vault.save(K_TGT, tgt)

    fun clearTgt() = vault.remove(K_TGT)

    fun solver(): Pair<String, String>? {
        val u = vault.read(K_SOLVER_USER).orEmpty()
        val p = vault.read(K_SOLVER_PASS).orEmpty()
        return if (u.isBlank() || p.isBlank()) null else u to p
    }

    fun saveSolver(username: String, password: String) {
        vault.save(K_SOLVER_USER, username.trim())
        vault.save(K_SOLVER_PASS, password)
    }

    fun clearAll() = vault.clear()
}
