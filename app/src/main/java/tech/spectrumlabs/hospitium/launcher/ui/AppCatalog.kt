// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import tech.spectrumlabs.hospitium.launcher.protocol.LauncherApp

/** A launchable TV app. [banner] is the 16:9 home-screen banner, or the icon if it has none. */
data class TvApp(val packageName: String, val label: String, val banner: Bitmap?, val hasBanner: Boolean)

/** Reads the TV's launchable apps from the package manager (visible via the manifest's <queries>). */
class AppCatalog(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    fun installed(): List<TvApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { info ->
                val banner = info.loadBanner(pm) ?: info.applicationInfo.loadBanner(pm)
                val image = banner ?: info.loadIcon(pm)
                TvApp(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    banner = runCatching { image?.toBitmap(width = 320, height = if (banner != null) 180 else 320) }.getOrNull(),
                    hasBanner = banner != null,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun launch(packageName: String): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    companion object {
        /** The server's featured apps that are installed, in the server's order, with its labels. */
        fun featured(wanted: List<LauncherApp>, installed: List<TvApp>): List<TvApp> {
            val byPackage = installed.associateBy { it.packageName }
            return wanted.mapNotNull { w ->
                byPackage[w.packageName]?.let { app -> if (w.label.isNotBlank()) app.copy(label = w.label) else app }
            }
        }
    }
}
