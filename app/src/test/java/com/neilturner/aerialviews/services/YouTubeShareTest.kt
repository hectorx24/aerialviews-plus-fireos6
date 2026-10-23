package com.neilturner.aerialviews.services

import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.videos.AerialExifMetadata
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.AerialMediaMetadata
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal class YouTubeShareTest {
    private fun item(
        id: String,
        source: AerialMediaSource,
    ) = AerialMedia(
        uri = mockk(relaxed = true, name = id),
        source = source,
        metadata = AerialMediaMetadata(exif = AerialExifMetadata(description = id)),
    )

    private val apple = List(139) { item("apple$it", AerialMediaSource.APPLE) }
    private val youtube = List(65) { item("yt$it", AerialMediaSource.YOUTUBE) }
    private val pinnedIds = setOf("JqUsUe6B43g", "xBSG2hZXTF0")
    private val pinned = pinnedIds.map { item(it, AerialMediaSource.YOUTUBE) }

    @Test
    fun `mix is about half apple, forty three youtube and seven pinned`() {
        val random = Random(42)
        var total = 0
        var yt = 0
        var pin = 0
        repeat(200) {
            val mixed = MediaServiceHelper.applyYouTubeShare(apple + youtube + pinned, 43, pinnedIds, 7, random)
            total += mixed.size
            pin += mixed.count { it.metadata.exif.description in pinnedIds }
            yt += mixed.count { it.source == AerialMediaSource.YOUTUBE && it.metadata.exif.description !in pinnedIds }
        }
        println("APPLE=${(total - yt - pin) * 100.0 / total} YT=${yt * 100.0 / total} PINNED=${pin * 100.0 / total}")
        assertTrue(yt * 100.0 / total in 38.0..48.0)
        assertTrue(pin * 100.0 / total in 5.0..9.0)
    }

    @Test
    fun `only youtube or only other sources are just shuffled`() {
        assertEquals(apple.size, MediaServiceHelper.applyYouTubeShare(apple, 8, pinnedIds).size)
        assertEquals(youtube.size, MediaServiceHelper.applyYouTubeShare(youtube, 8, pinnedIds).size)
    }
}
