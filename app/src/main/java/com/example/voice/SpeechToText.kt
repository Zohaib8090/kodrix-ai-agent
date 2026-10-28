package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

sealed interface SttEvent {
    data object Ready : SttEvent
    data class Partial(val text: String) : SttEvent
    data class Final(val text: String) : SttEvent
    data class Failure(val code: Int, val message: String, val recoverable: Boolean) : SttEvent
}

/**
 * Speech-to-text behind an interface so the engine can be swapped
 * (Android SpeechRecognizer now; an offline Whisper/Vosk engine later) without touching the agent.
 * All methods must be called on the main thread.
 */
interface SpeechToText {
    fun start(onEvent: (SttEvent) -> Unit)
    fun stop()
    fun cancel()
    fun release()
}

/**
 * Android's built-in recognizer. Prefers the fully on-device recognizer (API 31+) when the phone has one,
 * otherwise falls back to the system default (which may use the network, e.g. Google's service).
 */
class AndroidSpeechToText(
    private val context: Context,
    private val languageTag: String? = null,
    private val preferOffline: Boolean = true,
) : SpeechToText {

    private var recognizer: SpeechRecognizer? = null
    val usingOnDevice: Boolean get() = onDevice

    private var onDevice = false

    override fun start(onEvent: (SttEvent) -> Unit) {
        release()
        if (!SpeechRecognizer.isRecognitionAvailable(context) && !onDeviceAvailable()) {
            onEvent(SttEvent.Failure(-1, "No speech recognition service is installed on this phone.", false))
            return
        }
        val r = createRecognizer()
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onEvent(SttEvent.Ready)
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {
                firstResult(partialResults)?.let { onEvent(SttEvent.Partial(it)) }
            }

            override fun onResults(results: Bundle?) {
                val text = firstResult(results)
                if (text.isNullOrBlank()) onEvent(SttEvent.Failure(SpeechRecognizer.ERROR_NO_MATCH, "I didn't catch that.", true))
                else onEvent(SttEvent.Final(text))
            }

            override fun onError(error: Int) {
                Log.w("Stt", "recognizer error $error")
                onEvent(describe(error))
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            if (preferOffline) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            languageTag?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
        }
        r.startListening(intent)
    }

    private fun onDeviceAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    private fun createRecognizer(): SpeechRecognizer {
        if (preferOffline && onDeviceAvailable()) {
            onDevice = true
            return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        }
        onDevice = false
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun firstResult(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    override fun stop() { recognizer?.stopListening() }
    override fun cancel() { recognizer?.cancel() }
    override fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    companion object {
        /** Maps SpeechRecognizer error codes to a user-readable message. Pure function, unit-tested. */
        fun describe(error: Int): SttEvent.Failure = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> SttEvent.Failure(error, "I didn't catch that.", true)
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SttEvent.Failure(error, "I didn't hear anything.", true)
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SttEvent.Failure(error, "Microphone permission is needed.", false)
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                SttEvent.Failure(error, "Speech recognition needs internet on this phone.", true)
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SttEvent.Failure(error, "The recognizer is busy, try again.", true)
            SpeechRecognizer.ERROR_AUDIO -> SttEvent.Failure(error, "Microphone error.", true)
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                SttEvent.Failure(error, "That language isn't available offline.", false)
            else -> SttEvent.Failure(error, "Speech recognition failed (code $error).", true)
        }
    }
}
