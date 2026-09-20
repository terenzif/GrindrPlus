package com.grindrplus.hooks

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Logger
import com.grindrplus.core.logd
import com.grindrplus.core.loge
import com.grindrplus.core.logi
import com.grindrplus.ui.Utils.copyToClipboard
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import de.robv.android.xposed.XposedHelpers.getObjectField

// supported version: 26.16.1 (static JADX — device confirmation required)
class EnableUnlimited : Hook(
    "Enable unlimited",
    "Enable Grindr Unlimited features"
) {
    private val profileViewState = "com.grindrapp.android.ui.profileV2.model.ProfileViewState"
    private val profileModel = "com.grindrapp.android.persistence.model.Profile"
    private val tabLayoutClass = "com.google.android.material.tabs.TabLayout"

    // Interface xub static b() shows app_restart_required dialog (was dk.c.d)
    private val paywallUtils = "xub"
    // ViewBinding for persistent_banner_ad_compose_view (was Z4.a)
    private val persistentAdBannerContainer = "h68"
    // Interstitial subscribe classes not re-resolved on 26.16.1 — empty until device/JADX follow-up
    private val subscribeToInterstitialsList = emptyList<String>()
    private val viewsToHide = mapOf(
        // FunctionReferenceImpl bind helpers (default package on 26.16.1)
        "ny8" to listOf("upsell_bottom_bar"), // ProfileTagCascadeFragmentBinding
        "yr3" to listOf(
            "plans_title",
            "store_in_profile_drawer_card",
            "sideDrawerBoostContainer",
            "drawer_profile_offer_card"
        ), // DrawerProfileBinding
        "eb9" to listOf("micros_fab", "right_now_fabs_container") // FragmentRadarBinding
        // CascadeFragment → CascadeFragmentV2 (Compose): binding fingerprint not found yet
    )

    override fun init() {
        val userSessionClass = findClass(GrindrPlus.userSession)
        userSessionClass.hook( // rolesUpdated() — was "W", now Y(List)
            "Y", HookStage.BEFORE
        ) { param ->
            val allRoles = listOf(
                "Plus",
                "Xtra",
                "Unlimited",
                "Premium",
                "Free_Plus",
                "Free_Unlimited",
                "Free_Premium"
            )

            logd("Updating user roles to enable unlimited features")

            param.setArg(0, allRoles)
        }

        // Prevent leak of faked roles into HTTP headers (UserSession.E() on 26.16.1; was "F")
        userSessionClass.hook(
            "E", HookStage.BEFORE
        ) { param ->
            param.setResult("[]")
        }

        subscribeToInterstitialsList.forEach { className ->
            runCatching {
                findClass(className)
                    .hook("emit", HookStage.BEFORE) { param ->
                        val modelName = param.arg<Any>(0)::class.java.name
                        if (!modelName.contains("NoInterstitialCreated")
                            && !modelName.contains("OnInterstitialDismissed")
                        ) {
                            param.setResult(null)
                        }
                    }
            }.onFailure {
                loge("Skip interstitial hook $className: ${it.message}")
            }
        }

        runCatching {
            findClass(tabLayoutClass).hook("addTab", HookStage.AFTER) { param ->
                val blockedTabs = mapOf(
                    4 to "Store"
                )

                val tab = param.arg<Any>(0)
                val position = getObjectField(tab, "position") as? Int ?: -1

                logd("Trying to add tab at position $position")

                if (position in blockedTabs.keys) {
                    val tabName = blockedTabs[position] ?: "Unknown"

                    val tabView = getObjectField(tab, "view") as? View
                    tabView?.let { view ->
                        (view.parent as? ViewGroup)?.removeView(view)
                        logi("Removed tab '$tabName' at position $position")
                    }
                }
            }
        }.onFailure {
            loge("Skip tabLayout hook: ${it.message}")
        }

        viewsToHide.forEach { (className, viewIds) ->
            runCatching {
                findClass(className).hook(
                    "invoke", HookStage.AFTER
                ) { param ->
                    if (param.args().isNotEmpty()) {
                        val rootView = param.arg<View>(0)
                        hideViews(rootView, viewIds)
                    }
                }
            }.onFailure {
                loge("Skip viewsToHide $className: ${it.message}")
            }
        }

        runCatching {
            findClass(persistentAdBannerContainer).hook("a", HookStage.BEFORE) { param ->
                if (param.args().isNotEmpty()) {
                    val rootView = param.arg<View>(0)
                    hideViews(
                        rootView,
                        listOf("persistent_banner_ad_container", "persistent_banner_ad_compose_view")
                    )
                }
            }
        }.onFailure {
            loge("Skip persistent ad banner hook: ${it.message}")
        }

        setOf("isBlockable", "component60").forEach { method ->
            runCatching {
                findClass(profileModel).hook(method, HookStage.BEFORE) { param ->
                    param.setResult(true)
                }
            }.onFailure {
                loge("Skip profileModel.$method: ${it.message}")
            }
        }

        runCatching {
            // Static helper on interface xub (was instance method d on dk.c)
            findClass(paywallUtils).hook("b", HookStage.BEFORE) { param ->
                val stackTrace = Thread.currentThread().stackTrace.dropWhile {
                    !it.toString().contains("LSPHooker")
                }.drop(1).joinToString("\n")

                val activity = GrindrPlus.currentActivity
                if (activity != null) {
                    android.app.AlertDialog.Builder(activity)
                        .setTitle("Paywalled Feature Detected")
                        .setMessage(
                            "This feature is server-enforced and cannot be bypassed in this version.\n\n" +
                                "If you think this is a mistake, please report it to the developer. " +
                                "You can copy the stack trace below to help with troubleshooting."
                        )
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setCancelable(false)
                        .setNegativeButton("Copy Stack Trace") { _, _ ->
                            copyToClipboard(
                                "Stack trace",
                                stackTrace
                            )
                        }
                        .setPositiveButton("Ok", null)
                        .show()
                }

                param.setResult(null)
            }
        }.onFailure {
            loge("Skip paywallUtils hook: ${it.message}")
        }

        runCatching {
            findClass(profileViewState).hook("isChatPaywalled", HookStage.BEFORE) { param ->
                param.setResult(false)
            }
        }.onFailure {
            loge("Skip isChatPaywalled hook: ${it.message}")
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun hideViews(rootView: View, viewIds: List<String>) {
        viewIds.forEach { viewId ->
            try {
                val id = rootView.resources.getIdentifier(
                    viewId, "id", "com.grindrapp.android"
                )
                if (id > 0) {
                    val view = rootView.findViewById<View>(id)
                    if (view != null) {
                        logd("View with ID: $viewId found and will be hidden")
                        val params = view.layoutParams
                        params.height = 0
                        view.layoutParams = params
                        view.visibility = View.GONE
                    }
                } else {
                    logd("View with ID: $viewId not found")
                }
            } catch (e: Exception) {
                loge("Error hiding view with ID: $viewId: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
            }
        }
    }
}
