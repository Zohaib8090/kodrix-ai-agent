package com.example.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log

/**
 * Registering this service is what makes Kodrix selectable as the phone's default digital assistant
 * (Settings > Apps > Default apps > Digital assistant app). The system then routes the assistant
 * gesture (power-button long press, Side key, corner swipe, KEYCODE_ASSIST) to it.
 */
class KodrixVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        Log.i("KodrixAssistant", "VoiceInteractionService ready")
    }
}

class KodrixSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = KodrixSession(this)
}

class KodrixSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.i("KodrixAssistant", "session shown, flags=$showFlags")
        ScreenContext.screenshot.value = null
        val intent = Intent(context, AssistActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startAssistantActivity(intent)
    }

    override fun onHandleScreenshot(screenshot: android.graphics.Bitmap?) {
        ScreenContext.screenshot.value = screenshot
    }
}
