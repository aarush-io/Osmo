package com.music.bitchord.data.settings

/**
 * A video quality the listener can set in Settings, Performance. There is one
 * choice for Wi-Fi and one for mobile data. The choices apply to a music video
 * and to a music video that is shown as the Canvas of a song.
 *
 * The defaults keep the rule the app had before these settings: 1080p on
 * Wi-Fi and 480p on mobile data.
 *
 * [OFF] is a choice for mobile data only: on a metered connection no Canvas
 * and no video loads, and the player shows the cover art. The audio does not
 * change, because the picture is always a separate video-only stream.
 */
enum class VideoQuality(
    /** The largest video height to ask for. */
    val maxHeight: Int,
) {
    /**
     * No video. Its height is never asked for: the player does not load a
     * picture at all. Only a Canvas saved with a downloaded song still plays,
     * because it uses no data.
     */
    OFF(480),
    P480(480),
    P1080(1080),

    /** The highest available height, to a limit of 4K. */
    MAX(2160),
    ;

    companion object {
        val WIFI_DEFAULT = P1080
        val MOBILE_DEFAULT = P480

        /**
         * A height above this limit can use a codec that some phones cannot
         * decode. Thus this is the height the player goes back to when a
         * larger one fails.
         */
        const val SAFE_HEIGHT = 1080

        /** The choices for Wi-Fi, in their order on screen. */
        val WIFI_CHOICES = listOf(P480, P1080, MAX)

        /** The choices for mobile data, in their order on screen. */
        val MOBILE_CHOICES = listOf(OFF, P480, P1080, MAX)

        /**
         * The choice for the connection. Mobile data is a metered connection,
         * which includes a metered hotspot. [metered] is null when the network
         * is not known, and then the Wi-Fi choice applies.
         */
        fun forConnection(wifi: VideoQuality, mobile: VideoQuality, metered: Boolean?): VideoQuality =
            if (metered == true) mobile else wifi

        /**
         * Whether no Canvas and no video may load now: the connection is
         * metered and the mobile data choice is [OFF]. A network that is not
         * known ([metered] null) is not mobile data, as in [forConnection].
         */
        fun videoOff(mobile: VideoQuality, metered: Boolean?): Boolean =
            metered == true && mobile == OFF

        /**
         * The height to ask for. [held] is the height from the quality box of
         * the video page, which has priority. [steppedDown] is true after a
         * height above [SAFE_HEIGHT] failed to play for this video.
         */
        fun heightToAsk(setting: VideoQuality, held: Int?, steppedDown: Boolean): Int {
            val asked = held ?: setting.maxHeight
            return if (steppedDown) minOf(asked, SAFE_HEIGHT) else asked
        }

        /**
         * The stored name back to its value. An unknown name, or a name that
         * is not in [allowed], gives [default].
         */
        fun decode(stored: String?, default: VideoQuality, allowed: List<VideoQuality> = entries): VideoQuality =
            allowed.firstOrNull { it.name == stored } ?: default
    }
}
