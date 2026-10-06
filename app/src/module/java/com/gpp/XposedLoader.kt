package com.gpp

import android.app.Application
import com.gpp.core.Constants.GRINDR_PACKAGE_NAME
import com.gpp.hooks.spoofSignatures
import com.gpp.hooks.sslUnpinning
import com.gpp.utils.HookStage
import com.gpp.utils.hook
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
        if (lpparam.packageName.startsWith("com.gpp")) {
            findAndHookMethod(
                "com.gpp.core.DeviceFlags",
                lpparam.classLoader,
                "isLSPosed",
                XC_MethodReplacement.returnConstant(true)
            )
        }

        if (!lpparam.packageName.contains(GRINDR_PACKAGE_NAME)) return

        spoofSignatures(lpparam)
        if (BuildConfig.DEBUG) {
            sslUnpinning(lpparam)
        }

        Application::class.java.hook("attach", HookStage.AFTER) {
            val application = it.thisObject()
            // TARGET_* arrays are tip/hint only — init soft-continues on mismatch (Wave 2).
            GrindrPlus.init(modulePath, application,
                BuildConfig.TARGET_GRINDR_VERSION_CODES,
                BuildConfig.TARGET_GRINDR_VERSION_NAMES)
        }
    }
}