package com.leowood2000.weathershortcut

import android.util.Log
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Root 执行器：
 * - 检测 root 可用性（带缓存+超时）
 * - 通过 su -c 执行命令（带超时、完整 stdout/stderr）
 * - 线程安全，所有方法都是阻塞式，调用方需自行确保在后台线程
 */
object RootExecutor {

    private const val TAG = "RootExecutor"
    private const val DETECT_TIMEOUT_SEC = 3L
    private const val EXEC_TIMEOUT_SEC = 5L

    enum class RootState { UNKNOWN, AVAILABLE, UNAVAILABLE }
    private val state = AtomicReference(RootState.UNKNOWN)

    /** 重置缓存（用于手动重试） */
    fun reset() { state.set(RootState.UNKNOWN) }

    /** 当前缓存状态（不触发检测） */
    fun cachedState(): RootState = state.get()

    /**
     * 检测 root 是否可用（带缓存，首次检测后不再重复）。
     * 返回 true/false；超时或失败返回 false。
     */
    fun isAvailable(): Boolean {
        val s = state.get()
        if (s == RootState.AVAILABLE) return true
        if (s == RootState.UNAVAILABLE) return false
        // UNKNOWN → 执行检测
        val result = exec("id", DETECT_TIMEOUT_SEC)
        val ok = result.success && result.stdout.contains("uid=0")
        state.set(if (ok) RootState.AVAILABLE else RootState.UNAVAILABLE)
        return ok
    }

    /**
     * 通过 su -c 执行命令。
     * @param timeoutSec 超时秒数
     * @return 完整执行结果
     */
    fun exec(command: String, timeoutSec: Long = EXEC_TIMEOUT_SEC): CommandResult {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val stdoutText = StringBuilder()
            val stderrText = StringBuilder()

            // 并行读取 stdout/stderr 防止管道阻塞
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
                // 即使超时也等待读取线程结束
                stdoutThread.join(500)
                stderrThread.join(500)
                // 超时说明 root 可能可用但命令卡住，不改变状态
                return@try CommandResult(
                    success = false,
                    channel = Channel.ROOT,
                    exitCode = null,
                    stdout = stdoutText.toString(),
                    stderr = stderrText.toString(),
                    error = "命令超时（${timeoutSec}s）"
                )
            }

            stdoutThread.join(1000)
            stderrThread.join(1000)

            val exitCode = proc.exitValue()
            CommandResult(
                success = exitCode == 0,
                channel = Channel.ROOT,
                exitCode = exitCode,
                stdout = stdoutText.toString(),
                stderr = stderrText.toString()
            )
        } catch (e: Exception) {
            Log.e(TAG, "exec failed: ${e.message}")
            // su 不存在等异常，标记为不可用
            state.set(RootState.UNAVAILABLE)
            CommandResult(
                success = false,
                channel = Channel.ROOT,
                error = e.message ?: e.javaClass.simpleName
            )
        }
    }
}
