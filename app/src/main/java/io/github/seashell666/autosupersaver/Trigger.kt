package io.github.seashell666.autosupersaver

import java.util.concurrent.TimeUnit

/** 以 shell 命令的形式激发系统动作，便于随时替换命令而不必改代码。 */
object Trigger {

    data class Result(val ok: Boolean, val output: String)

    fun run(command: String, timeoutSec: Long = 20): Result {
        if (command.isBlank()) return Result(false, "命令为空")
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return Result(false, "超时")
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            Result(process.exitValue() == 0, output.ifEmpty { "exit=${process.exitValue()}" })
        } catch (t: Throwable) {
            Result(false, t.toString())
        }
    }
}
