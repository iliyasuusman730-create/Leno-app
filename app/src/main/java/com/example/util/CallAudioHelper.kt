package com.example.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class CallAudioHelper(private val context: Context) {
    private var toneGenerator: ToneGenerator? = null
    private var isRinging = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun startOutgoingRing() {
        stopRinging()
        isRinging = true
        Thread {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70)
                while (isRinging) {
                    toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1000)
                    Thread.sleep(3000)
                }
            } catch (e: Exception) {
                Log.e("CallAudioHelper", "Error playing ring tone: ${e.message}")
            }
        }.start()
    }

    fun startIncomingRing() {
        stopRinging()
        isRinging = true
        Thread {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_RING, 85)
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    manager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                while (isRinging) {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_NETWORK_USA_RINGBACK, 1500)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 300, 500), -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(longArrayOf(0, 500, 300, 500), -1)
                    }
                    Thread.sleep(3500)
                }
            } catch (e: Exception) {
                Log.e("CallAudioHelper", "Error playing incoming ring: ${e.message}")
            }
        }.start()
    }

    fun playConnectedTone() {
        stopRinging()
        try {
            val gen = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
            gen.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            Thread {
                Thread.sleep(300)
                gen.release()
            }.start()
        } catch (e: Exception) {
            Log.e("CallAudioHelper", "Error playing connect tone: ${e.message}")
        }
    }

    fun playEndCallTone() {
        stopRinging()
        try {
            val gen = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
            gen.startTone(ToneGenerator.TONE_PROP_PROMPT, 300)
            Thread {
                Thread.sleep(400)
                gen.release()
            }.start()
        } catch (e: Exception) {
            Log.e("CallAudioHelper", "Error playing end call tone: ${e.message}")
        }
    }

    fun setSpeakerphoneOn(on: Boolean) {
        try {
            audioManager?.mode = if (on) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = on
        } catch (e: Exception) {
            Log.e("CallAudioHelper", "Error toggling speaker: ${e.message}")
        }
    }

    fun setMicrophoneMute(muted: Boolean) {
        try {
            audioManager?.isMicrophoneMute = muted
        } catch (e: Exception) {
            Log.e("CallAudioHelper", "Error toggling mic: ${e.message}")
        }
    }

    fun stopRinging() {
        isRinging = false
        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.e("CallAudioHelper", "Error stopping ring: ${e.message}")
        }
    }

    fun resetAudio() {
        stopRinging()
        try {
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = false
            audioManager?.isMicrophoneMute = false
        } catch (e: Exception) {
            Log.e("CallAudioHelper", "Error resetting audio: ${e.message}")
        }
    }
}
