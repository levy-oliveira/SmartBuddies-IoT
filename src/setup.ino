#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>

// MQTT
extern const char* MQTT_SERVER;
extern const int   MQTT_PORT;
extern const char* MQTT_TOPIC;

const char* WIFI_SSID = "Wokwi-GUEST";
const char* WIFI_PASSWORD = "";

const char* MQTT_SERVER = "7ae0f6e47e40403da1395867bebba435.s1.eu.hivemq.cloud";
const int   MQTT_PORT = 8883; // TLS
const char* MQTT_USER = "teste_mqtt";
const char* MQTT_PASSWORD = "Teste123";
const char* MQTT_TOPIC = "iot/teste";

// Objetos de rede
WiFiClientSecure espClient;
PubSubClient client(espClient);

// -- Conexão Wi-Fi --
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

// --- Conexão MQTT ---
void reconnect_mqtt() {
  while (!client.connected()) {
    Serial.print("Conectando ao broker MQTT... ");
    if (client.connect("ESP32Publisher", MQTT_USER, MQTT_PASSWORD)) {
      Serial.println("Conectado!");
    }
    else {
      Serial.print("Falhou (rc=");
      Serial.print(client.state());
      Serial.println("). Tentando novamente em 5s...");
      delay(5000);
    }
  }
}

void setup() {
  Serial.begin(115200);
  setup_wifi();

  espClient.setInsecure();  // Ignora verificação de certificado (TLS simplificado)
  client.setServer(MQTT_SERVER, MQTT_PORT);
}

void loop() {
  if (!client.connected()) reconnect_mqtt();
  client.loop();

  // --- Publica mensagem ---
  const char* msg = "Hello World!";
  client.publish(MQTT_TOPIC, msg);
  Serial.print("Mensagem publicada: ");
  Serial.println(msg);

  delay(2000);
}
