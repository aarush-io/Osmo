package com.music.bitchord.data.settings

import android.content.SharedPreferences

/** The phone's [DialStore]: the same preferences, and keys, the dials have always been kept in. */
class SharedPreferencesDialStore(private val prefs: SharedPreferences) : DialStore {
    override fun contains(key: String) = prefs.contains(key)
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun getInt(key: String, default: Int) = prefs.getInt(key, default)
    override fun getStringSet(key: String): Set<String> = prefs.getStringSet(key, emptySet())?.toSet().orEmpty()

    override fun edit(block: DialStore.Editor.() -> Unit) {
        val editor = prefs.edit()
        object : DialStore.Editor {
            override fun putString(key: String, value: String) { editor.putString(key, value) }
            override fun putBoolean(key: String, value: Boolean) { editor.putBoolean(key, value) }
            override fun putInt(key: String, value: Int) { editor.putInt(key, value) }
            override fun putStringSet(key: String, value: Set<String>) { editor.putStringSet(key, value) }
            override fun remove(key: String) { editor.remove(key) }
        }.block()
        editor.apply()
    }
}
