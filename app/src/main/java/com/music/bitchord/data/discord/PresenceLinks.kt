package com.music.bitchord.data.discord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt

/**
 * The picture and the links a Discord presence card gets for one track.
 *
 * Kept apart from [DiscordRPC] (which needs a Context and a socket) so the
 * rules can be unit tested, and so the settings preview draws exactly what the
 * card will show.
 */
object PresenceLinks {

    /**
     * Dial's own sleeve, for a track Discord cannot get a picture for.
     *
     * Discord only draws a picture that it can fetch over the web. A local
     * file's cover is a `content://` row on this phone, so Discord can never
     * see it. With no picture at all, Discord draws the icon of the presence's
     * application, and that application still has BitChord's logo. This makes
     * sure that the card always shows Dial.
     *
     * The link names one commit, not a branch: the file is there from the
     * moment the commit is pushed, and a merge (never a squash) keeps the
     * commit for good. The other two designs (icon, wordmark) sit beside it in `docs/discord/`.
     */
    const val PLACEHOLDER_ART_URL =
        "https://raw.githubusercontent.com/steeben/Dial/" +
            "0c2e1ddf39a1a71292cebe4893535b7fdacc6a66/docs/discord/presence-art-night.png"

    /** Discord draws the sleeve at roughly 96dp; 480px covers it on any density. */
    const val ART_PX = 480

    private val YOUTUBE_VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

    /** The track's own cover if Discord can fetch it, else [PLACEHOLDER_ART_URL]. */
    fun artUrl(song: Song): String =
        song.artworkAt(ART_PX)
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?: PLACEHOLDER_ART_URL

    /**
     * The YouTube Music page of [song], or null when the track has no YouTube
     * id. A local file's id is its `content://` URI, and a link built from it
     * opens nothing, so the card gets no "Listen" button for it.
     */
    fun listenUrl(song: Song): String? =
        song.videoId
            .takeIf { YOUTUBE_VIDEO_ID.matches(it) }
            ?.let { "https://music.youtube.com/watch?v=$it" }
}
