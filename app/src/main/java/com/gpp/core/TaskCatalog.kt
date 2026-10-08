package com.gpp.core

/**
 * Settings metadata for background tasks without loading module task implementations.
 * Keep in sync with [com.gpp.utils.TaskManager] registration list.
 */
object TaskCatalog {
    data class Entry(
        val id: String,
        val description: String,
        val defaultEnabled: Boolean = false,
    )

    val entries: List<Entry> = listOf(
        Entry(
            id = "Always Online",
            description = "Keeps you online by periodically fetching cascade",
            defaultEnabled = false,
        ),
    )

    suspend fun registerSettings() {
        entries.forEach { entry ->
            Config.initTaskSettings(entry.id, entry.description, entry.defaultEnabled)
        }
    }
}
