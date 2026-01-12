package com.example.smartpet.repository

import android.content.Context
import android.util.Log
import com.example.smartpet.model.SmartPetData
import com.google.gson.Gson
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import java.util.UUID

class MqttRepository(context: Context) {

    private val serverHost = ""
    private val username = "smart_pet"
    private val password = ""
    private val gson = Gson()

    private val client: Mqtt5AsyncClient = MqttClient.builder()
        .useMqttVersion5()
        .identifier(UUID.randomUUID().toString())
        .serverHost(serverHost)
        .serverPort(8883)
        .sslWithDefaultConfig()
        .buildAsync()

    init {
        connect()
    }

    private fun connect() {
        client.connectWith()
            .simpleAuth()
            .username(username)
            .password(password.toByteArray())
            .applySimpleAuth()
            .send()
            .whenComplete { connAck, throwable ->
                if (throwable != null) {
                    Log.e("MqttRepository", "Falha ao conectar com HiveMQ", throwable)
                } else {
                    Log.d("MqttRepository", "Conexão com HiveMQ bem-sucedida! ConnAck: $connAck")
                }
            }
    }

    fun publish(macAddress: String, data: SmartPetData) {
        val topic = "smartpet/$macAddress/data"
        val payload = gson.toJson(data)

        val publishMessage = Mqtt5Publish.builder()
            .topic(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .payload(payload.toByteArray())
            .build()

        client.publish(publishMessage)
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.e("MqttRepository", "Falha ao publicar no tópico: $topic", throwable)
                } else {
                    // Sucesso na publicação (log desativado para não poluir)
                }
            }
    }

    fun disconnect() {
        client.disconnect()
    }
}