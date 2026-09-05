package com.leowood2000.weathershortcut

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * 启动协调器：
 * 1. 检查 Google App + WeatherActivity 是否存在
 * 2. Root 优先 → 失败 fallback Shizuku → 最终失败
 * 3. 返回完整诊断结果
 *
 * 所有方法都是阻塞式，调用方必须在后台线程调用。
 */
object LaunchCoordinator {

    private const val TAG = "LaunchCoord"
    const val PKG = "com.google.android.googlequicksearchbox"
    const val ACTIVITY = "com.google.android.apps.search.weather.WeatherActivity"
    private const val AM_START = "am start -n $PKG/$ACTIVITY"

    /** Google App 是否安装 */
    fun isGoogleAppInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getApplicationInfo(PKG, 0)
        true
    }.getOrDefault(false)

    /** WeatherActivity 是否存在（通过 PackageManager 查询，即使 exported=false 也能查到） */
    fun activityExists(context: Context): Boolean = runCatching {
        context.packageManager.getActivityInfo(
            ComponentName(PKG, ACTIVITY), 0
        )
        true
    }.getOrDefault(false)

    /**
     * 完整诊断信息（用于状态页显示）。
     * 调用方在后台线程执行。
     */
    data class DiagnosticInfo(
        val googleAppInstalled: Boolean,
        val activityExists: Boolean,
        val rootState: RootExecutor.RootState,
        val shizukuBinder: Boolean,
        val shizukuGranted: Boolean,
        val shizukuIdentity: ShizukuExecutor.ShizukuIdentity,
        val rootLaunchTest: CommandResult? = null,
        val shizukuLaunchTest: CommandResult? = null
    )

    /** 收集诊断信息（轻量检测，不执行 am start） */
    fun diagnose(context: Context): DiagnosticInfo {
        val googleOk = isGoogleAppInstalled(context)
        val actOk = activityExists(context)
        val rootOk = RootExecutor.isAvailable()
        val shizukuBinder = ShizukuExecutor.isBinderAlive()
        val shizukuGranted = if (shizukuBinder) ShizukuExecutor.isGranted() else false
        val shizukuIdentity = if (shizukuGranted) ShizukuExecutor.getIdentity() else ShizukuExecutor.ShizukuIdentity.UNAVAILABLE

        return DiagnosticInfo(
            googleAppInstalled = googleOk,
            activityExists = actOk,
            rootState = RootExecutor.cachedState(),
            shizukuBinder = shizukuBinder,
            shizukuGranted = shizukuGranted,
            shizukuIdentity = shizukuIdentity
        )
    }

    /**
     * 启动天气页：前置检查 → Root 优先 → Shizuku fallback。
     *
     * 1. 检查 Google App 是否安装
     * 2. 检查 WeatherActivity 是否存在
     * 3. Root 通道 → 成功则返回
     * 4. Shizuku 通道 → 成功则返回
     * 5. 全部失败 → 返回最后一个通道的结果或"无可用通道"
     *
     * @param context 用于 PackageManager 检查
     * @return 最终结果
     */
    fun launchWeather(context: Context): CommandResult {
        Log.i(TAG, "launchWeather: 开始启动流程")

        // 前置检查：Google App 是否安装
        if (!isGoogleAppInstalled(context)) {
            Log.w(TAG, "launchWeather: Google App 未安装")
            return CommandResult(
                success = false,
                channel = Channel.NONE,
                error = "Google App 未安装"
            )
        }

        // 前置检查：WeatherActivity 是否存在
        if (!activityExists(context)) {
            Log.w(TAG, "launchWeather: WeatherActivity 不存在")
            return CommandResult(
                success = false,
                channel = Channel.NONE,
                error = "WeatherActivity 不存在（Google App 版本可能过旧或 Activity 名称已变更）"
            )
        }

        // Root 优先
        if (RootExecutor.isAvailable()) {
            Log.i(TAG, "launchWeather: 尝试 Root 通道")
            val result = RootExecutor.exec(AM_START)
            if (result.success) {
                Log.i(TAG, "launchWeather: Root 通道成功")
                return result
            }
            Log.w(TAG, "launchWeather: Root 通道失败: ${result.diagnostic()}")
            // Root 失败 → 继续尝试 Shizuku
        }

        // Shizuku fallback
        if (ShizukuExecutor.isGranted()) {
            Log.i(TAG, "launchWeather: 尝试 Shizuku 通道")
            val result = ShizukuExecutor.exec(AM_START)
            if (result.success) {
                Log.i(TAG, "launchWeather: Shizuku 通道成功")
                return result
            }
            Log.w(TAG, "launchWeather: Shizuku 通道失败: ${result.diagnostic()}")
            return result
        }

        // 两个通道都不可用
        Log.w(TAG, "launchWeather: 无可用通道")
        return CommandResult(
            success = false,
            channel = Channel.NONE,
            error = "无可用通道（Root 不可用且 Shizuku 未授权）"
        )
    }
}
