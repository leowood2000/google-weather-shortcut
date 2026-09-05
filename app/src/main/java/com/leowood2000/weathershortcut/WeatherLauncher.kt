package com.leowood2000.weathershortcut

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * 谷歌天气启动器：
 * 目标 = com.google.android.googlequicksearchbox / .apps.search.weather.WeatherActivity
 * 该 Activity 未导出（exported=false），只能通过 root / Shizuku shell 权限拉起。
 */
object WeatherLauncher {

    const val PKG = "com.google.android.googlequicksearchbox"
    const val ACTIVITY = "com.google.android.apps.search.weather.WeatherActivity"
    const val AM_START = "am start -n $PKG/$ACTIVITY"

    /** Google App 是否已安装 */
    fun isGoogleAppInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getApplicationInfo(PKG, 0)
        true
    }.getOrDefault(false)

    /** 通过 shell (root/Shizuku) 拉起天气 */
    fun launchViaShell(): Boolean =
        PermissionHelper.runCommand(AM_START)

    /** 检查目标 Activity 是否存在（用于诊断） */
    fun activityExists(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        val info = pm.getActivityInfo(ComponentName(PKG, ACTIVITY), 0)
        true
    }.getOrDefault(false)

    /**
     * 检测当前能否直接 startActivity（导出检查）。
     * 期望返回 false（未导出）——这证明了为什么需要 shell。
     */
    fun isDirectlyLaunchable(context: Context): Boolean = runCatching {
        val intent = Intent().setComponent(ComponentName(PKG, ACTIVITY))
        val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_ALL)
        info != null && info.activityInfo.exported
    }.getOrDefault(false)
}