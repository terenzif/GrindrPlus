package com.grindrplus

import android.app.Application
import android.util.Log
import com.grindrplus.core.Constants.GRINDR_PACKAGE_NAME
import com.grindrplus.hooks.spoofSignatures
import com.grindrplus.hooks.sslUnpinning
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookZygoteInit, IXposedHookLoadPackage {
    private lateinit var modulePath: String

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName.startsWith("com.grindrplus")) {
            findAndHookMethod(
                "com.grindrplus.manager.utils.MiscUtilsKt",
                lpparam.classLoader,
                "isLSPosed",
                XC_MethodReplacement.returnConstant(true)
            )
        }

        if (!lpparam.packageName.contains(GRINDR_PACKAGE_NAME)) return

        try {
            spoofSignatures(lpparam)
        } catch (t: Throwable) {
            Log.e("GrindrPlus", "spoofSignatures failed (continuing init): ${t.message}", t)
        }
        if (BuildConfig.DEBUG) {
            try {
                sslUnpinning(lpparam)
            } catch (t: Throwable) {
                Log.w("GrindrPlus", "sslUnpinning failed: ${t.message}")
            }
        }

        Application::class.java.hook("attach", HookStage.AFTER) {
            val application = it.thisObject()
            GrindrPlus.init(modulePath, application,
                BuildConfig.TARGET_GRINDR_VERSION_CODES,
                BuildConfig.TARGET_GRINDR_VERSION_NAMES)
        }
    }
}