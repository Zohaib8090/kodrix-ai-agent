package com.example.assistant

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow

/** The screenshot Android hands the assistant when it opens (if the user allows "use screenshot"). */
object ScreenContext {
    val screenshot = MutableStateFlow<Bitmap?>(null)
}
