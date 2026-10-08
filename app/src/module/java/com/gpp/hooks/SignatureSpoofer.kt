package com.gpp.hooks

import android.content.ContextWrapper
import com.gpp.GppXposed
import com.gpp.core.Constants.GRINDR_PACKAGE_NAME
import com.gpp.utils.HookStage
import com.gpp.utils.compat.XposedHelpers
import com.gpp.utils.hook

private const val packageSignature = "823f5a17c33b16b4775480b31607e7df35d67af8"
private const val firebaseInstallationServiceClient =
    "com.google.firebase.installations.remote.FirebaseInstallationServiceClient"
private const val configRealtimeHttpClient =
    "com.google.firebase.remoteconfig.internal.ConfigRealtimeHttpClient"
private const val configFetchHttpClient =
    "com.google.firebase.remoteconfig.internal.ConfigFetchHttpClient"

@OptIn(ExperimentalStdlibApi::class)
fun spoofSignatures(classLoader: ClassLoader, packageName: String = GRINDR_PACKAGE_NAME) {
    listOf(
        firebaseInstallationServiceClient,
        configRealtimeHttpClient,
        configFetchHttpClient,
    ).forEach { className ->
        runCatching {
            XposedHelpers.findClass(className, classLoader)
                .hook("getFingerprintHashForPackage", HookStage.BEFORE) { param ->
                    param.setResult(packageSignature)
                }
        }
    }

    runCatching {
        XposedHelpers.findClass("ly.img.android.c", classLoader)
            .hook("d", HookStage.BEFORE) { param ->
                param.setResult(GRINDR_PACKAGE_NAME)
            }
    }

    runCatching {
        val requestClass = XposedHelpers.findClass(
            "com.facebook.login.LoginClient\$Request",
            classLoader,
        )
        val handler = XposedHelpers.findClass(
            "com.facebook.login.KatanaProxyLoginMethodHandler",
            classLoader,
        )
        val method = XposedHelpers.findMethodExact(handler, "tryAuthorize", requestClass)
        GppXposed.require().hook(method).setId("gpp:fb.tryAuthorize").intercept { chain ->
            chain.proceed()
            0
        }
    }

    if (packageName != GRINDR_PACKAGE_NAME) {
        fun isFirebaseInstallationServiceClient() = Thread.currentThread().stackTrace.any {
            it.className.startsWith(firebaseInstallationServiceClient)
        }

        ContextWrapper::class.java.hook("getPackageName", HookStage.AFTER) { param ->
            if (isFirebaseInstallationServiceClient()) {
                param.setResult(GRINDR_PACKAGE_NAME)
            }
        }

        runCatching {
            XposedHelpers.findClass("com.google.firebase.messaging.Metadata", classLoader)
                .hook("getPackageInfo", HookStage.BEFORE) { param ->
                    val pkg = param.arg<String>(0)
                    if (pkg.contains("grindr")) {
                        param.setArg(0, GRINDR_PACKAGE_NAME)
                    }
                }
        }
    }
}
