package com.grindrplus.core

import com.grindrplus.GrindrPlus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object NetworkRepository {
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient()

    fun fetchRemoteData(url: String, callback: (List<Pair<Long, Int>>) -> Unit) {
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Logger.e("Failed to fetch remote data: ${e.message}", LogSource.MODULE)
                Logger.writeRaw(e.stackTraceToString())
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    it.body?.string()?.let { jsonString ->
                        try {
                            val jsonArray = JSONArray(jsonString)
                            val parsedPoints = mutableListOf<Pair<Long, Int>>()

                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val time = obj.getLong("time")
                                val id = obj.getInt("id")
                                parsedPoints.add(time to id)
                            }

                            callback(parsedPoints)
                        } catch (e: Exception) {
                            Logger.e("Failed to parse remote data: ${e.message}", LogSource.MODULE)
                            Logger.writeRaw(e.stackTraceToString())
                        }
                    }
                }
            }
        })
    }

    fun fetchOwnUserId() {
        ioScope.launch {
            try {
                Logger.d("Fetching own user ID...", LogSource.MODULE)
                GrindrPlus.httpClient.sendRequest(
                    url = "https://grindr.mobi/v5/me/profile",
                    method = "GET"
                ).use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        if (!responseBody.isNullOrEmpty()) {
                            val jsonResponse = JSONObject(responseBody)
                            val profilesArray = jsonResponse.optJSONArray("profiles")

                            if (profilesArray != null && profilesArray.length() > 0) {
                                val profile = profilesArray.getJSONObject(0)
                                val profileId = profile.optString("profileId")

                                if (profileId.isNotEmpty()) {
                                    GrindrPlus.myProfileId = profileId
                                } else {
                                    Logger.w("Profile ID field is empty in response", LogSource.MODULE)
                                }
                            } else {
                                Logger.w("No profiles array found in response", LogSource.MODULE)
                            }
                        } else {
                            Logger.w("Empty response body from profile endpoint", LogSource.MODULE)
                        }
                    } else {
                        Logger.e("Failed to fetch own profile: HTTP ${response.code}", LogSource.MODULE)
                    }
                }
            } catch (e: Exception) {
                Logger.e("Error fetching own user ID: ${e.message}", LogSource.MODULE)
                Logger.writeRaw(e.stackTraceToString())
            }
        }
    }
}
