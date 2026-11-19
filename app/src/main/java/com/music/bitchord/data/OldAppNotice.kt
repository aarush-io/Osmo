package com.music.bitchord.data

import android.content.Context

/**
 * A one-time notice for people who had the app under its old package
 * (com.steeben.bitchord, up to 1.4.1). Osmo uses its own application ID, so
 * Android installs it beside the old app and does not carry data over. This
 * notice points at Export data in the old app and Import data here.
 */
object OldAppNotice {

    private const val OLD_PACKAGE = "com.steeben.bitchord"
    private const val NEW_PACKAGE = "com.osmo.music"
    private const val PREFS = "osmo_old_app_notice"
    private const val KEY_SEEN = "seen"

    /** Only in Osmo, only while the old app is installed, and only until it has been answered. */
    fun shouldShow(context: Context): Boolean {
        if (context.packageName != NEW_PACKAGE) return false
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SEEN, false)) return false
        return runCatching { context.packageManager.getPackageInfo(OLD_PACKAGE, 0) }.isSuccess
    }

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SEEN, true).apply()
    }
}
