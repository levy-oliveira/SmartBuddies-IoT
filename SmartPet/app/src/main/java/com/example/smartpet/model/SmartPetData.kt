package com.example.smartpet.model

import com.google.gson.annotations.SerializedName

data class SmartPetData(
    // Identificador do tipo de pacote: "RESULT" ou "STATUS"
    val type: String = "STATUS",

    val bpm: Int = 0,

    @SerializedName("status_ia")
    val statusIa: String = "",

    val rmssd: Float = 0f,

    @SerializedName("raw_anomaly")
    val anomaly: Float = 0f,

    val timestamp: Long = System.currentTimeMillis()
)