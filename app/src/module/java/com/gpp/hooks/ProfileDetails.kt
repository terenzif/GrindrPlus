package com.gpp.hooks

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.gpp.GrindrPlus
import com.gpp.core.Config
import com.gpp.core.Logger
import com.gpp.core.Obfuscation
import com.gpp.core.Utils
import com.gpp.core.Utils.calculateBMI
import com.gpp.core.Utils.h2n
import com.gpp.core.Utils.w2n
import com.gpp.core.logw
import com.gpp.debug.AgentDebugLog
import com.gpp.ui.Utils.copyToClipboard
import com.gpp.ui.Utils.formatEpochSeconds
import com.gpp.ui.Utils.getId
import com.gpp.utils.Hook
import com.gpp.utils.HookStage
import com.gpp.utils.hook
import com.gpp.utils.hookConstructor
import com.gpp.utils.compat.XposedHelpers.callMethod
import com.gpp.utils.compat.XposedHelpers.getObjectField
import com.gpp.utils.compat.XposedHelpers.setObjectField
import java.util.ArrayList
import kotlin.math.roundToInt

class ProfileDetails : Hook(
	"Profile details",
	"Add extra fields and details to profiles"
) {
    private var boostedProfilesList = emptyList<String>()

    @SuppressLint("DefaultLocale")
    override fun init() {
        // ServerDrivenCascadeCacheState removed — track boosting from CascadeProfileUiData ctor
        runCatching {
            findClass(Obfuscation.G.ProfileDetails.CASCADE_PROFILE_UI_DATA)
                .hookConstructor(HookStage.AFTER) { param ->
                    val profile = param.thisObject()
                    if (callMethod(profile, "isBoosting") as Boolean) {
                        boostedProfilesList += callMethod(profile, "getProfileId") as String
                    }
                }
        }.onFailure {
            logw("ProfileDetails CascadeProfileUiData: ${it.message}")
        }

        val blockedObserver = Obfuscation.G.ProfileDetails.BLOCKED_PROFILES_OBSERVER
        if (blockedObserver.isNotEmpty()) {
            findClass(blockedObserver).hook("onChanged", HookStage.AFTER) { param ->
                // recently got merged into a case statement, so filter for the right argument type
                if ((getObjectField(param.thisObject(), "a") as Int) != 0) return@hook

                // what is the expected class?It is Object in the decompiled source
                val obj = getObjectField(param.thisObject(), "b")
                val profileList = getObjectField(obj, "o") as ArrayList<*>

                for (profile in profileList) {
                    val profileId = callMethod(profile, "getProfileId") as String
                    val displayName =
                        (callMethod(profile, "getDisplayName") as? String)
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { "$it ($profileId)" } ?: profileId
                    setObjectField(profile, "displayName", displayName)
                }
            }
        }

        val profileViewHolder = Obfuscation.G.ProfileDetails.PROFILE_VIEW_HOLDER
        if (profileViewHolder.isNotEmpty()) {
            // r29 uses view binding field b → o0d; long-press on display name remains via ProfileBarView
            runCatching {
                findClass(profileViewHolder)
            }.onFailure {
                logw("ProfileDetails PROFILE_VIEW_HOLDER: ${it.message}")
            }
        }

        findClass(Obfuscation.G.ProfileDetails.PROFILE_BAR_VIEW).hook("setProfile", HookStage.BEFORE) { param ->
            val profileId = getObjectField(param.arg(0), "profileId") as String
            // #region agent log
            AgentDebugLog.log(
                hypothesisId = "H20",
                location = "ProfileDetails.setProfile",
                message = "profile_bar_set",
                data = mapOf("profileIdLen" to profileId.length),
                runId = "e2e-features",
            )
            // #endregion
            val accountCreationTime =
                formatEpochSeconds(GrindrPlus.spline.invert(profileId.toDouble()).toLong())
            val distance = callMethod(param.arg(0), "getDistance") ?: "Unknown (hidden)"
            setObjectField(param.arg(0), "distance", distance)

            if (profileId in boostedProfilesList) {
                val lastSeen = callMethod(param.arg(0), "getLastSeenText")
                setObjectField(param.arg(0), "lastSeenText", "$lastSeen (Boosting)")
            }

            val displayName = callMethod(param.arg(0), "getDisplayName") ?: profileId
            setObjectField(param.arg(0), "displayName", displayName)

            val barView = param.thisObject() as View
            val nameTargets = resolveProfileNameTargets(barView)
            // #region agent log
            AgentDebugLog.log(
                hypothesisId = "H24",
                location = "ProfileDetails.setProfile",
                message = "name_targets",
                data = mapOf(
                    "count" to nameTargets.size,
                    "ids" to nameTargets.map { it.second }.joinToString(","),
                ),
                runId = "e2e-features",
            )
            // #endregion
            if (nameTargets.isEmpty()) {
                logw("ProfileDetails: no profile name/near/lastSeen TextViews found")
                return@hook
            }

            val showHiddenDetails = View.OnClickListener { clicked ->
                // #region agent log
                AgentDebugLog.log(
                    hypothesisId = "H20",
                    location = "ProfileDetails.displayNameClick",
                    message = "hidden_details_dialog",
                    data = mapOf(
                        "profileIdLen" to profileId.length,
                        "hasCreation" to accountCreationTime.isNotEmpty(),
                        "via" to ((clicked as? TextView)?.resources?.getResourceEntryName(clicked.id) ?: "?"),
                    ),
                    runId = "e2e-features",
                )
                // #endregion
                val properties =
                    mapOf(
                        "Estimated creation" to accountCreationTime,
                        "Profile ID" to profileId,
                        "Approximate distance" to
                                Utils.safeGetField(param.arg(0), "approximateDistance") as? Boolean,
                        "Favorite" to
                                Utils.safeGetField(param.arg(0), "isFavorite") as? Boolean,
                        "From viewed me" to
                                Utils.safeGetField(param.arg(0), "isFromViewedMe") as? Boolean,
                        "JWT boosting" to
                                Utils.safeGetField(param.arg(0), "isJwtBoosting") as? Boolean,
                        "New" to Utils.safeGetField(param.arg(0), "isNew") as? Boolean,
                        "Teleporting" to
                                Utils.safeGetField(param.arg(0), "isTeleporting") as? Boolean,
                        "Online now" to
                                Utils.safeGetField(param.arg(0), "onlineNow") as? Boolean,
                        "Is roaming" to
                                Utils.safeGetField(param.arg(0), "isRoaming") as? Boolean,
                        "Found via roam" to
                                Utils.safeGetField(param.arg(0), "foundViaRoam") as? Boolean,
                        "Is top pick" to
                                Utils.safeGetField(param.arg(0), "isTopPick") as? Boolean,
                        "Is visiting" to
                                Utils.safeGetField(param.arg(0), "isVisiting") as? Boolean,
                        "Is distance approximate" to
                                Utils.safeGetField(param.arg(0), "approximateDistance") as? Boolean,
                    )
                        .filterValues { it != null }

                val detailsText = properties.map { (key, value) -> "• $key: $value" }.joinToString("\n")

                val dialog =
                    AlertDialog.Builder(clicked.context)
                        .setTitle("Hidden profile details")
                        .setMessage(detailsText)
                        .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                        .setNeutralButton("Copy Details") { dialog, _ ->
                            copyToClipboard("Profile Details", detailsText)
                            GrindrPlus.showToast(Toast.LENGTH_SHORT, "Profile details copied to clipboard")
                            dialog.dismiss()
                        }
                        .create()

                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.WHITE)
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(android.graphics.Color.WHITE)
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.WHITE)
                }

                dialog.show()
            }

            nameTargets.forEach { (tv, _) ->
                tv.setOnLongClickListener {
                    GrindrPlus.showToast(Toast.LENGTH_LONG, "Profile ID: $profileId")
                    copyToClipboard("Profile ID", profileId)
                    true
                }
                tv.setOnClickListener(showHiddenDetails)
            }
        }

        // iq3.c(double, approx, show, special, abbreviated, isFeet?) — isFeet null → SettingsPref.b()
        findClass(Obfuscation.G.ProfileDetails.DISTANCE_UTILS)
            .hook(Obfuscation.G.ProfileDetails.DISTANCE_UTILS_METHOD, HookStage.AFTER) { param ->
            val distance = param.arg<Double>(0)
            val isFeet = param.argNullable<Boolean>(5) ?: run {
                val settingsPref = getObjectField(param.thisObject(), "b")
                callMethod(settingsPref, "b") as Boolean
            }

            param.setResult(
                if (isFeet) {
                    val feet = (distance * 3.280839895).roundToInt()
                    if (feet < 5280) {
                        String.format("%d feet", feet)
                    } else {
                        String.format("%d miles %d feet", feet / 5280, feet % 5280)
                    }
                } else {
                    val meters = distance.roundToInt()
                    if (meters < 1000) {
                        String.format("%d meters", meters)
                    } else {
                        String.format("%d km %d m", meters / 1000, meters % 1000)
                    }
                }
            )
        }

        findClass(Obfuscation.G.ProfileDetails.PROFILE_VIEW_STATE).hook("getWeight", HookStage.AFTER) { param ->
            if (Config.get("show_bmi_in_profile", true) as Boolean) {
                val weight = param.getResult()
                val height = callMethod(param.thisObject(), "getHeight")

                if (weight != null && height != null) {
                    val BMI =
                        calculateBMI(
                            "kg" in weight.toString(),
                            w2n("kg" in weight.toString(), weight.toString()),
                            h2n("kg" in weight.toString(), height.toString())
                        )
                    if (Config.get("do_gui_safety_checks", true) as Boolean) {
                        if (weight.toString().contains("(")) {
                            logw("BMI details are already present?")
                            return@hook
                        }
                    }
                    val annotated =
                        "$weight - ${String.format("%.1f", BMI)} (${
                            mapOf(
                                "Underweight" to 18.5,
                                "Normal weight" to 24.9,
                                "Overweight" to 29.9,
                                "Obese" to Double.MAX_VALUE
                            ).entries.first { it.value > BMI }.key
                        })"
                    // #region agent log
                    AgentDebugLog.log(
                        hypothesisId = "H21",
                        location = "ProfileDetails.getWeight",
                        message = "bmi_annotated",
                        data = mapOf(
                            "bmiRounded" to String.format("%.1f", BMI),
                            "resultLen" to annotated.length,
                        ),
                        runId = "e2e-features",
                    )
                    // #endregion
                    param.setResult(annotated)
                }
            }
        }
    }

    /**
     * ProfileBarView stores views in an obfuscated `binding` field; resource ids stay stable.
     * Prefer findViewById over view-binding field walks (H24).
     */
    private fun resolveProfileNameTargets(barView: View): List<Pair<TextView, String>> {
        val ctx = barView.context
        val names = listOf(
            "profile_display_name",
            "profile_near_text",
            "profile_last_seen_text",
            "profile_height_weight_tone",
        )
        val found = mutableListOf<Pair<TextView, String>>()
        for (name in names) {
            val id = getId(name, "id", ctx)
            if (id == 0) continue
            val tv = barView.findViewById<TextView>(id) ?: continue
            found += tv to name
        }
        if (found.isNotEmpty()) return found

        // Fallback: inflate-time binding field (confirmed as `binding` on recent packs).
        runCatching {
            val binding = getObjectField(barView, "binding") ?: return@runCatching
            for (field in binding.javaClass.declaredFields) {
                field.isAccessible = true
                val value = field.get(binding) as? TextView ?: continue
                val entry = runCatching { value.resources.getResourceEntryName(value.id) }.getOrNull()
                    ?: field.name
                if (entry.contains("display_name") || entry.contains("near_text") ||
                    entry.contains("last_seen") || entry.contains("height_weight")
                ) {
                    found += value to entry
                }
            }
        }
        return found
    }
}
