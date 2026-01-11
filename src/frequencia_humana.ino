#include <classification-iot_inferencing.h>

#include <Arduino.h>
#include <Wire.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include "MAX30105.h"
#include <ArduinoJson.h>

// // ===== BLE =====
// #include <BLEDevice.h>
// #include <BLEServer.h>
// #include <BLEUtils.h>
// #include <BLE2902.h>

// ================= SENSOR =================
#define IR_THRESHOLD 25000
MAX30105 sensor;

// ================= CONFIG =================
const int SAMPLE_RATE = 100;
const int MW_SIZE = 10;
const unsigned long WINDOW_MS = 30000;
const unsigned long REFRACTORY_MS = 250;
const int MAX_BEATS = 120;

// ================= WIFI / MQTT =================
const char* WIFI_SSID = "M35 de Levy";
const char* WIFI_PASS = "123454321";

const char* MQTT_SERVER = "7ae0f6e47e40403da1395867bebba435.s1.eu.hivemq.cloud";
const int   MQTT_PORT = 8883;
const char* MQTT_USER = "teste_mqtt";
const char* MQTT_PASSWORD = "Teste123";

const char* MQTT_TOPIC_MAIN = "bluetooth/30s";
const char* MQTT_TOPIC_BLE  = "bluetooth/1s";

WiFiClientSecure espClient;
PubSubClient mqtt(espClient);

// ================= BLE =================
// BLECharacteristic *bleChar;

#define BLE_SERVICE_UUID        "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
#define BLE_CHARACTERISTIC_UUID "6e400003-b5a3-f393-e0a9-e50e24dcca9e"

// ================= BUFFERS =================
float prevRaw = 0, prevHP = 0;
float mwBuffer[MW_SIZE];
int mwIndex = 0;

float bpmList[MAX_BEATS];
float ibiList[MAX_BEATS];
int beatIndex = 0;

unsigned long startWindow = 0;
unsigned long lastBeatTime = 0;
unsigned long lastBleSend = 0;
bool pendingWindowStart = true;

// ================= FILTROS =================
float highPass(float x, float prevX, float prevY) {
    const float alpha = 0.95;
    return alpha * (prevY + x - prevX);
}

float mwIntegrate(float x) {
    mwBuffer[mwIndex] = x;
    mwIndex = (mwIndex + 1) % MW_SIZE;
    float sum = 0;
    for (int i = 0; i < MW_SIZE; i++) sum += mwBuffer[i];
    return sum / MW_SIZE;
}

// ================= WIFI / MQTT =================
void connectWiFi() {
    WiFi.begin(WIFI_SSID, WIFI_PASS);
    while (WiFi.status() != WL_CONNECTED) delay(300);
}

void connectMQTT() {
    while (!mqtt.connected()) {
        mqtt.connect("ESP32_EI", MQTT_USER, MQTT_PASSWORD);
        delay(2000);
    }
}

// ================= HRV =================
float calcularRMSSD() {
    if (beatIndex < 2) return 0;
    float sum = 0;
    int valid = 0;

    for (int i = 1; i < beatIndex; i++) {
        float diff = ibiList[i] - ibiList[i - 1];
        if (abs(diff) < 0.5) {
            sum += diff * diff;
            valid++;
        }
    }
    return valid > 0 ? sqrt(sum / valid) : 0;
}

// ================= ZONA FC =================
int calcularZona(float bpmMean) {
    const int FC_MAX = 190;
    float p = bpmMean / FC_MAX;
    if (p < 0.60) return 1;
    if (p < 0.70) return 2;
    if (p < 0.80) return 3;
    if (p < 0.90) return 4;
    return 5;
}

// ================= BLE + MQTT REALTIME =================
void sendRealtimeBLE_MQTT(float bpmMean, int zona) {

    unsigned long ts = millis();

    // ---- BLE ----
    // StaticJsonDocument<64> bleDoc;
    // bleDoc["bpm"]  = bpmMean;
    // bleDoc["zona"] = zona;

    // char blePayload[64];
    // serializeJson(bleDoc, blePayload);
    // bleChar->setValue((uint8_t*)blePayload, strlen(blePayload));
    // bleChar->notify();

    // ---- MQTT ----
    StaticJsonDocument<128> mqttDoc;
    mqttDoc["timestamp"] = ts;
    mqttDoc["bpm_medio"] = bpmMean;
    mqttDoc["zona"]      = zona;

    char mqttPayload[128];
    serializeJson(mqttDoc, mqttPayload);
    mqtt.publish(MQTT_TOPIC_BLE, mqttPayload);

    Serial.print("[RT] ");
    Serial.println(mqttPayload);
}

// ================= EDGE IMPULSE =================
void runEdgeImpulse(float bpmMean, float bpmMax, float delta, float rmssd, int zona) {

    float features[5] = { bpmMean, bpmMax, delta, rmssd, (float)zona };

    signal_t signal;
    signal.total_length = 5;
    signal.get_data = [&](size_t offset, size_t length, float *out_ptr) -> int {
        for (size_t i = 0; i < length; i++) {
            out_ptr[i] = features[offset + i];
        }
        return 0;
    };

    ei_impulse_result_t result = { 0 };

    if (run_classifier(&signal, &result, false) != EI_IMPULSE_OK) return;

    float best = 0;
    const char* label = "";

    for (size_t i = 0; i < EI_CLASSIFIER_LABEL_COUNT; i++) {
        if (result.classification[i].value > best) {
            best = result.classification[i].value;
            label = result.classification[i].label;
        }
    }

    StaticJsonDocument<256> doc;
    doc["bpm_mean"] = bpmMean;
    doc["bpm_max"]  = bpmMax;
    doc["delta"]    = delta;
    doc["rmssd"]    = rmssd;
    doc["zona"]     = zona;
    doc["classe"]   = label;
    doc["score"]    = best;

    char payload[256];
    serializeJson(doc, payload);
    mqtt.publish(MQTT_TOPIC_MAIN, payload);

    Serial.println(payload);
}

// ================= SETUP =================
void setup() {
    Serial.begin(115200);
    Wire.begin();

    sensor.begin(Wire, I2C_SPEED_FAST);
    sensor.setup(0x1F, 4, 2, 400, 411, 16384);

    connectWiFi();
    espClient.setInsecure();
    mqtt.setServer(MQTT_SERVER, MQTT_PORT);
    connectMQTT();

    // ---- BLE ----
    // BLEDevice::init("ESP32_HEART");
    // BLEServer *server = BLEDevice::createServer();
    // BLEService *service = server->createService(BLE_SERVICE_UUID);

    // bleChar = service->createCharacteristic(
    //     BLE_CHARACTERISTIC_UUID,
    //     BLECharacteristic::PROPERTY_NOTIFY
    // );

    // bleChar->addDescriptor(new BLE2902());
    // service->start();
    // BLEDevice::getAdvertising()->start();

    Serial.println("Sistema iniciado");
}

// ================= LOOP =================
void loop() {
    mqtt.loop();

    long raw = sensor.getIR();
    unsigned long now = millis();

    if (raw < IR_THRESHOLD) {
        beatIndex = 0;
        pendingWindowStart = true;
        lastBeatTime = 0;
        return;
    }

    float hp = highPass(raw, prevRaw, prevHP);
    float der = hp - prevHP;
    float sq = der * der;
    float ecgLike = mwIntegrate(sq);

    prevRaw = raw;
    prevHP = hp;

    static float thr = 0;
    thr = 0.9 * thr + 0.1 * ecgLike;

    if (ecgLike > thr * 2.0) {
        if (now - lastBeatTime < REFRACTORY_MS) return;

        if (pendingWindowStart) {
            startWindow = now;
            beatIndex = 0;
            pendingWindowStart = false;
        }

        if (lastBeatTime > 0 && beatIndex < MAX_BEATS) {
            float ibi = (now - lastBeatTime) / 1000.0;
            if (ibi >= 0.45 && ibi <= 1.5) {
                ibiList[beatIndex] = ibi;
                bpmList[beatIndex] = 60.0 / ibi;
                beatIndex++;
            }
        }

        lastBeatTime = now;
    }

    // ===== REALTIME 1s =====
    if (beatIndex >= 2 && now - lastBleSend >= 1000) {
        float sum = 0;
        for (int i = 0; i < beatIndex; i++) sum += bpmList[i];
        float bpmMean = sum / beatIndex;
        int zona = calcularZona(bpmMean);
        sendRealtimeBLE_MQTT(bpmMean, zona);
        lastBleSend = now;
    }

    // ===== EDGE IMPULSE 30s =====
    if (!pendingWindowStart && now - startWindow >= WINDOW_MS) {
        if (beatIndex >= 8) {
            float sum = 0, maxBpm = 0;
            for (int i = 0; i < beatIndex; i++) {
                sum += bpmList[i];
                if (bpmList[i] > maxBpm) maxBpm = bpmList[i];
            }

            float bpmMean = sum / beatIndex;
            float bpmMax  = min(maxBpm, bpmMean + 30);
            float delta   = bpmMax - bpmMean;
            float rmssd   = calcularRMSSD();
            int zona      = calcularZona(bpmMean);

            runEdgeImpulse(bpmMean, bpmMax, delta, rmssd, zona);
        }

        beatIndex = 0;
        pendingWindowStart = true;
    }

    delay(1000 / SAMPLE_RATE);
}
