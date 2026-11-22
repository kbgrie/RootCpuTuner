package com.example.cputuner

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

data class CoreInfo(
    val coreIndex: Int,
    val currentFreqMhz: Int,
    val minFreqMhz: Int,
    val maxFreqMhz: Int,
    val governor: String
)

object RootShell {

    // Thực thi lệnh với quyền Root (su)
    fun execSu(command: String): String {
        val output = StringBuilder()
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
        } catch (e: Exception) {
            return ""
        }
        return output.toString().trim()
    }

    // Kiểm tra quyền Root
    fun checkRoot(): Boolean {
        return execSu("id").contains("uid=0")
    }

    // Lấy số lượng nhân CPU
    fun getCoreCount(): Int {
        return Runtime.getRuntime().availableProcessors()
    }

    // Đọc thông số từng nhân CPU theo thời gian thực
    fun getAllCoresInfo(): List<CoreInfo> {
        val count = getCoreCount()
        val list = mutableListOf<CoreInfo>()
        for (i in 0 until count) {
            val basePath = "/sys/devices/system/cpu/cpu$i/cpufreq"
            val cur = execSu("cat $basePath/scaling_cur_freq").toIntOrNull()?.div(1000) ?: 0
            val min = execSu("cat $basePath/scaling_min_freq").toIntOrNull()?.div(1000) ?: 0
            val max = execSu("cat $basePath/scaling_max_freq").toIntOrNull()?.div(1000) ?: 0
            val gov = execSu("cat $basePath/scaling_governor").ifEmpty { "unknown" }
            list.add(CoreInfo(i, cur, min, max, gov))
        }
        return list
    }

    // Lấy danh sách các mức xung nhịp phần cứng hỗ trợ (kHz)
    fun getAvailableFrequencies(): List<Int> {
        val raw = execSu("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies")
        return raw.split("\\s+".toRegex())
            .mapNotNull { it.toIntOrNull() }
            .sorted()
    }

    // Lấy danh sách Governor hỗ trợ (performance, schedutil, powersave...)
    fun getAvailableGovernors(): List<String> {
        val raw = execSu("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors")
        return raw.split("\\s+".toRegex()).filter { it.isNotBlank() }
    }

    // Đọc nhiệt độ CPU (tự động dò thermal_zone phù hợp cho cả máy thật & Cloud Phone)
    fun getCpuTemperature(): Float {
        for (i in 0..15) {
            val type = execSu("cat /sys/class/thermal/thermal_zone$i/type").lowercase()
            if (type.contains("cpu") || type.contains("tsens") || type.contains("soc") || i == 0) {
                val tempRaw = execSu("cat /sys/class/thermal/thermal_zone$i/temp").toFloatOrNull()
                if (tempRaw != null && tempRaw > 0) {
                    return if (tempRaw > 1000) tempRaw / 1000f else tempRaw
                }
            }
        }
        return 0f
    }

    // Tính % tải CPU tổng thể từ /proc/stat
    private var prevIdle: Long = 0
    private var prevTotal: Long = 0

    fun getCpuLoadPercent(): Int {
        val statLine = execSu("head -n 1 /proc/stat")
        val parts = statLine.split("\\s+".toRegex()).drop(1).mapNotNull { it.toLongOrNull() }
        if (parts.size < 4) return 0

        val idle = parts[3] + (parts.getOrNull(4) ?: 0L)
        val total = parts.sum()

        val diffIdle = idle - prevIdle
        val diffTotal = total - prevTotal

        prevIdle = idle
        prevTotal = total

        if (diffTotal <= 0) return 0
        return (((diffTotal - diffIdle).toFloat() / diffTotal.toFloat()) * 100).toInt().coerceIn(0, 100)
    }

    // Ép xung: Áp dụng Governor và Max Frequency cho toàn bộ nhân
    fun applyOverclock(governor: String, maxFreqKhz: Int?) {
        val count = getCoreCount()
        val commands = StringBuilder()
        for (i in 0 until count) {
            val basePath = "/sys/devices/system/cpu/cpu$i/cpufreq"
            commands.append("chmod 644 $basePath/scaling_governor\n")
            commands.append("echo $governor > $basePath/scaling_governor\n")
            if (maxFreqKhz != null && maxFreqKhz > 0) {
                commands.append("chmod 644 $basePath/scaling_max_freq\n")
                commands.append("echo $maxFreqKhz > $basePath/scaling_max_freq\n")
                // Khóa xung tối thiểu lên mức cao nếu chọn chế độ performance
                if (governor == "performance") {
                    commands.append("chmod 644 $basePath/scaling_min_freq\n")
                    commands.append("echo $maxFreqKhz > $basePath/scaling_min_freq\n")
                }
            }
        }
        execSu(commands.toString())
    }
}
