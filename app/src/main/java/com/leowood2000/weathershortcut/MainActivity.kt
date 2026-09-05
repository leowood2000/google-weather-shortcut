package com.leowood2000.weathershortcut

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

/**
 * 主界面：
 * 显示 root / Shizuku / 目标 Activity 状态，
 * 提供「打开 Google 天气」「固定桌面快捷方式」「授权 Shizuku」三个操作。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var btnLaunch: Button
    private lateinit var btnPin: Button
    private lateinit var btnShizuku: Button

    private val shizukuListener = object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            refreshStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        btnLaunch = findViewById(R.id.btnLaunch)
        btnPin = findViewById(R.id.btnPin)
        btnShizuku = findViewById(R.id.btnShizuku)

        btnShizuku.setOnClickListener { PermissionHelper.requestShizukuPermission() }
        btnLaunch.setOnClickListener {
            val ok = WeatherLauncher.launchViaShell()
            statusText.text = if (ok) {
                "已拉起 Google 原生天气页 ✓\n（等待前台切换）"
            } else {
                "启动失败\n需要 root 或先授权 Shizuku\n\n${statusText.text}"
            }
        }
        btnPin.setOnClickListener {
            val ok = ShortcutHelper.pinShortcut(this)
            statusText.text = if (ok) {
                "已在系统弹窗请求固定桌面快捷方式\n请在弹窗中确认添加\n\n${statusText.text}"
            } else {
                "固定快捷方式失败（设备不支持）\n\n${statusText.text}"
            }
        }

        Shizuku.addRequestPermissionResultListener(shizukuListener)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuListener) }
    }

    private fun refreshStatus() {
        val root = PermissionHelper.isRootAvailable()
        val shizukuPing = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val shizukuGranted = PermissionHelper.isShizukuGranted()
        val googleInstalled = WeatherLauncher.isGoogleAppInstalled(this)

        statusText.text = buildString {
            append("Google App：").append(if (googleInstalled) "已安装" else "未安装").append('\n')
            append("原生天气可直启：否（exported=false）\n")
            append("Root：").append(if (root) "可用" else "不可用").append('\n')
            append("Shizuku 服务：").append(if (shizukuPing) "运行中" else "未运行").append('\n')
            append("Shizuku 授权：").append(if (shizukuGranted) "已授权" else "未授权").append('\n')
            append("可用通道：").append(
                when {
                    root -> "Root ✓"
                    shizukuGranted -> "Shizuku ✓"
                    else -> "无（请授权 Shizuku 或使用 root）"
                }
            )
        }
    }
}