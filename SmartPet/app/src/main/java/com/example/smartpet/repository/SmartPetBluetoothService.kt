package com.example.smartpet.repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.example.smartpet.model.SmartPetData
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SmartPetBluetoothService(private val bluetoothAdapter: BluetoothAdapter?) {

    // UUID Padrão para Serial Port Profile (SPP)
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val sockets = ConcurrentHashMap<String, BluetoothSocket>()

    @SuppressLint("MissingPermission")
    fun connectAndListen(macAddress: String): Flow<SmartPetData> = flow {
        if (bluetoothAdapter == null) {
            emit(SmartPetData(statusIa = "Erro: BT nulo"))
            return@flow
        }

        try {
            val device = bluetoothAdapter.getRemoteDevice(macAddress)
            val socket = device.createRfcommSocketToServiceRecord(uuid)
            sockets[macAddress] = socket
            socket.connect()

            emit(SmartPetData(statusIa = "Conectado!"))

            val inputStream = socket.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream))
            val gson = Gson()

            while (kotlin.coroutines.coroutineContext.isActive) {
                val line = reader.readLine()
                if (line == null) {
                    break // Stream closed
                }
                Log.d("SmartPetBluetoothService", "JSON Recebido: $line")
                try {
                    // Converte o JSON da ESP32 para Objeto Kotlin
                    val data = gson.fromJson(line, SmartPetData::class.java)
                    emit(data)
                } catch (e: Exception) {
                    Log.e("SmartPet", "Erro Parse JSON: $line")
                }
            }
        } catch (e: Exception) {
            if (kotlin.coroutines.coroutineContext.isActive) {
                emit(SmartPetData(statusIa = "Falha conexão"))
                Log.e("SmartPet", "Erro Socket para $macAddress", e)
            }
        } finally {
            disconnect(macAddress)
        }
    }.flowOn(Dispatchers.IO)

    fun disconnect(macAddress: String) {
        try {
            sockets[macAddress]?.close()
        } catch (e: Exception) {
            Log.e("SmartPet", "Erro ao fechar o socket para $macAddress", e)
        } finally {
            sockets.remove(macAddress)
        }
    }

    fun disconnectAll() {
        sockets.keys.forEach { address ->
            disconnect(address)
        }
    }
}