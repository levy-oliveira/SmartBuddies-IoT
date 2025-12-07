#include <BluetoothSerial.h>
#include <Wire.h>
#include "MAX30105.h"
#include "heartRate.h"
#include <math.h>

// --- NOME DA BIBLIOTECA DO EDGE IMPULSE ---
#include <dog_ecg_anomaly_detection_inferencing.h> 

BluetoothSerial SerialBT;
MAX30105 particleSensor;

const unsigned long COLLECTION_WINDOW_MS = 30000; 
const int MAX_BEATS_IN_WINDOW = 200; 

long beatTimestamps[MAX_BEATS_IN_WINDOW];
float rrIntervals[MAX_BEATS_IN_WINDOW]; 
int beatCount = 0;

bool isCollecting = false;
unsigned long windowStartTime = 0;

void setup() {
  Serial.begin(115200);
  SerialBT.begin("SmartPet_Device");
  Serial.println("SmartPet Iniciado!");

  if (!particleSensor.begin(Wire, 400000)) { 
    Serial.println("MAX30102 nao encontrado.");
    while (1);
  }

  particleSensor.setup(); 
  particleSensor.setPulseAmplitudeRed(0x0A); 
  particleSensor.setPulseAmplitudeGreen(0); 
}

void loop() {
  
  if (!isCollecting) {
    Serial.println("Iniciando coleta...");
    
    // --- PROTOCOLO NOVO: TIPO STATUS ---
    // Envia aviso que começou, mas com type STATUS para não zerar a tela do Android
    String json = "{";
    json += "\"type\": \"STATUS\","; 
    json += "\"status_ia\": \"Coletando (Aguarde 30s)...\"";
    json += "}";
    SerialBT.println(json);
    
    beatCount = 0;
    windowStartTime = millis();
    isCollecting = true;
  }

  if (isCollecting) {
    long irValue = particleSensor.getIR();

    if (checkForBeat(irValue) == true) {
      if (beatCount < MAX_BEATS_IN_WINDOW) {
        beatTimestamps[beatCount] = millis();
        beatCount++;
        Serial.print("Beat: "); Serial.println(beatCount);
      }
    }

    if (millis() - windowStartTime > COLLECTION_WINDOW_MS) {
      Serial.println("Coleta finalizada. Processando...");
      isCollecting = false;
      processAndInfer(); 
    }
  }
}

void processAndInfer() {
  
  // --- CASO DE ERRO (DADOS INSUFICIENTES) ---
  if (beatCount < 2) {
    // Aqui usamos type RESULT porque 0 é um resultado válido de erro que deve ser mostrado
    String json = "{";
    json += "\"type\": \"RESULT\",";
    json += "\"bpm\": 0,";
    json += "\"status_ia\": \"Dados Insuficientes\",";
    json += "\"rmssd\": 0,";
    json += "\"raw_anomaly\": 0";
    json += "}";
    SerialBT.println(json);
    return;
  }

  // --- CÁLCULOS MATEMÁTICOS ---
  int rrCount = beatCount - 1;
  for (int i = 0; i < rrCount; i++) {
    rrIntervals[i] = (beatTimestamps[i+1] - beatTimestamps[i]) / 1000.0;
  }

  float sumRR = 0; float minRR = rrIntervals[0]; float maxRR = rrIntervals[0];
  for (int i = 0; i < rrCount; i++) {
    sumRR += rrIntervals[i];
    if (rrIntervals[i] < minRR) minRR = rrIntervals[i];
    if (rrIntervals[i] > maxRR) maxRR = rrIntervals[i];
  }
  float meanRR = sumRR / rrCount;
  float bpm = (meanRR > 0) ? (60.0 / meanRR) : 0;

  float sumSquaredDiffs = 0;
  for (int i = 0; i < rrCount; i++) sumSquaredDiffs += pow(rrIntervals[i] - meanRR, 2);
  float sdRR = sqrt(sumSquaredDiffs / rrCount);

  float rmssd = 0;
  if (rrCount > 1) {
    float sumSquaredDiffsRR = 0;
    for (int i = 0; i < rrCount - 1; i++) {
      float diff = rrIntervals[i+1] - rrIntervals[i];
      sumSquaredDiffsRR += (diff * diff);
    }
    rmssd = sqrt(sumSquaredDiffsRR / (rrCount - 1));
  }

  float pnn50 = 0;
  if (rrCount > 1) {
    int nn50Count = 0;
    for (int i = 0; i < rrCount - 1; i++) {
      if (abs(rrIntervals[i+1] - rrIntervals[i]) > 0.05) nn50Count++;
    }
    pnn50 = (float)nn50Count / (rrCount - 1);
  }

  float durationS = (beatTimestamps[beatCount-1] - beatTimestamps[0]) / 1000.0;

  // --- INFERÊNCIA ---
  float features[10];
  features[0] = 0.0; features[1] = (float)beatCount; features[2] = durationS;
  features[3] = meanRR; features[4] = sdRR; features[5] = rmssd;
  features[6] = minRR; features[7] = maxRR; features[8] = bpm; features[9] = pnn50;

  signal_t signal;
  numpy::signal_from_buffer(features, EI_CLASSIFIER_DSP_INPUT_FRAME_SIZE, &signal);
  ei_impulse_result_t result = { 0 };
  run_classifier(&signal, &result, false);

  float anomalyScore = result.anomaly;
  String statusIA = "";
  
  if (anomalyScore < 0.5) statusIA = "Padrao Basal";
  else if (anomalyScore < 1.5) statusIA = "Variacao Moderada";
  else statusIA = "Padrao Atipico";
  
  // --- PROTOCOLO NOVO: TIPO RESULT ---
  // Esse pacote ATUALIZA o dashboard
  String json = "{";
  json += "\"type\": \"RESULT\",";
  json += "\"bpm\": " + String((int)bpm) + ",";
  json += "\"status_ia\": \"" + statusIA + "\","; 
  json += "\"rmssd\": " + String(rmssd * 1000, 1) + ","; 
  json += "\"raw_anomaly\": " + String(anomalyScore);
  json += "}";

  SerialBT.println(json);
  Serial.println(json);
  
  delay(3000); // Dá tempo do Android ler antes de reiniciar o loop
}