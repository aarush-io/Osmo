package com.music.bitchord.data.settings

import com.music.bitchord.data.model.ShelfItem
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DialSettingsTest {
    private lateinit var store: InMemoryDialStore

    private fun song(id: String) = ShelfItem(id, "", null, videoId = id, browseId = null)
    private fun artist(id: String) = ShelfItem(id, "", null, videoId = null, browseId = id)
    private fun show(id: String) = ShelfItem(id, "", null, videoId = null, browseId = "MPSP$id")
    private fun ids(items: List<ShelfItem>) = items.map { it.speedDialId }

    @BeforeTest
    fun setUp() {
        store = InMemoryDialStore()
        DialSettings.isPodcastTile = { false }
        DialSettings.init(store)
    }

    @Test
    fun defaultsWhenNothingIsStored() {
        assertTrue(DialSettings.speedDials.value)
        assertTrue(DialSettings.mixesDial.value)
        assertFalse(DialSettings.artistDial.value)
        assertEquals(4, DialSettings.speedDialRows.value)
        assertEquals(SpeedDialSource.YOUTUBE, DialSettings.speedDialSource.value)
        assertEquals(DialTileStyle.GLASS, DialSettings.dialTileStyle.value)
    }

    @Test
    fun pinsAreWrittenUnderThePhonesKeyAndReadBack() {
        DialSettings.toggleSpeedDial(song("a"))
        DialSettings.toggleSpeedDial(song("b"))
        assertTrue(store.contains("custom_speed_dial_pins"))
        DialSettings.init(InMemoryDialStore(store.values))
        assertEquals(listOf("a", "b"), ids(DialSettings.speedDialPins.value))
    }

    @Test
    fun togglingAPinnedItemUnpinsIt() {
        assertTrue(DialSettings.toggleSpeedDial(song("a")))
        assertFalse(DialSettings.toggleSpeedDial(song("a")))
        assertEquals(emptyList(), DialSettings.speedDialPins.value)
    }

    @Test
    fun artistsGoToTheirOwnDialWhileItIsOnAndBackWhenOff() {
        DialSettings.toggleSpeedDial(artist("UCone"))
        assertEquals(listOf("UCone"), ids(DialSettings.speedDialPins.value))
        DialSettings.setArtistDial(true)
        assertEquals(emptyList(), DialSettings.speedDialPins.value)
        assertEquals(listOf("UCone"), ids(DialSettings.artistDialPins.value))
        DialSettings.setArtistDial(false)
        assertEquals(listOf("UCone"), ids(DialSettings.speedDialPins.value))
        assertEquals(emptyList(), DialSettings.artistDialPins.value)
    }

    @Test
    fun podcastShowsArePinnedToThePodcastsDial() {
        DialSettings.toggleSpeedDial(show("x"))
        assertEquals(emptyList(), DialSettings.speedDialPins.value)
        assertEquals(listOf("MPSPx"), ids(DialSettings.podcastDialPins.value))
    }

    @Test
    fun removedTilesStayOffTheDialUntilPinnedAgain() {
        DialSettings.toggleSpeedDial(song("a"))
        DialSettings.removeFromDial(song("a"))
        assertEquals(listOf("b"), ids(DialSettings.assembleDial(emptyList(), listOf(song("a"), song("b")))))
        DialSettings.toggleSpeedDial(song("a"))
        assertFalse("a" in DialSettings.hiddenFromDial.value)
    }

    @Test
    fun theDialIsCappedAndDeduplicated() {
        val picks = (1..50).map { song("s$it") } + song("s1")
        val dial = DialSettings.assembleDial(listOf(song("pin")), picks)
        assertEquals(DialSettings.SPEED_DIAL_SIZE, dial.size)
        assertEquals("pin", dial.first().speedDialId)
        assertEquals(dial.size, dial.map { it.speedDialId }.toSet().size)
    }

    @Test
    fun theArtistDialStaysOnForSomeoneWhoPinnedToItBeforeTheSwitchExisted() {
        val old = InMemoryDialStore(mapOf("custom_artist_dial_pins" to DialPins.encode(listOf(artist("UCold")))))
        DialSettings.init(old)
        assertTrue(DialSettings.artistDial.value)
    }

    @Test
    fun theOldContouredEdgeSwitchReadsAsTheEdgeStyle() {
        DialSettings.init(InMemoryDialStore(mapOf("custom_dial_contoured_edge" to true)))
        assertEquals(DialTileStyle.EDGE, DialSettings.dialTileStyle.value)
        DialSettings.setDialTileStyle(DialTileStyle.NONE)
        assertFalse(DialSettings.dialLabelShown("none") != DialSettings.dialLabels.value)
    }

    @Test
    fun rowsOutsideThreeOrFourFallBackToTheDefault() {
        DialSettings.init(InMemoryDialStore(mapOf("custom_speed_dial_rows" to 7)))
        assertEquals(DialSettings.DEFAULT_SPEED_DIAL_ROWS, DialSettings.speedDialRows.value)
        DialSettings.setSpeedDialRows(2)
        assertEquals(3, DialSettings.speedDialRows.value)
    }
}
