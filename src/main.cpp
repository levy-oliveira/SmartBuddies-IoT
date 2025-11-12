#include <ArduinoJson.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <Wire.h>
#include <math.h>

// ======== Configurações WiFi ========
const char* ssid = "";
const char* password = "";

// ======== Configurações MQTT (TagoIO) ========
const char* mqtt_server = "7ae0f6e47e40403da1395867bebba435.s1.eu.hivemq.cloud";
const int mqtt_port = 8883;
const char* mqtt_user = "teste_mqtt";
const char* mqtt_password = "Teste123";

// ======== Tópicos MQTT ========
const char* topic_pitch = "info/pitch";
const char* topic_roll = "info/roll";
const char* topic_yaw = "info/yaw";
const char* topic_passos = "info/passos";

// ======== Variáveis JSON ========
char pitch_json[100];
char roll_json[100];
char yaw_json[100];
char passos_json[100];

// ======== Objetos WiFi e MQTT ========
WiFiClientSecure espClient;
PubSubClient client(espClient);

// ======== Objeto MPU6050 ========
Adafruit_MPU6050 mpu;

// ======== Variáveis do MPU ========
float pitch = 0, roll = 0;
int passos = 0;
bool passoDetectado = false;
unsigned long lastTime = 0;
unsigned long lastReconnectAttempt = 0;

// ======== Conectar ao WiFi ========
void setup_wifi() {
  delay(10);
  Serial.println();
  Serial.print("Conectando ao WiFi: ");
  Serial.println(ssid);
  
  WiFi.begin(ssid, password);
  
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  
  Serial.println("");
  Serial.println("WiFi conectado!");
  Serial.print("Endereço IP: ");
  Serial.println(WiFi.localIP());
}

// ======== Reconectar ao MQTT ========
boolean reconnect() {
    while(!client.connected()) {
        Serial.println("Reconectando ao MQTT...");
        if (client.connect("ESP32Publisher", mqtt_user, mqtt_password)) {
            Serial.println("Reconectado ao MQTT!");
        } else {
            Serial.print("Falha na conexão, rc=");
            Serial.print(client.state());
            Serial.println(" Tentando novamente em 5 segundos");
            delay(5000);
        }
    }
    return client.connected();
}
// ======== Setup inicial ========
void setup() {
  Serial.begin(115200);
  
  // Conecta ao WiFi
  setup_wifi();
  
  // Configura servidor MQTT
  espClient.setInsecure(); 
  client.setServer(mqtt_server, mqtt_port);
  
  // Inicializa MPU6050 com Adafruit
  if (!mpu.begin()) {
    Serial.println("Falha ao inicializar MPU6050!");
    while (1) {
      delay(10);
    }
  }
  Serial.println("MPU6050 inicializado com sucesso!");
  
  // Configurações do sensor
  mpu.setAccelerometerRange(MPU6050_RANGE_2_G);
  mpu.setGyroRange(MPU6050_RANGE_250_DEG);
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
  // Gerencia conexão MQTT
  if (!client.connected()) {
    unsigned long now = millis();
    if (now - lastReconnectAttempt > 5000) {
      lastReconnectAttempt = now;
      Serial.println("Tentando reconectar ao MQTT...");
      if (reconnect()) {
        lastReconnectAttempt = 0;
      }
    }
  } else {
    client.loop();
  }
  
  // Calcula delta time
  unsigned long now = millis();
  float dt = (now - lastTime) / 1000.0;
  lastTime = now;
  
  // Lê dados do sensor
  sensors_event_t a, g, temp;
  mpu.getEvent(&a, &g, &temp);
  
  // Processa dados
  calcularAngulos(a, g, dt);
  contarPassos(a);
  
  // Publica dados apenas se conectado
  if (client.connected()) {
    // Cria e envia JSONs individuais para cada variável
    StaticJsonDocument<300> jsonPitch;
    jsonPitch["variable"] = "pitch";
    jsonPitch["value"] = pitch;
    serializeJson(jsonPitch, pitch_json);
    client.publish(topic_pitch, pitch_json);
    
    StaticJsonDocument<300> jsonRoll;
    jsonRoll["variable"] = "roll";
    jsonRoll["value"] = roll;
    serializeJson(jsonRoll, roll_json);
    client.publish(topic_roll, roll_json);
    
    StaticJsonDocument<300> jsonPassos;
    jsonPassos["variable"] = "passos";
    jsonPassos["value"] = passos;
    serializeJson(jsonPassos, passos_json);
    client.publish(topic_passos, passos_json);

        // Debug no Serial Monitor
    Serial.println("📡 Dados enviados ao TagoIO:");
    Serial.print("Pitch: "); Serial.print(pitch);
    Serial.print(" | Roll: "); Serial.print(roll);
    Serial.print(" | Passos: "); Serial.println(passos);
    
    Serial.print("Aceleração X: "); Serial.print(a.acceleration.x);
    Serial.print("  Y: "); Serial.print(a.acceleration.y);
    Serial.print("  Z: "); Serial.println(a.acceleration.z);
    
    Serial.print("Rotação X: "); Serial.print(g.gyro.x);
    Serial.print("  Y: "); Serial.print(g.gyro.y);
    Serial.print("  Z: "); Serial.println(g.gyro.z);
    
    Serial.print("Temperatura: "); Serial.print(temp.temperature);
    Serial.println(" °C");
    Serial.println("----------------------------");
  }
  
  
  
  delay(1000);
}