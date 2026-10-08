package com.gpp.hooks

import com.gpp.utils.Hook
import com.gpp.utils.HookStage
import com.gpp.utils.RetrofitUtils.RETROFIT_NAME
import com.gpp.utils.RetrofitUtils.createServiceProxy
import com.gpp.utils.RetrofitUtils.findPOSTMethod
import com.gpp.utils.hook

class ProfileViews : Hook(
	"Profile views",
	"Don't let others know you viewed their profile"
) {
    private val profileRestService = "com.grindrapp.android.api.ProfileRestService"
    private val blacklistedPaths = setOf(
        "v4/views/{profileId}",
        "v5/views/{profileId}",
        "v4/views"
    )

    override fun init() {
        val profileRestServiceClass = findClass(profileRestService)

        val methodBlacklist =
            blacklistedPaths.mapNotNull { findPOSTMethod(profileRestServiceClass, it)?.name }

        findClass(RETROFIT_NAME).hook("create", HookStage.AFTER) { param ->
            val service = param.getResult()
            if (service != null && profileRestServiceClass.isAssignableFrom(service.javaClass)) {
                param.setResult(
                    createServiceProxy(
                        service,
                        profileRestServiceClass,
                        methodBlacklist.toTypedArray()
                    )
                )
            }
        }
    }
}
