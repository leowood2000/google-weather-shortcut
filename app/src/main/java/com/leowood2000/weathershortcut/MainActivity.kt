package com.leowood2000.weathershortcut

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

/**
 * 主界面：
 * 所有 root/shizuku 检测与执行全部在后台线程，
 * UI 线程只负责刷新显示。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var btnLaunch: Button
    private lateinit var btnPin: Button
    private lateinit var btnShizuku: Button

    private val executor = Executors.newSingleThreadExecutor()
    private val shizukuListener = object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            // 授权回调后在后台线程重新检测
            runDiagnosticAsync()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        btnLaunch = findViewById(R.id.btnLaunch)
        btnPin = findViewById(R.id.btnPin)
        btnShizuku = findViewById(R.id.btnShizuku)

        statusText.text = "检测中…"

        btnShizuku.setOnClickListener {
            PermissionHelper.requestShizukuPermission()
        }

        btnLaunch.setOnClickListener {
            statusText.text = "正在启动 Google 天气…"
            executor.execute {
                val result = WeatherLauncher.launchViaShell(this@MainActivity)
                runOnUiThread {
                    statusText.text = if (result.success) {
                        "已拉起 Google 原生天气页 ✓\n\n${result.diagnostic()}"
                    } else {
                        "启动失败\n${result.diagnostic()}"
                    }
                }
                // 启动完成后刷新状态
                runDiagnosticAsync()
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
        // 重置 root 缓存，让每次回到前台重新检测（应对用户在后台授权了 root）
        PermissionHelper.resetRootCache()
        runDiagnosticAsync()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuListener) }
        executor.shutdown()
    }

    /** 在后台线程收集诊断信息，然后切回 UI 线程刷新 */
    private fun runDiagnosticAsync() {
        executor.execute {
            val info = LaunchCoordinator.diagnose(this)
            val text = formatStatus(info)
            runOnUiThread { statusText.text = text }
        }
    }

    /** 将诊断信息格式化为状态文本 */
    private fun formatStatus(info: LaunchCoordinator.DiagnosticInfo): String = buildString {
        // Google App
        append("Google App：")
        append(if (info.googleAppInstalled) "已安装 ✓" else "未安装 ✗")
        append('\n')

        // WeatherActivity
        append("WeatherActivity：")
        append(if (info.activityExists) "存在 ✓" else "不存在 ✗")
        append('\n')
        append('\n')

        // Root
        append("Root：\n")
        append("  权限：")
        append(when (info.rootState) {
            RootExecutor.RootState.AVAILABLE -> "可用 ✓"
            RootExecutor.RootState.UNAVAILABLE -> "不可用 ✗"
            RootExecutor.RootState.UNKNOWN -> "未检测"
        })
        append('\n')
        append("  启动能力：")
        append(when (info.rootState) {
            RootExecutor.RootState.AVAILABLE -> "可用（root 可启动 private Activity）"
            RootExecutor.RootState.UNAVAILABLE -> "不可用"
            RootExecutor.RootState.UNKNOWN -> "未测试"
        })
        append('\n')
        append('\n')

        // Shizuku
        append("Shizuku：\n")
        append("  服务：")
        append(if (info.shizukuBinder) "运行中 ✓" else "未运行 ✗")
        append('\n')
        append("  授权：")
        append(if (info.shizukuGranted) "已授权 ✓" else "未授权 ✗")
        append('\n')
        append("  运行身份：")
        append(when (info.shizukuIdentity) {
            ShizukuExecutor.ShizukuIdentity.ROOT -> "Root（权限等同 root）"
            ShizukuExecutor.ShizukuIdentity.ADB_SHELL -> "ADB shell（受限）"
            ShizukuExecutor.ShizukuIdentity.UNKNOWN -> "未知"
            ShizukuExecutor.ShizukuIdentity.UNAVAILABLE -> "不可用"
        })
        append('\n')
        append("  启动能力：")
        append(when {
            !info.shizukuGranted -> "未授权"
            info.shizukuIdentity == ShizukuExecutor.ShizukuIdentity.ROOT -> "可用（等同 root）"
            info.shizukuIdentity == ShizukuExecutor.ShizukuIdentity.ADB_SHELL ->
                "受限（ADB shell 未必能启动 private Activity）"
            else -> "待验证"
        })
        append('\n')
        append('\n')

        // 可用通道
        append("可用通道：")
        append(when {
            info.rootState == RootExecutor.RootState.AVAILABLE -> "Root ✓"
            info.shizukuGranted && info.shizukuIdentity == ShizukuExecutor.ShizukuIdentity.ROOT ->
                "Shizuku(Root) ✓"
            info.shizukuGranted -> "Shizuku(ADB) △ 启动能力待验证"
            else -> "无（请授权 Shizuku 或使用 root）"
        })
    }
}
