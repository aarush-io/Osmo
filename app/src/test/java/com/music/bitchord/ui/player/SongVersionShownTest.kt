package com.music.bitchord.ui.player

import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SongVersionShownTest {
    private val video = Song(
        videoId = "vid",
        title = "Artist - Song (Official Video)",
        artist = "Artist",
        thumbnailUrl = "https://i.ytimg.com/vi/vid/hqdefault.jpg",
        isVideo = true,
        queueTier = QueueTier.AUTOPLAY,
        queueEntryId = "entry-7",
        radioName = "Radio",
        playbackSource = "Radio",
        playbackSourceType = PlaybackSourceType.HOME,
        playbackSourceId = "seed",
    )
    private val song = Song(
        videoId = "atv",
        title = "Song",
        artist = "Artist",
        thumbnailUrl = "https://lh3.googleusercontent.com/cover",
    )

    @Test
    fun songIsShownAsItIs() {
        val shown = SongVersionShown.of(song, autoSwitch = true, version = null)
        assertSame(song, shown.song)
        assertFalse(shown.versionPending)
    }

    @Test
    fun videoKeptAsVideoIsShownAsItIs() {
        val shown = SongVersionShown.of(video, autoSwitch = false, version = song)
        assertSame(video, shown.song)
        assertFalse(shown.versionPending)
    }

    @Test
    fun videoWithNoAnswerYetIsPending() {
        val shown = SongVersionShown.of(video, autoSwitch = true, version = null)
        assertSame(video, shown.song)
        assertTrue(shown.versionPending)
    }

    @Test
    fun videoWithNoSongVersionShowsItself() {
        // resolveAudio stores the row itself when it finds no song version.
        val shown = SongVersionShown.of(video, autoSwitch = true, version = video)
        assertSame(video, shown.song)
        assertFalse(shown.versionPending)
    }

    @Test
    fun knownSongVersionIsShownWithTheRowsQueueIdentity() {
        val shown = SongVersionShown.of(video, autoSwitch = true, version = song)
        assertFalse(shown.versionPending)
        assertEquals("atv", shown.song.videoId)
        assertEquals("Song", shown.song.title)
        assertEquals("https://lh3.googleusercontent.com/cover", shown.song.thumbnailUrl)
        assertFalse(shown.song.isVideo)
        assertTrue(shown.song.isVideoOrigin)
        assertEquals(QueueTier.AUTOPLAY, shown.song.queueTier)
        assertEquals("entry-7", shown.song.queueEntryId)
        assertEquals("Radio", shown.song.radioName)
        assertEquals(PlaybackSourceType.HOME, shown.song.playbackSourceType)
        assertEquals("seed", shown.song.playbackSourceId)
    }
}
