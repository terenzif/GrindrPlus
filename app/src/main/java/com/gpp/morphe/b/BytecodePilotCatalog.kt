package com.gpp.morphe.b

/**
 * Pilot fingerprints for Morphe B bytecode (ADR 0007).
 * Needles are ASCII/MUTF-8 substrings expected in tip DEX string pools.
 * Locate-only until a per-feature instruction recipe is proven; do not rewrite
 * Cascade data-class getters (FavoritesHeaderData is not the Favorites layout hook).
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
