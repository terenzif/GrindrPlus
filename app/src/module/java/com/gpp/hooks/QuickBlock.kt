package com.gpp.hooks

import android.view.Menu
import android.view.MenuItem
import com.gpp.GrindrPlus
import com.gpp.core.Obfuscation
import com.gpp.core.loge
import com.gpp.core.logi
import com.gpp.core.mapping.MappingDictionary
import com.gpp.ui.Utils.getId
import com.gpp.utils.Hook
import com.gpp.utils.HookStage
import com.gpp.utils.SoftSkipException
import com.gpp.utils.hook
import com.gpp.utils.compat.XposedHelpers.callMethod
import com.gpp.utils.compat.XposedHelpers.getObjectField

class QuickBlock : Hook(
    "Quick block",
    "Ability to block users quickly"
) {
    override fun init() {
        var hooked = 0
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
                hooked++
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
                hooked++
            }.onFailure { loge("QuickBlock blockViewModel: ${it.message}") }
        }

        if (hooked == 0) {
            throw SoftSkipException("QuickBlock: no sites hooked (missing remaps)")
        }
    }
}
