package com.music.bitchord.data.podcasts

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.isPodcast

/**
 * The video ids that this app has seen to be podcast episodes, kept between
 * runs, so that Home's speed dial can leave them out.
 *
 * A card does not always say that it is an episode. The dial that was saved
 * last, the device's own record of what it played, the Home feed on disk and
 * a history row made into a card keep only a title, a picture and an id. An
 * episode that one of them holds came back to the dial as a track. This list
 * is what tells them apart: an episode goes on it when it plays, and when a
 * show's page or the Podcasts page lists it.
 *
 * The first run also takes the episodes that [EpisodeProgress] counted
 * before, so that the episodes played before this list was kept come off too.
 */
object KnownEpisodes {
    private var prefs: SharedPreferences? = null
    private val lock = Any()

    /** Oldest first, so that the oldest go when there are too many. */
    private val ids = LinkedHashSet<String>()

    fun init(context: Context) {
        val opened = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        synchronized(lock) {
            prefs = opened
            ids.clear()
            opened.getString(KEY_IDS, null)?.split(SEPARATOR)?.filterTo(ids) { it.isNotBlank() }
            if (!opened.getBoolean(KEY_SEEDED, false)) {
                // The episodes played before this list was kept.
                val counted = context.getSharedPreferences(EpisodeProgress.PREFS_NAME, Context.MODE_PRIVATE)
                    .all.keys
                ids.addAll(counted)
                trim()
                opened.edit().putString(KEY_IDS, ids.joinToString(SEPARATOR)).putBoolean(KEY_SEEDED, true).apply()
            }
        }
    }

    /** These are episodes. Writes only when one of them is new. */
    fun add(videoIds: Collection<String>) {
        if (videoIds.isEmpty()) return
        synchronized(lock) {
            var changed = false
            videoIds.forEach { id ->
                if (id.isNotBlank() && ids.add(id)) changed = true
            }
            if (!changed) return
            trim()
            prefs?.edit()?.putString(KEY_IDS, ids.joinToString(SEPARATOR))?.apply()
        }
    }

    fun add(videoId: String) = add(listOf(videoId))

    /** Whether [videoId] was seen to be an episode. */
    fun contains(videoId: String?): Boolean =
        videoId != null && synchronized(lock) { videoId in ids }

    /** A copy of the list, for a caller that checks many ids. */
    fun snapshot(): Set<String> = synchronized(lock) { HashSet(ids) }

    /** For tests: forgets every id, and writes nothing. */
    internal fun clearForTest() {
        synchronized(lock) {
            prefs = null
            ids.clear()
        }
    }

    /** Called with [lock] held. */
    private fun trim() {
        val extra = ids.size - MAX_KEPT
        if (extra <= 0) return
        val iterator = ids.iterator()
        repeat(extra) {
            iterator.next()
            iterator.remove()
        }
    }

    /** Many shows' worth of episodes; the list is small next to what it saves. */
    internal const val MAX_KEPT = 2000

    private const val PREFS_NAME = "known_episodes"
    private const val KEY_IDS = "ids"
    private const val KEY_SEEDED = "seeded"
    private const val SEPARATOR = ","
}

/**
 * Whether [this] card is a podcast show or episode: by what the card says
 * ([isPodcast]), or because its id was seen to be an episode ([KnownEpisodes]).
 * Home's speed dial gives no tile to such a card.
 */
val ShelfItem.isPodcastTile: Boolean
    get() = isPodcast || KnownEpisodes.contains(videoId)
