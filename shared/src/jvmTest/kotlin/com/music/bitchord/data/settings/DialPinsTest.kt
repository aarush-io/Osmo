package com.music.bitchord.data.settings

import com.music.bitchord.data.model.ShelfItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DialPinsTest {

    /**
     * Pins exactly as the phone's org.json writer stored them before the dials
     * were shared: keys in this order, nulls written out, and Android's
     * JSONStringer escaping every '/' as '\/'.
     */
    private val storedByOrgJson =
        """[{"title":"Discover Mix","subtitle":"Mix","thumb":"https:\/\/lh3.googleusercontent.com\/abc=w544",""" +
            """"video":null,"browse":"VLRDTMAK5uy_discover","podcast":false},""" +
            """{"title":"Song \"quoted\"","subtitle":"Artist • Album","thumb":null,"video":"dQw4w9WgXcQ",""" +
            """"browse":null,"podcast":false},""" +
            """{"title":"Episode","subtitle":"Show","thumb":"","video":"ep123","browse":null,"podcast":true}]"""

    private val expected = listOf(
        ShelfItem(
            title = "Discover Mix",
            subtitle = "Mix",
            thumbnailUrl = "https://lh3.googleusercontent.com/abc=w544",
            videoId = null,
            browseId = "VLRDTMAK5uy_discover",
        ),
        ShelfItem(
            title = "Song \"quoted\"",
            subtitle = "Artist • Album",
            thumbnailUrl = null,
            videoId = "dQw4w9WgXcQ",
            browseId = null,
        ),
        // A blank string read as no value, as org.json's optStringOrNull did.
        ShelfItem(
            title = "Episode",
            subtitle = "Show",
            thumbnailUrl = null,
            videoId = "ep123",
            browseId = null,
            isPodcastEpisode = true,
        ),
    )

    @Test
    fun readsPinsTheOrgJsonWriterStored() {
        assertEquals(expected, DialPins.decode(storedByOrgJson))
    }

    @Test
    fun writesTheSameKeysInTheSameOrderWithNullsWrittenOut() {
        val written = DialPins.encode(expected)
        val parsed = Json.parseToJsonElement(written) as JsonArray
        val original = Json.parseToJsonElement(storedByOrgJson) as JsonArray
        assertEquals(original.size, parsed.size)
        parsed.zip(original).forEachIndexed { index, (new, old) ->
            val newObject = new as kotlinx.serialization.json.JsonObject
            val oldObject = old as kotlinx.serialization.json.JsonObject
            assertEquals(oldObject.keys.toList(), newObject.keys.toList(), "keys of pin $index")
        }
        assertTrue("\"video\":null" in written, "nulls are written out")
    }

    @Test
    fun roundTripsThroughTheNewWriter() {
        val roundTripped = DialPins.decode(DialPins.encode(DialPins.decode(storedByOrgJson)))
        assertEquals(expected, roundTripped)
    }

    @Test
    fun anEpisodeWithARuntimeIsSavedAsAPodcast() {
        val episode = ShelfItem("Ep", "Show", null, "ep1", null, durationText = "34:00")
        assertEquals(true, DialPins.decode(DialPins.encode(listOf(episode))).single().isPodcastEpisode)
    }

    @Test
    fun nothingOrSomethingUnreadableIsAnEmptyDial() {
        assertEquals(emptyList(), DialPins.decode(null))
        assertEquals(emptyList(), DialPins.decode(""))
        assertEquals(emptyList(), DialPins.decode("not json"))
    }
}
