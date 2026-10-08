package com.gpp

import android.app.Application
import android.os.Bundle
import com.gpp.core.Constants.GRINDR_PACKAGE_NAME
import com.gpp.core.DeviceFlags
import com.gpp.core.LogSource
import com.gpp.core.Logger
import com.gpp.debug.AgentDebugLog
import com.gpp.hooks.installPairIpEarlyBypass
import com.gpp.hooks.spoofSignatures
import com.gpp.hooks.sslUnpinning
import com.gpp.utils.HookHandleRegistry
import com.gpp.utils.HookStage
import com.gpp.utils.hook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * libxposed API 102 entry (ADR 0008). Sole Java entry for Alloy + embed.
 */
class GppXposedModule : XposedModule() {
    private var modulePath: String = ""
    private var grindrReady: Boolean = false

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        GppXposed.attach(this)
        modulePath = moduleApplicationInfo.sourceDir ?: ""
        GppXposed.logFrameworkCaps()
        Logger.i(
            "GppXposedModule loaded process=${param.processName} system=${param.isSystemServer}",
            LogSource.MODULE,
        )
        // #region agent log
        AgentDebugLog.log(
            hypothesisId = "H1",
            location = "GppXposedModule.onModuleLoaded",
            message = "module_loaded",
            data = mapOf(
                "processName" to param.processName,
                "isSystemServer" to param.isSystemServer,
                "modulePathEmpty" to modulePath.isEmpty(),
            ),
        )
        // #endregion
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        val pkg = param.packageName
        if (pkg.startsWith("com.gpp")) {
            hookSelfFlags(param)
            return
        }
        if (!isGrindrHost(pkg)) return
        // #region agent log
        AgentDebugLog.log(
            hypothesisId = "H1",
            location = "GppXposedModule.onPackageLoaded",
            message = "grindr_package_loaded",
            data = mapOf("packageName" to pkg),
        )
        // #endregion
        // PairIP must be neutralized before ContentProvider / Application.attachBaseContext.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            runCatching {
                installPairIpEarlyBypass(param.defaultClassLoader)
            }.onFailure {
                Logger.w("PairIP early bypass failed: ${it.message}", LogSource.MODULE)
                // #region agent log
                AgentDebugLog.log(
                    hypothesisId = "H16",
                    location = "GppXposedModule.onPackageLoaded",
                    message = "pairip_bypass_failed",
                    data = mapOf("error" to (it.message ?: it.javaClass.simpleName)),
                )
                // #endregion
            }
        }
        // Early hooks that need default classloader (API 29+)
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                spoofSignatures(param.defaultClassLoader, pkg)
                // DEBUG ssl unpinning deferred until PairIP is proven stable on device.
            }
        }.onFailure {
            Logger.w("onPackageLoaded early hooks: ${it.message}", LogSource.MODULE)
            // #region agent log
            AgentDebugLog.log(
                hypothesisId = "H7",
                location = "GppXposedModule.onPackageLoaded",
                message = "early_hooks_failed",
                data = mapOf("error" to (it.message ?: it.javaClass.simpleName)),
            )
            // #endregion
        }
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (!isGrindrHost(param.packageName)) return
        if (grindrReady) return
        grindrReady = true
        // #region agent log
        AgentDebugLog.log(
            hypothesisId = "H9",
            location = "GppXposedModule.onPackageReady",
            message = "install_attach_hook",
            data = mapOf("packageName" to param.packageName),
        )
        // #endregion
        installApplicationAttachHook()
    }

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        return try {
            param.setSavedInstanceState(
                Bundle().apply {
                    putString("modulePath", modulePath)
                    putBoolean("grindrReady", grindrReady)
                },
            )
            runCatching { GrindrPlus.cleanupForHotReload() }
            true
        } catch (t: Throwable) {
            Logger.w("onHotReloading rejected: ${t.message}", LogSource.MODULE)
            false
        }
    }

    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        GppXposed.attach(this)
        HookHandleRegistry.replaceAll(param.oldHookHandles)
        val saved = param.savedInstanceState as? Bundle
        modulePath = saved?.getString("modulePath")
            ?: moduleApplicationInfo.sourceDir
            ?: ""
        grindrReady = false
        GppXposed.logFrameworkCaps()
        Logger.i("GppXposedModule hot-reloaded — re-init on next Application.attach", LogSource.MODULE)
        installApplicationAttachHook()
    }

    private fun installApplicationAttachHook() {
        Application::class.java.hook("attach", HookStage.AFTER) {
            // #region agent log
            AgentDebugLog.log(
                hypothesisId = "H9",
                location = "GppXposedModule.attach",
                message = "attach_after_enter",
                data = mapOf("thisClass" to (it.thisObject()?.javaClass?.name ?: "null")),
            )
            // #endregion
            val application = it.thisObject()
            GrindrPlus.init(
                modulePath.ifEmpty { moduleApplicationInfo.sourceDir ?: "" },
                application,
            )
            grindrReady = true
        }
    }

    private fun isGrindrHost(packageName: String): Boolean =
        packageName.contains(GRINDR_PACKAGE_NAME)

    private fun hookSelfFlags(param: XposedModuleInterface.PackageLoadedParam) {
        runCatching {
            val cl = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                param.defaultClassLoader
            } else {
                return
            }
            val flags = cl.loadClass(DeviceFlags::class.java.name)
            val method = flags.getDeclaredMethod("isLsPosed")
            hook(method).setId("gpp:DeviceFlags.isLsPosed").intercept { _ -> true }
        }.onFailure {
            Logger.w("self-hook isLsPosed failed: ${it.message}", LogSource.MODULE)
        }
    }
}
