package com.music.bitchord.data.settings

import com.music.bitchord.data.model.ShelfItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** What identifies an item on the speed dial: its track, or the page it opens. */
val ShelfItem.speedDialId: String? get() = videoId ?: browseId

/**
 * One saved dial tile, in the JSON the phone has always stored pins in:
 * `{"title","subtitle","thumb","video","browse","podcast"}`, nulls written out.
 */
@Serializable
internal data class DialPin(
    val title: String = "",
    val subtitle: String = "",
    val thumb: String? = null,
    val video: String? = null,
    val browse: String? = null,
    // So that a saved dial still knows an episode from a track.
    val podcast: Boolean = false,
)

/** Reads and writes lists of dial tiles (pins and the last-shown dials) as JSON. */
object DialPins {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
        coerceInputValues = true
        isLenient = true
    }
    private val listSerializer = ListSerializer(DialPin.serializer())

    fun encode(items: List<ShelfItem>): String = json.encodeToString(
        listSerializer,
        items.map { item ->
            DialPin(
                title = item.title,
                subtitle = item.subtitle,
                thumb = item.thumbnailUrl,
                video = item.videoId,
                browse = item.browseId,
                podcast = item.isPodcastEpisode || item.durationText != null,
            )
        },
    )

    /** Empty for nothing stored or anything unreadable, as the phone always did. */
    fun decode(stored: String?): List<ShelfItem> {
        if (stored.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(listSerializer, stored).map { pin ->
                ShelfItem(
                    title = pin.title,
                    subtitle = pin.subtitle,
                    thumbnailUrl = pin.thumb?.takeIf { it.isNotBlank() },
                    videoId = pin.video?.takeIf { it.isNotBlank() },
                    browseId = pin.browse?.takeIf { it.isNotBlank() },
                    isPodcastEpisode = pin.podcast,
                )
            }
        }.getOrDefault(emptyList())
    }
}
