package com.music.bitchord

import com.music.bitchord.data.osmoReleaseNotes
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesBrandTest {

    @Test
    fun `old title line reads Osmo`() {
        assertEquals("Osmo 1.4.1\n\n### Replay", osmoReleaseNotes("BitChord Dial 1.4.1\n\n### Replay"))
        assertEquals("# Osmo 1.2.0", osmoReleaseNotes("# BitChord 1.2.0"))
    }

    @Test
    fun `only the title line is renamed`() {
        val notes = "Osmo 1.5.0\n- instead of the BitChord logo"
        assertEquals(notes, osmoReleaseNotes(notes))
        assertEquals(
            "Osmo 1.4.1\n- BitChord Dial mark",
            osmoReleaseNotes("BitChord Dial 1.4.1\n- BitChord Dial mark"),
        )
    }
}
