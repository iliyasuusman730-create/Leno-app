package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.sqrt

class AudioRecorderHelper(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var mediaRecorder: MediaRecorder? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private var currentOutputFile: File? = null
    private var isRecording = false
    private var recordingStartTime = 0L
    private var selectedSampleRate = 44100
    private var selectedChannels = 1
    private var totalPcmBytesWritten = 0L
    private var maxRecordedAmplitude = 0

    private val _liveAmplitude = MutableStateFlow(0f)
    val liveAmplitude: StateFlow<Float> = _liveAmplitude.asStateFlow()

    fun startRecording(): Result<File> {
        // 1. Check microphone permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return Result.failure(SecurityException("Microphone permission (RECORD_AUDIO) is not granted."))
        }

        // 2. Stop any active recording and release hardware
        stopRecordingInternal(discard = true)

        // 3. Reset audio manager state to release mic from calls/VoIP
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isMicrophoneMute = false
        } catch (e: Exception) {
            Log.w("AudioRecorderHelper", "Failed to reset AudioManager: ${e.message}")
        }

        val voiceNotesDir = File(context.filesDir, "voice_notes")
        if (!voiceNotesDir.exists()) {
            voiceNotesDir.mkdirs()
        }

        // 4. Try MediaRecorder (AAC / MPEG-4) as primary native voice engine
        val m4aFile = File(voiceNotesDir, "voice_${System.currentTimeMillis()}.m4a")
        currentOutputFile = m4aFile
        val mediaRecorderSuccess = tryStartMediaRecorder(m4aFile)
        if (mediaRecorderSuccess) {
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            return Result.success(m4aFile)
        }

        // 5. Fallback to AudioRecord (PCM 16-bit to standard WAV) if MediaRecorder failed
        val wavFile = File(voiceNotesDir, "voice_${System.currentTimeMillis()}.wav")
        currentOutputFile = wavFile
        totalPcmBytesWritten = 0L
        maxRecordedAmplitude = 0

        val audioRecordSuccess = tryStartAudioRecord(wavFile)
        if (audioRecordSuccess) {
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            return Result.success(wavFile)
        }

        stopRecordingInternal(discard = true)
        return Result.failure(Exception("Unable to initialize device microphone."))
    }

    @SuppressLint("MissingPermission")
    private fun tryStartAudioRecord(outputFile: File): Boolean {
        val sampleRates = intArrayOf(16000, 44100, 8000)
        val audioSources = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        )

        for (source in audioSources) {
            for (rate in sampleRates) {
                try {
                    val channelConfig = AudioFormat.CHANNEL_IN_MONO
                    val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                    val minBufSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat)
                    if (minBufSize <= 0) continue

                    val bufferSize = (minBufSize * 2).coerceAtLeast(4096)
                    val record = AudioRecord(
                        source,
                        rate,
                        channelConfig,
                        audioFormat,
                        bufferSize
                    )

                    if (record.state != AudioRecord.STATE_INITIALIZED) {
                        record.release()
                        continue
                    }

                    record.startRecording()
                    if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop()
                        record.release()
                        continue
                    }

                    audioRecord = record
                    selectedSampleRate = rate
                    selectedChannels = 1

                    // Launch background writer thread
                    startPcmWavWriter(outputFile, record, bufferSize, rate, selectedChannels)
                    return true
                } catch (e: Exception) {
                    Log.w("AudioRecorderHelper", "AudioRecord init attempt failed (src=$source, rate=$rate): ${e.message}")
                }
            }
        }
        return false
    }

    private fun startPcmWavWriter(
        outputFile: File,
        record: AudioRecord,
        bufferSize: Int,
        sampleRate: Int,
        channels: Int
    ) {
        recordingJob = scope.launch {
            var fos: FileOutputStream? = null
            try {
                fos = FileOutputStream(outputFile)
                // Write placeholder 44-byte WAV header (will update total sizes upon stop)
                writeWavHeader(fos, sampleRate, channels, 0)

                val audioBuffer = ShortArray(bufferSize / 2)
                val byteBuffer = ByteArray(bufferSize)

                while (isActive && isRecording) {
                    val readShorts = record.read(audioBuffer, 0, audioBuffer.size)
                    if (readShorts > 0) {
                        var sumSquares = 0.0
                        var peak = 0

                        for (i in 0 until readShorts) {
                            val sample = audioBuffer[i]
                            val absVal = abs(sample.toInt())
                            if (absVal > peak) peak = absVal
                            sumSquares += (sample * sample).toDouble()

                            // Convert 16-bit short to Little-Endian bytes
                            byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                        }

                        val bytesToWrite = readShorts * 2
                        fos.write(byteBuffer, 0, bytesToWrite)
                        totalPcmBytesWritten += bytesToWrite
                        if (peak > maxRecordedAmplitude) maxRecordedAmplitude = peak

                        // Calculate normalized RMS amplitude (0.0 to 1.0)
                        val rms = sqrt(sumSquares / readShorts)
                        val normalized = (rms / 8000.0).toFloat().coerceIn(0.05f, 1.0f)
                        _liveAmplitude.value = normalized
                    }
                }
                fos.flush()
            } catch (e: Exception) {
                Log.e("AudioRecorderHelper", "Error in PCM recording stream: ${e.message}", e)
            } finally {
                try {
                    fos?.close()
                } catch (_: Exception) {}
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryStartMediaRecorder(outputFile: File): Boolean {
        val audioSources = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.CAMCORDER
        )
        val sampleRates = intArrayOf(16000, 44100)

        for (source in audioSources) {
            for (rate in sampleRates) {
                var mr: MediaRecorder? = null
                try {
                    @Suppress("DEPRECATION")
                    mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MediaRecorder(context)
                    } else {
                        MediaRecorder()
                    }

                    mr.setAudioSource(source)
                    mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    mr.setAudioEncodingBitRate(32000)
                    mr.setAudioSamplingRate(rate)
                    mr.setOutputFile(outputFile.absolutePath)

                    mr.prepare()
                    mr.start()

                    mediaRecorder = mr
                    startAmplitudeTracker(mr)
                    return true
                } catch (e: Exception) {
                    Log.w("AudioRecorderHelper", "MediaRecorder source $source rate $rate failed: ${e.message}")
                    try {
                        mr?.reset()
                        mr?.release()
                    } catch (_: Exception) {}
                }
            }
        }
        return false
    }

    private fun startAmplitudeTracker(mr: MediaRecorder) {
        recordingJob?.cancel()
        recordingJob = scope.launch(Dispatchers.Default) {
            while (isActive && isRecording) {
                try {
                    val maxAmp = mr.maxAmplitude
                    val norm = (maxAmp / 32767f).coerceIn(0.05f, 1.0f)
                    _liveAmplitude.value = norm
                } catch (_: Exception) {}
                delay(100)
            }
        }
    }

    fun stopRecording(): Result<File> {
        if (!isRecording) {
            return Result.failure(IllegalStateException("No active audio recording in progress."))
        }

        val file = currentOutputFile
        val elapsed = System.currentTimeMillis() - recordingStartTime
        isRecording = false

        // Stop coroutine & release AudioRecord
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.w("AudioRecorderHelper", "Error stopping AudioRecord: ${e.message}")
        } finally {
            audioRecord = null
        }

        // Stop MediaRecorder if used
        try {
            mediaRecorder?.let {
                it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.w("AudioRecorderHelper", "Error stopping MediaRecorder: ${e.message}")
        } finally {
            mediaRecorder = null
        }

        _liveAmplitude.value = 0f

        if (file == null || !file.exists()) {
            return Result.failure(Exception("Recorded audio file was not saved."))
        }

        // For WAV files, update the 44-byte WAV header with final PCM byte length
        if (file.name.endsWith(".wav", ignoreCase = true)) {
            updateWavHeader(file, selectedSampleRate, selectedChannels, totalPcmBytesWritten)
        }

        if (file.length() <= 44L || elapsed < 300) {
            file.delete()
            return Result.failure(Exception("Audio recording was too short."))
        }

        return Result.success(file)
    }

    fun cancelRecording() {
        stopRecordingInternal(discard = true)
    }

    private fun stopRecordingInternal(discard: Boolean) {
        isRecording = false
        _liveAmplitude.value = 0f
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (_: Exception) {} finally {
            audioRecord = null
        }

        try {
            mediaRecorder?.let {
                it.stop()
                it.release()
            }
        } catch (_: Exception) {} finally {
            mediaRecorder = null
        }

        if (discard) {
            currentOutputFile?.let {
                if (it.exists()) it.delete()
            }
            currentOutputFile = null
        }
    }

    fun getMaxAmplitude(): Int {
        return if (maxRecordedAmplitude > 0) {
            maxRecordedAmplitude
        } else {
            try {
                mediaRecorder?.maxAmplitude ?: 0
            } catch (_: Exception) {
                0
            }
        }
    }

    // =========================================================================
    // Standard Canonical 44-byte PCM RIFF WAV Header Utilities
    // =========================================================================

    private fun writeWavHeader(
        out: FileOutputStream,
        sampleRate: Int,
        channels: Int,
        totalPcmBytes: Long
    ) {
        val header = createWavHeaderBytes(sampleRate, channels, totalPcmBytes)
        out.write(header, 0, 44)
    }

    private fun updateWavHeader(
        wavFile: File,
        sampleRate: Int,
        channels: Int,
        totalPcmBytes: Long
    ) {
        try {
            val pcmLength = if (totalPcmBytes > 0) totalPcmBytes else (wavFile.length() - 44).coerceAtLeast(0)
            val header = createWavHeaderBytes(sampleRate, channels, pcmLength)
            RandomAccessFile(wavFile, "rw").use { raf ->
                raf.seek(0)
                raf.write(header, 0, 44)
            }
        } catch (e: Exception) {
            Log.e("AudioRecorderHelper", "Failed to update WAV header: ${e.message}", e)
        }
    }

    private fun createWavHeaderBytes(
        sampleRate: Int,
        channels: Int,
        totalPcmBytes: Long
    ): ByteArray {
        val totalDataLen = totalPcmBytes + 36
        val byteRate = (sampleRate * channels * 16 / 8).toLong()
        val header = ByteArray(44)

        // RIFF/WAVE header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // 'fmt ' chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // Subchunk1Size = 16 for PCM
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // AudioFormat = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte() // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0

        // 'data' chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalPcmBytes and 0xff).toByte()
        header[41] = ((totalPcmBytes shr 8) and 0xff).toByte()
        header[42] = ((totalPcmBytes shr 16) and 0xff).toByte()
        header[43] = ((totalPcmBytes shr 24) and 0xff).toByte()

        return header
    }

    companion object {
        fun verifyAudioFile(file: File?): Boolean {
            if (file == null || !file.exists() || file.length() <= 0L) return false
            if (file.length() <= 44L && file.name.endsWith(".wav", ignoreCase = true)) return false
            return try {
                RandomAccessFile(file, "r").use { raf ->
                    val header = ByteArray(12.coerceAtMost(file.length().toInt()))
                    raf.read(header)
                    if (file.name.endsWith(".wav", ignoreCase = true)) {
                        header[0] == 'R'.code.toByte() &&
                                header[1] == 'I'.code.toByte() &&
                                header[2] == 'F'.code.toByte() &&
                                header[3] == 'F'.code.toByte()
                    } else {
                        file.length() > 32L
                    }
                }
            } catch (e: Exception) {
                false
            }
        }
    }
}
