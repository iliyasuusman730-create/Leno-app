package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioPlayerHelper(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var pcmPlaybackJob: Job? = null
    private var activeAudioTrack: AudioTrack? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    var isPlaying: Boolean = false
        private set

    fun playAudio(
        filePathOrUri: String?,
        expectedDurationSeconds: Int = 10,
        onProgress: (currentPositionMs: Int, totalDurationMs: Int) -> Unit,
        onCompletion: () -> Unit,
        onError: (String) -> Unit
    ) {
        stop()

        if (filePathOrUri.isNullOrBlank()) {
            onError("Audio file path is empty.")
            onCompletion()
            return
        }

        val resolvedPath = resolveAudioSource(filePathOrUri)

        val isRemoteOrUri = resolvedPath.startsWith("http://") ||
                resolvedPath.startsWith("https://") ||
                resolvedPath.startsWith("content://")

        val file = if (!isRemoteOrUri) File(resolvedPath) else null

        if (file != null && (!file.exists() || file.length() <= 44L)) {
            Log.w("AudioPlayerHelper", "Audio file does not exist or has no audio payload: $resolvedPath")
            onError("Audio file is empty or missing.")
            onCompletion()
            return
        }

        // Try playing via MediaPlayer first
        playFromMediaPlayer(
            filePathOrUri = resolvedPath,
            onProgress = onProgress,
            onCompletion = onCompletion,
            onError = { mediaErr ->
                // If MediaPlayer fails on a local WAV file, fall back to direct AudioTrack PCM stream
                if (file != null && file.exists() && file.name.endsWith(".wav", ignoreCase = true)) {
                    Log.i("AudioPlayerHelper", "MediaPlayer failed on WAV ($mediaErr). Falling back to direct AudioTrack PCM player...")
                    playWavFileDirectly(file, onProgress, onCompletion, onError)
                } else {
                    onError(mediaErr)
                    onCompletion()
                }
            }
        )
    }

    private fun resolveAudioSource(input: String): String {
        if (input.startsWith("data:audio/")) {
            return try {
                val commaIndex = input.indexOf(',')
                if (commaIndex != -1) {
                    val base64Data = input.substring(commaIndex + 1)
                    val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                    val ext = if (input.startsWith("data:audio/wav")) "wav" else "m4a"
                    val safeHash = input.hashCode().toString().replace("-", "n")
                    val cacheFile = File(context.cacheDir, "cached_voice_$safeHash.$ext")
                    if (!cacheFile.exists() || cacheFile.length() != bytes.size.toLong()) {
                        cacheFile.writeBytes(bytes)
                    }
                    cacheFile.absolutePath
                } else input
            } catch (e: Exception) {
                Log.w("AudioPlayerHelper", "Failed to decode base64 audio: ${e.message}")
                input
            }
        }
        return input
    }

    private fun playFromMediaPlayer(
        filePathOrUri: String,
        onProgress: (currentPositionMs: Int, totalDurationMs: Int) -> Unit,
        onCompletion: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                if (filePathOrUri.startsWith("http://") || filePathOrUri.startsWith("https://") || filePathOrUri.startsWith("content://")) {
                    setDataSource(context, Uri.parse(filePathOrUri))
                } else {
                    val file = File(filePathOrUri)
                    setDataSource(file.absolutePath)
                }

                setVolume(1.0f, 1.0f)
                prepare()
                start()
            }

            mediaPlayer = player
            isPlaying = true

            startProgressTracker(onProgress)

            player.setOnCompletionListener {
                stopProgressTracker()
                isPlaying = false
                stop()
                onCompletion()
            }

            player.setOnErrorListener { _, what, extra ->
                stopProgressTracker()
                isPlaying = false
                stop()
                onError("Playback error: what=$what extra=$extra")
                true
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerHelper", "MediaPlayer failed: ${e.message}", e)
            isPlaying = false
            stop()
            onError("MediaPlayer failed: ${e.message}")
        }
    }

    private fun playWavFileDirectly(
        wavFile: File,
        onProgress: (currentPositionMs: Int, totalDurationMs: Int) -> Unit,
        onCompletion: () -> Unit,
        onError: (String) -> Unit
    ) {
        isPlaying = true
        pcmPlaybackJob = scope.launch {
            withContext(Dispatchers.IO) {
                var fis: FileInputStream? = null
                try {
                    fis = FileInputStream(wavFile)
                    val header = ByteArray(44)
                    val headerRead = fis.read(header)
                    if (headerRead < 44) {
                        withContext(Dispatchers.Main) {
                            onError("Corrupted WAV header")
                            onCompletion()
                        }
                        return@withContext
                    }

                    val channels = (header[22].toInt() and 0xFF) or ((header[23].toInt() and 0xFF) shl 8)
                    val sampleRate = ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    val bitsPerSample = (header[34].toInt() and 0xFF) or ((header[35].toInt() and 0xFF) shl 8)

                    val channelConfig = if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
                    val audioFormat = if (bitsPerSample == 8) AudioFormat.ENCODING_PCM_8BIT else AudioFormat.ENCODING_PCM_16BIT
                    val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(1)
                    val bytesPerSecond = sampleRate * channels * bytesPerSample

                    val totalPcmBytes = (wavFile.length() - 44).coerceAtLeast(0)
                    val totalDurationMs = if (bytesPerSecond > 0) ((totalPcmBytes * 1000) / bytesPerSecond).toInt() else 1000

                    val minBufSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                    val audioTrack = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(audioFormat)
                                .setSampleRate(sampleRate)
                                .setChannelMask(channelConfig)
                                .build()
                        )
                        .setBufferSizeInBytes((minBufSize * 2).coerceAtLeast(4096))
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()

                    activeAudioTrack = audioTrack
                    audioTrack.setVolume(1.0f)
                    audioTrack.play()

                    val buffer = ByteArray(4096)
                    var bytesPlayed = 0L

                    while (isActive && isPlaying) {
                        val read = fis.read(buffer)
                        if (read <= 0) break

                        audioTrack.write(buffer, 0, read)
                        bytesPlayed += read

                        val currentPosMs = if (bytesPerSecond > 0) ((bytesPlayed * 1000) / bytesPerSecond).toInt() else 0
                        withContext(Dispatchers.Main) {
                            onProgress(currentPosMs, totalDurationMs)
                        }
                    }

                    audioTrack.stop()
                    audioTrack.release()
                    activeAudioTrack = null
                } catch (e: Exception) {
                    Log.e("AudioPlayerHelper", "Direct WAV PCM playback error: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        onError("Direct WAV playback failed: ${e.message}")
                    }
                } finally {
                    try {
                        fis?.close()
                    } catch (_: Exception) {}
                }
            }

            isPlaying = false
            onCompletion()
        }
    }

    private fun startProgressTracker(onProgress: (currentPositionMs: Int, totalDurationMs: Int) -> Unit) {
        stopProgressTracker()
        progressJob = scope.launch {
            while (isActive && isPlaying) {
                try {
                    val player = mediaPlayer
                    if (player != null && player.isPlaying) {
                        val current = player.currentPosition
                        val duration = player.duration.coerceAtLeast(1)
                        onProgress(current, duration)
                    }
                } catch (e: Exception) {
                    // Ignore transient status errors during teardown
                }
                delay(80)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
            isPlaying = false
            stopProgressTracker()
            pcmPlaybackJob?.cancel()
            activeAudioTrack?.pause()
        } catch (e: Exception) {
            Log.w("AudioPlayerHelper", "Error pausing player: ${e.message}")
        }
    }

    fun resume() {
        try {
            mediaPlayer?.let {
                if (!it.isPlaying) {
                    it.start()
                    isPlaying = true
                }
            }
        } catch (e: Exception) {
            Log.w("AudioPlayerHelper", "Error resuming player: ${e.message}")
        }
    }

    fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun getDuration(): Int {
        return try {
            mediaPlayer?.duration ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
        } catch (e: Exception) {
            Log.w("AudioPlayerHelper", "Error seeking player: ${e.message}")
        }
    }

    fun stop() {
        stopProgressTracker()
        pcmPlaybackJob?.cancel()
        pcmPlaybackJob = null

        try {
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
        } catch (e: Exception) {
            // Ignore
        }
        activeAudioTrack = null

        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {
            // Ignore
        } finally {
            try {
                mediaPlayer?.release()
            } catch (e: Exception) {
                // Ignore
            }
            mediaPlayer = null
            isPlaying = false
        }
    }
}
