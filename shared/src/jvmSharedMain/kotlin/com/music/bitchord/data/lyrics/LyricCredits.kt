package com.music.bitchord.data.lyrics

/**
 * The lyric with the credit lines removed from its start: "Lyrics by …",
 * "Composed by …", "Written & produced by …", "Composer: …", "作词：…".
 *
 * Many sources stamp the credits as though they were sung, so they took the
 * place of the intro and pushed the first real line down the panel. Only the
 * run of lines at the start is examined, and the cut stops at the first line
 * that is not a credit, so a line in the middle of the song that happens to say
 * "written by" stays.
 *
 * Some sources also open with a header line that names the song: "Flower In
 * A Hurricane (feat. …) - Justice/Tame Impala". When [title] is known, a line
 * in that run that starts with the title and also names one of the [artist]s
 * or has a " - " goes with the credits too.
 *
 * Blank lines in that run go with the credits. When the first line that stays
 * starts late enough, it gets one intro break at 0, as
 * [withInstrumentalGaps] would give it. If nothing would be left, the lines are
 * returned as they were.
 */
fun List<LyricLine>.withoutLeadingCredits(title: String? = null, artist: String? = null): List<LyricLine> {
    val header = SongHeader.of(title, artist)
    var head = 0
    var credits = 0
    while (head < size && head < MAX_HEAD_LINES) {
        val line = this[head]
        when {
            line.text.isBlank() -> Unit
            isCreditLine(line.text) -> credits++
            header?.matches(line.text) == true -> credits++
            else -> break
        }
        head++
    }
    if (credits == 0 || head >= size) return this
    val first = this[head]
    val kept = subList(head, size)
    if (first.timeMs < MIN_GAP_MS) return kept.toList()
    // Not buildList: in its block, `size` is the size of the list being built.
    return listOf(LyricLine(0L, "")) + kept
}

/**
 * Whether [text] reads as a credit rather than a lyric. Three shapes:
 *
 *  - a role and then "by": "Lyrics by X", "Words and music by X", "Prod. by X";
 *  - a role and then a separator: "Lyrics: X", "Composer - X", "作曲：X";
 *  - a person's role and then a name, without a separator: "Composer John
 *    Smith", "作词 X". Only roles that name a person, and only when the name
 *    starts with a capital (or a letter with no case), so "Music makes the
 *    people come together" is a lyric.
 */
fun isCreditLine(text: String): Boolean {
    val line = text.trim().trim(*EDGE_MARKS).trim()
    if (line.isEmpty() || line.length > MAX_CREDIT_LENGTH) return false
    return BY_CREDIT.containsMatchIn(line) ||
        LABEL_CREDIT.containsMatchIn(line) ||
        NAMED_CREDIT.containsMatchIn(line) ||
        CJK_CREDIT.containsMatchIn(line)
}

/**
 * The song's own name and artists, to know a header line such as "Title (feat.
 * X) - Artist/Other" when a source puts one before the lyrics.
 */
private class SongHeader(private val title: String, private val artists: List<String>) {

    fun matches(text: String): Boolean {
        val line = text.matchKey()
        if (!line.startsWith(title)) return false
        // The first sung line can be the title itself: "Flower in a hurricane,
        // I ..." is a lyric. A header also names who made it, or has a dash.
        return text.contains(" - ") || text.contains(" – ") || artists.any { line.contains(it) }
    }

    companion object {
        fun of(title: String?, artist: String?): SongHeader? {
            // Only the name before any bracket or dash: the header and the
            // player can write "(feat. …)" and "[from …]" differently.
            val name = title?.forLyricsSearch()
                ?.substringBefore('(')?.substringBefore('[')?.substringBefore(" - ")
                ?.matchKey()?.takeIf { it.length >= 2 }
                ?: return null
            val artists = artist.orEmpty().artistForLyricsSearch()
                .split(ARTIST_SPLIT)
                .map { it.matchKey() }
                .filter { it.length >= 2 }
            return SongHeader(name, artists)
        }

        private val ARTIST_SPLIT = Regex("""\s*(?:,|&|/|\bfeat\.?|\bft\.?|\band\b|\bx\b|、)\s*""", RegexOption.IGNORE_CASE)
    }
}

/** Letters and digits only, in lower case: how a header line and a title are compared. */
private fun String.matchKey(): String = lowercase().filter { it.isLetterOrDigit() }

/** Credits are a few lines at the start; past this many lines it is the song. */
private const val MAX_HEAD_LINES = 12

/**
 * A credit with many names can be long: "Composed by: …" with eleven writers
 * is about 200 characters. A verse written as one line is longer than this.
 */
private const val MAX_CREDIT_LENGTH = 400

/** Brackets and bullets that some sources put around a credit line. */
private val EDGE_MARKS = charArrayOf('(', ')', '[', ']', '{', '}', '（', '）', '【', '】', '*', '•', '·', '-', '–', '—', '~', '"', '“', '”')

/** Words that say what someone did on the record. */
private const val ROLE = "(?:lyrics?|lyricists?|words?|music|musical|composed|composers?|compositions?|" +
    "written|writers?|songwriters?|songwriting|writing|" +
    "produced|producers?|production|prod\\.?|co-?produced|co-?producers?|vocals?|" +
    "arranged|arrangers?|arrangements?|mixed|mixing|mix|mastered|mastering|master|" +
    "recorded|recording|engineered|engineers?|engineering|programmed|programming|performed|" +
    "translated|translation|transcribed|transcription|synced|synchronized|timed|" +
    "lyric|guitars?|bass|drums|keyboards?|piano|" +
    "作词|作詞|作曲|编曲|編曲|制作人|製作人|制作|製作|监制|監製|混音|母带|母帶|和声|和聲|吉他|贝斯|貝斯|鼓|" +
    "작사|작곡|편곡|프로듀서)"

/** How two roles are joined: "Words and music", "Written & produced", "Lyricist/Composer". */
private const val JOIN = "(?:\\s*(?:&|\\+|/|,|\\band\\b)\\s*|\\s+)"

/**
 * Words that only qualify a role, so they never count as one alone: "Lead by
 * example" and "Track by track" are lyrics, "Lead vocals by X" is a credit.
 */
private const val QUALIFIER = "(?:(?:lead|backing|assistant|additional|executive|original|song|track)\\s+){0,2}"

/** A run of roles: "Lyrics", "Music composed", "Written, composed & additional production". */
private const val ROLES = "$QUALIFIER$ROLE(?:$JOIN$QUALIFIER$ROLE){0,5}"

private val BY_CREDIT = Regex(
    "^$ROLES\\s+(?:provided\\s+)?by\\b\\s*[:：]?\\s*\\S",
    RegexOption.IGNORE_CASE,
)

private val LABEL_CREDIT = Regex(
    "^$ROLES\\s*(?:[:：|]|\\s[-–—]\\s?)\\s*\\S",
    RegexOption.IGNORE_CASE,
)

/** Roles that name a person, so "Composer John Smith" needs no colon. */
private const val PERSON = "(?:composers?|lyricists?|songwriters?|producers?|co-?producers?|arrangers?)"

private val NAMED_CREDIT = Regex(
    "^(?i:$PERSON(?:$JOIN$PERSON){0,3})\\s+[\\p{Lu}\\p{Lo}]",
)

/** "作词 周杰伦", "작사 X": these labels are never the first word of a sung line. */
private val CJK_CREDIT = Regex(
    "^(?:作词|作詞|作曲|编曲|編曲|制作人|製作人|监制|監製|작사|작곡|편곡)\\s*\\S",
)
