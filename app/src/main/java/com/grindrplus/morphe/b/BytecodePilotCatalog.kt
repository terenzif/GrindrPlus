package com.grindrplus.morphe.b

/**
 * Pilot fingerprints for [FingerprintScanBytecodeBackend] (ADR 0007 dry-run).
 * Needles are ASCII/MUTF-8 substrings expected in tip DEX string pools.
 */
data class BytecodeFingerprint(
    val id: String,
    /** Links to [MorpheBPatchDescriptor.id] when applicable. */
    val patchId: String,
    val needles: List<String>,
)

object BytecodePilotCatalog {
    val fingerprints: List<BytecodeFingerprint> = listOf(
        BytecodeFingerprint(
            id = "favorites-cascade",
            patchId = "favorites",
            needles = listOf(
                "CascadeFavoritesItemUiModel",
                "FavoritesHeaderData",
                "fragment_favorite_recycler_view",
                "FavoritesFragment",
            ),
        ),
        BytecodeFingerprint(
            id = "chat-terminal-meta",
            patchId = "chat-terminal",
            needles = listOf(
                "chatMessageMetaData",
            ),
        ),
    )
}
