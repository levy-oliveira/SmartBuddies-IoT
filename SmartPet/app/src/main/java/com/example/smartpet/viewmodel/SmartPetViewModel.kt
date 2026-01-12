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
import com.example.smartpet.repository.MqttRepository
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

    // Histórico de atividades do cachorro para o gráfico
    private val _dogActivityHistory = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val dogActivityHistory: StateFlow<Map<String, List<String>>> = _dogActivityHistory.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val pairedDevices: StateFlow<List<ScannedDevice>> = _pairedDevices.asStateFlow()

    private var btService: SmartPetBluetoothService? = null
    private var mqttRepository: MqttRepository
    private var bluetoothAdapter: BluetoothAdapter? = null

    init {
        mqttRepository = MqttRepository(application)
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
                // Envia os dados para a HiveMQ e salva no CSV
                mqttRepository.publish(device.address, data)
                writeToCsv(data)

                val isActivityData = data.dogState.isNotEmpty() || data.steps > 0

                if (isActivityData) {
                    // Atualiza os dados gerais do pet (usado nos cards)
                    _petsData.value = _petsData.value.toMutableMap().apply {
                        this[device.address] = data
                    }

                    // Adiciona o estado ao histórico para o gráfico
                    val currentHistory = _dogActivityHistory.value[device.address]?.toMutableList() ?: mutableListOf()
                    currentHistory.add(0, data.dogState) // Adiciona no início
                    if (currentHistory.size > 60) { // Mantém o histórico com os últimos registros
                        currentHistory.removeLast()
                    }
                    _dogActivityHistory.value = _dogActivityHistory.value.toMutableMap().apply {
                        this[device.address] = currentHistory
                    }
                }

                // Atualiza o texto de status da conexão
                if (data.dogState.isNotEmpty()) {
                    _connectionStatuses.value = _connectionStatuses.value.toMutableMap().apply {
                        this[device.address] = "${data.dogState} (${device.name})"
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
                file.writeText("timestamp,steps,dogState\n")
            }
            data?.let {
                val csvRow = "${it.timestamp},${it.steps},${it.dogState}\n"
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
        _dogActivityHistory.value = _dogActivityHistory.value.toMutableMap().apply { remove(device.address) }
    }

    override fun onCleared() {
        super.onCleared()
        btService?.disconnectAll()
        mqttRepository.disconnect()
    }
}