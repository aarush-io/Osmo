package com.music.bitchord

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.HomeChip
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.settings.AppStyle
import com.music.bitchord.data.settings.CustomizationSettings
import com.music.bitchord.ui.components.LocalAmbientAccent
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.appStyle
import com.music.bitchord.ui.components.frostPageButtonFill
import com.music.bitchord.ui.components.lightweightLiquidGlass
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.ui.theme.LocalNowPlayingTint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Home filtered to one of YouTube Music's category chips ("Relax",
 * "Energize", "Workout" ...). Kept here and not as locals of the app's main
 * composable: every local one of that composable's lambdas reads is one more
 * argument to it, and those are counted (see MainActivityClassSizeTest).
 *
 * While a chip is selected, Home is drawn from YouTube Music's Home feed for
 * that chip, which is made for the signed-in account: the dial holds only that
 * feed's picks (no pins, no die, no history tile), and the shelves under it are
 * that feed's. Selecting the chip again, or a tap on the mark, goes back to
 * the whole of Home. A selection is not kept from one launch to the next.
 */
internal object HomeCategory {
    /** The chip Home is filtered to, or null for the whole of Home. */
    var selected: HomeChip? by mutableStateOf<HomeChip?>(null)
        private set

    /** The selected chip's feed: its shelves, in YouTube's order. */
    var feed: UiState<List<HomeShelf>> by mutableStateOf<UiState<List<HomeShelf>>>(UiState.Loading)
        private set

    /** Whether the next page of [feed] is being read. */
    var loadingMore by mutableStateOf(false)
        private set

    val active: Boolean get() = selected != null

    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    private var job: Job? = null
    private var moreJob: Job? = null
    private var continuation: String? = null
    private val seenTitles = HashSet<String>()

    /** The chips the last Home read carried, kept for the next launch to show at once. */
    private var savedChips by mutableStateOf<List<HomeChip>>(emptyList())
    private var prefs: SharedPreferences? = null

    /** Selects [chip], or, when it is the one selected, goes back to the whole of Home. */
    fun toggle(chip: HomeChip) {
        if (selected?.params == chip.params) {
            clear()
        } else {
            selected = chip
            feed = UiState.Loading
            load(chip)
        }
    }

    /** Back to the whole of Home. */
    fun clear() {
        job?.cancel()
        moreJob?.cancel()
        selected = null
        feed = UiState.Loading
        continuation = null
        loadingMore = false
        seenTitles.clear()
    }

    /**
     * Reads the selected chip's feed again: a pull to refresh, or a retry.
     * The feed showing stays until the new one is in.
     */
    fun reload() {
        val chip = selected ?: return
        load(chip)
    }

    private fun load(chip: HomeChip) {
        job?.cancel()
        moreJob?.cancel()
        loadingMore = false
        job = scope.launch {
            YtMusicRepository.homeCategory(chip.params)
                .onSuccess { page ->
                    if (selected?.params != chip.params) return@onSuccess
                    seenTitles.clear()
                    val shelves = page.shelves.filter { seenTitles.add(it.title.lowercase(Locale.ROOT)) }
                    continuation = page.continuation
                    feed = UiState.Success(shelves)
                }
                .onFailure { failure ->
                    if (selected?.params != chip.params) return@onFailure
                    if (feed !is UiState.Success) {
                        feed = UiState.Error(failure.message ?: failure.javaClass.simpleName)
                    }
                }
        }
    }

    /** The next page of the selected chip's feed, as the list nears its end. */
    fun loadMore() {
        val chip = selected ?: return
        val token = continuation ?: return
        if (loadingMore) return
        loadingMore = true
        moreJob = scope.launch {
            YtMusicRepository.moreHome(token).onSuccess { page ->
                if (selected?.params != chip.params) return@onSuccess
                val existing = (feed as? UiState.Success)?.data.orEmpty()
                val added = page.shelves.filter { seenTitles.add(it.title.lowercase(Locale.ROOT)) }
                // A page with nothing new: the feed has come round again.
                continuation = page.continuation.takeIf { added.isNotEmpty() }
                if (added.isNotEmpty()) feed = UiState.Success(existing + added)
            }
            if (selected?.params == chip.params) loadingMore = false
        }
    }

    /** Reads the chips saved by the last launch, once. */
    fun attach(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        savedChips = readChips(p.getString(KEY_CHIPS, null))
    }

    /** Keeps [chips] for the next launch, when they differ from what is kept. */
    fun save(chips: List<HomeChip>) {
        if (chips.isEmpty() || chips == savedChips) return
        savedChips = chips
        prefs?.edit()?.putString(KEY_CHIPS, writeChips(chips))?.apply()
    }

    /** The chips to show: this launch's, or until Home has been read, the last launch's. */
    fun chips(live: List<HomeChip>): List<HomeChip> = live.ifEmpty { savedChips }

    /**
     * Home as it is drawn while a chip is selected: a dial of the feed's picks
     * (when the dials are on), then the feed's shelves.
     */
    fun arrange(feed: UiState<List<HomeShelf>>, speedDialsOn: Boolean): UiState<List<HomeShelf>> {
        if (feed !is UiState.Success) return feed
        val shelves = feed.data.filterNot { it.title.equals(CustomizationSettings.SPEED_DIAL_TITLE, ignoreCase = true) }
        val dial = if (speedDialsOn) CustomizationSettings.assembleDial(emptyList(), dialPicks(shelves)) else emptyList()
        return UiState.Success(
            if (dial.isEmpty()) shelves else listOf(HomeShelf(CustomizationSettings.SPEED_DIAL_TITLE, dial)) + shelves,
        )
    }

    /**
     * The dial's picks from a chip's shelves: one from each shelf in turn, in
     * the feed's order, so the mixes, the songs and the playlists all have a
     * place on the dial's first page.
     */
    fun dialPicks(shelves: List<HomeShelf>): List<ShelfItem> {
        val longest = shelves.maxOfOrNull { it.items.size } ?: 0
        val picks = ArrayList<ShelfItem>()
        for (round in 0 until longest) {
            shelves.forEach { shelf -> shelf.items.getOrNull(round)?.let(picks::add) }
        }
        return picks
    }

    internal fun readChips(stored: String?): List<HomeChip> {
        if (stored.isNullOrBlank()) return emptyList()
        return runCatching {
            val json = JSONArray(stored)
            (0 until json.length()).mapNotNull { i ->
                val o = json.getJSONObject(i)
                val title = o.optString("title")
                val params = o.optString("params")
                if (title.isBlank() || params.isBlank()) null else HomeChip(title, params)
            }
        }.getOrDefault(emptyList())
    }

    internal fun writeChips(chips: List<HomeChip>): String {
        val json = JSONArray()
        chips.forEach { json.put(JSONObject().put("title", it.title).put("params", it.params)) }
        return json.toString()
    }

    private const val PREFS = "home_categories"
    private const val KEY_CHIPS = "chips"
}

/**
 * Home as Home's list draws it while a chip is selected, or null while none
 * is. Another account's chips are another account's: a change of account
 * goes back to the whole of Home.
 */
@Composable
internal fun rememberCategoryHome(account: Any?, speedDialsOn: Boolean, hidden: Set<String>): UiState<List<HomeShelf>>? {
    val first = remember { arrayOf<Any?>(account) }
    LaunchedEffect(account) {
        if (first[0] != account) {
            first[0] = account
            HomeCategory.clear()
        }
    }
    val chip = HomeCategory.selected
    val feed = HomeCategory.feed
    return remember(chip, feed, speedDialsOn, hidden) {
        if (chip == null) null else HomeCategory.arrange(feed, speedDialsOn)
    }
}

/** Home's category chips, beside the mark: for [com.music.bitchord.ui.screens.HomeScreen]'s mark row. */
internal val HomeCategoryRow: @Composable () -> Unit = { HomeCategoryChips() }

/**
 * The row of category chips: scrolls sideways, in the app style (Glass,
 * Frost or Solid). The selected chip is filled with the accent and gives a
 * small spring as it is chosen.
 */
@Composable
private fun HomeCategoryChips() {
    val context = LocalContext.current
    remember(context) { HomeCategory.attach(context) }
    val live by YtMusicRepository.homeChips.collectAsStateWithLifecycle()
    LaunchedEffect(live) { HomeCategory.save(live) }
    val chips = HomeCategory.chips(live)
    if (chips.isEmpty()) return
    val selected = HomeCategory.selected
    val haptics = rememberHaptics()
    val rowState = rememberLazyListState()
    // The chosen chip is brought fully into view.
    LaunchedEffect(selected?.params) {
        val at = chips.indexOfFirst { it.params == selected?.params }
        if (at < 0) return@LaunchedEffect
        val shown = rowState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == at }
        val end = rowState.layoutInfo.viewportEndOffset
        if (shown == null || shown.offset < 0 || shown.offset + shown.size > end) rowState.animateScrollToItem(at)
    }
    LazyRow(
        state = rowState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(end = PAGE_GUTTER),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(chips, key = { it.params }) { chip ->
            CategoryChip(
                title = chip.title,
                selected = chip.params == selected?.params,
                onClick = {
                    haptics.play(Haptic.Select)
                    HomeCategory.toggle(chip)
                },
            )
        }
    }
}

@Composable
private fun CategoryChip(title: String, selected: Boolean, onClick: () -> Unit) {
    // The accent the tab bar's selected tab wears.
    val accent = LocalAmbientAccent.current ?: LocalNowPlayingTint.current ?: MaterialTheme.colorScheme.primary
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val fill by animateColorAsState(
        targetValue = if (selected) accent else accent.copy(alpha = 0f),
        animationSpec = tween(CHIP_FILL_MS),
        label = "chipFill",
    )
    val ink by animateColorAsState(
        targetValue = when {
            !selected -> MaterialTheme.colorScheme.onBackground
            accent.luminance() > 0.5f -> Color.Black
            else -> Color.White
        },
        animationSpec = tween(CHIP_FILL_MS),
        label = "chipInk",
    )
    // Chosen, the chip dips and springs back; let go, it only loses its fill.
    val scale = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected && !seen) {
            scale.snapTo(CHIP_PRESS_SCALE)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
        seen = selected
    }
    val surface = when (appStyle()) {
        AppStyle.GLASS -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        AppStyle.FROST -> frostPageButtonFill(dark)
        AppStyle.SOLID -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            // No backdrop is sampled: the chips are in Home's list, which is
            // what the bars' glass samples. Glass gives the bars' tint, light
            // and rim; Frost a see-through veil; Solid the theme's fill.
            .lightweightLiquidGlass(CHIP_SHAPE, fallbackColor = surface)
            .background(fill, CHIP_SHAPE)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            color = ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.W600,
            maxLines = 1,
        )
    }
}

private val CHIP_SHAPE = RoundedCornerShape(12.dp)
private const val CHIP_FILL_MS = 240
private const val CHIP_PRESS_SCALE = 0.88f
