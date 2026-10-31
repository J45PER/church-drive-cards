package com.churchdrive.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/** The house's address for everyone: the people who use the apps never type it (an administrator can still change it, hidden on the sign-in screen). */
object HouseAddress {
    const val CLOUD_URL = "https://z4wyrbgulhsd63dfyimjem41urgk1lek.ui.nabu.casa"
}

/**
 * Which other Church Drive apps may read this app's sign-in. Google Play signs each app with its own key, so "same signing key" no
 * longer holds between the apps; instead the caller must be one of [PACKAGES] and its signing certificate must either be the same as
 * this app's (builds from GitHub) or have its SHA-256 fingerprint in [CERTS]. Each app's own fingerprint is shown in About, and the
 * ones Play uses are in Play Console under App integrity.
 */
object TrustedApps {
    val PACKAGES = setOf("com.churchdrive.manager", "com.churchdrive.todo", "com.churchdrive.satellites")

    /** SHA-256 fingerprints (hex, any case, colons optional) of the signing certificates Play uses for the apps above. Add each app's. */
    val CERTS: Set<String> = setOf(
        // Church Drive Manager (com.churchdrive.manager)
        "02:4a:91:9c:3a:fb:47:31:63:55:1f:a7:b8:ec:07:30:2b:ff:77:58:1f:83:0e:af:58:e5:55:32:3f:49:5f:5a",
        // Church Drive To Do (com.churchdrive.todo)
        "ee:66:d8:57:1a:d5:49:ae:9a:2d:eb:5e:72:fc:6d:57:9f:15:c5:07:79:99:ba:c6:1a:6e:1f:fd:90:8e:10:6e",
    )

    private fun normal(hex: String) = hex.replace(":", "").lowercase()

    /** This app's own signing certificate fingerprints, as shown in About (hex with colons). */
    fun ownCerts(context: Context): List<String> = certs(context, context.packageName).map { it.chunked(2).joinToString(":") }

    @Suppress("DEPRECATION")
    private fun certs(context: Context, pkg: String): List<String> = runCatching {
        val pm = context.packageManager
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        } else {
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
        }
        sigs.orEmpty().map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }
    }.getOrDefault(emptyList())

    /** Whether the app that owns [uid] is one of ours and signed by a certificate we trust. */
    fun trusts(context: Context, uid: Int): Boolean {
        val pm = context.packageManager
        val allowed = CERTS.map(::normal).toSet()
        val sameKey = pm.checkSignatures(context.applicationInfo.uid, uid) == PackageManager.SIGNATURE_MATCH
        return pm.getPackagesForUid(uid).orEmpty().any { pkg ->
            pkg in PACKAGES && (sameKey || certs(context, pkg).any { it in allowed })
        }
    }
}
