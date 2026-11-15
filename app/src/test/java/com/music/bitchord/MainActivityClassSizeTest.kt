package com.music.bitchord

import org.junit.Test

/**
 * Guards MainActivityKt against the JVM's per-method limit of 255 argument
 * slots. BitChordApp's lambdas are compiled to methods of that class taking
 * every local they read as an argument; once one passed 255, the class no
 * longer loaded and the app crashed at launch. Fails with the biggest methods
 * listed, well before the limit, so the next one is split in time.
 */
class MainActivityClassSizeTest {

    @Test
    fun mainActivityKtStaysUnderTheArgumentLimit() {
        val path = "com/music/bitchord/MainActivityKt.class"
        val (report, maxSlots) = ClassFileReport.describe(path)
        try {
            Class.forName("com.music.bitchord.MainActivityKt")
        } catch (e: Throwable) {
            throw AssertionError("MainActivityKt does not load: $e\n$report")
        }
        if (maxSlots > SLOT_BUDGET) {
            throw AssertionError("A MainActivityKt method takes $maxSlots argument slots (budget $SLOT_BUDGET, JVM limit 255)\n$report")
        }
    }

    private companion object {
        /** Headroom under the JVM's 255, so growth shows up here first. */
        const val SLOT_BUDGET = 230
    }
}
