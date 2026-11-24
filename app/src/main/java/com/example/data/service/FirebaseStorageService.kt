package com.example.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * Service to manage uploading and accessing media assets in Firebase Cloud Storage.
 */
class FirebaseStorageService(
    storageInstance: FirebaseStorage? = null
) {
    companion object {
        private const val TAG = "FirebaseStorageService"

        /**
         * Global flag tracking if Firebase Cloud Storage is provisioned on the backend.
         * Default is false to prevent StorageException 404 (Object does not exist at location)
         * when Cloud Storage is unprovisioned on this Firebase project.
         */
        @Volatile
        var isStorageProvisioned: Boolean = false

        private fun logW(msg: String) {
            try {
                Log.w(TAG, msg)
            } catch (_: Throwable) {
                // Ignore in JVM tests without Android logger
            }
        }

        private fun logE(msg: String, tr: Throwable? = null) {
            try {
                Log.e(TAG, msg, tr)
            } catch (_: Throwable) {
                // Ignore in JVM tests without Android logger
            }
        }
    }

    private val storage: FirebaseStorage? = storageInstance ?: if (isStorageProvisioned) {
        try {
            FirebaseStorage.getInstance()
        } catch (e: Throwable) {
            logW("Firebase Storage not initialized: ${e.message}")
            null
        }
    } else null

    /**
     * Uploads an image from an Android content Uri to Firebase Storage.
     * Returns the download URL string upon successful upload.
     */
    suspend fun uploadChatImage(
        uri: Uri,
        context: Context,
        chatId: String = "",
        fileName: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val stream: InputStream? = context.contentResolver.openInputStream(uri)
            val bytes = stream?.use { it.readBytes() }
                ?: return@withContext Result.failure(Exception("Cannot read image from Uri"))

            val compressedBytes = compressImageBytes(bytes)
            val finalName = fileName ?: "img_${UUID.randomUUID()}.jpg"
            val path = if (chatId.isNotBlank()) "chat_images/$chatId/$finalName" else "chat_images/$finalName"

            uploadBytesToStorage(compressedBytes, path)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload image from Uri: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Uploads an image File to Firebase Storage.
     * Returns the download URL string.
     */
    suspend fun uploadChatImageFile(
        file: File,
        chatId: String = "",
        fileName: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) {
                return@withContext Result.failure(Exception("File does not exist: ${file.absolutePath}"))
            }
            val bytes = file.readBytes()
            val compressedBytes = compressImageBytes(bytes)
            val finalName = fileName ?: "img_${UUID.randomUUID()}.jpg"
            val path = if (chatId.isNotBlank()) "chat_images/$chatId/$finalName" else "chat_images/$finalName"

            uploadBytesToStorage(compressedBytes, path)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload file to storage: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Internal helper to upload compressed bytes to Firebase Storage and retrieve the download URL.
     * If Firebase Storage is unavailable, falls back to a base64 Data URL so chat flow remains resilient.
     */
    suspend fun uploadBytesToStorage(
        bytes: ByteArray,
        path: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val storageRef = if (isStorageProvisioned) storage?.reference?.child(path) else null
        if (storageRef != null) {
            try {
                val metadata = StorageMetadata.Builder()
                    .setContentType("image/jpeg")
                    .build()

                val uploadTask = storageRef.putBytes(bytes, metadata).await()
                val downloadUrl = uploadTask.storage.downloadUrl.await().toString()
                return@withContext Result.success(downloadUrl)
            } catch (e: Exception) {
                isStorageProvisioned = false
                Log.w(TAG, "Firebase Storage upload error, using resilient data URL fallback: ${e.message}")
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                return@withContext Result.success("data:image/jpeg;base64,$base64")
            }
        } else {
            // Storage unprovisioned or unavailable, generate compact data URL
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Result.success("data:image/jpeg;base64,$base64")
        }
    }

    /**
     * Uploads a voice note audio file to Firebase Storage.
     * If Firebase Storage is unavailable or unprovisioned, returns a compact base64 data URL.
     */
    suspend fun uploadVoiceNoteFile(
        file: File,
        chatId: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val mime = if (file.name.endsWith(".wav", ignoreCase = true)) "audio/wav" else "audio/mp4"
        val storageRef = if (isStorageProvisioned) storage?.reference else null
        val fileName = "voice_${UUID.randomUUID().toString().take(12)}_${file.name}"
        val path = if (chatId.isNotBlank()) "chats/$chatId/voice_notes/$fileName" else "voice_notes/$fileName"

        if (storageRef != null) {
            try {
                val metadata = StorageMetadata.Builder()
                    .setContentType(mime)
                    .build()
                val uploadTask = storageRef.child(path).putFile(Uri.fromFile(file), metadata).await()
                val downloadUrl = uploadTask.storage.downloadUrl.await().toString()
                return@withContext Result.success(downloadUrl)
            } catch (e: Exception) {
                isStorageProvisioned = false
                Log.w(TAG, "Firebase Storage voice note upload error, using resilient data URL fallback: ${e.message}")
            }
        }
        return@withContext try {
            val bytes = file.readBytes()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Result.success("data:$mime;base64,$base64")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Uploads a lesson photo/diagram from an Android content Uri so students on all devices can view it.
     * Falls back to a compact compressed JPEG data URL (max 900px, 65% quality) if Cloud Storage is unprovisioned.
     */
    suspend fun uploadLessonImage(
        uri: Uri,
        context: Context,
        classId: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val stream: InputStream? = context.contentResolver.openInputStream(uri)
            val bytes = stream?.use { it.readBytes() }
                ?: return@withContext Result.failure(Exception("Cannot read selected image"))

            val compressedBytes = compressImageBytes(bytes, maxDimension = 900, quality = 65)
            val finalName = "lesson_img_${UUID.randomUUID()}.jpg"
            val path = if (classId.isNotBlank()) "lessons/$classId/images/$finalName" else "lessons/images/$finalName"

            uploadBytesToStorage(compressedBytes, path)
        } catch (e: Exception) {
            logE("Failed to upload lesson image from Uri: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Uploads a lesson whiteboard/camera File so students on all devices can view it.
     */
    suspend fun uploadLessonImageFile(
        file: File,
        classId: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) {
                return@withContext Result.failure(Exception("Image file does not exist"))
            }
            val bytes = file.readBytes()
            val compressedBytes = compressImageBytes(bytes, maxDimension = 900, quality = 65)
            val finalName = "lesson_wb_${UUID.randomUUID()}.jpg"
            val path = if (classId.isNotBlank()) "lessons/$classId/images/$finalName" else "lessons/images/$finalName"

            uploadBytesToStorage(compressedBytes, path)
        } catch (e: Exception) {
            logE("Failed to upload lesson image file: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Uploads a lesson attachment file (PDF, slides, worksheet, doc) to Cloud Storage, or embeds
     * compact files (<= 350 KB) as a base64 data URL so students on any device can open it.
     */
    suspend fun uploadLessonFile(
        uri: Uri,
        context: Context,
        fileName: String,
        classId: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(Exception("Unable to read selected file"))

            val storageRef = if (isStorageProvisioned) storage?.reference else null
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val path = if (classId.isNotBlank()) {
                "lessons/$classId/files/${UUID.randomUUID().toString().take(8)}_$safeName"
            } else {
                "lessons/files/${UUID.randomUUID().toString().take(8)}_$safeName"
            }

            if (storageRef != null) {
                try {
                    val metadata = StorageMetadata.Builder()
                        .setContentType(mime)
                        .build()
                    val uploadTask = storageRef.child(path).putBytes(bytes, metadata).await()
                    val downloadUrl = uploadTask.storage.downloadUrl.await().toString()
                    return@withContext Result.success(downloadUrl)
                } catch (e: Exception) {
                    isStorageProvisioned = false
                    logW("Firebase Storage file upload fallback: ${e.message}")
                }
            }

            // Without Cloud Storage, Firestore document size is capped at 1 MiB.
            // Keep inline base64 attachments safely <= 350 KB (~470 KB base64).
            if (bytes.size > 350 * 1024) {
                return@withContext Result.failure(
                    Exception("File is ${(bytes.size / 1024)} KB. Files over 350 KB require Cloud Storage enabled; please attach a smaller PDF/doc or share a link.")
                )
            }
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Result.success("data:$mime;base64,$base64")
        } catch (e: Exception) {
            logE("Failed to upload lesson file: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun compressImageBytes(bytes: ByteArray, maxDimension: Int = 1024, quality: Int = 75): ByteArray {
        return try {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
            val width = bitmap.width
            val height = bitmap.height

            val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                val ratio = width.toFloat() / height.toFloat()
                val (newWidth, newHeight) = if (width > height) {
                    maxDimension to (maxDimension / ratio).toInt()
                } else {
                    (maxDimension * ratio).toInt() to maxDimension
                }
                val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                bitmap.recycle()
                scaled
            } else {
                bitmap
            }

            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            scaledBitmap.recycle()
            baos.toByteArray()
        } catch (e: Exception) {
            Log.w(TAG, "Error compressing image bytes: ${e.message}")
            bytes
        }
    }
}
