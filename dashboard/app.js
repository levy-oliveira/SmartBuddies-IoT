document.addEventListener("DOMContentLoaded", () => {

    // --- CONFIGURAÇÕES MQTT ---
    const brokerHost = "cb05f449b31d488fa17717c4654e23e4.s1.eu.hivemq.cloud";
    const brokerPort = 8884;
    const brokerUser = "Pratica";
    const brokerPass = "Pratica1";
    
    const topicHeartRate = "esp32/frequencia_cardiaca"; 
    const topicStatus = "esp32/status";
    const topicPitch = "info/pitch";
    const topicRoll = "info/roll";
    const topicSteps = "info/passos";

    // --- VARIÁVEIS DO ALGORITMO DE BATIMENTO (Lógica portada do C++) ---
    let rates = [];          // Array para médias de BPM
    const RATE_SIZE = 4;     // Tamanho da média
    let lastBeatTime = 0;    // Timestamp do último batimento (em ms)
    let currentFilterValue = 0; // Para o filtro DC
    const ALPHA = 0.95;      // Fator de suavização do filtro

    // --- ELEMENTOS DOM ---
    const bpmValueDiv = document.getElementById("bpmValue");
    const statusDiv = document.getElementById("status");
    const MAX_DATA_POINTS = 60; 

    // --- FUNÇÕES GRÁFICAS ---
    function createChart(canvasId, label, color) {
        const ctx = document.getElementById(canvasId).getContext("2d");
        return new Chart(ctx, {
            type: 'line',
            data: {
                labels: [],
                datasets: [{
                    label: label,
                    data: [],
                    borderColor: color,
                    backgroundColor: color.replace('rgb', 'rgba').replace(')', ', 0.1)'),
                    borderWidth: 2,
                    pointRadius: 0,
                    tension: 0.3,
                    fill: true
                }]
            },
            options: {
                responsive: true,
                animation: false,
                scales: { x: { display: false }, y: { beginAtZero: false } },
                plugins: { legend: { display: false } }
            }
        });
    }

    const irChart = createChart('irChart', 'Pulso', 'rgb(255, 99, 132)');
    const pitchChart = createChart('pitchChart', 'Pitch', 'rgb(75, 192, 192)');
    const rollChart = createChart('rollChart', 'Roll', 'rgb(153, 102, 255)');
    const stepsChart = createChart('stepsChart', 'Passos', 'rgb(255, 159, 64)');

    function updateChart(chart, val) {
        chart.data.labels.push("");
        chart.data.datasets[0].data.push(val);
        if (chart.data.labels.length > MAX_DATA_POINTS) {
            chart.data.labels.shift();
            chart.data.datasets[0].data.shift();
        }
        chart.update();
    }

    // --- LÓGICA MQTT E CÁLCULO ---
    const clientId = "web-calc-" + Math.random().toString(16).substr(2, 8);
    const client = new Paho.MQTT.Client(brokerHost, brokerPort, clientId);

    client.onConnectionLost = (res) => { 
        console.log("Caiu!"); 
        statusDiv.innerText = "Reconectando..."; 
        setTimeout(() => client.connect(opts), 3000); 
    };

    client.onMessageArrived = (msg) => {
        const topic = msg.destinationName;
        const payload = msg.payloadString;

        try {
            if (topic === topicHeartRate) {
                // JSON recebido apenas com dados brutos: {"ir_value": 123456, "red_value": ...}
                const data = JSON.parse(payload);
                const irValue = data.ir_value;

                // 1. Atualiza o gráfico com o dado bruto
                updateChart(irChart, irValue);

                // 2. ALGORITMO DE CÁLCULO DE BPM (Executado no Navegador)
                processHeartRate(irValue);

            } else if (topic === topicStatus) {
                statusDiv.innerText = payload;
            } else {
                // Outros gráficos
                const data = JSON.parse(payload);
                if (topic === topicPitch) updateChart(pitchChart, data.value);
                if (topic === topicRoll) updateChart(rollChart, data.value);
                if (topic === topicSteps) updateChart(stepsChart, data.value);
            }
        } catch (e) { console.error(e); }
    };

    // --- FUNÇÃO DE PROCESSAMENTO MATEMÁTICO ---
    function processHeartRate(irValue) {
        const now = Date.now();

        // Se o valor for muito baixo, não há dedo no sensor
        if (irValue < 50000) {
            bpmValueDiv.innerText = "--";
            currentFilterValue = irValue; // Reseta filtro
            rates = []; // Limpa histórico
            return;
        }

        // 1. Filtro DC (Remove componente contínuo para achar a oscilação)
        // Mesma lógica que existia no C++
        currentFilterValue = (ALPHA * currentFilterValue) + ((1.0 - ALPHA) * irValue);
        const acValue = irValue - currentFilterValue;

        // 2. Detecção de Pico
        // Se a variação for positiva e forte (> 20) e já passou tempo suficiente desde o último
        if (acValue > 20 && (now - lastBeatTime > 250)) {
            
            const delta = now - lastBeatTime;
            lastBeatTime = now;

            // Calcula BPM instantâneo
            let bpm = 60 / (delta / 1000.0);

            // Filtra ruídos (BPM humano normal entre 40 e 220)
            if (bpm < 255 && bpm > 40) {
                rates.push(bpm);
                
                // Mantém apenas os últimos 4 valores para média
                if (rates.length > RATE_SIZE) {
                    rates.shift();
                }

                // Calcula Média
                let avgBpm = 0;
                for (let i = 0; i < rates.length; i++) {
                    avgBpm += rates[i];
                }
                avgBpm = Math.floor(avgBpm / rates.length);

                // Atualiza a tela
                bpmValueDiv.innerText = avgBpm;
                
                // Pisca o texto para feedback visual do batimento
                bpmValueDiv.style.color = "red";
                setTimeout(() => bpmValueDiv.style.color = "#333", 100);
            }
        }
    }

    const opts = {
        onSuccess: () => {
            console.log("Conectado!");
            statusDiv.innerText = "🟢 Monitorando (Cálculo via Navegador)";
            client.subscribe(topicHeartRate);
            client.subscribe(topicStatus);
            client.subscribe(topicPitch);
            client.subscribe(topicRoll);
            client.subscribe(topicSteps);
        },
        useSSL: true,
        userName: brokerUser,
        password: brokerPass
    };

    client.connect(opts);
});