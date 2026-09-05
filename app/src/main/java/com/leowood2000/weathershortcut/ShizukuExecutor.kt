package com.leowood2000.weathershortcut

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader

/**
 * Shizuku 执行器：
 * - Binder 状态检测
 * - 授权检测
 * - 运行身份识别（ADB shell / Root）
 * - 通过反射调用 newProcess 执行命令（Shizuku 13.x private API）
 *   官方推荐使用 UserService / Binder 方案，后续应迁移。
 * - 使用结束标记 + exit code 判定成功，不依赖 waitFor/exitValue
 * - 真正的 timeout 检测（线程 alive → destroy → 失败）
 */
object ShizukuExecutor {

    private const val TAG = "ShizukuExecutor"
    private const val EXEC_TIMEOUT_SEC = 5L
    private const val EXIT_MARKER = "__GWS_EXIT_CODE__"

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
     *
     * 成功判定策略：
     * 1. 在命令末尾追加 `; echo "__GWS_EXIT_CODE__=$?"`
     * 2. 等待 stdout 线程结束（流关闭 = 进程结束），带 timeout
     * 3. 从完整 stdout 中提取结束标记和 exit code
     * 4. 只有同时满足：发现完整结束标记 + exit code == 0 才判成功
     * 5. 如果线程在 timeout 后仍 alive → destroy → 判超时失败
     *
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

            // 在命令末尾追加结束标记，用于获取真实 exit code
            val wrappedCommand = "$command; echo $EXIT_MARKER=\$?"

            val proc = method.invoke(
                null,
                arrayOf("sh", "-c", wrappedCommand),
                null,
                null
            ) as Process

            val rawStdout = StringBuilder()
            val stderrText = StringBuilder()

            // 读取完整 stdout（含结束标记行），读取完整 stderr
            val stdoutThread = Thread {
                try {
                    BufferedReader(proc.inputStream.reader()).useLines {
                        it.forEach { line -> rawStdout.appendLine(line) }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "stdout read ended: ${e.message}")
                }
            }
            val stderrThread = Thread {
                try {
                    BufferedReader(proc.errorStream.reader()).useLines {
                        it.forEach { line -> stderrText.appendLine(line) }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "stderr read ended: ${e.message}")
                }
            }
            stdoutThread.start()
            stderrThread.start()

            // 等待 stdout 线程结束（流关闭 = 进程结束），带 timeout
            stdoutThread.join(timeoutSec * 1000)

            // 检查是否超时：stdout 线程仍 alive 说明进程没结束
            if (stdoutThread.isAlive) {
                Log.w(TAG, "exec timeout after ${timeoutSec}s, destroying process")
                runCatching { proc.destroyForcibly() }
                stdoutThread.join(500)
                stderrThread.join(500)
                return CommandResult(
                    success = false,
                    channel = Channel.SHIZUKU,
                    exitCode = null,
                    stdout = rawStdout.toString().trim(),
                    stderr = stderrText.toString().trim(),
                    error = "命令超时（${timeoutSec}s）"
                )
            }

            // stdout 线程已结束，等 stderr 也结束
            stderrThread.join(1000)

            val fullOutput = rawStdout.toString()
            val err = stderrText.toString().trim()

            // 从完整 stdout 中提取结束标记行
            val markerRegex = Regex("$EXIT_MARKER=(\\d+)")
            val markerMatch = markerRegex.find(fullOutput)

            if (markerMatch == null) {
                // 没有找到结束标记 — 进程异常终止或被 kill
                Log.w(TAG, "exec: no exit marker found in stdout")
                return CommandResult(
                    success = false,
                    channel = Channel.SHIZUKU,
                    exitCode = null,
                    stdout = fullOutput.trim(),
                    stderr = err,
                    error = "未找到结束标记（进程可能异常终止）"
                )
            }

            val exitCode = markerMatch.groupValues[1].toInt()

            // 移除结束标记行，得到纯净的 stdout
            val cleanStdout = fullOutput.replace(markerRegex, "").trim().trimEnd()

            CommandResult(
                success = exitCode == 0,
                channel = Channel.SHIZUKU,
                exitCode = exitCode,
                stdout = cleanStdout,
                stderr = err,
                error = if (exitCode != 0 && err.isNotEmpty()) err else null
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
