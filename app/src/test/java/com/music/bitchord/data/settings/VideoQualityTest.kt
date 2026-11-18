package com.music.bitchord.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoQualityTest {

    @Test
    fun `the defaults keep the rule from before the settings`() {
        val wifi = VideoQuality.WIFI_DEFAULT
        val mobile = VideoQuality.MOBILE_DEFAULT
        assertEquals(1080, VideoQuality.forConnection(wifi, mobile, metered = false).maxHeight)
        assertEquals(1080, VideoQuality.forConnection(wifi, mobile, metered = null).maxHeight)
        assertEquals(480, VideoQuality.forConnection(wifi, mobile, metered = true).maxHeight)
    }

    @Test
    fun `each connection has its own choice`() {
        assertEquals(
            VideoQuality.MAX,
            VideoQuality.forConnection(wifi = VideoQuality.MAX, mobile = VideoQuality.P1080, metered = false),
        )
        assertEquals(
            VideoQuality.P1080,
            VideoQuality.forConnection(wifi = VideoQuality.MAX, mobile = VideoQuality.P1080, metered = true),
        )
        assertEquals(2160, VideoQuality.MAX.maxHeight)
    }

    @Test
    fun `the held height of the quality box has priority`() {
        assertEquals(720, VideoQuality.heightToAsk(VideoQuality.MAX, held = 720, steppedDown = false))
        assertEquals(1440, VideoQuality.heightToAsk(VideoQuality.P480, held = 1440, steppedDown = false))
    }

    @Test
    fun `a video that failed above 1080p goes back to 1080p`() {
        assertEquals(1080, VideoQuality.heightToAsk(VideoQuality.MAX, held = null, steppedDown = true))
        assertEquals(1080, VideoQuality.heightToAsk(VideoQuality.P480, held = 2160, steppedDown = true))
        assertEquals(480, VideoQuality.heightToAsk(VideoQuality.P480, held = null, steppedDown = true))
    }

    @Test
    fun `an unknown stored name gives the default`() {
        assertEquals(VideoQuality.P480, VideoQuality.decode(null, VideoQuality.P480))
        assertEquals(VideoQuality.P1080, VideoQuality.decode("AUTO", VideoQuality.P1080))
        assertEquals(VideoQuality.MAX, VideoQuality.decode("MAX", VideoQuality.P480))
    }

    @Test
    fun `off stops video only on mobile data`() {
        assertTrue(VideoQuality.videoOff(VideoQuality.OFF, metered = true))
        assertFalse(VideoQuality.videoOff(VideoQuality.OFF, metered = false))
        // No network known: the Wi-Fi rule applies, as in forConnection.
        assertFalse(VideoQuality.videoOff(VideoQuality.OFF, metered = null))
        assertFalse(VideoQuality.videoOff(VideoQuality.P480, metered = true))
    }

    @Test
    fun `off is a choice for mobile data only`() {
        assertTrue(VideoQuality.OFF in VideoQuality.MOBILE_CHOICES)
        assertFalse(VideoQuality.OFF in VideoQuality.WIFI_CHOICES)
        assertEquals(VideoQuality.OFF, VideoQuality.MOBILE_CHOICES.first())
        assertEquals(
            VideoQuality.WIFI_DEFAULT,
            VideoQuality.decode("OFF", VideoQuality.WIFI_DEFAULT, allowed = VideoQuality.WIFI_CHOICES),
        )
        assertEquals(
            VideoQuality.OFF,
            VideoQuality.decode("OFF", VideoQuality.MOBILE_DEFAULT, allowed = VideoQuality.MOBILE_CHOICES),
        )
    }
}
