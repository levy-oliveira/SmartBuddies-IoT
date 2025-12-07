/*
 * Código de Teste Corrigido (v3) para o MAX30102 (ESP32)
 * * Biblioteca: https://github.com/devxplained/MAX3010x-Sensor-Library
 * * Objetivo: Verificar se o sensor é detectado e se está lendo
 * a luz Infravermelha (IR).
 */

#include <Wire.h>
#include "MAX3010x.h" // Correto

// Declarar o objeto com o construtor padrão
MAX30102 sensor; 

void setup() {
  Serial.begin(115200);
  Serial.println("Inicializando o sensor MAX30102...");

  // A função begin() não usa argumentos
  if (!sensor.begin()) { 
    Serial.println("Falha ao encontrar o sensor MAX30102. Verifique a fiação.");
    while (1);
  }

  Serial.println("Sensor MAX30102 encontrado!");

  // O nome da função é 'setLedCurrent' (singular)
  // Usamos um valor 0x1F (aprox 6.4mA)
  sensor.setLedCurrent(MAX30102::LED_RED, 0x1F); 
  sensor.setLedCurrent(MAX30102::LED_IR, 0x1F);
}

// ****** A CORREÇÃO ESTÁ AQUI ******
void loop() {
  // 'available()' verifica se há dados
  if (sensor.available()) {
    
    // CORREÇÃO 1: A função 'readSample()' RETORNA a amostra.
    // Devemos criar uma variável 'sample' para recebê-la.
    MAX30102Sample sample = sensor.readSample(); 

    // CORREÇÃO 2: Agora lemos o valor 'ir' da variável 'sample'
    long irValue = sample.ir;

    Serial.print("Valor IR: ");
    Serial.println(irValue);
    
    // O valor "sem dedo" nesta biblioteca é geralmente mais baixo
    if (irValue < 10000) { 
      Serial.println(" (Coloque o dedo sobre o sensor)");
    }
  }
}