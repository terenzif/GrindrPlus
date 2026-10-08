package com.gpp.hooks

import android.os.Bundle
import com.gpp.debug.AgentDebugLog
import com.gpp.utils.HookStage
import com.gpp.utils.compat.XposedHelpers
import com.gpp.utils.hook
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * PairIP early soft-fail for Alloy lab (evidence only — not a Play product path).
 *
 * Grindr 26.19 + Vector (A2 / tombstone docs/lab/pairip):
 * - Real libpairipcore SIGSEGV/SIGILL under Vector.
 * - v11 stub via findLibrary → init_ok, then SIGILL in libsqliteJni!JNI_OnLoad
 *   (encrypted native .text; not fixable by stubbing executeVM).
 * - Product direction: Morphe B static decrypt (ADR 0009).
 *
 * Retained: detect PairIP, stub Java entrypoints, seed string bags, wire
 * trampolines — enough for lab diagnosis on PairIP hosts.
 */
fun installPairIpEarlyBypass(classLoader: ClassLoader) {
    val pairIpPresent = runCatching {
        XposedHelpers.findClass("com.pairip.VMRunner", classLoader)
        true
    }.getOrDefault(false)

    // #region agent log
    AgentDebugLog.log(
        hypothesisId = "H30",
        location = "PairIpEarlyBypass.install",
        message = "install_begin_v11",
        data = mapOf(
            "strategy" to "findlibrary_stub_so_vm_seed",
            "pairIpPresent" to pairIpPresent,
        ),
        runId = "pairip-v11",
    )
    // #endregion

    if (!pairIpPresent) {
        // #region agent log
        AgentDebugLog.log(
            hypothesisId = "H30",
            location = "PairIpEarlyBypass.install",
            message = "skip_no_pairip",
            data = emptyMap(),
            runId = "pairip-v11",
        )
        // #endregion
        return
    }

    var stubs = 0

    // Redirect pairipcore to Magisk-pushed stub .so without hooking System.loadLibrary
    // (Vector+LSPlant loadLibrary hooks break split-APK native library namespaces).
    val stubPath = "/data/local/tmp/gpp_libpairipcore_stub.so"
    runCatching {
        val bdc = Class.forName("dalvik.system.BaseDexClassLoader")
        val findLib = bdc.getDeclaredMethod("findLibrary", String::class.java)
        findLib.isAccessible = true
        findLib.hook(HookStage.BEFORE) { param ->
            val name = param.argNullable<String>(0) ?: return@hook
            if (name == "pairipcore") {
                val f = java.io.File(stubPath)
                if (f.isFile) {
                    param.setResult(stubPath)
                    // #region agent log
                    AgentDebugLog.log(
                        hypothesisId = "H35",
                        location = "PairIpEarlyBypass.findLibrary",
                        message = "redirect_pairipcore_stub",
                        data = mapOf("path" to stubPath, "size" to f.length()),
                        runId = "pairip-v11",
                    )
                    // #endregion
                }
            }
        }
        stubs++
    }.onFailure { err ->
        // #region agent log
        AgentDebugLog.log(
            hypothesisId = "H35",
            location = "PairIpEarlyBypass.findLibrary",
            message = "redirect_failed",
            data = mapOf("error" to (err.message ?: err.javaClass.simpleName)),
            runId = "pairip-v11",
        )
        // #endregion
    }

    fun stubVoid(className: String, methodName: String) {
        runCatching {
            // findClass uses Class.forName(..., false) — avoids VMRunner clinit/loadLibrary.
            val clazz = XposedHelpers.findClass(className, classLoader)
            clazz.declaredMethods
                .filter { it.name == methodName && !Modifier.isAbstract(it.modifiers) }
                .forEach { method ->
                    method.hook(HookStage.BEFORE) { param ->
                        param.setResult(null)
                    }
                    stubs++
                }
        }
    }

    stubVoid("com.pairip.SignatureCheck", "verifyIntegrity")
    stubVoid("com.pairip.StartupLauncher", "launch")
    stubVoid("com.pairip.licensecheck.LicenseClient", "checkLicense")
    stubVoid("com.pairip.VMRunner", "invoke")

    runCatching {
        XposedHelpers.findClass("com.pairip.licensecheck.LicenseContentProvider", classLoader)
            .hook("onCreate", HookStage.BEFORE) { param ->
                param.setResult(true)
            }
        stubs++
    }

    val wired = wireSyntheticTrampolines(classLoader)
    val filled = blankFillPairIpStringBags(classLoader)
    val nullSafe = installPairIpNullStringGuards(classLoader)

    // #region agent log
    AgentDebugLog.log(
        hypothesisId = "H30",
        location = "PairIpEarlyBypass.install",
        message = "install_done_v11",
        data = mapOf(
            "stubs" to stubs,
            "wired" to wired,
            "stringFieldsFilled" to filled,
            "nullSafeHooks" to nullSafe,
        ),
        runId = "pairip-v11",
    )
    // #endregion
}

/**
 * Known plaintext values for PairIP string-bag fields (stock 26.19 / 185656).
 * Empty-string fill is NOT safe for Room DB names (`StringsKt.isBlank` → NPE/abort).
 */
private val PAIRIP_STRING_SEEDS: Map<String, Map<String, String>> = mapOf(
    "net.pubnative.lite.sdk.models.SNQT.CjuQlTlTXCv" to mapOf(
        // hx0.w → PublicAssignmentsDatabase (device databases/ listing)
        "pwxhCBaPzCWgEeM" to "public_grindr_assignments.db",
    ),
    "com.grindrapp.android.rightnow.data.model.vhV.wWaUCzkOUbnstH" to mapOf(
        // ppa.i3 — sibling of plaintext "FIRST_TPA_NATIVE_REFRESH_INBOX_ADS"
        "sAzTFUjmV" to "SECOND_TPA_NATIVE_REFRESH_INBOX_ADS",
    ),
    "com.google.zxing.Hx.jjPgxRUOfNpJqE" to mapOf(
        // dc6 filter prefs — between by_tags and gender_id; placeholder avoids NPE
        "EvirVOy" to "filter_cascade_pairip_evirvoy",
    ),
)

/** Log-prefix bags where "" is acceptable (Firebase Perf formatting). */
private val PAIRIP_BLANK_OK_BAGS = listOf(
    "ly.img.android.pesdk.ui.viewholder.NA.doTDPhdfgIKaw",
)

/**
 * PairIP StartupLauncher VM decrypts into static String bags. Without native VM they
 * stay null — seed known critical values, blank-fill log-only bags.
 */
private fun blankFillPairIpStringBags(classLoader: ClassLoader): Int {
    var filled = 0

    for ((holder, seeds) in PAIRIP_STRING_SEEDS) {
        runCatching {
            val clazz = XposedHelpers.findClass(holder, classLoader)
            var seeded = 0
            for ((fieldName, value) in seeds) {
                val field = clazz.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(null, value)
                seeded++
                filled++
            }
            // #region agent log
            AgentDebugLog.log(
                hypothesisId = "H32",
                location = "PairIpEarlyBypass.stringBag",
                message = "seed_known",
                data = mapOf("holder" to holder, "seeded" to seeded),
                runId = "pairip-v9",
            )
            // #endregion
        }.onFailure { err ->
            // #region agent log
            AgentDebugLog.log(
                hypothesisId = "H32",
                location = "PairIpEarlyBypass.stringBag",
                message = "seed_failed",
                data = mapOf(
                    "holder" to holder,
                    "error" to (err.message ?: err.javaClass.simpleName),
                ),
                runId = "pairip-v9",
            )
            // #endregion
        }
    }

    for (name in PAIRIP_BLANK_OK_BAGS) {
        runCatching {
            val clazz = XposedHelpers.findClass(name, classLoader)
            var beforeNull = 0
            for (field in clazz.declaredFields) {
                if (!Modifier.isStatic(field.modifiers)) continue
                if (field.type != String::class.java) continue
                field.isAccessible = true
                if (field.get(null) == null) {
                    beforeNull++
                    field.set(null, "")
                    filled++
                }
            }
            // #region agent log
            AgentDebugLog.log(
                hypothesisId = "H31",
                location = "PairIpEarlyBypass.stringBag",
                message = "blank_fill",
                data = mapOf("holder" to name, "nullBefore" to beforeNull),
                runId = "pairip-v9",
            )
            // #endregion
        }
    }
    return filled
}

/**
 * Survive remaining null PairIP strings: StringBuilder(null) NPE, Firebase Perf,
 * and Room DB builder (`xi4.B`) when the name arg is still a null encrypted field.
 */
private fun installPairIpNullStringGuards(classLoader: ClassLoader): Int {
    var hooks = 0

    runCatching {
        StringBuilder::class.java.getConstructor(String::class.java).hook(HookStage.BEFORE) { param ->
            if (param.argNullable<String>(0) == null) {
                param.setArg(0, "")
            }
        }
        hooks++
    }

    // Firebase Performance transport — drop work that NPEs on null PairIP strings.
    runCatching {
        XposedHelpers.findClass("co20", classLoader)
            .declaredMethods
            .filter { it.name == "d" && !Modifier.isStatic(it.modifiers) }
            .forEach { method ->
                method.hook(HookStage.BEFORE) { param ->
                    param.setResult(null)
                }
                hooks++
            }
        // #region agent log
        AgentDebugLog.log(
            hypothesisId = "H31",
            location = "PairIpEarlyBypass.firebasePerf",
            message = "co20_d_noop",
            data = emptyMap(),
            runId = "pairip-v9",
        )
        // #endregion
    }

    runCatching {
        XposedHelpers.findClass(
            "com.google.firebase.perf.FirebasePerformance",
            classLoader,
        ).hook("setPerformanceCollectionEnabled", HookStage.BEFORE) { param ->
            param.setArg(0, false)
        }
        hooks++
    }

    // Room databaseBuilder — recover null/blank PairIP DB names by entity class.
    runCatching {
        val roomDbNames = mapOf(
            "com.grindrapp.android.platform.featureflags.data.persistence.PublicAssignmentsDatabase" to
                "public_grindr_assignments.db",
            "com.grindrapp.android.persistence.database.AppDatabase" to "grindr_user_1.db",
            "com.grindrapp.android.platform.featureflags.data.persistence.AssignmentsDatabase" to
                "grindr_assignments.db",
            "com.grindrapp.android.chat.v2.data.datasource.database.ChatDatabase" to
                "grindr_chat_v2_final.db",
        )
        XposedHelpers.findClass("xi4", classLoader)
            .declaredMethods
            .filter { it.name == "B" && Modifier.isStatic(it.modifiers) }
            .forEach { method ->
                method.hook(HookStage.BEFORE) { param ->
                    val cls = param.argNullable<Class<*>>(1) ?: return@hook
                    val name = param.argNullable<String>(2)
                    if (name.isNullOrBlank()) {
                        val fallback = roomDbNames[cls.name] ?: return@hook
                        param.setArg(2, fallback)
                        // #region agent log
                        AgentDebugLog.log(
                            hypothesisId = "H32",
                            location = "PairIpEarlyBypass.xi4.B",
                            message = "db_name_fallback",
                            data = mapOf(
                                "dbClass" to cls.name.substringAfterLast('.'),
                                "fallback" to fallback,
                                "wasNull" to (name == null),
                            ),
                            runId = "pairip-v9",
                        )
                        // #endregion
                    }
                }
                hooks++
            }
    }

    // Immutable map builder used by Hilt (ppa.i3 / zlh workers) — skip null PairIP keys.
    runCatching {
        XposedHelpers.findClass("sn8", classLoader)
            .declaredMethods
            .filter { it.name == "f" && it.parameterTypes.size == 2 }
            .forEach { method ->
                method.hook(HookStage.BEFORE) { param ->
                    val key = param.argNullable<Any>(0)
                    val value = param.argNullable<Any>(1)
                    if (key == null || value == null) {
                        // #region agent log
                        AgentDebugLog.log(
                            hypothesisId = "H33",
                            location = "PairIpEarlyBypass.sn8.f",
                            message = "skip_null_entry",
                            data = mapOf(
                                "keyNull" to (key == null),
                                "valueNull" to (value == null),
                                "valueClass" to (value?.javaClass?.simpleName ?: "null"),
                            ),
                            runId = "pairip-v9",
                        )
                        // #endregion
                        param.setResult(null)
                    }
                }
                hooks++
            }
    }

    // DataStore preference APIs — PairIP-encrypted keys arrive null and NPE on getClass().
    fun coerceNullStringArgs(owner: String, methodNames: Set<String>) {
        runCatching {
            XposedHelpers.findClass(owner, classLoader)
                .declaredMethods
                .filter { it.name in methodNames }
                .forEach { method ->
                    method.hook(HookStage.BEFORE) { param ->
                        val args = param.args()
                        var coerced = false
                        for (i in args.indices) {
                            if (method.parameterTypes.getOrNull(i) == String::class.java && args[i] == null) {
                                param.setArg(i, "pairip_missing_$i")
                                coerced = true
                            }
                        }
                        if (coerced) {
                            // #region agent log
                            AgentDebugLog.log(
                                hypothesisId = "H34",
                                location = "PairIpEarlyBypass.$owner.${method.name}",
                                message = "coerced_null_pref_args",
                                data = emptyMap(),
                                runId = "pairip-v9",
                            )
                            // #endregion
                        }
                    }
                    hooks++
                }
        }
    }
    coerceNullStringArgs("mwa", setOf("j", "k", "l", "m", "n", "o"))
    coerceNullStringArgs("c060", setOf("m0", "n0", "o0", "p0"))

    // Last resort: foreground kills on any uncaught NPE (seen with DefaultDispatcher).
    runCatching {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (error is NullPointerException) {
                // #region agent log
                AgentDebugLog.log(
                    hypothesisId = "H34",
                    location = "PairIpEarlyBypass.UEH",
                    message = "swallowed_npe",
                    data = mapOf(
                        "thread" to thread.name,
                        "err" to (error.message?.take(120) ?: "NPE"),
                    ),
                    runId = "pairip-v9",
                )
                // #endregion
                return@setDefaultUncaughtExceptionHandler
            }
            previous?.uncaughtException(thread, error)
        }
        hooks++
        // #region agent log
        AgentDebugLog.log(
            hypothesisId = "H34",
            location = "PairIpEarlyBypass.UEH",
            message = "installed",
            data = emptyMap(),
            runId = "pairip-v9",
        )
        // #endregion
    }

    return hooks
}

/**
 * PairIP trampolines call static Method fields that the VM normally fills.
 * Synthetic `$00x` accessors already perform the correct non-virtual `super` call.
 */
private fun wireSyntheticTrampolines(classLoader: ClassLoader): Int {
    var wired = 0

    fun findSynthetic(owner: Class<*>, prefix: String, vararg params: Class<*>): Method? =
        owner.declaredMethods.firstOrNull { m ->
            m.name.startsWith(prefix) &&
                Modifier.isStatic(m.modifiers) &&
                m.parameterTypes.contentEquals(params)
        }?.also { it.isAccessible = true }

    fun setStaticMethodField(holderClass: String, fieldName: String, method: Method?): Boolean {
        if (method == null) return false
        return runCatching {
            val holder = XposedHelpers.findClass(holderClass, classLoader)
            val field = holder.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, method)
            true
        }.getOrDefault(false)
    }

    runCatching {
        val realApp = XposedHelpers.findClass("com.grindrapp.android.RealApplication", classLoader)
        val gki = XposedHelpers.findClass("gki", classLoader)
        val synth = findSynthetic(realApp, "onCreate$", gki)
        if (setStaticMethodField(
                "com.grindrapp.android.ui.spotify.Hhls.mGXl",
                "gakLNM",
                synth,
            )
        ) {
            wired++
            // #region agent log
            AgentDebugLog.log(
                hypothesisId = "H30",
                location = "PairIpEarlyBypass.wire",
                message = "wired_gakLNM",
                data = mapOf("synth" to (synth?.name ?: "null")),
                runId = "pairip-v9",
            )
            // #endregion
        }
    }

    runCatching {
        val home = XposedHelpers.findClass(
            "com.grindrapp.android.ui.home.HomeActivity",
            classLoader,
        )
        val hilt = XposedHelpers.findClass(
            "com.grindrapp.android.ui.home.Hilt_HomeActivity",
            classLoader,
        )
        val singleStart = XposedHelpers.findClass(
            "com.grindrapp.android.ui.base.SingleStartActivity",
            classLoader,
        )
        val appCompat = XposedHelpers.findClass(
            "androidx.appcompat.app.AppCompatActivity",
            classLoader,
        )

        val onCreate = findSynthetic(home, "onCreate$", hilt, Bundle::class.java)
        val onResume = findSynthetic(home, "onResume$", singleStart)
        val onStop = findSynthetic(home, "onStop$", appCompat)
        val onDestroy = findSynthetic(home, "onDestroy$", hilt)

        val holder = "com.grindrapp.android.ui.spotify.Hhls.mGXl"
        if (setStaticMethodField(holder, "sZPhvwWjcBZmeJL", onCreate)) wired++
        if (setStaticMethodField(holder, "OtUJb", onResume)) wired++
        if (setStaticMethodField(holder, "GfIj", onStop)) wired++

        // onDestroy uses a different holder (inmobi); wire if present.
        runCatching {
            val inmobi = XposedHelpers.findClass(
                "com.inmobi.media.ads.nativeAd.ZpS.sZFfxZ",
                classLoader,
            )
            val field = inmobi.getDeclaredField("riv")
            field.isAccessible = true
            if (onDestroy != null) {
                field.set(null, onDestroy)
                wired++
            }
        }

        // #region agent log
        AgentDebugLog.log(
            hypothesisId = "H30",
            location = "PairIpEarlyBypass.wire",
            message = "wired_home",
            data = mapOf(
                "onCreate" to (onCreate?.name ?: "null"),
                "onResume" to (onResume?.name ?: "null"),
                "onStop" to (onStop?.name ?: "null"),
                "onDestroy" to (onDestroy?.name ?: "null"),
            ),
            runId = "pairip-v9",
        )
        // #endregion
    }.onFailure { err ->
        // #region agent log
        AgentDebugLog.log(
            hypothesisId = "H30",
            location = "PairIpEarlyBypass.wire",
            message = "wire_home_failed",
            data = mapOf("error" to (err.message ?: err.javaClass.simpleName)),
            runId = "pairip-v9",
        )
        // #endregion
    }

    return wired
}
