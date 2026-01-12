package com.example.smartpet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartpet.viewmodel.SmartPetViewModel

@Composable
fun PetScreen(viewModel: SmartPetViewModel, selectedDeviceAddress: String?) {
    val petsData by viewModel.petsData.collectAsState()
    val activityHistory by viewModel.dogActivityHistory.collectAsState()

    val currentPetData = selectedDeviceAddress?.let { petsData[it] }
    val currentActivityHistory = selectedDeviceAddress?.let { activityHistory[it] } ?: emptyList()

    val dailyGoal = 3000 // Meta de 3000 passos
    val remainingSteps = dailyGoal - (currentPetData?.steps ?: 0)
    val progress = (currentPetData?.steps?.toFloat() ?: 0f) / dailyGoal

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F0F0))
            .padding(16.dp)
    ) {
        Text("Pet", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // Card: Passos do Cachorro (Gráfico de Atividade)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Atividade Recente", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(16.dp))
                if (currentActivityHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Aguardando dados de atividade...", color = Color.Gray)
                    }
                } else {
                    ActivityHistoryChart(history = currentActivityHistory)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card: Status Atual
        StatusAtualCard(dogState = currentPetData?.dogState)

        Spacer(modifier = Modifier.height(16.dp))

        // Card: Meta Diária
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Meta Diária", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Passos Restantes: $remainingSteps passos", fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
            }
        }
    }
}

@Composable
fun StatusAtualCard(dogState: String?) {
    val (icon, label, color) = when (dogState?.uppercase()) {
        "PARADO" -> Triple(Icons.Default.Hotel, "Parado", Color(0xFF448AFF))
        "CAMINHANDO" -> Triple(Icons.Default.DirectionsWalk, "Caminhando", Color(0xFF4CAF50))
        "CORRENDO" -> Triple(Icons.Default.DirectionsRun, "Correndo", Color(0xFFFF9800))
        else -> Triple(Icons.Default.Pets, "Inativo", Color.Gray)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Status Atual", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(16.dp))
            Icon(icon, contentDescription = label, modifier = Modifier.size(64.dp), tint = color)
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
fun ActivityHistoryChart(history: List<String>) {
    Canvas(modifier = Modifier
        .fillMaxWidth()
        .height(150.dp)) {
        val barCount = history.size
        if (barCount == 0) return@Canvas

        val barWidth = size.width / barCount

        history.forEachIndexed { index, state ->
            val (barHeight, color) = when (state.uppercase()) {
                "PARADO" -> Pair(0.25f, Color(0xFF448AFF))
                "CAMINHANDO" -> Pair(0.6f, Color(0xFF4CAF50))
                "CORRENDO" -> Pair(1.0f, Color(0xFFFF9800))
                else -> Pair(0.1f, Color.Gray)
            }

            drawRect(
                color = color,
                topLeft = Offset(x = index * barWidth, y = size.height * (1 - barHeight)),
                size = Size(width = barWidth * 0.8f, height = size.height * barHeight)
            )
        }
    }
}
