# 🐾 SmartPet IoT - Monitoramento de Saúde Animal com Edge AI

![Status](https://img.shields.io/badge/Status-Em_Desenvolvimento-yellow)
![Platform](https://img.shields.io/badge/Platform-ESP32-blue)
![App](https://img.shields.io/badge/Mobile-Android_Kotlin-green)
![AI](https://img.shields.io/badge/Edge_AI-Edge_Impulse-orange)

## 📖 Sobre o Projeto

O **SmartPet IoT** é uma solução completa de monitoramento de saúde para animais de estimação, focado na detecção de anomalias cardíacas utilizando Inteligência Artificial na borda (Edge Computing). 

O sistema coleta dados vitais através de sensores, processa as informações localmente no microcontrolador (ESP32) para identificar padrões anormais em tempo real e transmite os dados para visualização tanto localmente (App Android via Bluetooth) quanto remotamente (Dashboard Web via MQTT).

## 🚀 Arquitetura do Sistema

O projeto é composto por quatro módulos principais que interagem entre si:

1.  **Firmware Embarcado (ESP32):** Coleta dados do sensor MAX30102, executa o modelo de Machine Learning (TinyML) e gerencia as conexões.
2.  **Edge AI (Modelo de ML):** Um modelo treinado via Edge Impulse para detectar anomalias em sinais de frequência cardíaca/ECG, exportado como biblioteca Arduino.
3.  **Aplicativo Mobile (Android):** Interface local desenvolvida em Kotlin para conexão via Bluetooth Low Energy (BLE).
4.  **Dashboard Web:** Interface para monitoramento remoto que consome dados de um Broker MQTT.

---

## 🛠️ Tecnologias e Hardware

### Hardware
* **Microcontrolador:** ESP32 (Suporte a WiFi e BLE).
* **Sensor:** MAX30102 (Oxímetria e Frequência Cardíaca).

### Software
* **Firmware:** C++ / Arduino IDE.
* **Mobile:** Android Nativo (Kotlin) com Jetpack Compose/XML.
* **Web:** HTML5, CSS3, JavaScript.
* **AI/ML:** Edge Impulse (TensorFlow Lite for Microcontrollers).
* **Protocolos:** MQTT (Telemetria remota), BLE (Conexão local).
* **Data Science:** Python (Scripts de filtragem e extração de características).

---

## 📂 Estrutura do Repositório

Este repositório é um monorepo que contém todos os componentes do sistema:

* `/src` & `/include`: Código fonte do firmware principal para o ESP32 (`setup.ino`, `frequencia.ino`).
* `/SmartPet`: Código fonte do aplicativo Android (projeto Android Studio).
* `/dashboard`: Arquivos da interface Web (`index.html`, `app.js`).
* `/ei-dog_ecg...`: Biblioteca exportada do Edge Impulse contendo o modelo de inferência treinado.
* `/ecg_filter`: Scripts Python utilizados para pré-processamento de dados (filtros de sinal) e geração de datasets.

---

## ⚙️ Instalação e Uso

### 1. Firmware (ESP32)
O firmware é responsável pela lógica principal.
1.  Instale a **Arduino IDE**.
2.  Adicione a biblioteca do modelo (`ei-dog_ecg...zip`) via *Sketch > Include Library > Add .ZIP Library*.
3.  Certifique-se de ter as bibliotecas do ESP32 e do sensor MAX30102 instaladas.
4.  Configure as credenciais WiFi e MQTT no arquivo `include/config.h` ou `include/secret.h`.
5.  Faça o upload do código contido em `src/` para a sua placa ESP32.

### 2. Aplicativo Android
Para visualizar os dados via Bluetooth:
1.  Abra a pasta `SmartPet` no **Android Studio**.
2.  Aguarde a sincronização do Gradle.
3.  Compile e execute o aplicativo no seu dispositivo Android.
4.  No App, busque pelo dispositivo BLE do SmartPet e pareie para ver a frequência cardíaca e o status da inferência.

### 3. Dashboard Web
Para visualização remota via Internet:
1.  Navegue até a pasta `dashboard`.
2.  Abra o arquivo `index.html` em qualquer navegador moderno.
3.  Certifique-se de que o arquivo `app.js` esteja configurado com o endereço correto do seu Broker MQTT (o mesmo configurado no ESP32).

### 4. Modelo de IA (Edge Impulse)
O modelo embarcado foi treinado para classificar o comportamento cardíaco:
* Os scripts em `ecg_filter` podem ser usados para limpar novos dados de treinamento.
* A inferência ocorre localmente no ESP32, garantindo baixa latência e funcionamento mesmo sem internet.

---

## 📈 Funcionalidades

* [x] Leitura de batimentos cardíacos (BPM) em tempo real.
* [x] Inferência de IA na borda (Anomalia vs. Normal).
* [x] Transmissão de dados via Bluetooth Low Energy.
* [x] Publicação de dados em tópicos MQTT.
* [x] Visualização gráfica em App e Web.

---

## 🤝 Contribuição

Contribuições são bem-vindas! Sinta-se à vontade para abrir issues ou enviar pull requests para melhorias no código do app, otimização do modelo ou refatoração do firmware.

## 📄 Licença

Este projeto está sob a licença [MIT](LICENSE) (ou insira a licença adequada aqui).