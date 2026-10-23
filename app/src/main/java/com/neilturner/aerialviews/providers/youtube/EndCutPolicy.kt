package com.neilturner.aerialviews.providers.youtube

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Where a YouTube video stops, so the end of an upload (outro, "subscribe" cards, channel promotions) never shows.
 * In order: a manual end point in seconds for that video; else SponsorBlock's outro/selfpromo/interaction segment at the
 * end of the video (looked up once per video and cached); else the whole video (no cut).
 *
 * Manual end points and the videos always offered ("pinned") can be edited without rebuilding, in a text file on the
 * device: Android/data/<package>/files/yt_endcuts.txt, one line each: `VIDEOID=SECONDS` (stop there) and `pin:VIDEOID`
 * (always part of the YouTube offer, rarely); `*=SECONDS` stops every video there (testing). Without the file the defaults are the two SPY x FAMILY clips.
 */
object EndCutPolicy {
    private const val PREFS = "yt_end_cuts"
    private const val DAY_MS = 86_400_000L
    private const val TTL_FOUND_MS = 30 * DAY_MS
    private const val TTL_NONE_MS = 7 * DAY_MS
    private const val LOOKUP_TIMEOUT_MS = 2_500L
    private val CATEGORIES = setOf("outro", "selfpromo", "interaction")

    private fun fileLines(context: Context): List<String> =
        runCatching {
            val file = File(context.getExternalFilesDir(null), "yt_endcuts.txt")
            if (file.isFile) file.readLines().map { it.substringBefore("#").trim() }.filter { it.isNotEmpty() } else emptyList()
        }.getOrDefault(emptyList())

    /** One line per link: a YouTube URL (or an 11-character id) and, optionally, where to cut it (`90`, `1:30`, `1:02:03`). */
    private data class Link(val id: String, val endSeconds: Double?)

    private fun links(context: Context): List<Link> {
        val text = runCatching { com.neilturner.aerialviews.models.prefs.YouTubeVideoPrefs.customLinks }.getOrDefault("")
        return text.lines().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            val id = Regex("(?:youtu\\.be/|v=|/shorts/|/embed/)([A-Za-z0-9_-]{11})").find(parts[0])?.groupValues?.get(1)
                ?: parts[0].takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }
                ?: return@mapNotNull null
            val end = parts.getOrNull(1)?.split(":")?.mapNotNull { it.toDoubleOrNull() }?.takeIf { it.isNotEmpty() }
                ?.fold(0.0) { total, part -> total * 60 + part }
            Link(id, end)
        }
    }

    /** The percentages come from the settings; `pinshare=` / `share=` lines in yt_endcuts.txt still override them (testing). */
    fun pinnedPercent(context: Context): Int =
        percent(context, "pinshare") ?: com.neilturner.aerialviews.models.prefs.YouTubeVideoPrefs.pinnedPercent.toIntOrNull() ?: 7

    fun sharePercent(context: Context): Int? = percent(context, "share")

    private fun percent(context: Context, key: String): Int? =
        fileLines(context).firstOrNull { it.startsWith("$key=") }?.substringAfter("=")?.trim()?.toIntOrNull()

    fun pinnedIds(context: Context): List<String> {
        val fromFile = fileLines(context).filter { it.startsWith("pin:") }.map { it.removePrefix("pin:").trim() }.filter { it.isNotEmpty() }
        return if (fromFile.isNotEmpty()) fromFile else links(context).map { it.id }.distinct()
    }

    fun manualEnds(context: Context): Map<String, Double> {
        val fromFile =
            fileLines(context).filter { !it.startsWith("pin:") && it.contains("=") && !it.startsWith("share=") && !it.startsWith("pinshare=") }.mapNotNull { line ->
                val seconds = line.substringAfter("=").trim().toDoubleOrNull()
                val id = line.substringBefore("=").trim()
                if (seconds != null && id.isNotEmpty()) id to seconds else null
            }.toMap()
        return links(context).mapNotNull { l -> l.endSeconds?.let { l.id to it } }.toMap() + fromFile
    }

    /** The position (ms) where to stop, or null to play to the end. Call off the main thread when possible. */
    fun endPositionMs(
        context: Context,
        videoId: String,
        durationMs: Long,
    ): Long? {
        if (videoId.isBlank()) return null
        val manual = manualEnds(context)
        (manual[videoId] ?: manual["*"])?.let { return (it * 1000).toLong() }   // "*=SECONDS": every video (testing)
        return sponsorBlockEnd(context, videoId, durationMs)
    }

    private fun sponsorBlockEnd(
        context: Context,
        videoId: String,
        durationMs: Long,
    ): Long? {
        val store = prefs(context)
        val now = System.currentTimeMillis()
        store.getString("sb:$videoId", null)?.split("|")?.let { cached ->
            val ms = cached.getOrNull(0)?.toLongOrNull()
            val at = cached.getOrNull(1)?.toLongOrNull()
            if (ms != null && at != null && now - at < if (ms > 0) TTL_FOUND_MS else TTL_NONE_MS) return ms.takeIf { it > 0 }
        }
        val found =
            runCatching {
                runBlocking(Dispatchers.IO) { withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { fetchSponsorBlock(videoId, durationMs) } }
            }.getOrNull() ?: return null // timeout or offline: nothing cached, asked again next time
        store.edit().putString("sb:$videoId", "$found|$now").apply()
        android.util.Log.i("EndCut", "$videoId: SponsorBlock end=${if (found > 0) "${found}ms" else "none"}")
        return found.takeIf { it > 0 }
    }

    /** SponsorBlock: the earliest outro/selfpromo/interaction segment that sits at the end; -1 when there is none. */
    private fun fetchSponsorBlock(
        videoId: String,
        durationMs: Long,
    ): Long {
        val categories = URLEncoder.encode(JSONArray(CATEGORIES.toList()).toString(), "UTF-8")
        val connection = URL("https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=$categories").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            if (connection.responseCode == 404) return -1
            val segments = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            var best = Long.MAX_VALUE
            for (i in 0 until segments.length()) {
                val item = segments.getJSONObject(i)
                if (item.optString("category") !in CATEGORIES || item.optString("actionType", "skip") != "skip") continue
                val range = item.getJSONArray("segment")
                val start = range.getDouble(0)
                val end = range.getDouble(1)
                val total = item.optDouble("videoDuration", 0.0).takeIf { it > 0 } ?: (durationMs / 1000.0)
                // Only a segment in the last part of the video counts: never an intro or a mid-video sponsor.
                if (total > 0 && start > total * 0.55 && end >= total - 15.0) best = minOf(best, (start * 1000).toLong() - 250L)
            }
            return if (best == Long.MAX_VALUE) -1 else best.coerceAtLeast(1_000L)
        } finally {
            connection.disconnect()
        }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
