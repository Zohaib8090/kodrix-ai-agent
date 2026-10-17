package com.example.data.ai

import com.example.data.model.ProviderConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatStreamTest {

    private class Canned(val code: Int, val body: String) : Interceptor {
        var lastRequest: Request? = null
        var lastBody: String = ""
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            lastRequest = request
            val buffer = okio.Buffer()
            request.body?.writeTo(buffer)
            lastBody = buffer.readUtf8()
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .body(body.toResponseBody("text/event-stream".toMediaType()))
                .build()
        }
    }

    private fun client(canned: Canned) = OkHttpClient.Builder().addInterceptor(canned).build()

    private class OpenAiLike(config: ProviderConfig, client: OkHttpClient) : BaseAiProvider(config, client) {
        override suspend fun verify() = throw UnsupportedOperationException()
    }

    @Test
    fun `openai compatible stream emits content deltas and stops at DONE`() = runBlocking {
        val sse = """
            data: {"choices":[{"delta":{"role":"assistant"}}]}

            data: {"choices":[{"delta":{"content":"Hel"}}]}

            data: {"choices":[{"delta":{"content":null}}]}

            data: {"choices":[{"delta":{"content":"lo"}}]}

            data: [DONE]

            data: {"choices":[{"delta":{"content":"ignored"}}]}
        """.trimIndent()
        val canned = Canned(200, sse)
        val provider = OpenAiLike(ProviderConfig(id = "x", name = "X", apiKey = "k"), client(canned))

        val parts = provider.chatStream("sys", "user").toList()

        assertEquals(listOf("Hel", "lo"), parts)
        assertTrue(JSONObject(canned.lastBody).getBoolean("stream"))
    }

    @Test
    fun `http errors surface as exceptions with the status code`() = runBlocking {
        val provider = OpenAiLike(ProviderConfig(id = "x", name = "X", apiKey = "k"), client(Canned(401, "{\"error\":\"bad key\"}")))
        try {
            provider.chatStream("s", "u").toList()
            fail("expected an exception")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("401"))
        }
    }

    @Test
    fun `missing api key fails before any request`() = runBlocking {
        val canned = Canned(200, "")
        val provider = OpenAiLike(ProviderConfig(id = "x", name = "X", apiKey = ""), client(canned))
        try {
            provider.chatStream("s", "u").first()
            fail("expected an exception")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("API key"))
        }
        assertEquals(null, canned.lastRequest)
    }

    @Test
    fun `gemini stream concatenates text parts and skips thoughts`() = runBlocking {
        val sse = """
            data: {"candidates":[{"content":{"parts":[{"text":"plan","thought":true},{"text":"Hi "}]}}]}

            data: {"candidates":[{"content":{"parts":[{"text":"there"}]}}]}
        """.trimIndent()
        val canned = Canned(200, sse)
        val provider = GeminiProvider(ProviderConfig(id = "gemini", name = "Gemini", apiKey = "k"), client(canned))

        assertEquals(listOf("Hi ", "there"), provider.chatStream("s", "u").toList())
        assertTrue(canned.lastRequest!!.url.toString().contains(":streamGenerateContent"))
        assertEquals("sse", canned.lastRequest!!.url.queryParameter("alt"))
    }

    @Test
    fun `anthropic stream emits text deltas and reports api errors`() = runBlocking {
        val sse = """
            event: message_start
            data: {"type":"message_start"}

            event: content_block_delta
            data: {"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"hmm"}}

            event: content_block_delta
            data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"Hello"}}

            event: content_block_delta
            data: {"type":"content_block_delta","delta":{"type":"text_delta","text":" world"}}

            event: message_stop
            data: {"type":"message_stop"}
        """.trimIndent()
        val canned = Canned(200, sse)
        val provider = AnthropicProvider(ProviderConfig(id = "anthropic", name = "Claude", apiKey = "k"), client(canned))
        assertEquals(listOf("Hello", " world"), provider.chatStream("s", "u").toList())
        assertTrue(JSONObject(canned.lastBody).getBoolean("stream"))

        val failing = AnthropicProvider(
            ProviderConfig(id = "anthropic", name = "Claude", apiKey = "k"),
            client(Canned(200, "data: {\"type\":\"error\",\"error\":{\"message\":\"overloaded\"}}"))
        )
        try {
            failing.chatStream("s", "u").toList()
            fail("expected an exception")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("overloaded"))
        }
    }

    @Test
    fun `a stream cut off midway still delivers what arrived first`() = runBlocking {
        val sse = "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\ndata: {not json"
        val provider = OpenAiLike(ProviderConfig(id = "x", name = "X", apiKey = "k"), client(Canned(200, sse)))
        val received = mutableListOf<String>()
        try {
            provider.chatStream("s", "u").collect { received.add(it) }
            fail("expected a parse failure")
        } catch (e: Exception) {
            // The view model keeps the text received so far when this happens.
        }
        assertEquals(listOf("partial"), received)
    }
}
