package com.example.sync

import android.accounts.Account
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.crypto.CryptoManager
import com.example.data.db.AppDatabase
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed class DriveDownloadResult {
    data class Success(val meta: JSONObject, val isEncrypted: Boolean) : DriveDownloadResult()
    data class RequiresPassword(val encBytes: ByteArray, val meta: JSONObject, val salt: String, val verifier: String) : DriveDownloadResult()
    data class Error(val message: String) : DriveDownloadResult()
}

object GoogleDriveSync {
    const val ENC_NAME = "finance.db.enc"
    const val META_NAME = "finance.db.meta"
    const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    const val SCOPE_DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getOAuthToken(context: Context, database: AppDatabase): String {
        return database.getSetting("google_oauth_token", "")
    }

    fun setOAuthToken(database: AppDatabase, token: String) {
        database.setSetting("google_oauth_token", token.trim())
    }

    fun getAccountEmail(database: AppDatabase): String {
        return database.getSetting("google_account_email", "")
    }

    fun setAccountEmail(database: AppDatabase, email: String) {
        database.setSetting("google_account_email", email.trim())
    }

    fun getLastSyncedAt(database: AppDatabase): String {
        return database.getSetting("last_synced_at", "")
    }

    fun setLastSyncedAt(database: AppDatabase, timestamp: String) {
        database.setSetting("last_synced_at", timestamp)
    }

    fun getCertificateSha1(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            val cert = signatures?.firstOrNull()?.toByteArray() ?: return "N/A"
            val md = MessageDigest.getInstance("SHA-1")
            val digest = md.digest(cert)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            "N/A"
        }
    }

    suspend fun fetchOAuthTokenForAccount(context: Context, account: Account): Result<String> = withContext(Dispatchers.IO) {
        try {
            val scopeString = "oauth2:$SCOPE_DRIVE_FILE $SCOPE_DRIVE_APPDATA"
            val token = GoogleAuthUtil.getToken(context, account, scopeString)
            Result.success(token)
        } catch (e: UserRecoverableAuthException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEffectiveOAuthToken(context: Context, database: AppDatabase): String = withContext(Dispatchers.IO) {
        val savedToken = getOAuthToken(context, database).trim()
        if (savedToken.isNotEmpty()) {
            return@withContext savedToken
        }
        val email = getAccountEmail(database).trim()
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        val targetEmail = if (email.isNotEmpty()) email else (lastAccount?.email ?: "")
        if (targetEmail.isNotEmpty()) {
            try {
                val account = lastAccount?.account ?: android.accounts.Account(targetEmail, "com.google")
                val scopeString = "oauth2:$SCOPE_DRIVE_FILE $SCOPE_DRIVE_APPDATA"
                val token = GoogleAuthUtil.getToken(context, account, scopeString)
                if (!token.isNullOrBlank()) {
                    setOAuthToken(database, token)
                    return@withContext token
                }
            } catch (e: Exception) {
                // fall through
            }
        }
        ""
    }

    suspend fun uploadToDrive(
        context: Context,
        database: AppDatabase,
        key: ByteArray?,
        deviceName: String = "Android Device"
    ): Result<String> = withContext(Dispatchers.IO) {
        val token = getEffectiveOAuthToken(context, database)
        if (token.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Please log in with a Google account to authorize Google Drive access."))
        }

        try {
            val dbFile = context.getDatabasePath(database.dbPath)
            val encFileOnDisk = CryptoManager.getEncFile(dbFile)
            val metaFileOnDisk = CryptoManager.getMetaFile(dbFile)
            val localMeta = CryptoManager.loadMeta(dbFile)
            val isEncrypted = localMeta.optBoolean("encryption", false)

            val snapshotFile = File(context.cacheDir, "db_snapshot.tmp")
            val encFile = File(context.cacheDir, "db_enc.tmp")
            val metaFile = File(context.cacheDir, "db_meta.tmp")

            try {
                val dbBytes: ByteArray
                val encryptedBlob: ByteArray

                if (dbFile.exists()) {
                    dbBytes = dbFile.readBytes()
                    snapshotFile.writeBytes(dbBytes)
                    if (isEncrypted && key != null) {
                        encryptedBlob = CryptoManager.encryptBytes(dbBytes, key)
                    } else if (isEncrypted && encFileOnDisk.exists()) {
                        encryptedBlob = encFileOnDisk.readBytes()
                    } else {
                        encryptedBlob = dbBytes
                    }
                } else if (encFileOnDisk.exists()) {
                    encryptedBlob = encFileOnDisk.readBytes()
                    dbBytes = ByteArray(0)
                } else {
                    return@withContext Result.failure(IllegalStateException("Local database file does not exist."))
                }

                encFile.writeBytes(encryptedBlob)

                val nowIso = CryptoManager.utcNowIso()
                val metaJson = JSONObject(localMeta.toString()).apply {
                    put("schema_version", CryptoManager.SCHEMA_VERSION)
                    put("updated_at", nowIso)
                    put("device_name", deviceName)
                    if (dbBytes.isNotEmpty()) {
                        put("db_md5", CryptoManager.md5(dbBytes))
                    }
                }
                metaFile.writeText(metaJson.toString(2), Charsets.UTF_8)

                // Upload Encrypted DB
                val encFileId = uploadFile(token, ENC_NAME, encFile.readBytes(), database.getSetting("google_drive_enc_id", ""))
                database.setSetting("google_drive_enc_id", encFileId)

                // Upload Meta
                val metaFileId = uploadFile(token, META_NAME, metaFile.readBytes(), database.getSetting("google_drive_meta_id", ""))
                database.setSetting("google_drive_meta_id", metaFileId)

                setLastSyncedAt(database, nowIso)
                CryptoManager.saveMeta(dbFile, metaJson)

                Result.success(nowIso)
            } finally {
                snapshotFile.delete()
                encFile.delete()
                metaFile.delete()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFromDrive(
        context: Context,
        database: AppDatabase,
        currentKey: ByteArray?
    ): DriveDownloadResult = withContext(Dispatchers.IO) {
        val token = getEffectiveOAuthToken(context, database)
        if (token.isEmpty()) {
            return@withContext DriveDownloadResult.Error("Please log in with a Google account to authorize Google Drive access.")
        }

        try {
            val encId = findFileId(token, ENC_NAME)
                ?: return@withContext DriveDownloadResult.Error("Database backup '$ENC_NAME' not found on Google Drive. Please upload first.")
            val metaId = findFileId(token, META_NAME)
                ?: return@withContext DriveDownloadResult.Error("Metadata file '$META_NAME' not found on Google Drive.")

            val encBytes = downloadFileBytes(token, encId)
            val metaBytes = downloadFileBytes(token, metaId)

            val metaJson = JSONObject(String(metaBytes, Charsets.UTF_8))
            val isEncrypted = metaJson.optBoolean("encryption", false)

            if (isEncrypted) {
                val salt = metaJson.optString("salt", "")
                val verifier = metaJson.optString("verifier", "")

                // Check if current key in memory works
                if (currentKey != null && verifier.isNotEmpty() && CryptoManager.checkVerifier(currentKey, verifier)) {
                    val decrypted = CryptoManager.decryptBytes(encBytes, currentKey)
                    applyDownloadedDb(context, database, decrypted, encBytes, metaJson, currentKey, encId, metaId)
                    return@withContext DriveDownloadResult.Success(metaJson, true)
                }

                // If not unlocked or key mismatch, request master password from user
                return@withContext DriveDownloadResult.RequiresPassword(encBytes, metaJson, salt, verifier)
            } else {
                // Unencrypted database
                applyDownloadedDb(context, database, encBytes, null, metaJson, null, encId, metaId)
                return@withContext DriveDownloadResult.Success(metaJson, false)
            }
        } catch (e: Exception) {
            DriveDownloadResult.Error(e.localizedMessage ?: "Download failed")
        }
    }

    fun applyDownloadedDb(
        context: Context,
        database: AppDatabase,
        plainDbBytes: ByteArray,
        encDbBytes: ByteArray?,
        metaJson: JSONObject,
        key: ByteArray?,
        encId: String,
        metaId: String
    ) {
        database.close()
        val dbFile = context.getDatabasePath(database.dbPath)
        dbFile.parentFile?.mkdirs()
        dbFile.writeBytes(plainDbBytes)

        val isEncrypted = metaJson.optBoolean("encryption", false)
        val encFile = CryptoManager.getEncFile(dbFile)

        if (isEncrypted) {
            if (encDbBytes != null) {
                encFile.writeBytes(encDbBytes)
            } else if (key != null) {
                CryptoManager.encryptFile(dbFile, encFile, key)
            }
            database.cryptoKey = key
        } else {
            if (encFile.exists()) {
                CryptoManager.secureDelete(encFile)
            }
            database.cryptoKey = null
        }

        CryptoManager.saveMeta(dbFile, metaJson)
        database.reopen()
        val updatedAt = metaJson.optString("updated_at", CryptoManager.utcNowIso())
        setLastSyncedAt(database, updatedAt)
        database.setSetting("google_drive_enc_id", encId)
        database.setSetting("google_drive_meta_id", metaId)
    }

    private fun findFileId(token: String, fileName: String): String? {
        val query = "name='$fileName' and trashed=false"
        val url = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&spaces=drive&fields=files(id,name,modifiedTime)&pageSize=1"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        val json = JSONObject(body)
        val files = json.optJSONArray("files") ?: return null
        if (files.length() > 0) {
            return files.getJSONObject(0).getString("id")
        }
        return null
    }

    private fun downloadFileBytes(token: String, fileId: String): ByteArray {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("Failed to download file: HTTP ${response.code}")
        }
        return response.body?.bytes() ?: throw IllegalStateException("Downloaded file content is empty")
    }

    private fun uploadFile(token: String, fileName: String, data: ByteArray, existingFileId: String?): String {
        val targetId = if (!existingFileId.isNullOrEmpty()) existingFileId else findFileId(token, fileName)
        val mediaType = "application/octet-stream".toMediaType()
        val requestBody = data.toRequestBody(mediaType)

        if (targetId != null) {
            val url = "https://www.googleapis.com/upload/drive/v3/files/$targetId?uploadType=media"
            val request = Request.Builder()
                .url(url)
                .patch(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) return targetId
        }

        val metadata = JSONObject().apply {
            put("name", fileName)
        }
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("metadata", null, metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addFormDataPart("file", fileName, requestBody)
            .build()

        val createUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id"
        val createRequest = Request.Builder()
            .url(createUrl)
            .post(multipartBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        val createResponse = client.newCall(createRequest).execute()
        if (!createResponse.isSuccessful) {
            throw IllegalStateException("Failed to create file on Google Drive: HTTP ${createResponse.code} - ${createResponse.body?.string()}")
        }
        val createJson = JSONObject(createResponse.body?.string() ?: "{}")
        return createJson.getString("id")
    }
}

