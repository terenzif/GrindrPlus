package com.grindrplus.hooks

import android.view.Menu
import android.view.MenuItem
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Obfuscation
import com.grindrplus.core.loge
import com.grindrplus.core.logi
import com.grindrplus.core.mapping.MappingDictionary
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
    override fun init() {
        val profileViewHolder = Obfuscation.G.ProfileDetails.PROFILE_VIEW_HOLDER
        val bindMethod =
            MappingDictionary.resolve("QuickBlock.profileViewHolder.bindMethod", "j")
        val viewBindingField =
            MappingDictionary.resolve("QuickBlock.profileViewHolder.viewBindingField", "b")
        val toolbarField =
            MappingDictionary.resolve("QuickBlock.profileViewHolder.toolbarField", "s")

        if (profileViewHolder.isEmpty() || bindMethod.isEmpty() ||
            viewBindingField.isEmpty() || toolbarField.isEmpty()
        ) {
            logi("QuickBlock: profileViewHolder soft-skip (empty remap)")
        } else {
            runCatching {
                // j(ProfileViewState) binds toolbar including menu_actions
                findClass(profileViewHolder).hook(bindMethod, HookStage.AFTER) { param ->
                    val profileViewState = param.arg(0) as Any
                    val profileId =
                        callMethod(profileViewState, "getProfileId") as? String ?: return@hook
                    val viewBinding = getObjectField(param.thisObject(), viewBindingField)
                    val profileToolbar = getObjectField(viewBinding, toolbarField)
                    val toolbarMenu = callMethod(profileToolbar, "getMenu") as Menu
                    val menuActions = getId("menu_actions", "id", GrindrPlus.context)
                    val actionsMenuItem =
                        callMethod(toolbarMenu, "findItem", menuActions) as MenuItem
                    actionsMenuItem.setOnMenuItemClickListener {
                        GrindrPlus.httpClient.blockUser(profileId)
                        true
                    }
                }
            }.onFailure { loge("QuickBlock profileViewHolder: ${it.message}") }
        }

        val blockViewModel = MappingDictionary.resolve("QuickBlock.blockViewModel", "mu0")
        val showDialogMethod =
            MappingDictionary.resolve("QuickBlock.blockViewModel.showDialogMethod", "J")
        if (blockViewModel.isEmpty() || showDialogMethod.isEmpty()) {
            logi("QuickBlock: blockViewModel soft-skip (empty remap)")
        } else {
            runCatching {
                // J() shows the block confirm dialog — skip UI and block via HTTP
                findClass(blockViewModel).hook(showDialogMethod, HookStage.BEFORE) { param ->
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
}
