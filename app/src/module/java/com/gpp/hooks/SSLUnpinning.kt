package com.gpp.hooks

import android.annotation.SuppressLint
import com.gpp.utils.HookStage
import com.gpp.utils.compat.XposedHelpers
import com.gpp.utils.hook
import com.gpp.utils.hookConstructor
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@OptIn(ExperimentalStdlibApi::class)
@SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier")
fun sslUnpinning(classLoader: ClassLoader) {
    runCatching {
        XposedHelpers.findClass("okhttp3.OkHttpClient\$Builder", classLoader)
            .hookConstructor(HookStage.AFTER) { param ->
                XposedHelpers.callMethod(
                    param.thisObject(),
                    "sslSocketFactory",
                    unsafeSslContext.socketFactory,
                    unsafeTrustManager,
                )
                XposedHelpers.setObjectField(
                    param.thisObject(),
                    "hostnameVerifier",
                    HostnameVerifier { _: String?, _: SSLSession? -> true },
                )
            }
    }

    runCatching {
        val builder = XposedHelpers.findClass("okhttp3.OkHttpClient\$Builder", classLoader)
        val pinner = XposedHelpers.findClass("okhttp3.CertificatePinner", classLoader)
        val method = XposedHelpers.findMethodExact(builder, "certificatePinner", pinner)
        method.hook(HookStage.BEFORE) { param ->
            param.setResult(param.thisObject())
        }
    }

    runCatching {
        val trust = XposedHelpers.findClass(
            "com.android.org.conscrypt.TrustManagerImpl",
            classLoader,
        )
        val method = XposedHelpers.findMethodExact(
            trust,
            "verifyChain",
            List::class.java,
            List::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType!!,
            ByteArray::class.java,
            ByteArray::class.java,
        )
        method.hook(HookStage.BEFORE) { param ->
            param.setResult(param.arg(0))
        }
    }
}

val unsafeTrustManager = @SuppressLint("CustomX509TrustManager")
object : X509TrustManager {
    @SuppressLint("TrustAllX509TrustManager")
    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) {
    }

    @SuppressLint("TrustAllX509TrustManager")
    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) {
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

val unsafeSslContext: SSLContext = SSLContext.getInstance("TLSv1.3").apply {
    val trustAlLCerts = arrayOf<TrustManager>(unsafeTrustManager)
    this.init(null, trustAlLCerts, SecureRandom())
}
