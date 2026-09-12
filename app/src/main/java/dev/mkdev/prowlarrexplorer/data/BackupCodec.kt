package dev.mkdev.prowlarrexplorer.data

import android.util.Base64
import dev.mkdev.prowlarrexplorer.domain.AppSettings
import dev.mkdev.prowlarrexplorer.domain.ProwlarrConfig
import dev.mkdev.prowlarrexplorer.domain.QbitConfig
import dev.mkdev.prowlarrexplorer.domain.ThemeMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupError(message: String) : Exception(message)

/** Contenu d'une sauvegarde : tout ce qui se règle, rien de ce qui se recalcule. */
@Serializable
data class BackupPayload(
    val prowlarrUrl: String,
    val prowlarrApiKey: String,
    val qbitUrl: String,
    val qbitApiKey: String,
    val qbitUser: String,
    val qbitPassword: String,
    val theme: String,
    val notifyDone: Boolean,
    val qbCategory: String,
) {
    fun settings() = AppSettings(ProwlarrConfig(prowlarrUrl, prowlarrApiKey), QbitConfig(qbitUrl, qbitApiKey, qbitUser, qbitPassword))
    fun themeMode() = ThemeMode.entries.firstOrNull { it.name == theme } ?: ThemeMode.SYSTEM
}

/** Enveloppe écrite dans le fichier ; tout est en base64, la clé vient de la phrase secrète. */
@Serializable
private data class Envelope(val app: String = "ProwlarrExplorer", val v: Int = 1, val salt: String, val iv: String, val data: String)

/**
 * Fichier .pxbak : JSON chiffré AES-256-GCM, clé dérivée de la phrase secrète (PBKDF2-HMAC-SHA256,
 * 200 000 itérations, sel aléatoire). Sans la phrase, le fichier est inexploitable.
 */
object BackupCodec {

    const val EXTENSION = "pxbak"
    private const val ITERATIONS = 200_000
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encrypt(payload: BackupPayload, passphrase: String): ByteArray {
        require(passphrase.length >= 6) { "phrase trop courte" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv))
        val sealed = cipher.doFinal(json.encodeToString(BackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8))
        val env = Envelope(salt = b64(salt), iv = b64(iv), data = b64(sealed))
        return json.encodeToString(Envelope.serializer(), env).toByteArray(Charsets.UTF_8)
    }

    fun decrypt(bytes: ByteArray, passphrase: String): BackupPayload {
        val env = runCatching { json.decodeFromString(Envelope.serializer(), String(bytes, Charsets.UTF_8)) }
            .getOrElse { throw BackupError("Fichier non reconnu") }
        if (env.app != "ProwlarrExplorer" || env.v != 1) throw BackupError("Fichier non reconnu (${env.app} v${env.v})")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(passphrase, unb64(env.salt)), GCMParameterSpec(128, unb64(env.iv)))
        val plain = runCatching { cipher.doFinal(unb64(env.data)) }.getOrElse { throw BackupError("Phrase secrète incorrecte") }
        return json.decodeFromString(BackupPayload.serializer(), String(plain, Charsets.UTF_8))
    }

    private fun key(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
}
