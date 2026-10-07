package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.LenoRepository
import com.example.data.entity.UserEntity
import com.example.util.AudioPlayerHelper
import com.example.util.AudioRecorderHelper
import com.example.viewmodel.CallState
import com.example.viewmodel.LenoViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioAndCallSystemVerificationTest {

    private lateinit var app: Application
    private lateinit var shadowApp: ShadowApplication
    private lateinit var db: AppDatabase
    private lateinit var repository: LenoRepository

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        shadowApp = shadowOf(app)
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LenoRepository(db, app)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // =========================================================================
    // 1. MICROPHONE PERMISSION & RECORDER INITIALIZATION VERIFICATION
    // =========================================================================

    @Test
    fun testAudioRecording_FailsWhenMicrophonePermissionDenied() {
        // Deny RECORD_AUDIO permission
        shadowApp.denyPermissions(Manifest.permission.RECORD_AUDIO)

        val helper = AudioRecorderHelper(app)
        val result = helper.startRecording()

        assertTrue("Recording must fail if RECORD_AUDIO permission is not granted", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Exception must be SecurityException", exception is SecurityException)
        assertTrue(
            "Exception message must mention permission",
            exception?.message?.contains("permission", ignoreCase = true) == true
        )
    }

    @Test
    fun testAudioRecording_MitigatesWebRTCInterferenceByResettingAudioManager() {
        val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Simulate active WebRTC VoIP mode and muted mic
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isMicrophoneMute = true

        shadowApp.grantPermissions(Manifest.permission.RECORD_AUDIO)

        val helper = AudioRecorderHelper(app)
        // startRecording() must reset AudioManager mode to NORMAL and un-mute mic
        val result = helper.startRecording()

        assertEquals("AudioManager mode must be reset to MODE_NORMAL for recording", AudioManager.MODE_NORMAL, audioManager.mode)
        assertFalse("Microphone mute must be disabled before recording", audioManager.isMicrophoneMute)

        helper.cancelRecording()
    }

    // =========================================================================
    // 2. AUDIO FILE TRACK & NON-ZERO AUDIO DATA VERIFICATION
    // =========================================================================

    @Test
    fun testAudioFileVerification_RejectsZeroByteOrCorruptedFiles() {
        val tempDir = File(app.cacheDir, "audio_validation_test")
        tempDir.mkdirs()

        // 1. Non-existent file
        val nonExistent = File(tempDir, "missing.m4a")
        assertFalse("Non-existent file must fail verification", AudioRecorderHelper.verifyAudioFile(nonExistent))

        // 2. 0-byte empty file
        val emptyFile = File(tempDir, "empty.m4a")
        emptyFile.createNewFile()
        assertEquals(0L, emptyFile.length())
        assertFalse("0-byte audio file must fail verification", AudioRecorderHelper.verifyAudioFile(emptyFile))

        // 3. Valid non-empty audio payload (M4A)
        val validFile = File(tempDir, "valid_audio.m4a")
        FileOutputStream(validFile).use { fos ->
            // Write standard MPEG-4 / AAC audio header and data frames
            val fakeM4aBytes = byteArrayOf(
                0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70, // ftyp box
                0x4D, 0x34, 0x41, 0x20, 0x00, 0x00, 0x00, 0x00, // M4A brand
                0x6D, 0x70, 0x34, 0x32, 0x69, 0x73, 0x6F, 0x6D, // compatible brands
                0x00, 0x00, 0x00, 0x08, 0x66, 0x72, 0x65, 0x65  // free atom
            )
            fos.write(fakeM4aBytes)
            fos.write(ByteArray(1024) { (it % 255).toByte() })
        }
        assertTrue("Valid audio file must exist", validFile.exists())
        assertTrue("Valid audio file must have non-zero size", validFile.length() > 0)
        assertTrue("Audio verification helper must confirm non-empty file with audio track", AudioRecorderHelper.verifyAudioFile(validFile))

        // 4. Valid canonical PCM WAV file
        val validWav = File(tempDir, "valid_audio.wav")
        FileOutputStream(validWav).use { fos ->
            val wavHeader = byteArrayOf(
                'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
                0x00, 0x04, 0x00, 0x00,
                'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(),
                'f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte(),
                16, 0, 0, 0,
                1, 0, // PCM
                1, 0, // 1 channel
                0x44, 0xAC.toByte(), 0x00, 0x00, // 44100 Hz
                0x88.toByte(), 0x58, 0x01, 0x00, // byte rate
                2, 0, 16, 0,
                'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(),
                0x00, 0x04, 0x00, 0x00
            )
            fos.write(wavHeader)
            fos.write(ByteArray(1024) { 0x40 })
        }
        assertTrue("WAV file must be recognized by verifyAudioFile", AudioRecorderHelper.verifyAudioFile(validWav))

        // Clean up
        emptyFile.delete()
        validFile.delete()
        validWav.delete()
    }

    @Test
    fun testAudioPlayerHelper_InitializesAndStopsGracefully() {
        val player = AudioPlayerHelper(app)
        assertFalse("Player must not be playing initially", player.isPlaying)

        var errorReported = false
        player.playAudio(
            filePathOrUri = "/non_existent_audio_file.m4a",
            onProgress = { _, _ -> },
            onCompletion = { },
            onError = { errorReported = true }
        )

        assertTrue("Player must report error on non-existent audio source", errorReported)
        assertFalse("Player must not be playing after error", player.isPlaying)

        player.stop()
        assertFalse("Player must be stopped", player.isPlaying)
    }

    // =========================================================================
    // 3. VOICE MESSAGE INTEGRATION IN REPOSITORY & DATABASE
    // =========================================================================

    @Test
    fun testVoiceNoteMessage_PersistsAudioMetadataInDatabase() {
        runBlocking {
            // Register sender and receiver
            val sender = repository.registerWithLino("Sender User", "sender_user", "Pass1234").getOrThrow()
            val receiver = repository.registerWithLino("Receiver User", "receiver_user", "Pass5678").getOrThrow()

            val voiceFile = File(app.filesDir, "voice_notes/voice_test_123.m4a")
            voiceFile.parentFile?.mkdirs()
            FileOutputStream(voiceFile).use { fos ->
                fos.write("TEST_AAC_AUDIO_STREAM_DATA".toByteArray())
            }

            // Send Voice Note Message
            repository.sendMessage(
                senderId = sender.userId,
                receiverId = receiver.userId,
                text = "🎤 Voice Note (0:12)",
                audioUrl = voiceFile.absolutePath,
                audioDurationSeconds = 12
            )

            // Retrieve messages from Room DB and verify audio metadata persists
            val messages = db.messageDao().getMessagesBetweenUsers(sender.userId, receiver.userId).first()
            val retrievedMsg = messages.find { it.audioUrl == voiceFile.absolutePath }

            assertNotNull("Retrieved message must exist in database", retrievedMsg)
            assertEquals(voiceFile.absolutePath, retrievedMsg?.audioUrl)
            assertEquals(12, retrievedMsg?.audioDurationSeconds)
            assertTrue("Audio file referenced by message must exist on disk", File(retrievedMsg!!.audioUrl!!).exists())

            voiceFile.delete()
        }
    }

    // =========================================================================
    // 4. CALL STATE MACHINE: NEVER CONNECTS INACTIVE/OFFLINE USERS
    // =========================================================================

    @Test
    fun testCall_NeverAutoConnects_AndRejectsOfflineRecipient() {
        runBlocking {
            val caller = repository.registerWithLino("Caller User", "caller_user", "Pass1234").getOrThrow()
            val viewModel = LenoViewModel(app)

            val offlineUser = UserEntity(
                userId = "usr_offline_recipient",
                username = "offline_user",
                displayName = "Offline Recipient",
                bio = "Offline Bio",
                avatarUrl = "",
                passwordHash = "hash",
                isOnline = false
            )

            // 1. Call offline user
            viewModel.startVoiceCall(offlineUser)

            val callState = viewModel.callState.value
            assertTrue("Call state must be Outgoing", callState is CallState.Outgoing)
            val outgoing = callState as CallState.Outgoing
            assertTrue("Status must indicate user is offline", outgoing.statusText.contains("offline", ignoreCase = true))
            assertFalse("Cannot simulate answer for offline user", outgoing.canSimulateAnswer)
            assertFalse("Must never enter Connected state", viewModel.callState.value is CallState.Connected)

            viewModel.endCall()
            assertTrue("Must return to Idle", viewModel.callState.value is CallState.Idle)
        }
    }

    @Test
    fun testCall_OnlineRecipient_ConnectsOnlyWhenAnswered() {
        runBlocking {
            val caller = repository.registerWithLino("Caller User 2", "caller_user_2", "Pass1234").getOrThrow()
            val viewModel = LenoViewModel(app)

            val onlineUser = UserEntity(
                userId = "usr_online_recipient",
                username = "online_user",
                displayName = "Online Recipient",
                bio = "Online Bio",
                avatarUrl = "",
                passwordHash = "hash",
                isOnline = true
            )

            // 1. Initiate call
            viewModel.startVoiceCall(onlineUser)
            val state1 = viewModel.callState.value
            assertTrue("Call state must be Outgoing", state1 is CallState.Outgoing)
            assertEquals("Calling...", (state1 as CallState.Outgoing).statusText)
            assertFalse("Must not be connected yet", viewModel.callState.value is CallState.Connected)

            // 2. Explicitly accept / answer
            viewModel.answerCallForTesting()
            val state2 = viewModel.callState.value
            assertTrue("Call must be Connected only after explicit answer", state2 is CallState.Connected)
            val connected = state2 as CallState.Connected
            assertEquals(onlineUser.userId, connected.partner.userId)
            assertEquals(0, connected.durationSeconds)

            // 3. End call
            viewModel.endCall()
            assertTrue("Call must be Idle after hanging up", viewModel.callState.value is CallState.Idle)
        }
    }
}
