package com.leowood2000.weathershortcut

import android.content.Context
import android.util.Log

/**
 * 天气启动器（门面层）：
 * 对外提供简单 API，内部委托给 LaunchCoordinator。
 * 保留此类是为了兼容 ShortcutHelper 等引用。
 */
object WeatherLauncher {

    private const val TAG = "WeatherLauncher"

    /** Google App 是否已安装 */
    fun isGoogleAppInstalled(context: Context): Boolean =
        LaunchCoordinator.isGoogleAppInstalled(context)

    /** WeatherActivity 是否存在 */
    fun activityExists(context: Context): Boolean =
        LaunchCoordinator.activityExists(context)

    /**
     * 通过 shell (root/Shizuku) 拉起天气。
     * 必须在后台线程调用。
     * @return 完整结果（包含成功/失败、通道、exitCode、stdout/stderr）
     */
    fun launchViaShell(): CommandResult =
        LaunchCoordinator.launchWeather()
}
