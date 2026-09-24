package com.grindrplus.manager.play

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Certificate hashes for Play [PurchaseHelper.purchase] — same encoding as Aurora Store's
 * `CertUtil.getEncodedCertificateHashes` (SHA-256, Base64 URL_SAFE / NO_PADDING / NO_WRAP).
 */
object PlayPackageCerts {

    fun encodedSha256Hashes(context: Context, packageName: String): List<String> {
        return try {
            val pm = context.packageManager
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, flags)
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = info.signingInfo ?: return emptyList()
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                info.signatures ?: return emptyList()
            }
            val factory = CertificateFactory.getInstance("X509")
            signatures.mapNotNull { sig ->
                runCatching {
                    val cert = factory.generateCertificate(sig.toByteArray().inputStream()) as X509Certificate
                    val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                    Base64.encodeToString(
                        digest,
                        Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                    )
                }.getOrNull()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Last rotation hash (Aurora Store uses `.last()` when the package is installed). */
    fun latestEncodedHash(context: Context, packageName: String): String? =
        encodedSha256Hashes(context, packageName).lastOrNull()
}
