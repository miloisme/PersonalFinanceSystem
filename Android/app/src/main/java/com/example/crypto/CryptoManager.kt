package com.example.crypto

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Utility object for database encryption, key derivation (PBKDF2), file shredding, and metadata management.
 * Uses PBKDF2WithHmacSHA256 (600,000 iterations) and AES-256-GCM.
 */
object CryptoManager {
    private const val KEY_ITERATIONS = 600000
    private const val NONCE_LEN = 12
    private const val TAG_LEN_BITS = 128
    private const val VERIFIER_PLAINTEXT = "pfm-verifier-ok"
    const val SCHEMA_VERSION = 1

    /**
     * Generates a cryptographically secure 16-byte random salt.
     */
    fun genSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }

    /**
     * Derives a 256-bit AES key from password and salt using PBKDF2WithHmacSHA256 (600k iterations).
     */
    fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, KEY_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM with a random 12-byte IV/nonce.
     */
    fun encryptBytes(data: ByteArray, key: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_LEN)
        SecureRandom().nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(TAG_LEN_BITS, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(data)
        val result = ByteArray(NONCE_LEN + ciphertext.size)
        System.arraycopy(nonce, 0, result, 0, NONCE_LEN)
        System.arraycopy(ciphertext, 0, result, NONCE_LEN, ciphertext.size)
        return result
    }

    /**
     * Decrypts ciphertext blob (IV + AES-GCM tag) using the derived 256-bit AES key.
     */
    fun decryptBytes(blob: ByteArray, key: ByteArray): ByteArray {
        if (blob.size < NONCE_LEN + 16) {
            throw IllegalArgumentException("Ciphertext blob is too short")
        }
        val nonce = ByteArray(NONCE_LEN)
        System.arraycopy(blob, 0, nonce, 0, NONCE_LEN)
        val ciphertextLen = blob.size - NONCE_LEN
        val ciphertext = ByteArray(ciphertextLen)
        System.arraycopy(blob, NONCE_LEN, ciphertext, 0, ciphertextLen)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(TAG_LEN_BITS, nonce)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Encrypts a source file into a target destination file.
     */
    fun encryptFile(srcFile: File, dstFile: File, key: ByteArray) {
        val data = srcFile.readBytes()
        val encrypted = encryptBytes(data, key)
        dstFile.writeBytes(encrypted)
    }

    /**
     * Decrypts an encrypted source file into a target destination file.
     */
    fun decryptFile(srcFile: File, dstFile: File, key: ByteArray) {
        val blob = srcFile.readBytes()
        val data = decryptBytes(blob, key)
        dstFile.writeBytes(data)
    }

    /**
     * Creates a Base64 encrypted verifier token to confirm password correctness without decrypting the full database.
     */
    fun makeVerifier(key: ByteArray): String {
        val blob = encryptBytes(VERIFIER_PLAINTEXT.toByteArray(Charsets.UTF_8), key)
        return Base64.encodeToString(blob, Base64.NO_WRAP)
    }

    /**
     * Verifies if a key correctly decrypts the verifier token.
     */
    fun checkVerifier(key: ByteArray, verifierB64: String): Boolean {
        return try {
            val blob = Base64.decode(verifierB64, Base64.DEFAULT)
            val decrypted = decryptBytes(blob, key)
            String(decrypted, Charsets.UTF_8) == VERIFIER_PLAINTEXT
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns metadata file reference for a database file.
     */
    fun getMetaFile(dbFile: File): File {
        return File(dbFile.parentFile, dbFile.name + ".meta")
    }

    /**
     * Returns encrypted file reference for a database file.
     */
    fun getEncFile(dbFile: File): File {
        return File(dbFile.parentFile, dbFile.name + ".enc")
    }

    /**
     * Loads encryption metadata JSON object for a database file.
     */
    fun loadMeta(dbFile: File): JSONObject {
        val metaFile = getMetaFile(dbFile)
        if (!metaFile.exists()) {
            return JSONObject().apply { put("encryption", false) }
        }
        return try {
            val json = JSONObject(metaFile.readText(Charsets.UTF_8))
            if (!json.has("encryption")) {
                json.put("encryption", false)
            }
            json
        } catch (e: Exception) {
            JSONObject().apply { put("encryption", false) }
        }
    }

    /**
     * Saves encryption metadata JSON object for a database file.
     */
    fun saveMeta(dbFile: File, meta: JSONObject) {
        val metaFile = getMetaFile(dbFile)
        metaFile.writeText(meta.toString(2), Charsets.UTF_8)
    }

    /**
     * Overwrites file contents with random bytes 3 times before deleting to prevent recovery.
     */
    fun secureDelete(file: File) {
        if (!file.exists()) return
        try {
            val length = file.length()
            if (length > 0) {
                val raf = RandomAccessFile(file, "rws")
                val randomBytes = ByteArray(1024)
                val random = SecureRandom()
                for (pass in 0 until 3) {
                    raf.seek(0)
                    var written = 0L
                    while (written < length) {
                        random.nextBytes(randomBytes)
                        val toWrite = minOf(randomBytes.size.toLong(), length - written).toInt()
                        raf.write(randomBytes, 0, toWrite)
                        written += toWrite
                    }
                }
                raf.close()
            }
        } catch (e: Exception) {
            // Ignore failure on shredding, attempt delete below
        }
        file.delete()
    }

    /**
     * Computes MD5 checksum hex string.
     */
    fun md5(data: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val digested = md.digest(data)
        return digested.joinToString("") { "%02x".format(it) }
    }

    /**
     * Returns current time formatted as ISO 8601 UTC string.
     */
    fun utcNowIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    /**
     * Converts byte array to lowercase hexadecimal string.
     */
    fun byteArrayToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Converts hexadecimal string to byte array.
     */
    fun hexToByteArray(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
