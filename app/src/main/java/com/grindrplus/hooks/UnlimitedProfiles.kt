package com.grindrplus.hooks

import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.core.Utils.openProfile
import com.grindrplus.core.loge
import com.grindrplus.core.logi
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.XposedHelpers.getObjectField
import java.lang.reflect.Proxy

// supported version: 26.16.1
class UnlimitedProfiles : Hook(
    "Unlimited profiles",
    "Allow unlimited profiles"
) {
    private val function2 = "kotlin.jvm.functions.Function2"
    // ServerDrivenCascadeViewModel$onProfileClicked removed with Cascade V2 — skip swipe path
    private val onProfileClicked = ""
    private val profileWithPhoto = "com.grindrapp.android.persistence.pojo.ProfileWithPhoto"
    // ServerDrivenCascadeCacheState removed — use CascadeProfileUiData rows + tag cascade click gate
    private val cascadeProfileUiData =
        "com.grindrapp.android.persistence.model.serverdrivencascade.CascadeProfileUiData"
    private val profileTagCascadeFragment = "com.grindrapp.android.ui.tagsearch.ProfileTagCascadeFragment"

    override fun init() {
        // Old getItems filter on CacheState is gone. Null out upsell type via profile list is N/A;
        // CascadeProfileUiData has no getUpsellType — upsells are separate UI models.

        runCatching {
            // Was method O; now z(int) — returns false when position hits Unlimited ad insert
            findClass(profileTagCascadeFragment)
                .hook("z", HookStage.BEFORE) { param ->
                    param.setResult(true)
                }
        }.onFailure { loge("UnlimitedProfiles ProfileTagCascadeFragment: ${it.message}") }

        val profileClass = findClass("com.grindrapp.android.persistence.model.Profile")
        val profileWithPhotoClass = findClass(profileWithPhoto)
        val function2Class = findClass(function2)
        val flowKtClass = findClass("kotlinx.coroutines.flow.FlowKt")
        val profileRepoClass = findClass("com.grindrapp.android.persistence.repository.ProfileRepo")

        profileRepoClass.hook("getProfilesWithPhotosFlow", HookStage.AFTER) { param ->
            val requestedProfileIds = param.arg<List<String>>(0)
            if (requestedProfileIds.isEmpty()) return@hook

            val originalFlow = param.getResult()
            val profileWithPhotoConstructor = profileWithPhotoClass
                .getConstructor(profileClass, List::class.java)
            val profileConstructor = profileClass.getConstructor()

            val proxy = Proxy.newProxyInstance(
                GrindrPlus.classLoader,
                arrayOf(function2Class)
            ) { _, _, args ->
                @Suppress("UNCHECKED_CAST")
                val profilesWithPhoto = args[0] as List<Any>

                if (requestedProfileIds.size > profilesWithPhoto.size) {
                    val profileIds = ArrayList<String>(profilesWithPhoto.size)

                    for (profileWithPhoto in profilesWithPhoto) {
                        val profile = callMethod(profileWithPhoto, "getProfile")
                        profileIds.add(callMethod(profile, "getProfileId") as String)
                    }

                    val profileIdSet = profileIds.toHashSet()

                    val missingProfiles = ArrayList<Any>()
                    for (profileId in requestedProfileIds) {
                        if (profileId !in profileIdSet) {
                            val profile = profileConstructor.newInstance()
                            callMethod(profile, "setProfileId", profileId)
                            callMethod(profile, "setRemoteUpdatedTime", 1L)
                            callMethod(profile, "setLocalUpdatedTime", 0L)
                            missingProfiles.add(
                                profileWithPhotoConstructor.newInstance(profile, emptyList<Any>())
                            )
                        }
                    }

                    if (missingProfiles.isNotEmpty()) {
                        val result = ArrayList<Any>(profilesWithPhoto.size + missingProfiles.size)
                        result.addAll(profilesWithPhoto)
                        result.addAll(missingProfiles)
                        return@newProxyInstance result
                    }
                }

                profilesWithPhoto
            }

            val transformedFlow = callStaticMethod(flowKtClass, "mapLatest", originalFlow, proxy)
            param.setResult(transformedFlow)
        }

        if (onProfileClicked.isNotEmpty()) {
            findClass(onProfileClicked).hook("invokeSuspend", HookStage.BEFORE) { param ->
                if (Config.get("disable_profile_swipe", false) as Boolean) {
                    getObjectField(
                        param.thisObject(),
                        param.thisObject().javaClass.declaredFields
                            .firstOrNull { it.type.name.contains("CascadeProfile") }?.name
                    )?.let { cachedProfile ->
                        runCatching { getObjectField(cachedProfile, "profileId").toString() }
                            .onSuccess { profileId ->
                                openProfile(profileId)
                                param.setResult(null)
                            }
                            .onFailure { loge("Profile ID not found in cached profile") }
                    }
                }
            }
        } else {
            logi("UnlimitedProfiles: onProfileClicked remap skipped (Cascade V2 — no ServerDrivenCascadeViewModel)")
        }

        // Ensure CascadeProfileUiData exists (soft)
        runCatching { findClass(cascadeProfileUiData) }
            .onFailure { loge("UnlimitedProfiles CascadeProfileUiData: ${it.message}") }
    }
}
