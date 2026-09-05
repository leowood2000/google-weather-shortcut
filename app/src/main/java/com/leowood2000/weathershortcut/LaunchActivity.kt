package com.leowood2000.weathershortcut

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.concurrent.Executors

/**
 * 桌面快捷方式的落地页：
 * 点击快捷方式 → 本 Activity 启动 → 后台线程用 LaunchCoordinator 拉起天气 → 自动 finish。
 * 透明主题，用户几乎感知不到。
 */
class LaunchActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        executor.execute {
            val result = LaunchCoordinator.launchWeather()
            mainHandler.post {
                if (!result.success) {
                    val msg = when (result.channel) {
                        Channel.ROOT -> "Root 启动失败：${result.error ?: result.stderr.trim().take(80)}"
                        Channel.SHIZUKU -> "Shizuku 启动失败：${result.error ?: result.stderr.trim().take(80)}"
                        Channel.NONE -> "需 Root 或 Shizuku 授权才能打开原生天气"
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
                finishAndRemoveTask()
                overridePendingTransition(0, 0)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }
}
