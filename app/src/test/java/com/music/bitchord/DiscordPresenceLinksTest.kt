package com.music.bitchord

import com.music.bitchord.data.discord.PresenceLinks
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordPresenceLinksTest {
    private fun song(id: String, art: String?) =
        Song(videoId = id, title = "Wait", artist = "M83", thumbnailUrl = art)

    @Test
    fun localFileGetsDialPlaceholderAndNoListenLink() {
        val local = song(
            "content://media/external/audio/media/42",
            "content://media/external/audio/albumart/7",
        )
        assertEquals(PresenceLinks.PLACEHOLDER_ART_URL, PresenceLinks.artUrl(local))
        assertNull(PresenceLinks.listenUrl(local))
    }

    @Test
    fun trackWithNoArtGetsPlaceholder() {
        assertEquals(PresenceLinks.PLACEHOLDER_ART_URL, PresenceLinks.artUrl(song("dQw4w9WgXcQ", null)))
        assertEquals(PresenceLinks.PLACEHOLDER_ART_URL, PresenceLinks.artUrl(song("dQw4w9WgXcQ", "file:///sdcard/a.jpg")))
    }

    @Test
    fun webTrackKeepsItsCoverAndLink() {
        val web = song("dQw4w9WgXcQ", "https://lh3.googleusercontent.com/abc=w120-h120")
        assertTrue(PresenceLinks.artUrl(web).startsWith("https://lh3.googleusercontent.com/"))
        assertEquals("https://music.youtube.com/watch?v=dQw4w9WgXcQ", PresenceLinks.listenUrl(web))
    }

    @Test
    fun placeholderIsAnHttpsImage() {
        assertTrue(PresenceLinks.PLACEHOLDER_ART_URL.startsWith("https://"))
        assertTrue(PresenceLinks.PLACEHOLDER_ART_URL.endsWith(".png"))
    }

    @Test
    fun deviceFilePathsHaveNoListenLink() {
        assertNull(PresenceLinks.listenUrl(song("file:///sdcard/Music/a.flac", null)))
        assertNull(PresenceLinks.listenUrl(song("/sdcard/Music/a.flac", null)))
    }
}
