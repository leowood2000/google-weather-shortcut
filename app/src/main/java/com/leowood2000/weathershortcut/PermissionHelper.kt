package com.leowood2000.weathershortcut

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * 权限/通道工具：
 * 1) Shizuku（免 root）：通过 adb 无线调试授权后获得 shell 权限
 * 2) Root：直接 su 执行命令
 */
object PermissionHelper {

    private const val TAG = "GWShortcut"

    /** Shizuku 是否已授权 */
    fun isShizukuGranted(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** 请求 Shizuku 授权 */
    fun requestShizukuPermission() {
        try {
            Shizuku.requestPermission(10086)
        } catch (e: Exception) {
            Log.e(TAG, "requestShizukuPermission failed", e)
        }
    }

    /** 是否 root 可用 */
    fun isRootAvailable(): Boolean = runCatching {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val out = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        out.contains("uid=0")
    }.getOrDefault(false)

    /** 执行命令（优先 root，其次 Shizuku） */
    fun runCommand(command: String): Boolean {
        // 优先 root
        if (isRootAvailable()) {
            return runCatching {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                val code = p.waitFor()
                code == 0
            }.getOrDefault(false)
        }
        // 其次 Shizuku
        if (isShizukuGranted()) {
            return runCatching {
                val result = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
                result.waitFor() == 0
            }.getOrDefault(false)
        }
        return false
    }
}