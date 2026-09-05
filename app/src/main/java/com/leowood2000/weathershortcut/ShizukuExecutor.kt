package com.leowood2000.weathershortcut

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * Shizuku 执行器：
 * - Binder 状态检测
 * - 授权检测
 * - 运行身份识别（ADB shell / Root）
 * - 通过反射调用 newProcess 执行命令（Shizuku 13.x private API）
 *   官方推荐使用 IRemoteProcess / UserService，但那需要绑定 AIDL；
 *   当前方案作为务实 fallback，封装为独立模块便于以后替换。
 * - 完整 stdout/stderr + 超时
 */
object ShizukuExecutor {

    private const val TAG = "ShizukuExecutor"
    private const val EXEC_TIMEOUT_SEC = 5L

    /** Shizuku Binder 是否存活 */
    fun isBinderAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    /** 是否已授权 */
    fun isGranted(): Boolean = runCatching {
        isBinderAlive() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /**
     * Shizuku 运行身份。
     * uid=0 → Root Shizuku；uid=2000 → ADB shell Shizuku。
     */
    enum class ShizukuIdentity { ROOT, ADB_SHELL, UNKNOWN, UNAVAILABLE }

    /** 检测 Shizuku 的运行身份（需要已授权） */
    fun getIdentity(): ShizukuIdentity {
        if (!isGranted()) return ShizukuIdentity.UNAVAILABLE
        val result = exec("id", 3L)
        val out = result.stdout
        return when {
            out.contains("uid=0") -> ShizukuIdentity.ROOT
            out.contains("uid=2000") -> ShizukuIdentity.ADB_SHELL
            else -> ShizukuIdentity.UNKNOWN
        }
    }

    /**
     * 通过 Shizuku newProcess（反射）执行命令。
     * 不依赖 waitFor()/exitValue()（Shizuku Process 包装类行为非标准），
     * 改为靠 stdout/stderr 线程结束来判断执行完成。
     * @param timeoutSec 超时秒数
     * @return 完整执行结果
     */
    @Suppress("UNCHECKED_CAST")
    fun exec(command: String, timeoutSec: Long = EXEC_TIMEOUT_SEC): CommandResult {
        if (!isGranted()) {
            return CommandResult(
                success = false,
                channel = Channel.SHIZUKU,
                error = "Shizuku 未授权"
            )
        }
        return try {
            // Shizuku 13.x: newProcess 是 private + deprecated，反射调用
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val proc = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process

            val stdoutText = StringBuilder()
            val stderrText = StringBuilder()

            // 读取 stdout/stderr 到各自 StringBuilder
            val stdoutThread = Thread {
                try {
                    proc.inputStream.bufferedReader().useLines { it.forEach { line -> stdoutText.appendLine(line) } }
                } catch (e: Exception) {
                    Log.w(TAG, "stdout read ended: ${e.message}")
                }
            }
            val stderrThread = Thread {
                try {
                    proc.errorStream.bufferedReader().useLines { it.forEach { line -> stderrText.appendLine(line) } }
                } catch (e: Exception) {
                    Log.w(TAG, "stderr read ended: ${e.message}")
                }
            }
            stdoutThread.start()
            stderrThread.start()

            // 等待线程结束（流关闭即代表进程结束），不调 waitFor/exitValue
            stdoutThread.join(timeoutSec * 1000)
            stderrThread.join(500)

            val out = stdoutText.toString().trim()
            val err = stderrText.toString().trim()

            // stdout 有内容 = 命令执行成功
            // stderr 非空且 stdout 空 = 命令失败
            val success = out.isNotEmpty()
            CommandResult(
                success = success,
                channel = Channel.SHIZUKU,
                exitCode = if (success) 0 else 1,
                stdout = out,
                stderr = err,
                error = if (!success && err.isNotEmpty()) err else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "exec failed: ${e.javaClass.simpleName}: ${e.message}", e)
            CommandResult(
                success = false,
                channel = Channel.SHIZUKU,
                error = "${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }
}
