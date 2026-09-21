package com.grindrplus.tasks

import com.grindrplus.GrindrPlus
import com.grindrplus.core.CoroutineHelper.callSuspendFunction
import com.grindrplus.core.Logger
import com.grindrplus.core.Utils.coordsToGeoHash
import com.grindrplus.core.loge
import com.grindrplus.core.logi
import com.grindrplus.utils.Task
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.getObjectField

class AlwaysOnline :
    Task(
        id = "Always Online",
        description = "Keeps you online by periodically fetching cascade",
        initialDelayMillis = 30 * 1000,
        intervalMillis = 5 * 60 * 1000
    ) {
    override suspend fun execute() {
        try {
            val serverDrivenCascadeRepoInstance =
                GrindrPlus.instanceManager.getInstance<Any>(GrindrPlus.serverDrivenCascadeRepo)
                    ?: throw IllegalStateException(
                        "Cascade repo instance ${GrindrPlus.serverDrivenCascadeRepo} not captured yet"
                    )
            val grindrLocationProviderInstance =
                GrindrPlus.instanceManager.getInstance<Any>(GrindrPlus.grindrLocationProvider)
                    ?: throw IllegalStateException(
                        "Location provider ${GrindrPlus.grindrLocationProvider} not captured yet"
                    )

            val location = getObjectField(grindrLocationProviderInstance, "d")
            val latitude = callMethod(location, "getLatitude") as Double
            val longitude = callMethod(location, "getLongitude") as Double
            val geoHash = coordsToGeoHash(latitude, longitude)

            // 26.16.1: us1.a(lat, lon, useNearbyGeoHash, …) → CascadeService.getCascadePage
            // (replaces fetchCascadePage(geoHash, …) on deleted ServerDrivenCascadeRepo).
            val method =
                serverDrivenCascadeRepoInstance.javaClass.methods.firstOrNull { m ->
                    m.name == "a" &&
                        m.parameterTypes.size == 9 &&
                        m.parameterTypes[0] == Double::class.javaPrimitiveType &&
                        m.parameterTypes[1] == Double::class.javaPrimitiveType
                } ?: throw IllegalStateException(
                    "Unable to find us1.a(lat,lon,…) cascade fetch method (geoHash=$geoHash)"
                )

            val result = callSuspendFunction { continuation ->
                method.invoke(
                    serverDrivenCascadeRepoInstance,
                    latitude,
                    longitude,
                    false, // use nearby geohash path as "page" rather than explore
                    null, null, null, null, // optional map bounds
                    null, // page size hint
                    continuation
                )
            }

            if (result != null && !result.toString().contains("Failure")) {
                logi("AlwaysOnline task executed successfully")
            } else {
                loge("AlwaysOnline task failed: $result")
            }
        } catch (e: Exception) {
            loge("Error in AlwaysOnline task: ${e.message}")
            Logger.writeRaw(e.stackTraceToString())
        }
    }
}
