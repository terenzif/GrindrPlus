package com.grindrplus.hooks

import android.view.Menu
import android.view.MenuItem
import com.grindrplus.GrindrPlus
import com.grindrplus.core.loge
import com.grindrplus.ui.Utils.getId
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.getObjectField

// supported version: 26.16.1
class QuickBlock : Hook(
    "Quick block",
    "Ability to block users quickly"
) {
    private val blockViewModel = "mu0" // STATUS_BLOCK_DIALOG_SHOWN in ju0; dialog in J()
    // Was ui.profileV2.g — profile RecyclerView ViewHolder with menu_actions is r29
    private val profileViewHolder = "r29"

    override fun init() {
        runCatching {
            // j(ProfileViewState) binds toolbar including menu_actions
            findClass(profileViewHolder).hook("j", HookStage.AFTER) { param ->
                val profileViewState = param.arg(0) as Any
                val profileId = callMethod(profileViewState, "getProfileId") as? String ?: return@hook
                val viewBinding = getObjectField(param.thisObject(), "b")
                val profileToolbar = getObjectField(viewBinding, "s")
                val toolbarMenu = callMethod(profileToolbar, "getMenu") as Menu
                val menuActions = getId("menu_actions", "id", GrindrPlus.context)
                val actionsMenuItem = callMethod(toolbarMenu, "findItem", menuActions) as MenuItem
                actionsMenuItem.setOnMenuItemClickListener {
                    GrindrPlus.httpClient.blockUser(profileId)
                    true
                }
            }
        }.onFailure { loge("QuickBlock profileViewHolder: ${it.message}") }

        runCatching {
            // J() shows the block confirm dialog — skip UI and block via HTTP
            findClass(blockViewModel).hook("J", HookStage.BEFORE) { param ->
                val profileId = param.thisObject().javaClass.declaredFields
                    .asSequence()
                    .filter { it.type == String::class.java }
                    .mapNotNull { field ->
                        try {
                            field.isAccessible = true
                            field.get(param.thisObject()) as? String
                        } catch (e: Exception) {
                            null
                        }
                    }
                    .firstOrNull { it.isNotEmpty() && it.all { char -> char.isDigit() } }
                if (profileId != null) {
                    GrindrPlus.httpClient.blockUser(profileId)
                }
                param.setResult(null)
            }
        }.onFailure { loge("QuickBlock blockViewModel: ${it.message}") }
    }
}
