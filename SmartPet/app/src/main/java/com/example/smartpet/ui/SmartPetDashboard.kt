package com.example.smartpet.ui

import android.content.Intent
import android.graphics.Paint
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartpet.viewmodel.ScannedDevice
import com.example.smartpet.viewmodel.SmartPetViewModel

@Composable
fun SmartPetDashboard(viewModel: SmartPetViewModel) {
    val petsData by viewModel.petsData.collectAsState()
    val connectionStatuses by viewModel.connectionStatuses.collectAsState()
    val bpmHistories by viewModel.bpmHistories.collectAsState()
    val devices by viewModel.pairedDevices.collectAsState()
    var selectedDeviceAddress by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    val selectedPetData = selectedDeviceAddress?.let { petsData[it] }
    val selectedBpmHistory = selectedDeviceAddress?.let { bpmHistories[it] } ?: emptyList()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("SmartPet", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Monitoramento Completo do Pet", fontSize = 14.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dispositivos Pareados", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    IconButton(onClick = { viewModel.refreshPairedDevices() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar")
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                if (devices.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Sem dispositivos pareados.", fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) {
                            Text("Abrir Configs Bluetooth")
                        }
                    }
                } else {
                    LazyColumn {
                        items(devices) { device ->
                            DeviceItem(device = device, status = connectionStatuses[device.address] ?: "Desconectado") { 
                                if (connectionStatuses[device.address]?.contains("Conectado") == true) {
                                    viewModel.disconnectFromPet(device)
                                } else {
                                    viewModel.connectToPet(device)
                                }
                                selectedDeviceAddress = device.address
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dashboard
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DataCard(title = "BPM", value = selectedPetData?.bpm?.toString() ?: "--", color = Color(0xFFFF5252))
            DataCard(title = "Análise IA", value = selectedPetData?.statusIa ?: "--", color = Color(0xFF448AFF))
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DataCard(title = "RMSSD (Var)", value = "${selectedPetData?.rmssd?.let { String.format("%.1f", it) } ?: "--"} ms", color = Color(0xFF4CAF50))
            DataCard(title = "Anomalia Raw", value = selectedPetData?.anomaly?.let { String.format("%.2f", it) } ?: "--", color = Color(0xFFFFC107))
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DataCard(title = "Passos", value = selectedPetData?.steps?.toString() ?: "--", color = Color(0xFF9C27B0))
            DataCard(title = "Estado do Cão", value = selectedPetData?.dogState ?: "--", color = Color(0xFF03A9F4))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Histórico de Batimentos", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Ocupa o resto da tela
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            EnhancedLineChart(selectedBpmHistory)
        }
    }
}

@Composable
fun EnhancedLineChart(dataPoints: List<Int>) {
    if (dataPoints.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Aguardando dados...", color = Color.Gray, fontSize = 12.sp)
        }
        return
    }

    val graphColor = Color(0xFFFF5252)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val maxVal = 180f
        val minVal = 40f
        val range = maxVal - minVal

        val width = size.width
        val height = size.height
        val paddingBottom = 40f
        val paddingLeft = 60f

        val graphHeight = height - paddingBottom
        val graphWidth = width - paddingLeft

        val textPaint = Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 30f
            textAlign = Paint.Align.RIGHT
        }

        val gridPaint = Paint().apply {
            color = android.graphics.Color.LTGRAY
            strokeWidth = 2f
        }

        val steps = 4
        for (i in 0..steps) {
            val value = minVal + (range * i / steps)
            val y = graphHeight - ((value - minVal) / range * graphHeight)

            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(paddingLeft, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )

            drawContext.canvas.nativeCanvas.drawText(
                "${value.toInt()}",
                paddingLeft - 10f,
                y + 10f,
                textPaint
            )
        }

        val path = Path()
        val points = mutableListOf<Offset>()

        // Se tiver apenas 1 ponto, desenha no meio
        val stepX = if (dataPoints.size > 1) graphWidth / (dataPoints.size - 1) else 0f

        dataPoints.forEachIndexed { index, bpm ->
            val safeBpm = bpm.coerceIn(minVal.toInt(), maxVal.toInt())

            val x = paddingLeft + (index * stepX)
            val y = graphHeight - ((safeBpm - minVal) / range * graphHeight)

            points.add(Offset(x, y))

            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = graphColor,
            style = Stroke(width = 5.dp.toPx())
        )

        points.forEachIndexed { index, offset ->
            drawCircle(color = graphColor, radius = 4.dp.toPx(), center = offset)
            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = offset)

            if (index == 0 || index == points.size - 1) {
                val label = if (index == points.size - 1) "Agora" else "-${(points.size - 1 - index) * 30}s"

                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    offset.x,
                    height,
                    Paint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = 28f
                        textAlign = Paint.Align.CENTER
                    }
                )
            }
        }
    }
}

@Composable
fun DeviceItem(device: ScannedDevice, status: String, onClick: (ScannedDevice) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(device) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = device.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(text = status, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
fun DataCard(title: String, value: String, color: Color) {
    Card(
        modifier = Modifier.width(165.dp).height(85.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }
}
