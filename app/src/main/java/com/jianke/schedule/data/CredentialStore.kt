package com.jianke.schedule.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 账号 / TGT / 打码平台凭据的本地存储。
 *
 * 全部塞进一个 JSON，整体用 **Android Keystore 里的 AES-256-GCM 密钥**加密后
 * 落到 SharedPreferences——密钥不出安全硬件，被拷走 plist/xml 也解不开。
 *
 * 说明：这是本项目第一次在本机保存密码（原设计是「账号密码不经过 App」）。
 * 换了这套之后，手机丢失 + 设备被破解才可能泄露，普通木马读不到明文。
 * 申请了生物识别的话还能再上一把锁，这里先留出 [clearAll] 给用户随时反悔。
 */
object CredentialStore {

    private const val SP = "gxk_cred"
    private const val KEY_BLOB = "blob"
    private const val ALIAS = "gxk_cred_key"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    data class Account(val username: String, val password: String)

    private fun sp(context: Context) = context.getSharedPreferences(SP, Context.MODE_PRIVATE)

    // ---------- 账号 ----------

    fun hasAccount(context: Context): Boolean = account(context) != null

    fun account(context: Context): Account? = read(context)?.let { o ->
        val u = o.optString("u")
        val p = o.optString("p")
        if (u.isBlank() || p.isBlank()) null else Account(u, p)
    }

    fun saveAccount(context: Context, username: String, password: String) {
        val o = read(context) ?: JSONObject()
        o.put("u", username.trim())
        o.put("p", password)
        // 账号变了，旧 TGT 必然无效，顺手清掉避免拿别人的票去换
        o.remove("tgt")
        write(context, o)
    }

    // ---------- TGT（免验证码续期的钥匙） ----------

    fun tgt(context: Context): String? =
        read(context)?.optString("tgt").orEmpty().ifBlank { null }

    fun saveTgt(context: Context, tgt: String) {
        val o = read(context) ?: JSONObject()
        o.put("tgt", tgt)
        write(context, o)
    }

    fun clearTgt(context: Context) {
        val o = read(context) ?: return
        o.remove("tgt")
        write(context, o)
    }

    // ---------- 打码平台 ----------

    fun solver(context: Context): Pair<String, String>? = read(context)?.let { o ->
        val u = o.optString("su")
        val p = o.optString("sp")
        if (u.isBlank() || p.isBlank()) null else u to p
    }

    fun saveSolver(context: Context, username: String, password: String) {
        val o = read(context) ?: JSONObject()
        o.put("su", username.trim())
        o.put("sp", password)
        write(context, o)
    }

    fun clearAll(context: Context) {
        sp(context).edit().remove(KEY_BLOB).apply()
    }

    // ---------- 加解密 ----------

    private fun read(context: Context): JSONObject? {
        val blob = sp(context).getString(KEY_BLOB, null) ?: return null
        return runCatching { JSONObject(decrypt(blob)) }.getOrNull()
    }

    private fun write(context: Context, o: JSONObject) {
        val blob = runCatching { encrypt(o.toString()) }.getOrNull() ?: return
        sp(context).edit().putString(KEY_BLOB, blob).apply()
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null as java.io.InputStream?) }
        (ks.getEntry(ALIAS, null as KeyStore.ProtectionParameter?) as? KeyStore.SecretKeyEntry)
            ?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String {
        val all = Base64.decode(blob, Base64.NO_WRAP)
        val iv = all.copyOfRange(0, IV_BYTES)
        val ct = all.copyOfRange(IV_BYTES, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }
}
