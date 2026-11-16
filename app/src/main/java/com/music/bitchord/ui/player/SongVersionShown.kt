package com.music.bitchord.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.CanvasAudioFixes
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * What the player and the mini player show for the track that is playing.
 *
 * A music video in the queue plays as its song version: the video row starts,
 * and the song is swapped in once it is found. Until then the row's own title
 * and thumbnail were on screen, and the song's cover flashed in over the
 * thumbnail a moment later. When the song version is already known (looked up
 * ahead, see [SongVersionLookahead]) it is shown from the first frame instead;
 * when it is not, [versionPending] tells the player to keep the last cover
 * until it is.
 */
@Immutable
data class ShownTrack(
    val song: Song,
    /** [song] is a music video whose song version is still being looked up. */
    val versionPending: Boolean,
)

object SongVersionShown {

    /**
     * [playing] as the player shows it. [autoSwitch] is whether this video
     * row is changed to its song version (not in a Listen Together party, not
     * kept as a video from the menu). [version] is the stored answer for it,
     * null while there is none yet: the row itself when it has no song version.
     */
    fun of(playing: Song, autoSwitch: Boolean, version: Song?): ShownTrack = when {
        !playing.isVideo || !autoSwitch -> ShownTrack(playing, versionPending = false)
        version == null -> ShownTrack(playing, versionPending = true)
        version.isVideo || version.videoId == playing.videoId -> ShownTrack(playing, versionPending = false)
        else -> ShownTrack(version.standingInFor(playing), versionPending = false)
    }

    /**
     * The song version with the queue identity of the row it replaces, as the
     * switch itself builds it (see `switchToMusicOnly`).
     */
    fun Song.standingInFor(video: Song): Song = copy(
        isVideoOrigin = true,
        queueTier = video.queueTier,
        queueEntryId = video.queueEntryId,
        radioName = video.radioName,
        playbackSource = video.playbackSource,
        playbackSourceType = video.playbackSourceType,
        playbackSourceId = video.playbackSourceId,
    )

    /** Whether a lookahead for [song] has anything to find. */
    fun worthLookingUp(song: Song?): Boolean =
        song != null && song.isVideo && !CanvasAudioFixes.keepsVideoAudio(song.videoId) &&
            YtMusicRepository.cachedAudioVersion(song.videoId) == null
}

/** [SongVersionShown.of] for the track playing, again each time a song version is found. */
@Composable
fun rememberShownTrack(playing: Song?, autoSwitch: Boolean): ShownTrack? {
    if (playing == null) return null
    // Listened to only while a music video plays: this is read in the app's
    // top scope, and a song version found ahead for the next track must not
    // recompose all of it.
    val found = if (playing.isVideo && autoSwitch) {
        YtMusicRepository.audioVersionsFound.collectAsStateWithLifecycle().value
    } else {
        0
    }
    return remember(playing, autoSwitch, found) {
        SongVersionShown.of(
            playing = playing,
            autoSwitch = autoSwitch,
            version = if (playing.isVideo) YtMusicRepository.cachedAudioVersion(playing.videoId) else null,
        )
    }
}

/**
 * Looks up the song version of a music video next to the playing track, the
 * one after and the one before, so a skip onto it shows the song's cover at
 * once and the switch has no search to wait for. Only once the queue has held
 * still for [LOOKAHEAD_SETTLE_MS], so skipping through a queue asks for
 * nothing. Nothing in a Listen Together party, where videos play as they are.
 */
@Composable
fun SongVersionLookahead(next: Song?, previous: Song?, enabled: Boolean) {
    LookUpSongVersion(next.takeIf { enabled })
    LookUpSongVersion(previous.takeIf { enabled })
}

@Composable
private fun LookUpSongVersion(song: Song?) {
    LaunchedEffect(song?.videoId) {
        if (song == null || !SongVersionShown.worthLookingUp(song)) return@LaunchedEffect
        delay(LOOKAHEAD_SETTLE_MS)
        try {
            YtMusicRepository.resolveAudio(song)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The switch asks again when the video starts.
        }
    }
}

/** How long the queue holds still before a song version is looked up ahead. */
private const val LOOKAHEAD_SETTLE_MS = 1_500L
