package com.music.bitchord

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.unit.Dp
import com.music.bitchord.ui.components.glassAccentFill
import com.music.bitchord.ui.components.liquidGlass
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
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
import com.music.bitchord.ui.components.styleSurfaceWithoutBackdrop
import com.music.bitchord.ui.components.lightweightLiquidGlass
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.ui.theme.LocalNowPlayingTint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import coil3.SingletonImageLoader
import com.music.bitchord.ui.screens.SpeedDialIntro
import com.music.bitchord.ui.screens.speedDialArtRequest
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
    private var appContext: Context? = null

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
        // The whole of Home's dial comes back in the same sweep; its art is
        // still in memory from before.
        if (selected != null) SpeedDialIntro.requestSweep()
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
            val page = YtMusicRepository.homeCategory(chip.params).getOrElse { failure ->
                if (selected?.params == chip.params && feed !is UiState.Success) {
                    feed = UiState.Error(failure.message ?: failure.javaClass.simpleName)
                }
                return@launch
            }
            if (selected?.params != chip.params) return@launch
            val seen = HashSet<String>()
            val shelves = page.shelves.filter { seen.add(it.title.lowercase(Locale.ROOT)) }
            // The dial's first page of art into memory before the dial shows,
            // so its tiles all come in together, in one sweep.
            preloadDialArt(shelves)
            if (selected?.params != chip.params) return@launch
            seenTitles.clear()
            seenTitles.addAll(seen)
            continuation = page.continuation
            SpeedDialIntro.requestSweep()
            feed = UiState.Success(shelves)
        }
    }

    private suspend fun preloadDialArt(shelves: List<HomeShelf>) {
        val context = appContext ?: return
        if (!CustomizationSettings.speedDials.value) return
        val firstPage = (arrange(UiState.Success(shelves), speedDialsOn = true) as? UiState.Success)
            ?.data?.firstOrNull()?.items.orEmpty()
            .take(SPEED_DIAL_COLUMNS * CustomizationSettings.speedDialRows.value)
        val loader = SingletonImageLoader.get(context)
        withTimeoutOrNull(DIAL_ART_WAIT_MS) {
            coroutineScope {
                firstPage.forEach { item -> launch { loader.execute(speedDialArtRequest(context, item.thumbnailUrl)) } }
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
        appContext = context.applicationContext
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
    private const val SPEED_DIAL_COLUMNS = 3

    /** The longest the dial waits for its art before it comes in anyway. */
    private const val DIAL_ART_WAIT_MS = 2_500L
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
internal val HomeCategoryRow: @Composable () -> Unit = { HomeCategoryChips(floating = false) }

/**
 * The category chips again, held under the top bar while Home is scrolled
 * down into its feed (the "For You" part, or the category's): drawn over the
 * pages, as the mark is. [inFeed] says whether the list is down there.
 */
@Composable
internal fun BoxScope.HomeCategoryPinnedRow(inFeed: () -> Boolean, top: Dp) {
    val latest by rememberUpdatedState(inFeed)
    val shown by remember { derivedStateOf { latest() } }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(220)) + slideInVertically(tween(280)) { -it / 3 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(220)) { -it / 3 },
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(top = top),
    ) {
        HomeCategoryChips(floating = true)
    }
}

/**
 * The row of category chips: scrolls sideways, in the app style (Glass,
 * Frost or Solid). The selected chip is filled with the accent and gives a
 * small spring as it is chosen. [floating]: the row held over the pages,
 * under the top bar, rather than the one in Home's list.
 */
@Composable
private fun HomeCategoryChips(floating: Boolean) {
    val context = LocalContext.current
    remember(context) { HomeCategory.attach(context) }
    val live by YtMusicRepository.homeChips.collectAsStateWithLifecycle()
    LaunchedEffect(live) { HomeCategory.save(live) }
    val chips = HomeCategory.chips(live)
    if (chips.isEmpty()) return
    val selected = HomeCategory.selected
    val haptics = rememberHaptics()
    val rowState = rememberLazyListState()
    // The chosen chip is brought into view: the held row puts it first.
    LaunchedEffect(selected?.params) {
        val at = chips.indexOfFirst { it.params == selected?.params }
        if (at < 0) return@LaunchedEffect
        if (floating) {
            rowState.scrollToItem(at)
            return@LaunchedEffect
        }
        val shown = rowState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == at }
        val end = rowState.layoutInfo.viewportEndOffset
        if (shown == null || shown.offset < 0 || shown.offset + shown.size > end) rowState.animateScrollToItem(at)
    }
    LazyRow(
        state = rowState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(start = if (floating) PAGE_GUTTER else 0.dp, end = PAGE_GUTTER),
        modifier = Modifier.fillMaxWidth().chipRowEdgeFade(rowState),
    ) {
        items(chips, key = { it.params }) { chip ->
            CategoryChip(
                title = chip.title,
                selected = chip.params == selected?.params,
                floating = floating,
                onClick = {
                    haptics.play(Haptic.Select)
                    HomeCategory.toggle(chip)
                },
            )
        }
    }
}

@Composable
private fun CategoryChip(title: String, selected: Boolean, floating: Boolean, onClick: () -> Unit) {
    // The accent the tab bar's selected tab wears.
    val accent = LocalAmbientAccent.current ?: LocalNowPlayingTint.current ?: MaterialTheme.colorScheme.primary
    val style = appStyle()
    // Glass and Frost let a touch of their blur through the accent, so the
    // chosen chip reads as tinted glass and not as paint on it.
    val fillAlpha = if (style == AppStyle.SOLID) 1f else CHIP_GLASS_FILL
    val fill by animateColorAsState(
        targetValue = accent.copy(alpha = if (selected) fillAlpha else 0f),
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
    // In Home's list, Frost and Solid take the held row's own fill and
    // hairline; Glass keeps its lightweight look (null).
    val inList = Modifier.styleSurfaceWithoutBackdrop(CHIP_SHAPE, carriesText = true)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .then(
                if (floating) {
                    // Over the pages, as the bars are: the real glass (or
                    // frost, or solid fill) of the page scrolling under it.
                    Modifier.liquidGlass(CHIP_SHAPE, carriesText = true)
                } else {
                    // In Home's list, which is what the bars' glass samples,
                    // so nothing is sampled here: Glass gives the bars' tint,
                    // light and rim. Frost and Solid give the same fill and
                    // hairline as the held row, so the chips do not change
                    // as Home scrolls from the dial into its feed (a pale
                    // veil here made them bright beside the held ones).
                    inList ?: Modifier.lightweightLiquidGlass(
                        CHIP_SHAPE,
                        fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    )
                },
            )
            // The accent is laid on the glass under its light and rim; an
            // opaque fill over the glass hid both on the held row.
            .glassAccentFill(CHIP_SHAPE, fill)
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

/**
 * Feathers the chip row's ends, as the player's scrolling titles are, rather
 * than cutting the chips off sharp. Each end fades only where there are chips
 * past it, and the left fade comes in over the first [CHIP_EDGE_FADE] of the
 * scroll, so a row at its start shows its first chip clear. Read at draw time:
 * a scroll only redraws the mask.
 */
private fun Modifier.chipRowEdgeFade(state: LazyListState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = CHIP_EDGE_FADE.toPx().coerceAtMost(size.width / 2f)
        if (edge <= 0f) return@drawWithContent
        val info = state.layoutInfo
        val scrolled = if (state.firstVisibleItemIndex > 0) edge else state.firstVisibleItemScrollOffset.toFloat()
        val last = info.visibleItemsInfo.lastOrNull()
        val remaining = if (last == null || info.totalItemsCount == 0) {
            0f
        } else if (last.index < info.totalItemsCount - 1) {
            edge
        } else {
            (last.offset + last.size + info.afterContentPadding - info.viewportEndOffset).toFloat()
        }
        fun fade(strength: Float, fromLeft: Boolean) {
            if (strength <= 0f) return
            val x0 = if (fromLeft) 0f else size.width - edge
            val clear = Color.Black.copy(alpha = 1f - strength)
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to if (fromLeft) clear else Color.Black,
                    1f to if (fromLeft) Color.Black else clear,
                    startX = x0,
                    endX = x0 + edge,
                ),
                topLeft = Offset(x0, 0f),
                size = Size(edge, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        fade((scrolled / edge).coerceIn(0f, 1f), fromLeft = true)
        fade((remaining / edge).coerceIn(0f, 1f), fromLeft = false)
    }

private val CHIP_SHAPE = RoundedCornerShape(12.dp)
private val CHIP_EDGE_FADE = 28.dp
private const val CHIP_FILL_MS = 240
private const val CHIP_GLASS_FILL = 0.86f
private const val CHIP_PRESS_SCALE = 0.88f
