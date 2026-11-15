package com.music.bitchord.data.sources

import com.music.bitchord.data.Http
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "BitChord"

/**
 * Monochrome's own track streamer: lossless FLAC, no account, no manifest.
 *
 * Two requests and nothing else:
 *
 *  - `GET /search/tracks?q=…&limit=…` answers `{"tracks":[…]}`, each row with
 *    a `trackId`, `title`, `artistNames`, `duration` in milliseconds and an
 *    `artwork` URL.
 *  - `GET /track/<trackId>` is the FLAC itself, a progressive file the player
 *    opens directly.
 *
 * Which row is the song being played is not decided here. [SourceResolver]
 * searches with the same queries it gives every source and matches the rows on
 * title, artist and runtime through [TrackMatcher], so a miss here — no row,
 * no FLAC, a server that does not answer — is the next source in line or
 * YouTube Music, as for any other source.
 *
 * The streamer only answers a browser, so every request carries a desktop
 * Chrome User-Agent, the stream included (see [SourceStream.headers]).
 */
class MonochromeSource(
    override val config: SourceConfig,
) : MusicSource, SourceRegistry.ConfigBacked {

    override val configId: String get() = config.id
    override val kind: SourceKind get() = SourceKind.MONOCHROME
    override val displayName: String get() = config.label.ifBlank { SourceKind.MONOCHROME.label }

    /** No account and no address to check, so nothing to report before a song is asked for. */
    override suspend fun health(): SourceHealth = SourceHealth.Ok()

    /** One catalogue at one tier: [waitForAll] and [request] change nothing here. */
    override suspend fun search(
        query: String,
        limit: Int,
        waitForAll: Boolean,
        request: StreamRequest?,
    ): List<Song> {
        if (query.isBlank() || resting()) return emptyList()
        val url = "$BASE_URL/search/tracks".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("limit", limit.coerceIn(1, MAX_LIMIT).toString())
            .build()
        TrackLog.d(TAG, "▶ Monochrome search query=\"$query\"")
        val body = withContext(Dispatchers.IO) {
            val call = Request.Builder().url(url)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()
            try {
                client.newCall(call).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("search answered ${response.code}")
                    response.body?.string().orEmpty()
                }
            } catch (e: IOException) {
                rest()
                throw e
            }
        }
        val rows = MonochromeParsing.tracksIn(body)
        TrackLog.d(TAG, "  ✓ Monochrome returned ${rows.size} tracks")
        return rows.take(limit).map { it.toSong(config.id) }
    }

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? {
        if (resting()) return null
        val url = "$BASE_URL/track".toHttpUrl().newBuilder().addPathSegment(trackId).build().toString()
        // The head of the file: it has to open as FLAC, and its STREAMINFO
        // block says the bit depth and sample rate the player will get.
        val head = withContext(Dispatchers.IO) {
            val call = Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .header("Range", "bytes=0-${HEAD_BYTES - 1}")
                .build()
            try {
                client.newCall(call).execute().use { response ->
                    if (!response.isSuccessful) {
                        TrackLog.w(TAG, "  ✗ Monochrome stream answered ${response.code} for $trackId")
                        return@withContext null
                    }
                    // Read only the head, also when the server ignores the
                    // range and starts to send the whole file.
                    val source = response.body?.source() ?: return@withContext null
                    source.request(HEAD_BYTES.toLong())
                    source.buffer.readByteArray(minOf(HEAD_BYTES.toLong(), source.buffer.size))
                }
            } catch (e: IOException) {
                rest()
                throw e
            }
        } ?: return null
        val info = MonochromeParsing.flacInfo(head)
        if (info == null) {
            TrackLog.w(TAG, "  ✗ Monochrome stream for $trackId is not FLAC")
            return null
        }
        TrackLog.d(TAG, "  ✓ Monochrome FLAC ${info.bitsPerSample ?: "?"}-bit ${info.sampleRateHz ?: "?"} Hz for $trackId")
        return SourceStream(
            url = url,
            format = StreamFormat(codec = "flac", sampleRateHz = info.sampleRateHz, bitDepth = info.bitsPerSample),
            headers = mapOf("User-Agent" to USER_AGENT),
        )
    }

    /**
     * Whether the server failed a request a short time ago.
     *
     * A server that does not answer costs each track a full timeout, which is
     * time the track does not start on YouTube Music. After one failure the
     * source is left out for [REST_MS], then asked again.
     */
    private fun resting(): Boolean = System.currentTimeMillis() < restUntil.get()

    private fun rest() {
        restUntil.set(System.currentTimeMillis() + REST_MS)
        TrackLog.w(TAG, "Monochrome did not answer; left out for ${REST_MS / 1000}s")
    }

    private companion object {
        const val BASE_URL = "https://tracks.monochrome.st"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/128.0.0.0 Safari/537.36"
        const val MAX_LIMIT = 25
        /** "fLaC", the metadata block header and the 34-byte STREAMINFO block. */
        const val HEAD_BYTES = 42
        const val REST_MS = 2 * 60 * 1000L

        /** Shared by every instance, so an edit that rebuilds the source does not forget a dead server. */
        val restUntil = AtomicLong(0L)

        val client by lazy {
            Http.client.newBuilder()
                .callTimeout(8, TimeUnit.SECONDS)
                .build()
        }
    }
}

/** The parts of [MonochromeSource] that read answers, kept apart so they can be tested without a network. */
internal object MonochromeParsing {

    data class Track(
        val id: String,
        val title: String,
        val artist: String,
        val durationSec: Int?,
        val artwork: String?,
        val explicit: Boolean?,
    ) {
        fun toSong(configId: String) = Song(
            videoId = SourceRegistry.trackKey(configId, id),
            title = title,
            artist = artist.ifBlank { "Unknown Artist" },
            thumbnailUrl = artwork,
            durationText = durationSec?.let { String.format(Locale.ROOT, "%d:%02d", it / 60, it % 60) },
            // Every row is the FLAC. Said so the resolver ranks it as it ranks
            // any lossless row — see [SourceResolver.preferred].
            sourceQuality = ModuleSource.LOSSLESS,
            isExplicit = explicit,
        )
    }

    /** What a FLAC file's STREAMINFO says. Null parts are parts the head did not state. */
    data class FlacInfo(val bitsPerSample: Int?, val sampleRateHz: Int?)

    private val json = Json { ignoreUnknownKeys = true }

    /** The playable rows of a `/search/tracks` answer. Anything that does not read is no rows. */
    fun tracksIn(text: String): List<Track> {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
        val tracks = root["tracks"] as? JsonArray ?: return emptyList()
        return tracks.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            if ((row["playable"] as? JsonPrimitive)?.booleanOrNull == false) return@mapNotNull null
            val id = row.string("trackId") ?: row.string("id") ?: return@mapNotNull null
            val title = row.string("title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val artists = (row["artistNames"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
                .orEmpty()
            // Milliseconds now; older answers gave seconds.
            val duration = (row["duration"] as? JsonPrimitive)?.longOrNull
                ?.takeIf { it > 0 }
                ?.let { if (it > 10_000) it / 1000 else it }
                ?.toInt()
            Track(
                id = id,
                title = title,
                artist = artists.joinToString(", "),
                durationSec = duration,
                artwork = row.string("artwork")?.takeIf { it.startsWith("https://") },
                explicit = (row["explicit"] as? JsonPrimitive)?.booleanOrNull,
            )
        }
    }

    /**
     * "fLaC", then the STREAMINFO block: 4 bytes of block header, 10 bytes of
     * block and frame sizes, then 20 bits of sample rate, 3 of channels - 1
     * and 5 of bits per sample - 1. Null when the bytes are not a FLAC file.
     * A head too short or too odd to read is still FLAC, with its format
     * unknown.
     */
    fun flacInfo(bytes: ByteArray): FlacInfo? {
        if (bytes.size < 4 || bytes.copyOfRange(0, 4).decodeToString() != "fLaC") return null
        if (bytes.size < 4 + 4 + 14) return FlacInfo(null, null)
        val b = bytes.copyOfRange(8, 22).map { it.toInt() and 0xff }
        val sampleRate = (b[10] shl 12) or (b[11] shl 4) or (b[12] shr 4)
        val bits = (((b[12] and 0x01) shl 4) or (b[13] shr 4)) + 1
        return FlacInfo(
            bitsPerSample = bits.takeIf { it in 8..32 },
            sampleRateHz = sampleRate.takeIf { it in 8_000..768_000 },
        )
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
}
