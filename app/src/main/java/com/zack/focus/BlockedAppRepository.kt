package com.zack.focus

import android.content.Context
import android.content.Intent
import java.util.Locale

data class InstalledAppInfo(
    val packageName: String,
    val label: String
)

class BlockedAppRepository(private val context: Context) {

    fun getLaunchableApps(): List<InstalledAppInfo> {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0)

        return resolveInfos
            .mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null

                val label = resolveInfo.loadLabel(packageManager)?.toString().orEmpty().trim()
                val fallbackLabel = if (label.isBlank()) pkg else label

                InstalledAppInfo(
                    packageName = pkg,
                    label = fallbackLabel
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(
                compareBy<InstalledAppInfo> { it.label.lowercase(Locale.getDefault()) }
                    .thenBy { it.packageName }
            )
    }
}
