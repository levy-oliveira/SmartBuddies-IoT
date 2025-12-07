package com.example.smartpet.viewmodel

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartpet.model.SmartPetData
import com.example.smartpet.repository.SmartPetBluetoothService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScannedDevice(val name: String, val address: String)

class SmartPetViewModel : ViewModel() {

    private val _petData = MutableStateFlow(SmartPetData())
    val petData: StateFlow<SmartPetData> = _petData.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Desconectado")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _bpmHistory = MutableStateFlow<List<Int>>(emptyList())
    val bpmHistory: StateFlow<List<Int>> = _bpmHistory.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val pairedDevices: StateFlow<List<ScannedDevice>> = _pairedDevices.asStateFlow()

    private var btService: SmartPetBluetoothService? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    fun initBluetooth(adapter: BluetoothAdapter?) {
        this.bluetoothAdapter = adapter
        btService = SmartPetBluetoothService(adapter)
        refreshPairedDevices()
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        if (bluetoothAdapter == null) return
        try {
            val devices = bluetoothAdapter?.bondedDevices
            val deviceList = devices?.map { device ->
                ScannedDevice(device.name ?: "Desconhecido", device.address)
            } ?: emptyList()
            _pairedDevices.value = deviceList
        } catch (e: SecurityException) {
            Log.e("SmartPetViewModel", "Permissão BT pendente")
        } catch (e: Exception) {
            Log.e("SmartPetViewModel", "Erro: ${e.message}")
        }
    }

    fun connectToPet(device: ScannedDevice) {
        _connectionStatus.value = "Conectando..."

        viewModelScope.launch {
            btService?.connectAndListen(device.address)?.collect { data ->

                if (data.statusIa.isNotEmpty()) {
                    if (data.type == "STATUS") {
                        _connectionStatus.value = "${data.statusIa} (${device.name})"
                    } else {
                        _connectionStatus.value = "Resultado Recebido (${device.name})"
                    }
                }

                if (data.type == "RESULT") {
                    _petData.value = data

                    val currentList = _bpmHistory.value.toMutableList()
                    currentList.add(data.bpm)
                    if (currentList.size > 20) currentList.removeAt(0)
                    _bpmHistory.value = currentList
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        btService?.disconnect()
    }
}