package com.example.agent

import com.example.agent.tools.WebText
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class WebToolsTest {
    private val sample = """
        <div class="result"><h2><a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fpage%3Fa%3D1&amp;rut=abc">Example &amp; <b>Page</b></a></h2>
        <a class="result__snippet" href="//duckduckgo.com/l/?uddg=x">A <b>snippet</b> about it.</a></div>
        <div class="result"><a class="result__a" href="//duckduckgo.com/y.js?ad_provider=bing">Ad</a><a class="result__snippet">ad text</a></div>
        <div class="result"><a class="result__a" href="https://direct.org/x">Direct</a><a class="result__snippet">Second.</a></div>
    """.trimIndent()

    @Test fun parsesResultsUnwrapsLinksAndDropsAds() {
        val hits = WebText.parseDuckDuckGo(sample)
        assertEquals(2, hits.size)
        assertEquals("Example & Page", hits[0].title)
        assertEquals("https://example.com/page?a=1", hits[0].url)
        assertEquals("A snippet about it.", hits[0].snippet)
        assertEquals("https://direct.org/x", hits[1].url)
    }

    @Test fun htmlToTextDropsScriptsAndTags() {
        val t = WebText.htmlToText("<html><head><title>T</title></head><body><script>evil()</script><p>Hello <b>world</b></p><div>Line2</div></body></html>")
        assertEquals("Hello world\nLine2", t)
    }

    @Test fun blocksPrivateAndLocalTargets() {
        val local = { h: String -> listOf(InetAddress.getByName(if (h == "evil.test") "192.168.1.5" else "93.184.216.34")) }
        assertFalse(WebText.isPublicHttpUrl("http://localhost:8080/", local))
        assertFalse(WebText.isPublicHttpUrl("http://127.0.0.1/", { listOf(InetAddress.getByName("127.0.0.1")) }))
        assertFalse(WebText.isPublicHttpUrl("https://evil.test/", local))
        assertFalse(WebText.isPublicHttpUrl("file:///etc/passwd", local))
        assertFalse(WebText.isPublicHttpUrl("http://169.254.169.254/", { listOf(InetAddress.getByName("169.254.169.254")) }))
        assertTrue(WebText.isPublicHttpUrl("https://example.com/a", local))
    }
}
