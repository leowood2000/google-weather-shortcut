package com.leowood2000.weathershortcut

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * 权限辅助：
 * 仅负责 Shizuku 权限请求与状态查询。
 * 执行逻辑已移至 ShizukuExecutor / RootExecutor / LaunchCoordinator。
 */
object PermissionHelper {

    /** Shizuku 是否已授权 */
    fun isShizukuGranted(): Boolean = ShizukuExecutor.isGranted()

    /** Shizuku Binder 是否存活 */
    fun isShizukuAlive(): Boolean = ShizukuExecutor.isBinderAlive()

    /** 请求 Shizuku 授权 */
    fun requestShizukuPermission() {
        try {
            if (ShizukuExecutor.isBinderAlive() && !isShizukuGranted()) {
                Shizuku.requestPermission(10086)
            }
        } catch (_: Exception) { }
    }

    /** Root 是否可用（带缓存） */
    fun isRootAvailable(): Boolean = RootExecutor.isAvailable()

    /** 重置 Root 缓存 */
    fun resetRootCache() = RootExecutor.reset()
}
