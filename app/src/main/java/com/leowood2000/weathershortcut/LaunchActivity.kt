package com.leowood2000.weathershortcut

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.concurrent.Executors

/**
 * 桌面快捷方式的落地页：
 * 点击快捷方式 → 本 Activity 启动 → 后台线程用 shell (root/Shizuku) 拉起 Google 原生天气页 → 自动 finish。
 * 使用透明主题，用户几乎感知不到它的存在。
 */
class LaunchActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        executor.execute {
            val ok = WeatherLauncher.launchViaShell()
            mainHandler.post {
                if (!ok) {
                    Toast.makeText(this, "需 root 或 Shizuku 授权才能打开原生天气", Toast.LENGTH_LONG).show()
                }
                finishAndRemoveTask()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}