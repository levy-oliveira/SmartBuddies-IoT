package com.example.smartpet.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartpet.model.SmartPetData
import com.example.smartpet.repository.SmartPetBluetoothService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class ScannedDevice(val name: String, val address: String)

class SmartPetViewModel(application: Application) : AndroidViewModel(application) {

    private val _petsData = MutableStateFlow<Map<String, SmartPetData>>(emptyMap())
    val petsData: StateFlow<Map<String, SmartPetData>> = _petsData.asStateFlow()

    private val _connectionStatuses = MutableStateFlow<Map<String, String>>(emptyMap())
    val connectionStatuses: StateFlow<Map<String, String>> = _connectionStatuses.asStateFlow()

    private val _bpmHistories = MutableStateFlow<Map<String, List<Int>>>(emptyMap())
    val bpmHistories: StateFlow<Map<String, List<Int>>> = _bpmHistories.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val pairedDevices: StateFlow<List<ScannedDevice>> = _pairedDevices.asStateFlow()

    private var btService: SmartPetBluetoothService? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    init {
        // Inicia o cabeçalho do CSV se o arquivo não existir
        viewModelScope.launch(Dispatchers.IO) {
            writeToCsv(null)
        }
    }

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
        _connectionStatuses.value = _connectionStatuses.value.toMutableMap().apply {
            this[device.address] = "Conectando..."
        }

        viewModelScope.launch {
            btService?.connectAndListen(device.address)?.collect { data ->
                Log.d("SmartPetViewModel", "Dado recebido de ${device.address}: $data")

                // Salva os dados no arquivo CSV
                writeToCsv(data)

                val isHeartRateData = data.type == "RESULT"
                val isActivityData = data.dogState.isNotEmpty() || data.steps > 0

                // Atualiza os cards da UI com os novos dados
                if (isHeartRateData || isActivityData) {
                    _petsData.value = _petsData.value.toMutableMap().apply {
                        this[device.address] = data
                    }
                }

                // Atualiza o gráfico de BPM apenas para o dispositivo cardíaco
                if (isHeartRateData) {
                    val currentList = _bpmHistories.value[device.address]?.toMutableList() ?: mutableListOf()
                    currentList.add(data.bpm)
                    if (currentList.size > 20) currentList.removeAt(0)
                    _bpmHistories.value = _bpmHistories.value.toMutableMap().apply {
                        this[device.address] = currentList
                    }
                }

                // Atualiza o texto de status da conexão
                val statusText = when {
                    isHeartRateData -> data.statusIa // "Normal"
                    isActivityData -> data.dogState // "PARADO"
                    data.statusIa.isNotEmpty() -> data.statusIa // "Conectado!"
                    else -> null
                }

                if (statusText != null) {
                    _connectionStatuses.value = _connectionStatuses.value.toMutableMap().apply {
                        this[device.address] = "$statusText (${device.name})"
                    }
                }
            }
        }
    }

    private fun writeToCsv(data: SmartPetData?) {
        val context = getApplication<Application>().applicationContext
        val fileName = "smartpet_log.csv"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

        try {
            if (!file.exists()) {
                // Escreve o cabeçalho
                file.writeText("timestamp,type,bpm,statusIa,rmssd,anomaly,steps,dogState\n")
            }

            data?.let {
                val csvRow = "${it.timestamp},${it.type},${it.bpm},${it.statusIa},${it.rmssd},${it.anomaly},${it.steps},${it.dogState}\n"
                file.appendText(csvRow)
            }
		} catch (e: Exception) {
            Log.e("SmartPetViewModel", "Erro ao escrever no CSV: ", e)
        }
    }

    fun disconnectFromPet(device: ScannedDevice) {
        btService?.disconnect(device.address)
        _petsData.value = _petsData.value.toMutableMap().apply { remove(device.address) }
        _connectionStatuses.value = _connectionStatuses.value.toMutableMap().apply { remove(device.address) }
        _bpmHistories.value = _bpmHistories.value.toMutableMap().apply { remove(device.address) }
    }

    override fun onCleared() {
        super.onCleared()
        btService?.disconnectAll()
    }
}