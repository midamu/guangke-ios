package com.jianke.schedule.shared.crypto

/**
 * 学校登录密码的加密。
 *
 * **不是标准 RSA/PKCS#1**：无填充、密码按**小端字节序**解释成大整数，
 * 输出不定长的小写 hex（不补前导零）。写错成 `RSA/ECB/PKCS1Padding` 时，
 * 服务端只会回「密码不正确」，极难从现象反推。
 *
 * Kotlin/Native 没有 `java.math.BigInteger`，所以这里用 base-2^32 的 limb 自己实现。
 * 算法的等价 Python 版本已对照内置 `pow()` 逐字节验证（见 HeadlessNetTest 的向量）。
 */
object Rsa {

    const val EXPONENT_HEX = "010001"

    /** 登录页 bundle 里硬编码的公钥（module 15）；学校换密钥时才需要更新。 */
    const val MODULUS_HEX =
        "00b5eeb166e069920e80bebd1fea4829d3d1f3216f2aabe79b6c47a3c18dcee5fd22c2e7ac519" +
            "cab59198ece036dcf289ea8201e2a0b9ded307f8fb704136eaeb670286f5ad44e691005ba9ea5" +
            "af04ada5367cd724b5a26fdb5120cc95b6431604bd219c6b7d83a6f8f24b43918ea988a76f93" +
            "c333aa5a20991493d4eb1117e7b1"

    /** @return 小写 hex 密文；空密码返回 "0" */
    fun encryptPassword(password: String): String {
        val clean = password.filterNot { it.isWhitespace() }
        if (clean.isEmpty()) return "0"
        val m = Mag.fromBytesLittleEndian(clean.encodeToByteArray())
        val n = Mag.fromHex(MODULUS_HEX)
        return m.modPow(EXPONENT_HEX.toInt(16), n).toHex()
    }
}

/** 无符号大整数：little-endian 的 32 位 limb，每个元素的值域 [0, 2^32)。 */
internal class Mag private constructor(private val limbs: LongArray) {

    private val size: Int get() = limbs.size

    val bitLength: Int
        get() {
            if (size == 0) return 0
            var idx = size - 1
            while (idx > 0 && limbs[idx] == 0L) idx--
            val top = limbs[idx]
            if (top == 0L) return 0
            return idx * 32 + (64 - top.countLeadingZeroBits())
        }

    fun bit(i: Int): Boolean {
        val idx = i / 32
        if (idx >= size) return false
        return ((limbs[idx] ushr (i % 32)) and 1L) != 0L
    }

    fun compareTo(other: Mag): Int {
        if (size != other.size) return if (size > other.size) 1 else -1
        for (i in size - 1 downTo 0) {
            if (limbs[i] != other.limbs[i]) return if (limbs[i] > other.limbs[i]) 1 else -1
        }
        return 0
    }

    fun add(other: Mag): Mag {
        val n = maxOf(size, other.size)
        val out = LongArray(n)
        var carry = 0L
        for (i in 0 until n) {
            val sum = (if (i < size) limbs[i] else 0L) +
                (if (i < other.size) other.limbs[i] else 0L) + carry
            out[i] = sum and MASK
            carry = sum ushr 32
        }
        return if (carry != 0L) normalized(out + carry) else normalized(out)
    }

    /** 要求 this >= other。 */
    fun subtract(other: Mag): Mag {
        val out = LongArray(size)
        var borrow = 0L
        for (i in 0 until size) {
            val b = if (i < other.size) other.limbs[i] else 0L
            var d = limbs[i] - b - borrow
            if (d < 0L) {
                d += BASE
                borrow = 1L
            } else {
                borrow = 0L
            }
            out[i] = d
        }
        return normalized(out)
    }

    fun shiftLeft1(): Mag {
        val out = LongArray(size)
        var carry = 0L
        for (i in 0 until size) {
            val v = (limbs[i] shl 1) or carry
            out[i] = v and MASK
            carry = v ushr 32
        }
        return if (carry != 0L) normalized(out + carry) else normalized(out)
    }

    private fun addMod(other: Mag, n: Mag): Mag {
        val r = add(other)
        return if (r.compareTo(n) >= 0) r.subtract(n) else r
    }

    private fun shiftLeft1Mod(n: Mag): Mag {
        val r = shiftLeft1()
        return if (r.compareTo(n) >= 0) r.subtract(n) else r
    }

    /** 模乘：double-and-add（对 this 的每一位做「累加 + 翻倍」）。 */
    fun mulMod(other: Mag, n: Mag): Mag {
        if (n.bitLength <= 1) return ZERO
        if (other.size == 0 || size == 0) return ZERO
        var acc = ZERO
        for (i in other.bitLength - 1 downTo 0) {
            acc = acc.shiftLeft1Mod(n)
            if (other.bit(i)) acc = acc.addMod(this, n)
        }
        return acc
    }

    /** 平方-乘模幂。指数是普通 Int（学校用的 65537）。 */
    fun modPow(exponent: Int, n: Mag): Mag {
        if (exponent <= 0) return ONE
        val highest = 31 - exponent.countLeadingZeroBits()
        var acc = ONE
        for (i in highest downTo 0) {
            acc = acc.mulMod(acc, n)
            if (((exponent ushr i) and 1) == 1) acc = acc.mulMod(this, n)
        }
        return acc
    }

    fun toHex(): String {
        val n = size
        if (n == 0) return "0"
        val sb = StringBuilder()
        sb.append(limbs[n - 1].toString(16))
        for (i in n - 2 downTo 0) sb.append(limbs[i].toString(16).padStart(8, '0'))
        return sb.toString()
    }

    companion object {
        // 2^32 与 2^32-1：用字面量而不是 `1L shl 32`，const val 只接受编译期常量
        private const val BASE = 4294967296L
        private const val MASK = 4294967295L

        val ZERO = Mag(LongArray(0))
        val ONE = Mag(longArrayOf(1L))

        internal fun normalized(limbs: LongArray): Mag {
            var end = limbs.size
            while (end > 0 && limbs[end - 1] == 0L) end--
            return if (end == limbs.size) Mag(limbs) else Mag(limbs.copyOf(end))
        }

        fun fromBytesLittleEndian(bytes: ByteArray): Mag {
            val limbs = LongArray((bytes.size + 3) / 4)
            for (i in bytes.indices) {
                val shift = (i % 4) * 8
                limbs[i / 4] = limbs[i / 4] or ((bytes[i].toLong() and 0xFF) shl shift)
            }
            return normalized(limbs)
        }

        fun fromHex(hex: String): Mag {
            var h = hex.trim()
            if (h.startsWith("0x") || h.startsWith("0X")) h = h.substring(2)
            h = h.trimStart('0')
            if (h.isEmpty()) return ZERO
            if (h.length % 2 == 1) h = "0$h"
            val bigEndian = ByteArray(h.length / 2) { i ->
                ((h[2 * i].digitToInt(16) shl 4) or h[2 * i + 1].digitToInt(16)).toByte()
            }
            return fromBytesLittleEndian(bigEndian.reversedArray())
        }
    }
}
