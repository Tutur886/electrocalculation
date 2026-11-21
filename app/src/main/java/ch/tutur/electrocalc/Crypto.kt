package ch.tutur.electrocalc

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chiffrement AES-256-GCM avec dérivation de clé PBKDF2 (HMAC-SHA256).
 * Chaque fichier chiffré : MAGIC(4) | salt(16) | iv(12) | ciphertext(+tag GCM)
 * Charge utile en clair : nameLen(2) | nameUtf8 | fileBytes
 */
object Crypto {
    private const val ITER = 120_000
    private const val KEY_BITS = 256
    private const val GCM_TAG = 128
    private val MAGIC = byteArrayOf(
        'E'.code.toByte(), 'V'.code.toByte(), 'C'.code.toByte(), '1'.code.toByte()
    )

    fun randomBytes(n: Int): ByteArray {
        val b = ByteArray(n)
        SecureRandom().nextBytes(b)
        return b
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITER, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    /** Empreinte du mot de passe pour le déclencheur caché (on ne stocke jamais le mot de passe). */
    fun hashPassword(password: String, salt: ByteArray): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITER, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun encrypt(password: String, fileName: String, data: ByteArray): ByteArray {
        val salt = randomBytes(16)
        val iv = randomBytes(12)
        val key = deriveKey(password.toCharArray(), salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG, iv))

        val nameBytes = fileName.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(2 + nameBytes.size + data.size)
        payload[0] = ((nameBytes.size shr 8) and 0xFF).toByte()
        payload[1] = (nameBytes.size and 0xFF).toByte()
        System.arraycopy(nameBytes, 0, payload, 2, nameBytes.size)
        System.arraycopy(data, 0, payload, 2 + nameBytes.size, data.size)

        val ct = cipher.doFinal(payload)
        val out = ByteArray(4 + 16 + 12 + ct.size)
        System.arraycopy(MAGIC, 0, out, 0, 4)
        System.arraycopy(salt, 0, out, 4, 16)
        System.arraycopy(iv, 0, out, 20, 12)
        System.arraycopy(ct, 0, out, 32, ct.size)
        return out
    }

    class Decrypted(val name: String, val data: ByteArray)

    fun decrypt(password: String, blob: ByteArray): Decrypted {
        require(blob.size > 32) { "Fichier trop court" }
        for (i in 0 until 4) require(blob[i] == MAGIC[i]) { "Format non reconnu" }
        val salt = blob.copyOfRange(4, 20)
        val iv = blob.copyOfRange(20, 32)
        val ct = blob.copyOfRange(32, blob.size)
        val key = deriveKey(password.toCharArray(), salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG, iv))
        val payload = cipher.doFinal(ct)
        val nameLen = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        val name = String(payload, 2, nameLen, Charsets.UTF_8)
        val data = payload.copyOfRange(2 + nameLen, payload.size)
        return Decrypted(name, data)
    }
}
