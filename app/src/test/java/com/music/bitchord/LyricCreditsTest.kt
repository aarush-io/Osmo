package com.music.bitchord

import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.isCreditLine
import com.music.bitchord.data.lyrics.withoutLeadingCredits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricCreditsTest {

    private fun lines(vararg stamped: Pair<Long, String>) = stamped.map { (time, text) -> LyricLine(time, text) }

    @Test
    fun `credits with by are credits`() {
        listOf(
            "Lyrics by Jane Doe",
            "Composed by John Smith",
            "Produced by Max Martin",
            "Written by A, B & C",
            "Words and music by Jane Doe",
            "Written & produced by Jane Doe",
            "Music composed by Hans Zimmer",
            "Prod. by Metro Boomin",
            "Written by: Jane Doe",
            "Lyrics provided by Musixmatch",
            "(Lyrics by Jane Doe)",
            "LYRICS BY JANE DOE",
            "Lead vocals by Jane Doe",
            "Original music by Jane Doe",
        ).forEach { assertTrue(it, isCreditLine(it)) }
    }

    @Test
    fun `credits without by are credits`() {
        listOf(
            "Lyrics: Jane Doe",
            "Composer: John Smith",
            "Lyricist / Composer: Jane Doe",
            "Lyrics & Music: Jane Doe",
            "Producer - Max Martin",
            "Executive producer: Jane Doe",
            "Mixing engineer: John Smith",
            "Composer John Smith",
            "Producers Max Martin & Shellback",
            "作词：周杰伦",
            "作曲 : 周杰伦",
            "作词 方文山",
            "작사 : 김이나",
        ).forEach { assertTrue(it, isCreditLine(it)) }
    }

    @Test
    fun `lyrics that mention credits are not credits`() {
        listOf(
            "Music makes the people come together",
            "Written in the stars",
            "Lyrics to a song I never sang",
            "Composer of my heart",
            "producer of the night",
            "Words don't come easy",
            "Song for you",
            "Music, sweet music",
            "I wrote it by the river",
            "Lead me home",
            "Master of puppets, I'm pulling your strings",
            "Lyrics:",
            "Lead by example",
            "Track by track we build it",
            "Keys to the kingdom",
        ).forEach { assertFalse(it, isCreditLine(it)) }
    }

    @Test
    fun `credits at the start are removed and the intro break is kept`() {
        val result = lines(
            0L to "",
            1_000L to "Lyrics by Jane Doe",
            2_500L to "Composed by John Smith",
            4_000L to "",
            15_000L to "First line",
            18_000L to "Second line",
        ).withoutLeadingCredits()
        assertEquals(listOf("", "First line", "Second line"), result.map { it.text })
        assertEquals(0L, result.first().timeMs)
        assertEquals(15_000L, result[1].timeMs)
    }

    @Test
    fun `an early first line gets no intro break`() {
        val result = lines(
            0L to "Written by Jane Doe",
            2_000L to "First line",
        ).withoutLeadingCredits()
        assertEquals(listOf("First line"), result.map { it.text })
    }

    @Test
    fun `untimed lyrics lose their credits too`() {
        val result = lines(
            0L to "Lyrics: Jane Doe",
            0L to "Music: John Smith",
            0L to "First line",
            0L to "Second line",
        ).withoutLeadingCredits()
        assertEquals(listOf("First line", "Second line"), result.map { it.text })
    }

    @Test
    fun `credit-like lines after the first lyric stay`() {
        val result = lines(
            0L to "Produced by Max Martin",
            5_000L to "First line",
            8_000L to "Written by the river",
            11_000L to "Composer: someone",
        ).withoutLeadingCredits()
        assertEquals(listOf("", "First line", "Written by the river", "Composer: someone"), result.map { it.text })
    }

    @Test
    fun `a header line and long credits at the start are removed`() {
        val writers = "Stu Mackenzie/Joey Walker/Ambrose Kenny-Smith/Nicholas Craig/Michael Cavanagh/" +
            "Lucas Harwood/Gaspard Auge/Xavier de Rosnay/Kevin Parker/Sarah Aarons/Solana Rowe"
        val result = lines(
            0L to "Flower In A Hurricane (feat. Tame Impala, SZA, King Gizzard & the Lizard Wizard) " +
                "[from GTAVI] - Justice/Tame Impala/SZA/Grand Theft Auto VI",
            1_000L to "Lyrics by :\u3000$writers",
            2_000L to "Composed by :\u3000$writers",
            3_000L to "",
            20_000L to "Only love never hesitates",
            24_000L to "Only trust has a price",
        ).withoutLeadingCredits(
            title = "Flower In A Hurricane (feat. Tame Impala, SZA, King Gizzard & the Lizard Wizard) [from GTA VI]",
            artist = "Justice, Tame Impala",
        )
        assertEquals(listOf("", "Only love never hesitates", "Only trust has a price"), result.map { it.text })
    }

    @Test
    fun `a first line that is the title stays`() {
        val original = lines(
            12_000L to "Flower in a hurricane, I hold on",
            15_000L to "Second line",
        )
        assertSame(original, original.withoutLeadingCredits(title = "Flower In A Hurricane", artist = "Justice"))
    }

    @Test
    fun `lyrics without credits are unchanged`() {
        val original = lines(0L to "", 12_000L to "First line", 15_000L to "Second line")
        assertSame(original, original.withoutLeadingCredits())
    }

    @Test
    fun `lyrics that are only credits are unchanged`() {
        val original = lines(0L to "Lyrics by Jane Doe", 3_000L to "Composed by John Smith")
        assertSame(original, original.withoutLeadingCredits())
    }
}
