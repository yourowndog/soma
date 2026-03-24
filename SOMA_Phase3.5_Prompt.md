# SOMA Phase 3.5: Screen Reading + Wake Word + Voice Foundations

## Difficulty: Hard — Sonnet recommended
## Prerequisite: Phase 3 complete (foreground service running, tools working)
## This phase replaces two standalone apps (ScreenSense + Wake Word) with
## native SOMA capabilities. We are REWRITING the concepts, not porting code.

---

## Context

Sam has two existing apps:
1. **Wake Word App** — Porcupine "Wake up Soma" detection → 5s recording →
   OpenAI Whisper transcription → sends to OpenClaw via WebSocket
2. **ScreenSense** — Accessibility service + OCR that reads screen content
   aloud via TTS (Kokoro/Piper local, OpenAI TTS cloud)

Both apps work but have issues. Wake Word has a rigid 5s recording window
and crashes occasionally. ScreenSense eats 1.4GB RAM, requires repeated
permission grants for screen sharing, and needs optimization.

We are NOT integrating their code. We are rebuilding their functionality
inside SOMA using SOMA's existing architecture (ToolRegistry, foreground
service, Compose UI).

---

## Task 3.5a: Wake Word Detection Service

### What to build

Integrate Porcupine wake word detection directly into SOMA's existing
foreground service (SomaService).

### Add dependency
```kotlin
implementation("ai.picovoice:porcupine-android:4.0.0")
```

### Create file: `voice/WakeWordDetector.kt`

```kotlin
package io.brokentooth.soma.voice

import android.content.Context
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import timber.log.Timber

/**
 * Wraps Porcupine SDK for "Wake up Soma" detection.
 * Runs inside SomaService's foreground service.
 *
 * When wake word is detected, calls the provided callback.
 * The callback triggers STT recording → transcription → agent processing.
 */
class WakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit
) {
    private var porcupineManager: PorcupineManager? = null

    /**
     * Initialize and start listening.
     *
     * @param accessKey Picovoice access key (from BuildConfig or settings)
     * @param modelPath Path to custom "wake_up_soma.ppn" model file.
     *                  Store in app assets or external files dir.
     *                  Sam already has this file from his existing app.
     */
    fun start(accessKey: String, modelPath: String) {
        try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath(modelPath)
                .setSensitivity(0.5f)
                .build(context, PorcupineManagerCallback { keywordIndex ->
                    Timber.i("🎤 Wake word detected! (index: $keywordIndex)")
                    onWakeWordDetected()
                })
            porcupineManager?.start()
            Timber.i("🎤 Wake word detector started")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start wake word detector")
        }
    }

    fun stop() {
        porcupineManager?.stop()
        porcupineManager?.delete()
        porcupineManager = null
    }

    fun isListening(): Boolean = porcupineManager != null
}
```

### Wire into SomaService

In `SomaService.kt`, add wake word detection:

```kotlin
private var wakeWordDetector: WakeWordDetector? = null

// In onCreate(), after existing foreground notification setup:
wakeWordDetector = WakeWordDetector(this) {
    // Wake word detected! Start voice interaction.
    onWakeWordTriggered()
}

// Start listening (user can toggle this in settings)
val prefs = getSharedPreferences("soma_prefs", MODE_PRIVATE)
if (prefs.getBoolean("wake_word_enabled", false)) {
    wakeWordDetector?.start(
        accessKey = BuildConfig.PICOVOICE_ACCESS_KEY,
        modelPath = getWakeWordModelPath()  // From assets or files dir
    )
}

private fun onWakeWordTriggered() {
    // 1. Play a short "listening" tone
    // 2. Start STT recording (see Task 3.5b)
    // 3. When transcription is ready, send to agent
    // 4. Speak the response via TTS
}

// In onDestroy():
wakeWordDetector?.stop()
```

### Add to BuildConfig

In `build.gradle.kts`:
```kotlin
buildConfigField(
    "String",
    "PICOVOICE_ACCESS_KEY",
    "\"${props.getProperty("PICOVOICE_ACCESS_KEY", "")}\""
)
```

### Settings toggle

Add a setting in the app: "Enable wake word detection" (toggle).
When enabled, SomaService starts the detector. When disabled, stops it.
This matters for battery — continuous mic listening uses power.

---

## Task 3.5b: Voice Recording + Transcription (STT)

### What to build

A voice recording system that listens until the user stops talking
(not a rigid 5-second window like the old app).

### Two STT options (implement BOTH, user picks in settings):

**Option 1: Android SpeechRecognizer (free, on-device)**
- Already designed in Phase 6 prompt
- Use Google's on-device speech recognition
- Gives partial results as user speaks
- Stops automatically when silence is detected
- No API cost

**Option 2: OpenAI Whisper API (higher quality, costs money)**
- Record audio using AudioRecord (not MediaRecorder — more control)
- Use Voice Activity Detection (VAD) to detect when user stops speaking
- Simple VAD: if RMS audio level drops below threshold for 1.5 seconds,
  stop recording
- Send audio to OpenAI Whisper API for transcription
- Sam already has an OpenAI API key for this

### Create file: `voice/VoiceRecorder.kt`

```kotlin
package io.brokentooth.soma.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/**
 * Records audio until the user stops speaking.
 * Uses simple Voice Activity Detection (VAD) based on audio energy levels.
 *
 * NOT a rigid 5-second window. Records until 1.5s of silence after speech.
 */
class VoiceRecorder {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state

    sealed class RecordingState {
        object Idle : RecordingState()
        object Recording : RecordingState()
        data class Done(val audioData: ByteArray) : RecordingState()
        data class Error(val message: String) : RecordingState()
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val SILENCE_THRESHOLD_RMS = 500.0  // Tunable
        private const val SILENCE_DURATION_MS = 1500L     // 1.5s of silence = stop
        private const val MAX_RECORDING_MS = 30000L       // Safety cap: 30 seconds
    }

    fun startRecording(scope: CoroutineScope) {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE, CHANNEL, ENCODING, bufferSize
        )

        _state.value = RecordingState.Recording

        recordingJob = scope.launch(Dispatchers.IO) {
            val output = ByteArrayOutputStream()
            val buffer = ShortArray(bufferSize / 2)
            var silenceStartMs = 0L
            var hasSpeechStarted = false
            val startTime = System.currentTimeMillis()

            audioRecord?.startRecording()

            try {
                while (isActive) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read <= 0) continue

                    // Write to output buffer
                    for (sample in buffer.take(read)) {
                        output.write(sample.toInt() and 0xFF)
                        output.write((sample.toInt() shr 8) and 0xFF)
                    }

                    // Calculate RMS energy
                    val rms = sqrt(buffer.take(read).map { it.toDouble() * it.toDouble() }.average())

                    if (rms > SILENCE_THRESHOLD_RMS) {
                        hasSpeechStarted = true
                        silenceStartMs = 0
                    } else if (hasSpeechStarted) {
                        if (silenceStartMs == 0L) {
                            silenceStartMs = System.currentTimeMillis()
                        } else if (System.currentTimeMillis() - silenceStartMs > SILENCE_DURATION_MS) {
                            Timber.i("🎤 Silence detected, stopping recording")
                            break
                        }
                    }

                    // Safety cap
                    if (System.currentTimeMillis() - startTime > MAX_RECORDING_MS) {
                        Timber.w("🎤 Max recording time reached")
                        break
                    }
                }
            } finally {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
            }

            _state.value = RecordingState.Done(output.toByteArray())
        }
    }

    fun stopRecording() {
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        _state.value = RecordingState.Idle
    }
}
```

### Create file: `voice/WhisperClient.kt`

```kotlin
package io.brokentooth.soma.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

/**
 * Sends audio to OpenAI Whisper API for transcription.
 */
class WhisperClient(private val apiKey: String) {

    private val http = OkHttpClient()

    suspend fun transcribe(audioData: ByteArray): String = withContext(Dispatchers.IO) {
        // Write to temp file (Whisper API needs a file upload)
        val tempFile = File.createTempFile("soma_audio", ".wav")
        try {
            // Write WAV header + PCM data
            writeWavFile(tempFile, audioData, sampleRate = 16000, channels = 1)

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("file", "audio.wav",
                    tempFile.readBytes().toRequestBody("audio/wav".toMediaType()))
                .build()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            val response = http.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")

            if (!response.isSuccessful) {
                throw Exception("Whisper API error: ${response.code} $body")
            }

            JSONObject(body).getString("text")
        } finally {
            tempFile.delete()
        }
    }

    private fun writeWavFile(file: File, pcmData: ByteArray, sampleRate: Int, channels: Int) {
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val fileSize = 36 + dataSize

        file.outputStream().use { out ->
            // RIFF header
            out.write("RIFF".toByteArray())
            out.write(intToBytes(fileSize))
            out.write("WAVE".toByteArray())
            // fmt chunk
            out.write("fmt ".toByteArray())
            out.write(intToBytes(16)) // chunk size
            out.write(shortToBytes(1)) // PCM format
            out.write(shortToBytes(channels.toShort()))
            out.write(intToBytes(sampleRate))
            out.write(intToBytes(byteRate))
            out.write(shortToBytes(blockAlign.toShort()))
            out.write(shortToBytes(bitsPerSample.toShort()))
            // data chunk
            out.write("data".toByteArray())
            out.write(intToBytes(dataSize))
            out.write(pcmData)
        }
    }

    private fun intToBytes(value: Int) = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )

    private fun shortToBytes(value: Short) = byteArrayOf(
        (value.toInt() and 0xFF).toByte(),
        ((value.toInt() shr 8) and 0xFF).toByte()
    )
}
```

### STT Setting

User picks in settings: "Speech recognition: On-device (free) / Whisper API (better)"

Add OPENAI_API_KEY to BuildConfig same pattern as other keys.

---

## Task 3.5c: Text-to-Speech Manager

### What to build

TTS system with multiple backends. Start with Android's built-in TTS.
Kokoro/Piper local and ElevenLabs cloud are future enhancements.

### Create file: `voice/TTSManager.kt`

Use the implementation from Phase 6 prompt (already written).
Key additions specific to Sam's needs:

```kotlin
/**
 * Smart TTS buffering: Don't wait for the full response.
 * Speak sentence-by-sentence as they arrive during streaming.
 *
 * This eliminates the "wait forever for the full response" problem.
 */
fun speakStreaming(textFlow: Flow<String>) {
    scope.launch {
        val buffer = StringBuilder()
        textFlow.collect { token ->
            buffer.append(token)
            val text = buffer.toString()

            // Check if we have a complete sentence
            val sentenceEnd = text.indexOfAny(charArrayOf('.', '!', '?', '\n'))
            if (sentenceEnd >= 0 && sentenceEnd > 10) { // Min 10 chars to avoid speaking fragments
                val sentence = text.substring(0, sentenceEnd + 1).trim()
                if (sentence.isNotEmpty()) {
                    speakQueued(sentence)  // Queue, don't interrupt
                }
                buffer.delete(0, sentenceEnd + 1)
            }
        }
        // Speak any remaining text
        val remaining = buffer.toString().trim()
        if (remaining.isNotEmpty()) {
            speakQueued(remaining)
        }
    }
}

/**
 * Queue speech without interrupting current playback.
 * Uses QUEUE_ADD instead of QUEUE_FLUSH.
 */
private fun speakQueued(text: String) {
    val cleaned = cleanForTTS(text)
    if (cleaned.isNotEmpty()) {
        tts?.speak(cleaned, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }
}
```

---

## Task 3.5d: Screen Reading Tool (Replaces ScreenSense)

### What to build

A SOMA tool that reads the current screen content using the accessibility
service. NOT always-on like ScreenSense was. On-demand only — the user
asks "what's on my screen?" and the agent reads it.

### Prerequisites

The AccessibilityService from Phase 8 (SomaAccessibilityService) is needed.
BUT we can implement a simplified version here and enhance it in Phase 8.

### Simplified screen reader

If SomaAccessibilityService already exists from Phase 3's foreground
service work, add a `readScreen()` method to it (see Phase 8 prompt
for the full implementation).

If it doesn't exist yet, create a minimal one:

```kotlin
// Minimal accessibility service — just for screen reading
class SomaAccessibilityService : AccessibilityService() {
    companion object {
        var instance: SomaAccessibilityService? = null
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return "No active window"
        val texts = mutableListOf<String>()
        collectText(root, texts)
        return if (texts.isEmpty()) "Screen appears empty" else texts.joinToString("\n")
    }

    private fun collectText(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        node.text?.toString()?.let { if (it.isNotBlank()) texts.add(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, texts) }
        }
    }
}
```

### Register as tools

```kotlin
register(ToolDefinition(
    name = "read_screen",
    description = "Read all visible text currently on the phone's screen. " +
        "Use when the user asks 'what's on my screen', 'read this to me', " +
        "'what does this say', or similar.",
    parameters = emptyMap()
)) { _ ->
    val service = SomaAccessibilityService.instance
        ?: return@register "Accessibility service not enabled. " +
            "Enable in Settings > Accessibility > SOMA"
    service.readScreen()
}

register(ToolDefinition(
    name = "read_screen_aloud",
    description = "Read the screen content aloud using text-to-speech. " +
        "Use when the user says 'read this out loud' or 'what does this say'.",
    parameters = emptyMap()
)) { _ ->
    val service = SomaAccessibilityService.instance
        ?: return@register "Accessibility service not enabled"
    val text = service.readScreen()
    ttsManager.speak(text)
    "Reading screen aloud: ${text.take(100)}..."
}
```

### NO always-on screen monitoring

Unlike ScreenSense, SOMA does NOT continuously monitor the screen.
It only reads when asked. This saves the 1.4GB RAM problem entirely.
The accessibility service stays registered but idle until a tool call
triggers `readScreen()`.

---

## Task 3.5e: Voice Interaction Flow (Ties It All Together)

### The complete voice flow in SomaService:

```kotlin
private fun onWakeWordTriggered() {
    serviceScope.launch {
        // 1. Acknowledge
        ttsManager.speak("Listening...")

        // 2. Record until silence
        val recorder = VoiceRecorder()
        recorder.startRecording(serviceScope)

        // Wait for recording to finish
        recorder.state.collect { state ->
            when (state) {
                is VoiceRecorder.RecordingState.Done -> {
                    // 3. Transcribe
                    val sttMode = getSttMode() // "ondevice" or "whisper"
                    val transcript = if (sttMode == "whisper") {
                        whisperClient.transcribe(state.audioData)
                    } else {
                        // Use Android SpeechRecognizer (different flow, not from raw audio)
                        // For MVP, just use Whisper
                        whisperClient.transcribe(state.audioData)
                    }

                    // 4. Send to agent
                    if (transcript.isNotBlank()) {
                        // Send as if the user typed it in chat
                        sendToAgent(transcript)
                    }
                }
                is VoiceRecorder.RecordingState.Error -> {
                    ttsManager.speak("Sorry, I didn't catch that.")
                }
                else -> {} // Idle, Recording — keep waiting
            }
        }
    }
}

private suspend fun sendToAgent(message: String) {
    // This needs to connect to the ChatViewModel somehow.
    // Options:
    // A) Broadcast intent that ChatViewModel receives
    // B) Shared event bus (simple Channel or SharedFlow)
    // C) Direct reference (if service has access to ViewModel - not ideal)

    // Simplest: Use a broadcast
    val intent = Intent("io.brokentooth.soma.VOICE_MESSAGE").apply {
        putExtra("message", message)
    }
    sendBroadcast(intent)

    // ChatViewModel registers a BroadcastReceiver and calls sendMessage()
    // The response is automatically spoken if auto-speak is enabled
}
```

### Auto-speak setting

Add to settings: "Speak responses: Never / When voice-activated / Always"

When set to "When voice-activated", only responses triggered by wake word
or mic button get spoken. Regular typed messages just show text.

---

## Task 3.5f: Android Assistant Integration

### Register SOMA as a digital assistant

Sam wants the Android assistant button (long-press power on Samsung)
to trigger SOMA instead of Google/Bixby.

In `AndroidManifest.xml`:
```xml
<!-- Voice interaction service for assistant role -->
<service
    android:name=".service.SomaVoiceInteractionService"
    android:permission="android.permission.BIND_VOICE_INTERACTION"
    android:exported="true">
    <meta-data
        android:name="android.voice_interaction"
        android:resource="@xml/voice_interaction_service" />
    <intent-filter>
        <action android:name="android.service.voice.VoiceInteractionService" />
    </intent-filter>
</service>

<service
    android:name=".service.SomaVoiceInteractionSessionService"
    android:permission="android.permission.BIND_VOICE_INTERACTION"
    android:exported="false" />
```

Create `res/xml/voice_interaction_service.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<voice-interaction-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:sessionService="io.brokentooth.soma.service.SomaVoiceInteractionSessionService"
    android:recognitionService="io.brokentooth.soma.service.SomaVoiceInteractionSessionService"
    android:supportsAssist="true" />
```

The user then goes to Settings > Apps > Default apps > Digital assistant
and selects SOMA. Now the assistant button triggers SOMA's voice flow.

**NOTE:** This is Samsung/Android specific and may require additional
configuration on One UI. The ASI app (vNeeL-code/ASI) has working
VoiceInteractionService code you can reference — see:
- `GemmaVoiceInteractionService.kt`
- `GemmaVoiceInteractionSession.kt`
- `GemmaVoiceInteractionSessionService.kt`

---

## Task 3.5g: Voice Command History (Parallel Chat)

Sam mentioned wanting a "parallel chat" showing voice command history.

### Implementation

Add a new message role: "voice" in addition to "user", "assistant", "system"

When a message comes from voice (wake word or mic button):
- Store with role = "voice" instead of "user"
- Display with a 🎤 icon instead of "You"
- Optionally show in a separate filtered view (/voice command to see
  only voice interactions)

This gives visibility into what SOMA heard and did via voice commands,
separate from typed chat.

---

## Permissions Summary (add to AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.BIND_VOICE_INTERACTION" />
```

Request RECORD_AUDIO at runtime before enabling wake word or mic button.

---

## Settings to Add

- Wake word: On/Off (toggle)
- Speech recognition: On-device / Whisper API
- TTS voice: System default (for now — Kokoro/Piper/ElevenLabs later)
- Auto-speak: Never / Voice-activated only / Always
- Picovoice access key (text input, saved to preferences)
- OpenAI API key for Whisper (text input, saved to preferences)

---

## What This Phase Does NOT Include (Deferred)

- Kokoro/Piper local TTS (Phase 6 enhancement — use system TTS for now)
- ElevenLabs TTS (Phase 6 enhancement — expensive, defer)
- Always-on screen monitoring (never — too expensive, on-demand only)
- Screen element selection/clipboard (Phase 8 accessibility task)
- OCR/vision models on screenshots (future enhancement)
- Complex voice conversation mode with follow-up (Phase 6)

---

## Testing

1. Enable wake word in settings
2. Say "Wake up Soma" → hear "Listening..." → speak command →
   see transcription appear in chat as 🎤 message → agent responds
3. Tap mic button in chat → speak → transcription → response
4. Say "Read my screen" via voice → agent calls read_screen tool →
   reads content aloud
5. Say "What's on my screen?" via typed text → agent returns screen content
   as text in chat
6. Response streaming while TTS speaks sentence-by-sentence (no long wait)
7. Long-press power button → SOMA voice assistant activates (if set as default)
8. Check dashboard → voice commands visible in history with 🎤 icon

---

## What Success Looks Like

Sam is driving his truck. He says "Wake up Soma." SOMA says "Listening..."
He says "Read my screen." SOMA reads the current screen content aloud.
He says "Open YouTube." YouTube opens. He says "What's my battery at?"
SOMA tells him. All hands-free. No screen touches. The old Wake Word app
and ScreenSense app can be uninstalled.
