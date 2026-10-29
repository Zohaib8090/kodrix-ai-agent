package com.example.agent.tools

import com.example.agent.Risk
import com.example.agent.Tool
import com.example.agent.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

data class SearchHit(val title: String, val url: String, val snippet: String)

/** Parsing and safety helpers, kept pure so they are unit-tested. */
object WebText {
    /** Extracts results from DuckDuckGo's HTML endpoint. */
    fun parseDuckDuckGo(html: String, limit: Int = 8): List<SearchHit> {
        val link = Regex("""<a[^>]*class="[^"]*result__a[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val snip = Regex("""<a[^>]*class="[^"]*result__snippet[^"]*"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val snippets = snip.findAll(html).map { clean(it.groupValues[1]) }.toList()
        return link.findAll(html).mapIndexedNotNull { i, m ->
            val url = unwrap(m.groupValues[1]) ?: return@mapIndexedNotNull null
            SearchHit(clean(m.groupValues[2]), url, snippets.getOrElse(i) { "" })
        }.take(limit).toList()
    }

    /** DuckDuckGo wraps result links as //duckduckgo.com/l/?uddg=<encoded>; ads are dropped. */
    fun unwrap(href: String): String? {
        val h = href.replace("&amp;", "&")
        if (h.contains("duckduckgo.com/y.js")) return null
        val m = Regex("""[?&]uddg=([^&]+)""").find(h)
        val url = if (m != null) URLDecoder.decode(m.groupValues[1], "UTF-8") else if (h.startsWith("//")) "https:$h" else h
        return url.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    fun clean(fragment: String): String = decodeEntities(fragment.replace(Regex("<[^>]+>"), "")).replace(Regex("\\s+"), " ").trim()

    private fun decodeEntities(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#x27;", "'").replace("&#39;", "'").replace("&nbsp;", " ")

    /** Readable text from an HTML page: drops scripts, styles and tags. */
    fun htmlToText(html: String): String {
        var t = html.replace(Regex("(?is)<(script|style|noscript|svg|head)[^>]*>.*?</\\1>"), " ")
        t = t.replace(Regex("(?i)<(br|/p|/div|/li|/h[1-6]|/tr)[^>]*>"), "\n")
        t = decodeEntities(t.replace(Regex("<[^>]+>"), " "))
        return t.lines().map { it.replace(Regex("[ \\t]+"), " ").trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }

    /** Blocks loopback, private and link-local targets so a prompt-injected page cannot probe the phone's network. */
    fun isPublicHttpUrl(raw: String, resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() }): Boolean {
        val uri = try { URI(raw.trim()) } catch (_: Exception) { return false }
        if (uri.scheme != "http" && uri.scheme != "https") return false
        val host = uri.host ?: return false
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".internal")) return false
        val addrs = try { resolve(host) } catch (_: Exception) { return false }
        return addrs.isNotEmpty() && addrs.none { it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress || it.isSiteLocalAddress || it.isMulticastAddress }
    }
}

private const val UA = "Mozilla/5.0 (Linux; Android 14) Kodrix/3"

private fun get(url: String, maxBytes: Int = 1_500_000): String {
    var current = url
    repeat(4) {
        val c = URL(current).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 20_000
        c.instanceFollowRedirects = false
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "en")
        val code = c.responseCode
        if (code in 300..399) {
            val next = c.getHeaderField("Location") ?: throw java.io.IOException("Redirect without Location")
            current = URL(URL(current), next).toString()
            if (!WebText.isPublicHttpUrl(current)) throw java.io.IOException("Redirect to a blocked address")
            return@repeat
        }
        if (code !in 200..299) throw java.io.IOException("HTTP $code")
        return c.inputStream.use { i ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (out.size() < maxBytes) { val n = i.read(buf); if (n < 0) break; out.write(buf, 0, n) }
            out.toString("UTF-8")
        }
    }
    throw java.io.IOException("Too many redirects")
}

class WebSearchTool : Tool {
    override val name = "web_search"
    override val description = "Search the web (DuckDuckGo). Returns titles, URLs and snippets. Use web_fetch to read a result page."
    override val risk = Risk.SAFE
    override val parameters = JSONObject("""{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}""")

    override suspend fun run(args: JSONObject): ToolResult {
        val q = args.optString("query").trim()
        if (q.isEmpty()) return ToolResult("Missing 'query'.", true)
        return try {
            val html = withContext(Dispatchers.IO) { get("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(q, "UTF-8")) }
            val hits = WebText.parseDuckDuckGo(html)
            if (hits.isEmpty()) ToolResult("No results found (or the search page changed). Try different words.", true)
            else ToolResult(hits.withIndex().joinToString("\n\n") { (i, h) -> "${i + 1}. ${h.title}\n${h.url}\n${h.snippet}" })
        } catch (e: Exception) {
            ToolResult("Web search failed: ${e.message}", true)
        }
    }
}

class WebFetchTool : Tool {
    override val name = "web_fetch"
    override val description = "Download a public web page and return its readable text (first ~8000 characters)."
    override val risk = Risk.SAFE
    override val parameters = JSONObject("""{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}""")

    override suspend fun run(args: JSONObject): ToolResult {
        val url = args.optString("url").trim()
        if (!withContext(Dispatchers.IO) { WebText.isPublicHttpUrl(url) }) return ToolResult("Only public http/https addresses can be fetched.", true)
        return try {
            val body = withContext(Dispatchers.IO) { get(url) }
            val text = if (body.trimStart().startsWith("<")) WebText.htmlToText(body) else body
            ToolResult(text.take(8000).ifBlank { "The page had no readable text." })
        } catch (e: Exception) {
            ToolResult("Could not fetch the page: ${e.message}", true)
        }
    }
}
