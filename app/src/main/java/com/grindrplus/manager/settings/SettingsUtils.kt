package com.grindrplus.manager.settings

import android.content.Context
import com.grindrplus.manager.ui.ApiKeyTestStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object SettingsUtils {
    fun testMapsApiKey(
        context: Context,
        viewModelScope: CoroutineScope,
        apiKey: String,
        showTestDialog: (Boolean, ApiKeyTestStatus, String, String, String) -> Unit
    ) {
        showTestDialog(true, ApiKeyTestStatus.ERROR, "Testing", "Testing your API key...", "")

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val url = URL("https://maps.googleapis.com/maps/api/geocode/json?address=USA&key=$apiKey")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000

                    val responseCode = connection.responseCode
                    val input = if (responseCode >= 400) {
                        connection.errorStream
                    } else {
                        connection.inputStream
                    }

                    val response = input.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)
                    val status = jsonResponse.optString("status", "")
                    val errorMessage = if (jsonResponse.has("error_message"))
                        jsonResponse.getString("error_message") else ""

                    Triple(status, errorMessage, response)
                }

                val (status, errorMessage, rawResponse) = result
                when {
                    status == "OK" -> {
                        showTestDialog(
                            false,
                            ApiKeyTestStatus.SUCCESS,
                            "Success!",
                            "Your Google Maps API key is working correctly. You can use it with GrindrPlus.",
                            rawResponse
                        )
                    }
                    status == "REQUEST_DENIED" && errorMessage.isNotEmpty() -> {
                        if (errorMessage.contains("API key is invalid")) {
                            showTestDialog(
                                false,
                                ApiKeyTestStatus.ERROR,
                                "Invalid API Key",
                                "Your API key is invalid. Please double-check that you've copied it correctly.",
                                rawResponse
                            )
                        } else if (errorMessage.contains("not authorized")) {
                            showTestDialog(
                                false,
                                ApiKeyTestStatus.ERROR,
                                "API Not Enabled",
                                "Your API key is valid but you need to enable the Geocoding API in the Google Cloud Console.",
                                rawResponse
                            )
                        } else {
                            showTestDialog(
                                false,
                                ApiKeyTestStatus.ERROR,
                                "API Error",
                                "Error: $errorMessage",
                                rawResponse
                            )
                        }
                    }
                    else -> {
                        showTestDialog(
                            false,
                            ApiKeyTestStatus.WARNING,
                            "Warning",
                            "API returned $status status",
                            rawResponse
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showTestDialog(
                    false,
                    ApiKeyTestStatus.ERROR,
                    "Connection Error",
                    "Failed to connect to Google Maps API: ${e.message}",
                    e.stackTraceToString()
                )
            }
        }
    }
}
