package com.example.voice

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechToTextTest {
    @Test
    fun noMatchAndTimeoutAreRecoverableWithFriendlyText() {
        val a = AndroidSpeechToText.describe(SpeechRecognizer.ERROR_NO_MATCH)
        assertTrue(a.recoverable); assertEquals("I didn't catch that.", a.message)
        assertTrue(AndroidSpeechToText.describe(SpeechRecognizer.ERROR_SPEECH_TIMEOUT).recoverable)
    }

    @Test
    fun missingPermissionIsNotRecoverableAndNamesTheMicrophone() {
        val f = AndroidSpeechToText.describe(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        assertFalse(f.recoverable); assertTrue(f.message.contains("Microphone"))
    }

    @Test
    fun networkErrorsExplainTheInternetRequirement() {
        assertTrue(AndroidSpeechToText.describe(SpeechRecognizer.ERROR_NETWORK).message.contains("internet"))
        assertTrue(AndroidSpeechToText.describe(SpeechRecognizer.ERROR_NETWORK_TIMEOUT).message.contains("internet"))
    }

    @Test
    fun unsupportedOfflineLanguageIsNotRecoverable() {
        assertFalse(AndroidSpeechToText.describe(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED).recoverable)
    }

    @Test
    fun unknownCodesStillProduceAMessage() {
        val f = AndroidSpeechToText.describe(9999)
        assertTrue(f.message.contains("9999")); assertEquals(9999, f.code)
    }
}
