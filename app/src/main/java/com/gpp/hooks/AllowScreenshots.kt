package com.gpp.hooks

import android.app.Activity
import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.view.Window
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import com.gpp.utils.Hook
import com.gpp.utils.HookStage
import com.gpp.utils.hook

// supported version: 25.20.0
class AllowScreenshots : Hook(
    "Allow screenshots",
    "Allow screenshots everywhere in the app"
) {
    override fun init() {
        Window::class.java.hook("setFlags", HookStage.BEFORE) { param ->
            var flags = param.arg<Int>(0)
            flags = flags and FLAG_SECURE.inv()
            param.setArg(0, flags)
        }

        Activity::class.java.hook("registerScreenCaptureCallback", HookStage.BEFORE) { param ->
            param.setResult(null)
        }

    ContentResolver::class.java.methods.first {
        it.name == "registerContentObserver" &&
                it.parameterTypes.contentEquals(arrayOf(Uri::class.java,
                    Boolean::class.javaPrimitiveType, ContentObserver::class.java))
        }.hook(HookStage.BEFORE) { param ->
            val uri = param.arg<Uri>(0)
            if (uri.host != "media") return@hook
            param.setResult(null)
        }
    }
}