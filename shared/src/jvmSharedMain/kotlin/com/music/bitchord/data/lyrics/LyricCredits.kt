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
 * Blank lines in that run go with the credits. When the first line that stays
 * starts late enough, it gets one intro break at 0, as
 * [withInstrumentalGaps] would give it. If nothing would be left, the lines are
 * returned as they were.
 */
fun List<LyricLine>.withoutLeadingCredits(): List<LyricLine> {
    var head = 0
    var credits = 0
    while (head < size && head < MAX_HEAD_LINES) {
        val line = this[head]
        when {
            line.text.isBlank() -> Unit
            isCreditLine(line.text) -> credits++
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

/** Credits are a few lines at the start; past this many lines it is the song. */
private const val MAX_HEAD_LINES = 12

/** A credit with many names can be long, but not as long as a verse. */
private const val MAX_CREDIT_LENGTH = 140

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
