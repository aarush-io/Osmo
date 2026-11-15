package com.music.bitchord

import org.junit.Test

/**
 * Guards NowPlayingScreenKt (the player) against the JVM's limit of 255
 * argument slots for each method.
 *
 * The player's body is split into lambdas of their own (see SeparateMethod in
 * NowPlayingScreen.kt), so that R8 does not get one very large method. Each
 * lambda is compiled to a method that takes all the locals it reads as
 * arguments. If one of those methods takes more than 255 slots, the class does
 * not load and the player stops the app. This test fails before that, with the
 * largest methods in the message.
 *
 * It cannot find the VerifyError of the PR #108 build: that fault was in the
 * dex code that R8 made, not in the JVM classes that this test reads.
 */
class NowPlayingScreenClassSizeTest {

    @Test
    fun nowPlayingScreenKtStaysUnderTheArgumentLimit() {
        val path = "com/music/bitchord/ui/player/NowPlayingScreenKt.class"
        val (report, maxSlots) = ClassFileReport.describe(path)
        try {
            // Loaded, not initialised: the file's top-level values are not
            // what is tested, and a method of more than 255 slots already
            // stops the class from loading.
            Class.forName("com.music.bitchord.ui.player.NowPlayingScreenKt", false, javaClass.classLoader)
        } catch (e: Throwable) {
            throw AssertionError("NowPlayingScreenKt does not load: $e\n$report")
        }
        if (maxSlots > SLOT_BUDGET) {
            throw AssertionError(
                "A NowPlayingScreenKt method takes $maxSlots argument slots (budget $SLOT_BUDGET, JVM limit 255)\n$report",
            )
        }
    }

    private companion object {
        /** Headroom under the JVM's 255, so growth shows up here first. */
        const val SLOT_BUDGET = 230
    }
}
