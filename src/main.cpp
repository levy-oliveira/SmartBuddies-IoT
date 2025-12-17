#include <BluetoothSerial.h>
#include <ArduinoJson.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <Wire.h>
#include <math.h>
#include <Modelo-movimento-dog_inferencing.h>

#define FREQUENCIA_HZ 100
#define INTERVALO_ENVIO_BT_MS 1000


// ======== Variáveis JSON ========
char passos_json[100];

// ======== Objetos BluetoothSerial ========
BluetoothSerial SerialBT;

// ======== Objeto MPU6050 ========
Adafruit_MPU6050 mpu;

// ======== Variáveis do MPU ========
float pitch = 0, roll = 0;
int passos = 0;
bool passoDetectado = false;
unsigned long lastTime = 0;
unsigned long ultimo_envio_bt = 0;  
unsigned long lastReconnectAttempt = 0;
String estado_anterior = "PARADO";

// ======== Variáveis do Modelo ========
#define EI_CLASSIFIER_SENSOR_AXES_COUNT 6
float features[EI_CLASSIFIER_DSP_INPUT_FRAME_SIZE]; // Buffer da IA


// ======== Setup inicial ========
void setup() {
  Serial.begin(115200);
  SerialBT.begin("SmartPet_Device");
  Serial.println("SmartPet Iniciado");

  Wire.begin(21, 22);
  
  // Inicializa MPU6050 com Adafruit
  if (!mpu.begin(0x68)) {
    Serial.println("Falha ao inicializar MPU6050!");
    while (1) {
      delay(10);
    }
  }
  Serial.println("MPU6050 inicializado com sucesso!");
  
  // Configurações do sensor
  mpu.setAccelerometerRange(MPU6050_RANGE_4_G);
  mpu.setGyroRange(MPU6050_RANGE_500_DEG);
  mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);
  
  lastTime = millis();
}

// ======== Contagem simples de passos ========
void contarPassos(sensors_event_t &a) {
  // Converte para g
  float AccXg = a.acceleration.x / 9.81;
  float AccYg = a.acceleration.y / 9.81;
  float AccZg = a.acceleration.z / 9.81;
  
  // Magnitude da aceleração
  float magnitude = sqrt(AccXg * AccXg + AccYg * AccYg + AccZg * AccZg);
  
  // Detecção de passo
  if (magnitude > 1.2 && !passoDetectado) {
    passos++;
    passoDetectado = true;
  }
  
  if (magnitude < 1.05) {
    passoDetectado = false;
  }
}

// ======== Loop principal ========
void loop() {
  long next_sample_time = micros();

  // -------------------------------------------------------
  // 1. BLOCO DE AMOSTRAGEM (Coleta dados + Conta passos)
  // -------------------------------------------------------
  // Preenchemos o buffer necessário para a IA (ex: 1.5 segundos de dados)
  for (int i = 0; i < EI_CLASSIFIER_DSP_INPUT_FRAME_SIZE; i += EI_CLASSIFIER_SENSOR_AXES_COUNT) {
      
      // Ler dados brutos do sensor
      sensors_event_t a, g, temp;
      mpu.getEvent(&a, &g, &temp);

      // --- A. Contagem de Passos em Tempo Real ---
      // Verificamos o passo AQUI dentro para não perder movimentos enquanto a IA carrega
      contarPassos(a);

      // --- B. Preencher Buffer da IA ---
      features[i + 0] = a.acceleration.x / 9.81f;
      features[i + 1] = a.acceleration.y / 9.81f;
      features[i + 2] = a.acceleration.z / 9.81f;
      features[i + 3] = g.gyro.x * 57.29578f;
      features[i + 4] = g.gyro.y * 57.29578f;
      features[i + 5] = g.gyro.z * 57.29578f;

      // Controle preciso de tempo (100Hz)
      next_sample_time += (1000000 / FREQUENCIA_HZ);
      while (micros() < next_sample_time); 
  }

  // -------------------------------------------------------
  // 2. BLOCO DE INTELIGÊNCIA ARTIFICIAL (Classificação)
  // -------------------------------------------------------
  ei_impulse_result_t result = { 0 };
  signal_t signal;
  
  // Converte array C++ para sinal Edge Impulse
  numpy::signal_from_buffer(features, EI_CLASSIFIER_DSP_INPUT_FRAME_SIZE, &signal);
  
  // Roda a Rede Neural
  EI_IMPULSE_ERROR res = run_classifier(&signal, &result, false);
  
  if (res != EI_IMPULSE_OK) return;

  // Descobre qual é a maior probabilidade (Parado, Caminhando, Correndo)
  String estado_instantaneo = "DESCONHECIDO";
  float maior_prob = 0.0;

  for (size_t ix = 0; ix < EI_CLASSIFIER_LABEL_COUNT; ix++) {
      if (result.classification[ix].value > maior_prob) {
          maior_prob = result.classification[ix].value;
          estado_instantaneo = String(result.classification[ix].label);
      }
  }
  
  static int contador_confirmacao = 0;
  static String ultimo_candidato = "";
  const int LIMITE_CONFIRMACAO = 2;
  String estado_final_para_envio = estado_anterior;

  if (estado_instantaneo == ultimo_candidato) {
      contador_confirmacao++;
  } else {
      ultimo_candidato = estado_instantaneo;
      contador_confirmacao = 0;
  }

  if (contador_confirmacao >= LIMITE_CONFIRMACAO && maior_prob > 0.60) {
      estado_final_para_envio = estado_instantaneo;
      estado_anterior = estado_final_para_envio; // Atualiza o histórico
  }

  // -------------------------------------------------------
  // 3. BLOCO DE ENVIO BLUETOOTH (JSON)
  // -------------------------------------------------------
  if (millis() - ultimo_envio_bt > INTERVALO_ENVIO_BT_MS) {
      
      StaticJsonDocument<200> doc;
      doc["timestamp"] = millis(); 
      doc["passos"]    = passos;
      doc["estado"]    = estado_final_para_envio;

      char json_output[200];
      serializeJson(doc, json_output);

      if (SerialBT.hasClient()) {
         SerialBT.println(json_output);
      }
      
      // Debug Serial
      Serial.print("Envio BT: "); Serial.println(json_output);

      ultimo_envio_bt = millis();
  }
}

int raw_feature_get_data(size_t offset, size_t length, float *out_ptr) {
    memcpy(out_ptr, features + offset, length * sizeof(float));
    return 0;
}