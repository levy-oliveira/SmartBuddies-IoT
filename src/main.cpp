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
char pitch_json[100];
char roll_json[100];
char yaw_json[100];
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

// ======== Cálculo de Pitch e Roll ========
void calcularAngulos(sensors_event_t &a, sensors_event_t &g, float dt) {
  // Acelerômetro em g (já normalizado pela Adafruit)
  float AccX = a.acceleration.x / 9.81;
  float AccY = a.acceleration.y / 9.81;
  float AccZ = a.acceleration.z / 9.81;
  
  // Giroscópio em graus/segundo (já convertido)
  float GyroX = g.gyro.x * 180.0 / PI;
  float GyroY = g.gyro.y * 180.0 / PI;
  
  // Ângulos do acelerômetro
  float accPitch = atan2(AccY, sqrt(AccX * AccX + AccZ * AccZ)) * 180 / PI;
  float accRoll  = atan2(-AccX, sqrt(AccY * AccY + AccZ * AccZ)) * 180 / PI;
  
  // Filtro complementar
  pitch = 0.98 * (pitch + GyroX * dt) + 0.02 * accPitch;
  roll  = 0.98 * (roll  + GyroY * dt) + 0.02 * accRoll;
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
  
  if (magnitude < 1.0) {
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
      features[i + 0] = a.acceleration.x;
      features[i + 1] = a.acceleration.y;
      features[i + 2] = a.acceleration.z;
      features[i + 3] = g.gyro.x;
      features[i + 4] = g.gyro.y;
      features[i + 5] = g.gyro.z;

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
  String estado_atual = "DESCONHECIDO";
  float maior_prob = 0.0;

  for (size_t ix = 0; ix < EI_CLASSIFIER_LABEL_COUNT; ix++) {
      if (result.classification[ix].value > maior_prob) {
          maior_prob = result.classification[ix].value;
          estado_atual = String(result.classification[ix].label);
      }
  }
  
  // Filtro de confiança: Se não tiver certeza (< 60%), mantém o último estado ou define como Indefinido
  if (maior_prob < 0.60) {
    estado_atual = estado_anterior;
  }
  else{
    estado_anterior = estado_atual;
  }

  // -------------------------------------------------------
  // 3. BLOCO DE ENVIO BLUETOOTH (JSON)
  // -------------------------------------------------------
  if (millis() - ultimo_envio_bt > INTERVALO_ENVIO_BT_MS) {
      
      StaticJsonDocument<200> doc;
      doc["timestamp"] = millis(); 
      doc["passos"]    = passos;
      doc["estado"]    = estado_atual;

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