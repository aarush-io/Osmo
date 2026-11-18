package com.music.bitchord.desktop

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.settings.DialSettings
import com.music.bitchord.data.model.UiState

/** The title Home gives its speed dial shelf, which the shared Home draws as the dial. */
internal const val SPEED_DIAL_TITLE = "Speed dial"
private const val RECENTS_TITLE = "Recents"

/**
 * Home's feed with the speed dial at its head, as the phone builds it: the
 * pins, then what was played lately, YouTube's own speed dial and the rest of
 * the feed, minus what was taken off the dial. YouTube's own speed dial shelf
 * is folded into it rather than shown twice.
 */
internal fun withSpeedDial(
    state: UiState<List<HomeShelf>>,
    speedDialsOn: Boolean,
    pins: List<ShelfItem>,
): UiState<List<HomeShelf>> {
    if (state !is UiState.Success) return state
    val shelves = state.data
    fun isDial(shelf: HomeShelf) = shelf.title.equals(SPEED_DIAL_TITLE, ignoreCase = true)
    if (!speedDialsOn) return UiState.Success(shelves.filterNot(::isDial))
    val official = shelves.firstOrNull(::isDial)?.items.orEmpty()
    val recents = shelves.firstOrNull { it.title.equals(RECENTS_TITLE, ignoreCase = true) }?.items.orEmpty()
    val rest = shelves
        .filterNot { isDial(it) || it.title.equals(RECENTS_TITLE, ignoreCase = true) }
        .flatMap { it.items }
    val dial = DialSettings.assembleDial(pins, recents + official + rest)
    val feed = shelves.filterNot(::isDial)
    return UiState.Success(if (dial.isEmpty()) feed else listOf(HomeShelf(SPEED_DIAL_TITLE, dial)) + feed)
}
