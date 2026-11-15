package com.music.bitchord

import com.music.bitchord.data.settings.LocalMusicSort
import com.music.bitchord.data.settings.LocalMusicSortField
import com.music.bitchord.data.settings.afterTapOn
import com.music.bitchord.data.settings.defaultSort
import com.music.bitchord.data.settings.field
import com.music.bitchord.data.settings.flipped
import com.music.bitchord.data.settings.isFlipped
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The library sort menu: one row for each field, a second tap turns the direction around. */
class LocalMusicSortTest {

    @Test
    fun theNamesSavedBeforeTheSecondDirectionAreStillThere() {
        // The setting is saved by name. A name that goes away sends its users back to A to Z.
        val names = LocalMusicSort.entries.map { it.name }
        listOf("TITLE_ASC", "TITLE_DESC", "DATE_ADDED", "DATE_MODIFIED", "LAST_PLAYED").forEach {
            assertTrue(it, it in names)
        }
        assertEquals(LocalMusicSortField.DATE_ADDED, LocalMusicSort.DATE_ADDED.field)
        assertFalse(LocalMusicSort.DATE_ADDED.isFlipped)
        assertFalse(LocalMusicSort.DATE_MODIFIED.isFlipped)
        assertFalse(LocalMusicSort.LAST_PLAYED.isFlipped)
    }

    @Test
    fun aTapOnAnotherRowStartsInItsFirstDirection() {
        assertEquals(LocalMusicSort.DATE_ADDED, LocalMusicSort.TITLE_DESC.afterTapOn(LocalMusicSortField.DATE_ADDED))
        assertEquals(LocalMusicSort.TITLE_ASC, LocalMusicSort.LAST_PLAYED_OLDEST.afterTapOn(LocalMusicSortField.TITLE))
    }

    @Test
    fun aTapOnThePickedRowTurnsTheDirectionAround() {
        assertEquals(LocalMusicSort.TITLE_DESC, LocalMusicSort.TITLE_ASC.afterTapOn(LocalMusicSortField.TITLE))
        assertEquals(LocalMusicSort.TITLE_ASC, LocalMusicSort.TITLE_DESC.afterTapOn(LocalMusicSortField.TITLE))
        assertEquals(
            LocalMusicSort.DATE_ADDED_OLDEST,
            LocalMusicSort.DATE_ADDED.afterTapOn(LocalMusicSortField.DATE_ADDED),
        )
        assertEquals(
            LocalMusicSort.LAST_PLAYED,
            LocalMusicSort.LAST_PLAYED_OLDEST.afterTapOn(LocalMusicSortField.LAST_PLAYED),
        )
    }

    @Test
    fun eachSortHasOneOppositeInTheSameField() {
        LocalMusicSort.entries.forEach { sort ->
            assertNotEquals(sort, sort.flipped())
            assertEquals(sort, sort.flipped().flipped())
            assertEquals(sort.field, sort.flipped().field)
            assertNotEquals(sort.isFlipped, sort.flipped().isFlipped)
        }
        LocalMusicSortField.entries.forEach { field ->
            assertEquals(field, field.defaultSort.field)
            assertFalse(field.defaultSort.isFlipped)
        }
    }
}
