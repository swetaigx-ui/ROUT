package com.rout.aiassistant.commands

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * News headlines via public RSS (Google News India). Free, no key.
 * Honest note kept at point of use: headlines depend on feed availability.
 */
object NewsService {

    /** Pure: extract up to `limit` item titles from RSS/XML text. */
    fun parseTitles(xml: String, limit: Int = 5): List<String> {
        val items = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
        val out = mutableListOf<String>()
        for (it in items) {
            val block = it.groupValues[1]
            val title = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(block)?.groupValues?.get(1) ?: continue
            val clean = title.replace("<![CDATA[", "").replace("]]>", "").trim()
            if (clean.isNotEmpty()) out.add(clean)
            if (out.size >= limit) break
        }
        return out
    }

    suspend fun headlines(hindi: Boolean = false, limit: Int = 5): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = if (hindi)
                "https://news.google.com/rss?hl=hi&gl=IN&ceid=IN:hi"
            else
                "https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000; readTimeout = 10000
            }
            val xml = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            conn.disconnect()
            parseTitles(xml, limit)
        } catch (e: Exception) { emptyList() }
    }
}
