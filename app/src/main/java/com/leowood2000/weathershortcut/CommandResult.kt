package com.leowood2000.weathershortcut

/**
 * 执行通道类型
 */
enum class Channel {
    ROOT,
    SHIZUKU,
    NONE
}

/**
 * 命令执行完整结果
 */
data class CommandResult(
    val success: Boolean,
    val channel: Channel = Channel.NONE,
    val exitCode: Int? = null,
    val stdout: String = "",
    val stderr: String = "",
    val error: String? = null
) {
    /** 人类可读的诊断摘要 */
    fun diagnostic(): String = buildString {
        append("通道：${channel.name}\n")
        if (exitCode != null) append("退出码：$exitCode\n")
        if (stdout.isNotBlank()) {
            val trimmed = stdout.trim().lines().take(3).joinToString("\n")
            append("stdout：$trimmed\n")
        }
        if (stderr.isNotBlank()) {
            val trimmed = stderr.trim().lines().take(3).joinToString("\n")
            append("stderr：$trimmed\n")
        }
        if (error != null) append("错误：$error\n")
    }
}
