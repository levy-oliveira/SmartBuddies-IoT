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
fun SmartPetDashboard(viewModel: SmartPetViewModel, onDeviceSelected: (String) -> Unit) {
    val petsData by viewModel.petsData.collectAsState()
    val connectionStatuses by viewModel.connectionStatuses.collectAsState()
    val devices by viewModel.pairedDevices.collectAsState()

    val context = LocalContext.current

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
                                onDeviceSelected(device.address)
                            }
                        }
                    }
                }
            }
        }

        // O resto do dashboard pode continuar aqui, ou ser removido se não for mais necessário
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
