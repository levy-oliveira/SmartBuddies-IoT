package com.example.smartpet.model

import com.google.gson.annotations.SerializedName

data class SmartPetData(
    // Identificador do tipo de pacote: "RESULT" ou "STATUS"
    val type: String = "STATUS",

    // Campos do monitor cardíaco
    val bpm: Int = 0,

    @SerializedName("status_ia")
    val statusIa: String = "",

    val rmssd: Float = 0f,

    @SerializedName("raw_anomaly")
    val anomaly: Float = 0f,

    // Campos do monitor de atividade
    @SerializedName("passos")
    val steps: Int = 0,

    @SerializedName("estado")
    val dogState: String = "",

    // Campo comum
    val timestamp: Long = System.currentTimeMillis()
)
