package com.zack.focus

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

fun hasOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

fun overlaySettingsIntent(context: Context): Intent {
    return Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
