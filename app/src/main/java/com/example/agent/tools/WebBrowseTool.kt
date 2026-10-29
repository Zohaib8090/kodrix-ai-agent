package com.example.agent.tools

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.agent.Risk
import com.example.agent.Tool
import com.example.agent.ToolResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Opens a page in a hidden WebView so JavaScript-heavy sites (single-page apps, dashboards) render before the text is read.
 * Uses a throwaway in-memory profile: no cookies or logins from the user's real browser are used.
 */
class WebBrowseTool(private val context: Context) : Tool {
    override val name = "web_browse"
    override val description =
        "Open a public web page in a real browser engine (runs JavaScript) and return its visible text (first ~8000 characters). " +
            "Use when web_fetch returns little or nothing. Cannot log in."
    override val risk = Risk.SAFE
    override val parameters = JSONObject("""{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}""")

    override suspend fun run(args: JSONObject): ToolResult {
        val url = args.optString("url").trim()
        if (!withContext(Dispatchers.IO) { WebText.isPublicHttpUrl(url) }) return ToolResult("Only public http/https addresses can be opened.", true)
        val text = withTimeoutOrNull(35_000) { render(url) }
            ?: return ToolResult("The page took too long to load.", true)
        return if (text.isBlank()) ToolResult("The page had no readable text.", true) else ToolResult(text.take(8000))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun render(url: String): String {
        val result = CompletableDeferred<String>()
        val main = Handler(Looper.getMainLooper())
        var web: WebView? = null
        main.post {
            try {
                val w = WebView(context.applicationContext)
                web = w
                w.settings.javaScriptEnabled = true
                w.settings.domStorageEnabled = true
                w.settings.allowFileAccess = false
                w.settings.allowContentAccess = false
                w.settings.setGeolocationEnabled(false)
                w.webViewClient = object : WebViewClient() {
                    // Redirects must stay on public addresses too.
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        !WebText.isPublicHttpUrl(request.url.toString())

                    override fun onPageFinished(view: WebView, u: String?) {
                        // Give client-side rendering a moment, then read what is visible.
                        main.postDelayed({
                            view.evaluateJavascript("(function(){return document.body ? document.body.innerText : ''})()") { raw ->
                                result.complete(decode(raw))
                            }
                        }, 2500)
                    }

                    override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) {}
                }
                w.loadUrl(url)
            } catch (e: Exception) {
                result.complete("")
            }
        }
        try {
            return result.await()
        } finally {
            main.post { web?.stopLoading(); web?.destroy() }
        }
    }

    private fun decode(raw: String?): String = try {
        org.json.JSONArray("[${raw ?: "\"\""}]").getString(0).lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    } catch (_: Exception) { "" }
}
