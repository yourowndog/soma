# SOMA Phase 6: Voice Integration

## Difficulty: Hard — Sonnet or Opus recommended
## Prerequisite: Phase 5 complete (or at least Phase 3 with foreground service)

---

## Context

SOMA runs as a foreground service and has tools for phone control.
Phase 6 adds voice: wake word detection, speech-to-text, and text-to-speech
so Sam can use SOMA hands-free while driving his truck.

---

## Task 6a: Text-to-Speech (Easiest — do first)

Create file: `voice/TTSManager.kt`

Use Android's built-in TextToSpeech:

```kotlin
package io.brokentooth.soma.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.Locale
import kotlin.coroutines.resume

class TTSManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(1.1f)  // Slightly faster for assistant feel
                isReady = true
                Timber.i("TTS initialized")
            } else {
                Timber.e("TTS init failed: $status")
            }
        }
    }

    fun speak(text: String) {
        if (!isReady) return
        // Clean text for speech — remove code blocks, markdown, tool patterns
        val cleaned = text
            .replace(Regex("```[\\s\\S]*?```"), "code block omitted")
            .replace(Regex("`[^`]+`"), "")
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")  // **bold** → bold
            .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")  // [link](url) → link
            .trim()
        if (cleaned.isNotEmpty()) {
            tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, "soma_tts")
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking == true
}
```

### Wire into ChatViewModel:

- After assistant response is committed, if voice mode is active, call
  `ttsManager.speak(responseText)`
- Add a speaker icon button in the top bar or per-message to read a
  specific response aloud
- Add a toggle in settings: "Auto-speak responses"

---

## Task 6b: Speech-to-Text

Create file: `voice/STTManager.kt`

Use Android's built-in SpeechRecognizer (simplest, free, on-device on
modern Samsung devices):

```kotlin
package io.brokentooth.soma.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

class STTManager(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow<STTState>(STTState.Idle)
    val state: StateFlow<STTState> = _state

    sealed class STTState {
        object Idle : STTState()
        object Listening : STTState()
        data class Partial(val text: String) : STTState()
        data class Result(val text: String) : STTState()
        data class Error(val message: String) : STTState()
    }

    fun startListening() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(listener)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer?.startListening(intent)
        _state.value = STTState.Listening
    }

    fun stopListening() {
        recognizer?.stopListening()
        _state.value = STTState.Idle
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = STTState.Listening
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            val msg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                else -> "Error code: $error"
            }
            _state.value = STTState.Error(msg)
            Timber.w("STT Error: $msg")
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            _state.value = STTState.Result(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            if (text.isNotEmpty()) {
                _state.value = STTState.Partial(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
```

### Wire into UI:

- Replace or augment the mic icon in ChatInput
- When mic is tapped: start STT → show partial text in input field → on result,
  auto-send the message
- Show a visual indicator (pulsing mic icon, waveform) while listening
- Permission: RECORD_AUDIO (add to manifest and request at runtime)

---

## Task 6c: Wake Word Detection (Porcupine)

**NOTE:** Sam already has Porcupine working in a separate app. This task
integrates it into SOMA.

Add dependency:
```kotlin
implementation("ai.picovoice:porcupine-android:3.0.0")
// Check for latest version
```

Create file: `voice/WakeWordService.kt`

This runs inside the existing SomaService (foreground service):

```kotlin
// Pseudocode — adapt to Porcupine's actual Android API

class WakeWordDetector(
    private val context: Context,
    private val onWakeWord: () -> Unit
) {
    private var porcupine: Porcupine? = null

    fun start() {
        // Initialize Porcupine with built-in "Hey Soma" or custom wake word
        // Sam has a custom Porcupine model — load from assets or files
        porcupine = Porcupine.Builder()
            .setAccessKey("YOUR_PICOVOICE_ACCESS_KEY")
            .setKeywordPath("path/to/custom_wake_word.ppn")
            .build(context)

        // Start audio capture loop in a coroutine
        // Feed audio frames to porcupine.process()
        // When detection occurs, call onWakeWord()
    }

    fun stop() {
        porcupine?.delete()
    }
}
```

### Voice Conversation Flow:

1. Wake word detected → play a short "listening" sound
2. Start STT → capture user speech
3. Send transcribed text to agent → get response
4. Speak response via TTS
5. After TTS finishes, briefly listen for follow-up (3 second window)
6. If user speaks again → go to step 3 (conversation mode)
7. If silence → return to wake word listening

### Add to SomaService:

```kotlin
// In SomaService.onCreate():
wakeWordDetector = WakeWordDetector(this) {
    // Wake word detected!
    sttManager.startListening()
    // When STT result arrives, send to agent
    // When agent responds, speak via TTS
}
wakeWordDetector.start()
```

---

## Task 6d: Voice Mode UI

When voice is active, show a different UI overlay:
- Large pulsing mic icon when listening
- Animated waveform or equalizer visualization
- Transcribed text appearing as it's recognized
- Agent response text appearing as it's spoken
- Minimal — designed to be glanceable while driving

This can be a separate Composable that overlays the chat screen,
or a separate Activity/Screen.

---

## Permissions Needed

Add to AndroidManifest.xml:
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
```

Request RECORD_AUDIO at runtime before starting STT or wake word.

---

## Testing

1. Tap mic icon → speak → text appears in input → auto-sends → response streams
2. Tap speaker icon on any message → reads it aloud
3. Toggle "Auto-speak" → all responses are spoken
4. Wake word "Hey SOMA" → listening starts → speak → agent responds vocally
5. After agent speaks, can continue conversation without re-triggering wake word
6. Voice works while screen is off (via foreground service)
7. Works while driving — no screen touches needed after wake word
