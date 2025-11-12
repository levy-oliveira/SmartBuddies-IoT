#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <Wire.h>
#include "MAX30100_PulseOximeter.h" // <-- NOVA BIBLIOTECA

// --- Configurações de Rede (Suas configurações) ---
const char* WIFI_SSID = "POCO-X3-NFC";
const char* WIFI_PASSWORD = "jean0800";

const char* MQTT_SERVER = "cb05f449b31d488fa17717c4654e23e4.s1.eu.hivemq.cloud";
const int   MQTT_PORT = 8883; // TLS
const char* MQTT_USER = "Pratica";
const char* MQTT_PASSWORD = "Pratica1";
const char* MQTT_TOPIC = "teste";

// Objetos de rede
WiFiClientSecure espClient;
PubSubClient client(espClient);

// --- Nova Biblioteca: Objeto do Sensor ---
PulseOximeter pox;

// --- Variável Global para BPM ---
volatile float currentBPM = 0; 
volatile long lastBeat = 0; // Para saber se o dedo está no sensor

// --- Controle de Tempo ---
long lastMqttPublish = 0;
const long mqttInterval = 5000; // Publicar a cada 5 segundos


// --- Callback da Nova Biblioteca ---
void onBeatDetected() {
    currentBPM = pox.getHeartRate();
    lastBeat = millis(); // Registra a hora da última batida
}


// --- Conexão Wi-Fi (Seu código) ---
void setup_wifi() {
  Serial.print("Conectando ao Wi-Fi ");
  Serial.println(WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWi-Fi conectado!");
  Serial.print("Endereço IP: ");
  Serial.println(WiFi.localIP());
}

// --- Conexão MQTT (Seu código) ---
void reconnect_mqtt() {
  while (!client.connected()) {
    Serial.print("Conectando ao broker MQTT... ");
    if (client.connect("ESP32Publisher", MQTT_USER, MQTT_PASSWORD)) {
      Serial.println("Conectado!");
    } else {
      Serial.print("Falhou (rc=");
      Serial.print(client.state());
      Serial.println("). Tentando novamente em 5s...");
      delay(5000);
    }
  }
}

// --- Configuração do Sensor (CORRIGIDO) ---
void setup_sensor() {
  Serial.println("Inicializando sensor MAX30102 (com MAX30100lib)...");
  
  // 1. Inicializa I2C (SDA=21, SCL=22) - Isto está CORRETO
  if (!Wire.begin()) {
    Serial.println("Falha ao iniciar I2C.");
    while (1);
  }

  // 2. Inicializa o sensor
  // *** ESTA É A LINHA CORRIGIDA ***
  // A biblioteca espera que begin() seja chamado sem argumentos.
  if (!pox.begin()) { 
    Serial.println("Sensor MAX não encontrado. Verifique a fiação.");
    while (1);
  }

  // Registra o callback. A função 'onBeatDetected' será chamada
  // automaticamente sempre que uma batida for detectada.
  pox.setOnBeatDetectedCallback(onBeatDetected);
  
  Serial.println("Sensor inicializado. Coloque o dedo.");
}


void setup() {
  Serial.begin(115200);
  
  setup_sensor(); // Chama a nova função de setup do sensor
  setup_wifi();

  espClient.setInsecure();
  client.setServer(MQTT_SERVER, MQTT_PORT);
}

void loop() {
  // 1. Garante a conexão MQTT
  if (!client.connected()) reconnect_mqtt();
  client.loop(); // Mantém o cliente MQTT "vivo"

  // 2. Apenas chama o 'update' da biblioteca do sensor.
  pox.update();

  // 3. Publica no MQTT em intervalos (sem usar delay)
  unsigned long now = millis();
  if (now - lastMqttPublish > mqttInterval) {
    lastMqttPublish = now;

    // Verifica se recebemos uma batida nos últimos 4 segundos
    if (now - lastBeat > 4000) {
      Serial.println("Posicione o dedo no sensor...");
      currentBPM = 0; // Zera o BPM se não houver leitura
    }

    // Prepara a mensagem para enviar
    char msgBuffer[20];
    dtostrf(currentBPM, 4, 2, msgBuffer); // Formato (float, min_width, precision, buffer)

    // Publica no MQTT
    client.publish(MQTT_TOPIC, msgBuffer);
    Serial.print("Mensagem publicada: ");
    Serial.println(msgBuffer);
  }
}