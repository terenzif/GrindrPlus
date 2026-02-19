package com.grindrplus.core.http

import com.grindrplus.core.http.Client
import com.grindrplus.core.http.Interceptor
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.lang.reflect.Field
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

class ClientTest {

    @Test
    fun testSendRequestBlocking() {
        // Prepare dependencies
        // Interceptor uses reflection on these objects, passing generic Object() is safe as long as methods are not found (logs error)
        val mockInterceptor = Interceptor(Object(), Object(), Object())
        val client = Client(mockInterceptor)

        // Inject a custom OkHttpClient that intercepts requests and returns a dummy response
        val mockHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("Test Response".toResponseBody(null))
                    .build()
            }
            .build()

        injectHttpClient(client, mockHttpClient)

        // Execute blocking request
        val response = client.sendRequest("http://test.url")

        // Verify
        assertTrue(response.isSuccessful)
        assertEquals("Test Response", response.body?.string())
    }

    @Test
    fun testSendRequestAsync() = runTest {
        val mockInterceptor = Interceptor(Object(), Object(), Object())
        val client = Client(mockInterceptor)

        val mockHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                // Simulate delay to prove non-blocking behavior if we measured time
                // Thread.sleep(100)
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("Async Response".toResponseBody(null))
                    .build()
            }
            .build()

        injectHttpClient(client, mockHttpClient)

        val response = client.sendRequestAsync("http://test.url")

        assertTrue(response.isSuccessful)
        assertEquals("Async Response", response.body?.string())
    }

    private fun injectHttpClient(client: Client, httpClient: OkHttpClient) {
        val field = Client::class.java.getDeclaredField("httpClient")
        field.isAccessible = true
        field.set(client, httpClient)
    }
}
