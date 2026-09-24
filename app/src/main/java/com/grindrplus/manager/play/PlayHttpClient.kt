package com.grindrplus.manager.play

import com.aurora.gplayapi.data.models.PlayResponse
import com.aurora.gplayapi.network.IHttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Working [IHttpClient] for gplayapi.
 * Aurora's [com.aurora.gplayapi.network.DefaultHttpClient] stubs [postAuth]/[getAuth]
 * (returns 444); Aurora Store ships its own client — we do the same here.
 */
class PlayHttpClient(
    @Volatile var userAgent: String = "com.aurora.store-4.7.4-76",
) : IHttpClient {

    private val _responseCode = MutableStateFlow(100)
    override val responseCode: StateFlow<Int> = _responseCode.asStateFlow()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun post(url: String, headers: Map<String, String>, body: ByteArray): PlayResponse {
        val request = Request.Builder()
            .url(url)
            .headers(headers.toHeaders())
            .post(body.toRequestBody(null, 0, body.size))
            .build()
        return process(request)
    }

    override fun post(
        url: String,
        headers: Map<String, String>,
        params: Map<String, String>,
    ): PlayResponse {
        val request = Request.Builder()
            .url(buildUrl(url, params))
            .headers(headers.toHeaders())
            .post("".toRequestBody(null))
            .build()
        return process(request)
    }

    override fun get(url: String, headers: Map<String, String>): PlayResponse =
        get(url, headers, emptyMap())

    override fun get(
        url: String,
        headers: Map<String, String>,
        params: Map<String, String>,
    ): PlayResponse {
        val request = Request.Builder()
            .url(buildUrl(url, params))
            .headers(headers.toHeaders())
            .get()
            .build()
        return process(request)
    }

    override fun get(
        url: String,
        headers: Map<String, String>,
        paramString: String,
    ): PlayResponse {
        val request = Request.Builder()
            .url(url + paramString)
            .headers(headers.toHeaders())
            .get()
            .build()
        return process(request)
    }

    override fun getAuth(url: String): PlayResponse {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .get()
            .build()
        return process(request)
    }

    override fun postAuth(url: String, body: ByteArray): PlayResponse {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .post(body.toRequestBody("application/json".toMediaType(), 0, body.size))
            .build()
        return process(request)
    }

    private fun buildUrl(url: String, params: Map<String, String>) =
        url.toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

    private fun process(request: Request): PlayResponse {
        _responseCode.value = 0
        return okHttpClient.newCall(request).execute().use { response ->
            toPlayResponse(response)
        }
    }

    private fun toPlayResponse(response: Response): PlayResponse {
        val bytes = response.body?.bytes() ?: ByteArray(0)
        return PlayResponse(
            isSuccessful = response.isSuccessful,
            code = response.code,
            responseBytes = bytes,
            errorString = if (!response.isSuccessful) response.message else "",
        ).also { _responseCode.value = response.code }
    }
}
