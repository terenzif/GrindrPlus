package com.grindrplus.core.http

import com.grindrplus.core.Logger
import com.grindrplus.core.LogSource
import com.grindrplus.core.mapping.MappingDictionary
import de.robv.android.xposed.XposedBridge
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.TimeZone
import okhttp3.Request.Builder
import okhttp3.ResponseBody.Companion.toResponseBody
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

class Interceptor(
    private val userSession: Any,
    private val userAgent: Any,
    private val deviceInfo: Any
) : Interceptor {

    private fun modifyRequest(originalRequest: Request): Request {
        val isLoggedInMethod = MappingDictionary.resolve("http.userSession.isLoggedInMethod", "r")
        val isLoggedIn = if (isLoggedInMethod.isEmpty()) {
            Logger.i("HTTP: isLoggedInMethod soft-skip (empty remap)", LogSource.HTTP)
            false
        } else {
            // search for 'getJwt().length() > 0 &&' in userSession
            invokeMethodSafe(userSession, isLoggedInMethod) as? Boolean ?: false
        }

        val builder: Builder = originalRequest.newBuilder()

        if (isLoggedIn) {
            val authTokenFlowMethod =
                MappingDictionary.resolve("http.userSession.authTokenFlowMethod", "x")
            val authToken = if (authTokenFlowMethod.isEmpty()) {
                Logger.i("HTTP: authTokenFlowMethod soft-skip (empty remap)", LogSource.HTTP)
                ""
            } else {
                // search for 'return FlowKt.asStateFlow' in userSession (return type is StateFlow<String>)
                val authTokenFlow = invokeMethodSafe(userSession, authTokenFlowMethod)
                if (authTokenFlow != null) {
                    invokeMethodSafe(authTokenFlow, "getValue") as? String ?: ""
                } else {
                    ""
                }
            }

            val rolesMethod = MappingDictionary.resolve("http.userSession.rolesMethod", "E")
            val roles = if (rolesMethod.isEmpty()) {
                Logger.i("HTTP: rolesMethod soft-skip (empty remap)", LogSource.HTTP)
                null
            } else {
                // Roles string for L-Grindr-Roles (was "F" on 25.20.0; UserSession.E() on 26.16.1)
                invokeMethodSafe(userSession, rolesMethod) as? String ?: ""
            }

            if (authToken.isNotEmpty()) {
                builder.header("Authorization", "Grindr3 $authToken")
                if (roles != null) {
                    builder.header("L-Grindr-Roles", roles)
                }
            } else {
                Logger.w("Auth token is empty, skipping auth headers", LogSource.HTTP)
            }

            builder.header("L-Time-Zone", TimeZone.getDefault().id)

            val deviceInfoLazyField =
                MappingDictionary.resolve("http.deviceInfo.lazyField", "d")
            if (deviceInfoLazyField.isEmpty()) {
                Logger.i("HTTP: deviceInfo.lazyField soft-skip (empty remap)", LogSource.HTTP)
            } else {
                // search for 'public final kotlin.Lazy' in deviceInfo
                val deviceInfoLazy = getFieldSafe(deviceInfo, deviceInfoLazyField) as? Any
                val lDeviceInfo = if (deviceInfoLazy != null) {
                    invokeMethodSafe(deviceInfoLazy, "getValue") as? String ?: ""
                } else {
                    ""
                }

                if (lDeviceInfo.isNotEmpty()) {
                    builder.header("L-Device-Info", lDeviceInfo)
                }
            }
        } else {
            builder.header("L-Time-Zone", "Unknown")
        }

        val userAgentStringMethod =
            MappingDictionary.resolve("http.userAgent.stringMethod", "a")
        val userAgentString = if (userAgentStringMethod.isEmpty()) {
            Logger.i("HTTP: userAgent.stringMethod soft-skip (empty remap)", LogSource.HTTP)
            "Grindr"
        } else {
            // search for 'getValue().getNameTitleCase()' in userAgent
            invokeMethodSafe(userAgent, userAgentStringMethod) as? String ?: "Grindr"
        }

        builder.header("Accept", "application/json; charset=UTF-8")
        builder.header("User-Agent", userAgentString)
        builder.header("L-Locale", "en_US")
        builder.header("Accept-language", "en-US")

        return builder.build()
    }

    private fun invokeMethodSafe(obj: Any?, methodName: String): Any? {
        return try {
            if (obj == null) {
                Logger.w("Object is null when trying to invoke method: $methodName", LogSource.HTTP)
                return null
            }

            val method = obj::class.java.getMethod(methodName)
            val result = method.invoke(obj)
            result
        } catch (e: NoSuchMethodException) {
            Logger.e("Method not found: $methodName on ${obj?.javaClass?.simpleName}", LogSource.HTTP)
            null
        } catch (e: Exception) {
            Logger.e("Failed to invoke method $methodName: ${e.message}", LogSource.HTTP)
            Logger.writeThrowable(e)
            null
        }
    }

    private fun getFieldSafe(obj: Any?, fieldName: String): Any? {
        return try {
            if (obj == null) {
                Logger.w("Object is null when trying to get field: $fieldName", LogSource.HTTP)
                return null
            }

            val field = obj::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            val value = field.get(obj)
            value
        } catch (e: NoSuchFieldException) {
            Logger.e("Field not found: $fieldName on ${obj?.javaClass?.simpleName}", LogSource.HTTP)
            null
        } catch (e: Exception) {
            Logger.e("Failed to get field $fieldName: ${e.message}", LogSource.HTTP)
            Logger.writeThrowable(e)
            null
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        return try {
            val modifiedRequest = modifyRequest(request)
            Logger.d("Intercepting request to: ${request.url}", LogSource.HTTP)
            chain.proceed(modifiedRequest)
        } catch (e: SocketTimeoutException) {
            Logger.e("Request timeout: ${e.message}", LogSource.HTTP)
            createErrorResponse(request, 408, "Request Timeout")
        } catch (e: TimeoutException) {
            Logger.e("Request timeout: ${e.message}", LogSource.HTTP)
            createErrorResponse(request, 408, "Request Timeout")
        } catch (e: IOException) {
            Logger.e("Network error: ${e.message}", LogSource.HTTP)
            Logger.writeThrowable(e)
            createErrorResponse(request, 503, "Network Error")
        } catch (e: Exception) {
            Logger.e("Unexpected error: ${e.message}", LogSource.HTTP)
            Logger.writeThrowable(e)
            createErrorResponse(request, 500, "Internal Error")
        }
    }

    private fun createErrorResponse(request: Request, code: Int, message: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .body("".toResponseBody(null))
            .build()
    }
}
