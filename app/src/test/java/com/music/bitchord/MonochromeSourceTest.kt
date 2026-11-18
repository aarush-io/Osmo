package com.music.bitchord

import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.settings.permits
import com.music.bitchord.data.sources.ModuleSource
import com.music.bitchord.data.sources.MonochromeParsing
import com.music.bitchord.data.sources.SourceConfig
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.SourceRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonochromeSourceTest {

    /** A real `/search/tracks` answer, cut to three rows. */
    private val search = """
        {"tracks":[
          {"id":"157948484734181376","trackId":"157948484734181376","title":"Eastside (with Halsey & Khalid)",
           "artistNames":["benny blanco","Halsey","Khalid"],
           "artwork":"https://tracks.monochrome.st/proxy/mi/157948004790894592-1A01.jpg",
           "explicit":false,"playable":true,"duration":173799,"isrc":"USUM71809132"},
          {"id":"157883001284677632","trackId":"157883001284677632","title":"Eastside (with Halsey & Khalid)",
           "artistNames":["benny blanco","Halsey","Khalid"],"explicit":false,"playable":false,"duration":170769},
          {"id":"156920203454877696","title":"I Can’t Get Enough","artistNames":["benny blanco"],
           "explicit":true,"duration":158}
        ],"releases":[],"artists":[]}
    """.trimIndent()

    @Test
    fun `search rows become songs keyed to the source`() {
        val rows = MonochromeParsing.tracksIn(search)

        // The row marked not playable is left out.
        assertEquals(listOf("157948484734181376", "156920203454877696"), rows.map { it.id })

        val song = rows.first().toSong("mono")
        assertEquals(SourceRegistry.trackKey("mono", "157948484734181376"), song.videoId)
        assertEquals("Eastside (with Halsey & Khalid)", song.title)
        assertEquals("benny blanco, Halsey, Khalid", song.artist)
        assertEquals("2:53", song.durationText)
        assertEquals(ModuleSource.LOSSLESS, song.sourceQuality)
        assertEquals(false, song.isExplicit)
        assertEquals("https://tracks.monochrome.st/proxy/mi/157948004790894592-1A01.jpg", song.thumbnailUrl)
    }

    @Test
    fun `a duration in seconds and a row with only an id still read`() {
        val row = MonochromeParsing.tracksIn(search).last()
        assertEquals("156920203454877696", row.id)
        assertEquals(158, row.durationSec)
        assertEquals("2:38", row.toSong("mono").durationText)
    }

    @Test
    fun `an answer that does not read is no rows`() {
        assertTrue(MonochromeParsing.tracksIn("").isEmpty())
        assertTrue(MonochromeParsing.tracksIn("<html>").isEmpty())
        assertTrue(MonochromeParsing.tracksIn("""{"data":[]}""").isEmpty())
        assertTrue(MonochromeParsing.tracksIn("""{"tracks":[{"title":"no id"}]}""").isEmpty())
    }

    /** "fLaC", a STREAMINFO block header and the 34-byte block for the given format. */
    private fun flacHead(sampleRate: Int, bits: Int, channels: Int = 2): ByteArray {
        val head = ByteArray(42)
        "fLaC".toByteArray().copyInto(head)
        head[4] = 0x00; head[5] = 0x00; head[6] = 0x00; head[7] = 0x22
        // Sample rate (20 bits), channels - 1 (3 bits), bits per sample - 1 (5 bits).
        val s = 18
        head[s] = (sampleRate shr 12).toByte()
        head[s + 1] = (sampleRate shr 4).toByte()
        head[s + 2] = (((sampleRate and 0x0f) shl 4) or ((channels - 1) shl 1) or ((bits - 1) shr 4)).toByte()
        head[s + 3] = (((bits - 1) and 0x0f) shl 4).toByte()
        return head
    }

    @Test
    fun `the FLAC head gives bit depth and sample rate`() {
        assertEquals(MonochromeParsing.FlacInfo(16, 44_100), MonochromeParsing.flacInfo(flacHead(44_100, 16)))
        assertEquals(MonochromeParsing.FlacInfo(24, 96_000), MonochromeParsing.flacInfo(flacHead(96_000, 24)))
        assertEquals(MonochromeParsing.FlacInfo(24, 192_000), MonochromeParsing.flacInfo(flacHead(192_000, 24, 6)))
    }

    @Test
    fun `a file that is not FLAC is refused, a short FLAC head is FLAC of unknown format`() {
        assertNull(MonochromeParsing.flacInfo("<html><body>".toByteArray()))
        assertNull(MonochromeParsing.flacInfo(ByteArray(0)))
        assertEquals(MonochromeParsing.FlacInfo(null, null), MonochromeParsing.flacInfo("fLaC".toByteArray()))
    }

    @Test
    fun `Monochrome is built in, off by default, and ranked between the addons and JioSaavn`() {
        assertFalse(SourceConfig(kind = SourceKind.MONOCHROME).enabled)
        assertFalse(SourceKind.MONOCHROME.needsServer)
        assertFalse(SourceKind.MONOCHROME.isUserAdded)
        assertTrue(SourceKind.MONOCHROME.canServeLossless)

        val sources = SourceRegistry.sourcesForInit(emptyList(), forceJioSaavnOff = false)
        assertFalse(sources.single { it.kind == SourceKind.MONOCHROME }.enabled)

        val order = SourceKind.entries.sortedBy { it.rank }
        assertTrue(order.indexOf(SourceKind.ADDON) < order.indexOf(SourceKind.MONOCHROME))
        assertTrue(order.indexOf(SourceKind.MONOCHROME) < order.indexOf(SourceKind.JIOSAAVN))
        assertTrue(order.indexOf(SourceKind.JIOSAAVN) < order.indexOf(SourceKind.YOUTUBE))
    }

    @Test
    fun `an install from before Monochrome gets it seeded once and keeps the listener's switch`() {
        val stored = listOf(
            SourceConfig(kind = SourceKind.JIOSAAVN, enabled = true),
            SourceConfig(kind = SourceKind.YOUTUBE),
        )
        val seeded = SourceRegistry.sourcesForInit(stored, forceJioSaavnOff = false)
        val monochrome = seeded.single { it.kind == SourceKind.MONOCHROME }
        assertFalse(monochrome.enabled)

        val switchedOn = seeded.map { if (it.id == monochrome.id) it.copy(enabled = true) else it }
        val nextLaunch = SourceRegistry.sourcesForInit(switchedOn, forceJioSaavnOff = false)
        assertNotNull(nextLaunch.singleOrNull { it.kind == SourceKind.MONOCHROME && it.enabled })
    }

    @Test
    fun `Monochrome is asked only while the ceiling is Lossless`() {
        assertTrue(AudioQuality.LOSSLESS.permits(SourceKind.MONOCHROME))
        assertFalse(AudioQuality.HIGH.permits(SourceKind.MONOCHROME))
        assertFalse(AudioQuality.MEDIUM.permits(SourceKind.MONOCHROME))
        assertFalse(AudioQuality.LOW.permits(SourceKind.MONOCHROME))
    }
}
