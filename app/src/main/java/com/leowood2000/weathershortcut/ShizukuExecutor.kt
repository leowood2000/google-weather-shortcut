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

            val stdoutThread = Thread {
                proc.inputStream.bufferedReader().useLines { it.forEach { line -> stdoutText.appendLine(line) } }
            }
            val stderrThread = Thread {
                proc.errorStream.bufferedReader().useLines { it.forEach { line -> stderrText.appendLine(line) } }
            }
            stdoutThread.start()
            stderrThread.start()

            val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                stdoutThread.join(500)
                stderrThread.join(500)
                CommandResult(
                    success = false,
                    channel = Channel.SHIZUKU,
                    exitCode = null,
                    stdout = stdoutText.toString(),
                    stderr = stderrText.toString(),
                    error = "命令超时（${timeoutSec}s）"
                )
            } else {
                stdoutThread.join(1000)
                stderrThread.join(1000)

                val exitCode = proc.exitValue()
                CommandResult(
                    success = exitCode == 0,
                    channel = Channel.SHIZUKU,
                    exitCode = exitCode,
                    stdout = stdoutText.toString(),
                    stderr = stderrText.toString()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "exec failed: ${e.message}")
            CommandResult(
                success = false,
                channel = Channel.SHIZUKU,
                error = e.message ?: e.javaClass.simpleName
            )
        }
    }
}
