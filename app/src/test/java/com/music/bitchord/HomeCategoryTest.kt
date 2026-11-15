package com.music.bitchord

import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.HomeChip
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.settings.CustomizationSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's category chips: how they are read from Home, and Home as a chip filters it. */
class HomeCategoryTest {

    private fun chip(text: String, params: String) = """
        { "chipCloudChipRenderer": {
          "text": { "runs": [{ "text": "$text" }] },
          "navigationEndpoint": { "browseEndpoint": { "browseId": "FEmusic_home", "params": "$params" } },
          "isSelected": false
        } }
    """

    // The shape music.youtube.com gave on 2026-11-15, the params cut short.
    private val home = Json.parseToJsonElement(
        """
        { "contents": { "singleColumnBrowseResultsRenderer": { "tabs": [{ "tabRenderer": { "content": {
          "sectionListRenderer": {
            "header": { "chipCloudRenderer": { "chips": [
              ${chip("Podcasts", "ggNCSgQIDBADSgQIBxAB")},
              ${chip("Relax", "ggNCSgQIDBABSgQIBxAD")},
              ${chip("Energize", "ggNCSgQIDBABSgQICRAD")},
              ${chip("Workout", "ggNCSgQIDBABSgQIChAD")}
            ], "horizontalScrollable": true } },
            "contents": []
          }
        } } }] } } }
        """,
    ).jsonObject

    @Test
    fun chipsAreReadInOrder_withoutPodcasts() {
        assertEquals(
            listOf(
                HomeChip("Relax", "ggNCSgQIDBABSgQIBxAD"),
                HomeChip("Energize", "ggNCSgQIDBABSgQICRAD"),
                HomeChip("Workout", "ggNCSgQIDBABSgQIChAD"),
            ),
            InnertubeParser.parseHomeChips(home),
        )
    }

    @Test
    fun theShortPodcastsParamsAreKnownToo() {
        assertTrue(InnertubeParser.isPodcastsChip("ggMGSgQIDBAD"))
    }

    @Test
    fun aPageWithoutChipsHasNone() {
        assertTrue(InnertubeParser.parseHomeChips(Json.parseToJsonElement("{}").jsonObject).isEmpty())
    }

    private fun item(id: String) = ShelfItem(id, "", null, null, "VL$id")

    @Test
    fun theDialTakesOneFromEachShelfInTurn() {
        val shelves = listOf(
            HomeShelf("Mixed for you", listOf(item("mix1"), item("mix2"))),
            HomeShelf("Quick picks", listOf(item("a"), item("b"), item("c"))),
            HomeShelf("Power boost", listOf(item("p1"))),
        )
        assertEquals(
            listOf("mix1", "a", "p1", "mix2", "b", "c"),
            HomeCategory.dialPicks(shelves).map { it.title },
        )
    }

    @Test
    fun aCategoryHomeLeadsWithItsDial_withoutPins() {
        val pinned = item("pinned")
        CustomizationSettings.setSpeedDialPins(listOf(pinned))
        try {
            val shelves = listOf(HomeShelf("Mixed for you", listOf(item("mix1"))))
            val arranged = HomeCategory.arrange(UiState.Success(shelves), speedDialsOn = true)
            val data = (arranged as UiState.Success).data
            assertEquals(CustomizationSettings.SPEED_DIAL_TITLE, data.first().title)
            assertEquals(listOf("mix1"), data.first().items.map { it.title })
            assertEquals(shelves, data.drop(1))
        } finally {
            CustomizationSettings.setSpeedDialPins(emptyList())
        }
    }

    @Test
    fun withTheDialsOff_onlyTheShelves() {
        val shelves = listOf(HomeShelf("Mixed for you", listOf(item("mix1"))))
        assertEquals(UiState.Success(shelves), HomeCategory.arrange(UiState.Success(shelves), speedDialsOn = false))
    }
}
