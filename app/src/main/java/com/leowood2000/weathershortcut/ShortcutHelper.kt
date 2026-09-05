package com.leowood2000.weathershortcut

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log

/**
 * 桌面快捷方式固定（pinned shortcut）。
 * 快捷方式 Intent 指向本应用的 LaunchActivity，点击后由 LaunchActivity 通过 shell 拉起天气。
 */
object ShortcutHelper {

    private const val TAG = "GWShortcut"
    const val SHORTCUT_ID = "google_weather"

    /**
     * 创建并固定桌面快捷方式（Android 8+ ShortcutManager.requestPinShortcut）
     * 需要系统弹窗确认（MIUI 桌面支持）。
     */
    fun pinShortcut(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val shortcutManager = activity.getSystemService(ShortcutManager::class.java)
        if (shortcutManager == null || !shortcutManager.isRequestPinShortcutSupported) {
            Log.w(TAG, "requestPinShortcut not supported")
            return false
        }

        // 桌面图标点击 → 本应用 LaunchActivity → LaunchActivity 用 shell 拉起天气
        val launchIntent = Intent(activity, LaunchActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val icon = Icon.createWithResource(activity, R.mipmap.ic_launcher)
        val shortcut = ShortcutInfo.Builder(activity, SHORTCUT_ID)
            .setShortLabel("谷歌天气")
            .setLongLabel("Google 天气（原生页）")
            .setIcon(icon)
            .setIntent(launchIntent)
            .build()

        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.getMainExecutor()
        } else {
            null
        }

        return try {
            if (callback != null) {
                shortcutManager.requestPinShortcut(shortcut, callback) { }
            } else {
                @Suppress("DEPRECATION")
                shortcutManager.requestPinShortcut(shortcut, null)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "pinShortcut failed", e)
            false
        }
    }
}