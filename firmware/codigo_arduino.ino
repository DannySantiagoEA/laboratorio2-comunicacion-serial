// =====================================================================
// LABORATORIO 2 - INTERFACES (UNIVERSIDAD DEL VALLE)
// Microcontrolador: ATmega328P (Esquemático de Proteus Lab2Interfaces.pdsprj)
// Cumple HU-02 (Telemetría 0x7A 0x7B ... 0x7C) y HU-03 (Comandos 0xAA ... 0x55)
// =====================================================================

const unsigned long BAUDIOS = 9600;

// --- 1. ADC: 8 entradas analógicas (PC0..PC5 = A0..A5, más ADC6=A6 y ADC7=A7) ---
const uint8_t NUM_ANALOGICAS = 8;
const uint8_t PINES_ADC[NUM_ANALOGICAS] = { A0, A1, A2, A3, A4, A5, A6, A7 };

// Modo de resolución: 0 -> 8 bits (12 bytes por trama), 1 -> 10 bits (20 bytes)
#define ADC_10_BITS 0

// --- 2. DIP-Switch de 4 bits en PD2, PD3, PD4, PD5 (Pines Arduino 2, 3, 4, 5) ---
const uint8_t NUM_SWITCHES = 4;
const uint8_t PINES_SWITCH[NUM_SWITCHES] = { 2, 3, 4, 5 };

// --- 3. 4 LEDs de salida (DI0..DI3) en PB0, PB1, PB2, PB3 (Pines Arduino 8, 9, 10, 11) ---
const uint8_t NUM_LEDS = 4;
const uint8_t PINES_LEDS[NUM_LEDS] = { 8, 9, 10, 11 };

// --- Delimitadores de Trama Telemetría (Micro -> PC) ---
const uint8_t CABECERA_1 = 0x7A;
const uint8_t CABECERA_2 = 0x7B;
const uint8_t FIN_TRAMA = 0x7C;

// --- Delimitadores de Comandos (PC -> Micro - HU-03) ---
const uint8_t CMD_HEADER = 0xAA;
const uint8_t CMD_TIPO_LEDS = 0x01;
const uint8_t CMD_TIPO_TS = 0x02;
const uint8_t CMD_TIPO_FLUJO = 0x03;
const uint8_t CMD_TAIL = 0x55;

#if ADC_10_BITS
const uint8_t BYTES_POR_CANAL = 2;
#else
const uint8_t BYTES_POR_CANAL = 1;
#endif

const uint8_t LONGITUD_TRAMA = 2 + 1 + (NUM_ANALOGICAS * BYTES_POR_CANAL) + 1;

// --- Variables Globales de Temporización y Estado ---
volatile uint16_t periodoMuestreoMs = 100;  // Ts inicial = 100 ms
volatile uint16_t contadorMs = 0;
volatile bool tocaEnviar = false;
volatile bool flujoHabilitado = true;

uint16_t lecturasAdc[NUM_ANALOGICAS];
uint8_t trama[LONGITUD_TRAMA];

// Buffer para recepción de comandos desde Java (HU-03)
uint8_t bufferRx[8];
uint8_t indiceRx = 0;

// =====================================================================
// CONFIGURACIÓN DE PERIFÉRICOS (UART, ADC, SWITCHES, LEDS, TIMER1)
// =====================================================================

void configurarUart() {
  Serial.begin(BAUDIOS, SERIAL_8N1);
}

void configurarAdc() {
  analogReference(DEFAULT);  // 5.0 V
}

void configurarSwitches() {
  for (uint8_t i = 0; i < NUM_SWITCHES; i++) {
    // En el esquemático el DIP-Switch cierra a GND, por lo que INPUT_PULLUP es indispensable
    pinMode(PINES_SWITCH[i], INPUT_PULLUP);
  }
}

void configurarLeds() {
  for (uint8_t i = 0; i < NUM_LEDS; i++) {
    pinMode(PINES_LEDS[i], OUTPUT);
    digitalWrite(PINES_LEDS[i], LOW);
  }
}

void configurarTimer1() {
  noInterrupts();
  TCCR1A = 0;
  TCCR1B = 0;
  TCNT1 = 0;
// Configuración automática según el reloj configurado en Proteus (16 MHz o 8 MHz o 1 MHz)
// Modo CTC (WGM12) con interrupción cada 1 ms
#if F_CPU >= 16000000UL
  OCR1A = 249;                                        // 16 MHz / 64 = 250 kHz -> 250 cuentas = 1 ms
  TCCR1B = (1 << WGM12) | (1 << CS11) | (1 << CS10);  // Prescaler 64
#elif F_CPU >= 8000000UL
  OCR1A = 124;                                        // 8 MHz / 64 = 125 kHz -> 125 cuentas = 1 ms
  TCCR1B = (1 << WGM12) | (1 << CS11) | (1 << CS10);  // Prescaler 64
#else
  OCR1A = 124;                          // 1 MHz / 8 = 125 kHz -> 125 cuentas = 1 ms
  TCCR1B = (1 << WGM12) | (1 << CS11);  // Prescaler 8
#endif
  TIMSK1 = (1 << OCIE1A);
  interrupts();
}

ISR(TIMER1_COMPA_vect) {
  contadorMs++;
  if (contadorMs >= periodoMuestreoMs) {
    contadorMs = 0;
    tocaEnviar = true;
  }
}

// =====================================================================
// ADQUISICIÓN Y EMPAQUETADO DE TRAMA (HU-02)
// =====================================================================

void leerAnalogicas() {
  for (uint8_t i = 0; i < NUM_ANALOGICAS; i++) {
    lecturasAdc[i] = analogRead(PINES_ADC[i]);
  }
}

uint8_t leerSwitches() {
  uint8_t byteDigital = 0;
  for (uint8_t i = 0; i < NUM_SWITCHES; i++) {
    // Switch cerrado a GND -> LOW -> bit en 1
    if (digitalRead(PINES_SWITCH[i]) == LOW) {
      byteDigital |= (1 << i);
    }
  }
  return byteDigital;
}

void construirYEnviarTrama() {
  uint8_t n = 0;
  trama[n++] = CABECERA_1;
  trama[n++] = CABECERA_2;
  trama[n++] = leerSwitches();

  for (uint8_t i = 0; i < NUM_ANALOGICAS; i++) {
#if ADC_10_BITS
    trama[n++] = highByte(lecturasAdc[i]);
    trama[n++] = lowByte(lecturasAdc[i]);
#else
    trama[n++] = (uint8_t)(lecturasAdc[i] >> 2);      // 10 bits (0..1023) -> 8 bits (0..255)
#endif
  }

  trama[n] = FIN_TRAMA;
  Serial.write(trama, LONGITUD_TRAMA);
}

// =====================================================================
// RECEPCIÓN Y EJECUCIÓN DE COMANDOS DESDE JAVA (HU-03)
// =====================================================================

void aplicarMascaraLeds(uint8_t mascara) {
  for (uint8_t i = 0; i < NUM_LEDS; i++) {
    bool encender = ((mascara >> i) & 0x01) != 0;
    digitalWrite(PINES_LEDS[i], encender ? HIGH : LOW);
  }
}

void procesarComandosEntrantes() {
  while (Serial.available() > 0) {
    uint8_t b = (uint8_t)Serial.read();

    if (indiceRx == 0) {
      if (b == CMD_HEADER) {
        bufferRx[indiceRx++] = b;
      }
      continue;
    }

    bufferRx[indiceRx++] = b;

    // Verificar según el tipo de comando en bufferRx[1]
    uint8_t tipoCmd = bufferRx[1];

    // Caso 1: Comando de LEDs (4 bytes: [0xAA, 0x01, Mascara, 0x55])
    if (tipoCmd == CMD_TIPO_LEDS && indiceRx == 4) {
      if (bufferRx[3] == CMD_TAIL) {
        aplicarMascaraLeds(bufferRx[2] & 0x0F);
      }
      indiceRx = 0;
    }
    // Caso 2: Comando de Cambio de Ts (5 bytes: [0xAA, 0x02, MSB, LSB, 0x55])
    else if (tipoCmd == CMD_TIPO_TS && indiceRx == 5) {
      if (bufferRx[4] == CMD_TAIL) {
        uint16_t nuevoTs = ((uint16_t)bufferRx[2] << 8) | (uint16_t)bufferRx[3];
        if (nuevoTs >= 10) {
          noInterrupts();
          periodoMuestreoMs = nuevoTs;
          contadorMs = 0;
          interrupts();
        }
      }
      indiceRx = 0;
    }
    // Caso 3: Comando de Control de Flujo (4 bytes: [0xAA, 0x03, Estado, 0x55])
    else if (tipoCmd == CMD_TIPO_FLUJO && indiceRx == 4) {
      if (bufferRx[3] == CMD_TAIL) {
        flujoHabilitado = (bufferRx[2] == 0x01);
      }
      indiceRx = 0;
    }
    // Si llega un comando desconocido o se llena el buffer, reiniciar sincronismo
    else if (indiceRx >= 5 || (tipoCmd != CMD_TIPO_LEDS && tipoCmd != CMD_TIPO_TS && tipoCmd != CMD_TIPO_FLUJO)) {
      indiceRx = 0;
    }
  }
}

// =====================================================================
// PROGRAMA PRINCIPAL
// =====================================================================

void setup() {
  configurarUart();
  configurarAdc();
  configurarSwitches();
  configurarLeds();
  configurarTimer1();
}

void loop() {
  // 1. Atender inmediatamente cualquier comando enviado desde la GUI de Java (HU-03)
  procesarComandosEntrantes();

  // 2. Si se cumplió el periodo Ts, muestrear y transmitir trama continua (HU-02)
  if (tocaEnviar) {
    tocaEnviar = false;
    if (flujoHabilitado) {
      leerAnalogicas();
      construirYEnviarTrama();
    }
  }
}