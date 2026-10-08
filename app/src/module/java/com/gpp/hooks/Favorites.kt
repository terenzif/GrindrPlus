package com.gpp.hooks

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.children
import com.gpp.GrindrPlus
import com.gpp.alloy.AlloyDexKit
import com.gpp.core.Config
import com.gpp.core.DeliveryChannel
import com.gpp.core.mapping.MappingDictionary
import com.gpp.ui.Utils
import com.gpp.utils.Hook
import com.gpp.utils.HookStage
import com.gpp.utils.SoftSkipException
import com.gpp.utils.hook
import com.gpp.utils.compat.XposedHelpers.callMethod
import com.gpp.utils.compat.XposedHelpers.getObjectField
import kotlin.math.roundToInt
import androidx.core.view.isGone

class Favorites : Hook(
    "Favorites",
    "Customize layout for the favorites tab"
) {
    private val recyclerViewLayoutParams =
        "androidx.recyclerview.widget.RecyclerView\$LayoutParams"
    private val favoritesFragment = MappingDictionary.resolve(
        "Favorites.FRAGMENT",
        "com.grindrapp.android.favorites.presentation.ui.FavoritesFragment"
    )

    override fun init() {
        val fragmentClass = resolveFavoritesFragmentClass()
            ?: throw SoftSkipException(
                "FavoritesFragment absent (Cascade V2) — remap Favorites.FRAGMENT or Alloy DexKit CascadeFavorites miss"
            )

        val recyclerViewLayoutParamsConstructor = findClass(recyclerViewLayoutParams)
            .getDeclaredConstructor(Int::class.java, Int::class.java)

        fragmentClass
            .hook("onViewCreated", HookStage.AFTER) { param ->
                val columnsNumber = (Config.get("favorites_grid_columns", 3) as Number).toInt()
                val view = param.arg<View>(0)
                val recyclerView = view.findViewById<View>(
                    Utils.getId(
                        "fragment_favorite_recycler_view",
                        "id", GrindrPlus.context
                    )
                )
                val gridLayoutManager = callMethod(
                    recyclerView, "getLayoutManager"
                )

                callMethod(gridLayoutManager, "setSpanCount", columnsNumber)
                val adapter = callMethod(recyclerView, "getAdapter")

                adapter.javaClass
                    .hook("onBindViewHolder", HookStage.AFTER) { param ->
                        val size = GrindrPlus.context
                            .resources.displayMetrics.widthPixels / columnsNumber
                        val rootLayoutParams = recyclerViewLayoutParamsConstructor
                            ?.newInstance(size, size) as? ViewGroup.LayoutParams

                        val itemView = getObjectField(
                            param.arg(
                                0
                            ), "itemView"
                        ) as View
                        itemView.layoutParams = rootLayoutParams

                        val distanceTextView =
                            itemView.findViewById<TextView>(
                                Utils.getId(
                                    "profile_distance", "id", GrindrPlus.context
                                )
                            )

                        var linearLayout = distanceTextView.parent as LinearLayout
                        linearLayout.orientation = LinearLayout.VERTICAL
                        linearLayout.children.forEach { child ->
                            child.layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        }

                        distanceTextView.gravity = Gravity.START

                        val profileOnlineNowIcon = itemView.findViewById<ImageView>(
                            Utils.getId(
                                "profile_online_now_icon",
                                "id", GrindrPlus.context
                            )
                        )
                        val profileLastSeen = itemView.findViewById<TextView>(
                            Utils.getId("profile_last_seen", "id", GrindrPlus.context)
                        )

                        val lastSeenLayoutParams = profileLastSeen
                            .layoutParams as LinearLayout.LayoutParams
                        if (profileOnlineNowIcon.isGone) {
                            lastSeenLayoutParams.topMargin = 0
                        } else {
                            lastSeenLayoutParams.topMargin = TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP, 5f,
                                GrindrPlus.context.resources.displayMetrics
                            ).roundToInt()
                        }
                        profileLastSeen.layoutParams = lastSeenLayoutParams

                        val profileNoteIcon = itemView.findViewById<ImageView>(
                            Utils.getId(
                                "profile_note_icon",
                                "id", GrindrPlus.context
                            )
                        )
                        val profileDisplayName = itemView.findViewById<TextView>(
                            Utils.getId(
                                "profile_display_name",
                                "id", GrindrPlus.context
                            )
                        )

                        val displayNameLayoutParams = profileDisplayName
                            .layoutParams as LinearLayout.LayoutParams
                        if (profileNoteIcon.isGone) {
                            displayNameLayoutParams.topMargin = 0
                        } else {
                            displayNameLayoutParams.topMargin = TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP, 4f,
                                GrindrPlus.context.resources.displayMetrics
                            ).roundToInt()
                        }
                        profileDisplayName.layoutParams = displayNameLayoutParams
                    }
            }
    }

    /**
     * Pack remaps first; on Alloy, DexKit string search is a soft fallback (ADR 0006).
     */
    private fun resolveFavoritesFragmentClass(): Class<*>? {
        try {
            return findClass(favoritesFragment)
        } catch (_: Throwable) {
            // fall through
        }
        if (!DeliveryChannel.current.isRootedModule) return null
        if (!AlloyDexKit.ensureInitialized(GrindrPlus.context)) return null
        // Tip 26.16.1 Cascade V2: FavoritesFragment / fragment_favorite_recycler_view are gone.
        // Prefer FavoritesFragment string first; Cascade UI-model hits are not Fragments.
        val candidates = listOfNotNull(
            AlloyDexKit.findClassByStrings("FavoritesFragment"),
            AlloyDexKit.findClassByStrings("fragment_favorite_recycler_view"),
            AlloyDexKit.findClassByStrings("CascadeFavoritesItemUiModel"),
            AlloyDexKit.findClassByStrings("FavoritesHeaderData"),
        )
        return candidates.firstOrNull { isFragmentWithOnViewCreated(it) }
    }

    private fun isFragmentWithOnViewCreated(clazz: Class<*>): Boolean {
        var c: Class<*>? = clazz
        var fragment = false
        while (c != null) {
            val n = c.name
            if (n == "androidx.fragment.app.Fragment" || n == "android.app.Fragment") {
                fragment = true
                break
            }
            c = c.superclass
        }
        if (!fragment) return false
        return try {
            clazz.getMethod("onViewCreated", View::class.java, android.os.Bundle::class.java)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
