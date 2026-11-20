package com.music.bitchord.data.settings

/**
 * Where [DialSettings] keeps the dial layout: a small key-value store with the
 * shape of Android's SharedPreferences, so the phone keeps reading the very
 * keys and values it always wrote, and the desktop can back it with a file.
 */
interface DialStore {
    fun contains(key: String): Boolean
    fun getString(key: String): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getInt(key: String, default: Int): Int
    fun getStringSet(key: String): Set<String>

    /** Applies [block]'s changes together, as one write. */
    fun edit(block: Editor.() -> Unit)

    interface Editor {
        fun putString(key: String, value: String)
        fun putBoolean(key: String, value: Boolean)
        fun putInt(key: String, value: Int)
        fun putStringSet(key: String, value: Set<String>)
        fun remove(key: String)
    }
}

/** A [DialStore] that keeps everything in memory: for tests, and before a real store is set. */
class InMemoryDialStore(initial: Map<String, Any> = emptyMap()) : DialStore {
    val values = LinkedHashMap<String, Any>(initial)

    override fun contains(key: String) = key in values
    override fun getString(key: String) = values[key] as? String
    override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
    override fun getInt(key: String, default: Int) = values[key] as? Int ?: default

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String): Set<String> = (values[key] as? Set<String>).orEmpty()

    override fun edit(block: DialStore.Editor.() -> Unit) {
        object : DialStore.Editor {
            override fun putString(key: String, value: String) { values[key] = value }
            override fun putBoolean(key: String, value: Boolean) { values[key] = value }
            override fun putInt(key: String, value: Int) { values[key] = value }
            override fun putStringSet(key: String, value: Set<String>) { values[key] = value.toSet() }
            override fun remove(key: String) { values.remove(key) }
        }.block()
    }
}
