package com.example.cputuner

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    CpuTunerScreen()
                }
            }
        }
    }
}

@Composable
fun CpuTunerScreen() {
    val context = LocalContext.current
    var isRooted by remember { mutableStateOf<Boolean?>(null) }
    var cores by remember { mutableStateOf<List<CoreInfo>>(emptyList()) }
    var cpuTemp by remember { mutableFloatStateOf(0f) }
    var cpuLoad by remember { mutableIntStateOf(0) }
    var availableFreqs by remember { mutableStateOf<List<Int>>(emptyList()) }
    var availableGovs by remember { mutableStateOf<List<String>>(emptyList()) }

    var selectedGov by remember { mutableStateOf("performance") }
    var selectedMaxFreq by remember { mutableIntStateOf(0) }

    // Khởi tạo & vòng lặp cập nhật thông số mỗi 1 giây
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val rootStatus = RootShell.checkRoot()
            isRooted = rootStatus
            if (rootStatus) {
                availableFreqs = RootShell.getAvailableFrequencies()
                availableGovs = RootShell.getAvailableGovernors()
                if (availableFreqs.isNotEmpty()) {
                    selectedMaxFreq = availableFreqs.last()
                }
                if (availableGovs.isNotEmpty() && !availableGovs.contains("performance")) {
                    selectedGov = availableGovs.first()
                }
            }
        }

        while (true) {
            if (isRooted == true) {
                withContext(Dispatchers.IO) {
                    cores = RootShell.getAllCoresInfo()
                    cpuTemp = RootShell.getCpuTemperature()
                    cpuLoad = RootShell.getCpuLoadPercent()
                }
            }
            delay(1000L)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header & Trạng thái Root
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ROOT CPU TUNER",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E676)
                )
                val badgeColor = when (isRooted) {
                    true -> Color(0xFF00E676)
                    false -> Color(0xFFFF5252)
                    null -> Color.Gray
                }
                Text(
                    text = when (isRooted) {
                        true -> "ROOT: ACTIVE"
                        false -> "NO ROOT"
                        null -> "CHECKING..."
                    },
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(badgeColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Thẻ Tổng quan: Tải CPU & Nhiệt độ
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "TẢI CPU",
                    value = "$cpuLoad%",
                    valueColor = if (cpuLoad > 80) Color(0xFFFF5252) else Color(0xFF40C4FF),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "NHIỆT ĐỘ",
                    value = if (cpuTemp > 0) String.format("%.1f °C", cpuTemp) else "N/A (VM)",
                    valueColor = if (cpuTemp > 75f) Color(0xFFFF5252) else Color(0xFFFFAB40),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Bảng điều khiển Ép xung (Overclock Control)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "BẢNG ĐIỀU KHIỂN XUNG NHỊP",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )

                    // Chọn Governor
                    Text("CPU Governor:", color = Color.LightGray, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val displayGovs = availableGovs.ifEmpty { listOf("performance", "schedutil", "powersave") }
                        displayGovs.take(3).forEach { gov ->
                            FilterChip(
                                selected = selectedGov == gov,
                                onClick = { selectedGov = gov },
                                label = { Text(gov, fontSize = 12.sp) }
                            )
                        }
                    }

                    // Chọn Max Frequency (nếu Kernel cho phép đọc bảng xung)
                    if (availableFreqs.isNotEmpty()) {
                        val minF = availableFreqs.first().toFloat()
                        val maxF = availableFreqs.last().toFloat()
                        Text(
                            text = "Xung tối đa: ${selectedMaxFreq / 1000} MHz",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                        Slider(
                            value = selectedMaxFreq.toFloat().coerceIn(minF, maxF),
                            onValueChange = { sliderVal ->
                                // Làm tròn về mức xung gần nhất mà phần cứng hỗ trợ
                                selectedMaxFreq = availableFreqs.minByOrNull { kotlin.math.abs(it - sliderVal.toInt()) } ?: sliderVal.toInt()
                            },
                            valueRange = minF..maxF
                        )
                    } else {
                        Text(
                            text = "Cloud Phone / Kernel ẩn bảng xung cố định — chỉ áp dụng ép xung qua Governor.",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = {
                            RootShell.applyOverclock(
                                governor = selectedGov,
                                maxFreqKhz = if (availableFreqs.isNotEmpty()) selectedMaxFreq else null
                            )
                            Toast.makeText(context, "Đã áp dụng: $selectedGov", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        Text("ÁP DỤNG ÉP XUNG", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Danh sách thông số từng nhân (Core 0 -> Core N)
        item {
            Text(
                text = "THÔNG SỐ TỪNG NHÂN (${cores.size} CORES)",
                fontWeight = FontWeight.Bold,
                color = Color.LightGray,
                fontSize = 14.sp
            )
        }

        items(cores) { core ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CPU ${core.coreIndex}",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Gov: ${core.governor}",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${core.currentFreqMhz} MHz",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676),
                            fontSize = 16.sp
                        )
                        Text(
                            text = "${core.minFreqMhz} - ${core.maxFreqMhz} MHz",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, color = valueColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}
