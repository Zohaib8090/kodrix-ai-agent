package com.example.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Android requires every voice-interaction service to declare a RecognitionService. Kodrix does its
 * speech recognition through the system recognizer in AssistActivity, so this one only reports
 * "unavailable" to anything that tries to use it directly.
 */
class KodrixRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        try { listener?.error(SpeechRecognizer.ERROR_CLIENT) } catch (_: Exception) {}
    }
    override fun onCancel(listener: Callback?) {}
    override fun onStopListening(listener: Callback?) {}
}
