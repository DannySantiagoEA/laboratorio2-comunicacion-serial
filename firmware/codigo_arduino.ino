
// =====================================================================
// PARÁMETROS (se declaran una sola vez y se usan en todo el programa)
// =====================================================================

// --- UART (debe coincidir con SerialCommunicationManager en Java) ---
const unsigned long BAUDIOS = 9600;

// --- ADC: 8 entradas analógicas ---
const uint8_t NUM_ANALOGICAS = 8;
const uint8_t PINES_ADC[NUM_ANALOGICAS] = {A0, A1, A2, A3, A4, A5, A6, A7};

// Resolución que viaja en la trama:
//   1 -> 10 bits, 2 bytes por canal (MSB, LSB) -> trama de 20 bytes
//   0 ->  8 bits, 1 byte por canal             -> trama de 12 bytes
// En Java debe usarse el mismo modo: setModoAdc10Bits(true/false)
#define ADC_10_BITS 0

// --- Switches: DIP-switch de 4 bits en los pines 22..25 (PA0..PA3) ---
const uint8_t NUM_SWITCHES = 4;
const uint8_t PINES_SWITCH[NUM_SWITCHES] = {22, 23, 24, 25};

// --- Trama micro -> PC ---
const uint8_t CABECERA_1 = 0x7A;
const uint8_t CABECERA_2 = 0x7B;
const uint8_t FIN_TRAMA  = 0x7C;

#if ADC_10_BITS
const uint8_t BYTES_POR_CANAL = 2;
#else
const uint8_t BYTES_POR_CANAL = 1;
#endif

// 2 de cabecera + 1 digital + 8 canales + 1 de fin
const uint8_t LONGITUD_TRAMA = 2 + 1 + NUM_ANALOGICAS * BYTES_POR_CANAL + 1;

// --- Timer1: base de tiempo de 1 ms ---
// 16 MHz / 64 = 250 kHz -> 250 cuentas = 1 ms -> OCR1A = 250 - 1
const uint16_t OCR1A_1MS = 249;

// =====================================================================
// VARIABLES GLOBALES
// =====================================================================

// Las que usa la interrupción del timer son volatile
volatile uint16_t periodoMuestreoMs = 100;   // Ts inicial: 100 ms (igual que en Java)
volatile uint16_t contadorMs = 0;
volatile bool tocaEnviar = false;

uint16_t lecturasAdc[NUM_ANALOGICAS];        // 0..1023
uint8_t trama[LONGITUD_TRAMA];

// =====================================================================
// TAREA #6: UART Y ADC
// =====================================================================

void configurarUart() {
  Serial.begin(BAUDIOS, SERIAL_8N1);   // 9600 bps, 8 datos, sin paridad, 1 stop
}

void configurarAdc() {
  analogReference(DEFAULT);            // Vref = 5 V -> 0..1023 equivale a 0..5 V
  for (uint8_t i = 0; i < NUM_ANALOGICAS; i++) {
    pinMode(PINES_ADC[i], INPUT);
  }
}

void leerAnalogicas() {
  for (uint8_t i = 0; i < NUM_ANALOGICAS; i++) {
    lecturasAdc[i] = analogRead(PINES_ADC[i]);
  }
}

// =====================================================================
// TAREA #7: SWITCHES -> BYTE DIGITAL
// =====================================================================

void configurarSwitches() {
  for (uint8_t i = 0; i < NUM_SWITCHES; i++) {
    pinMode(PINES_SWITCH[i], INPUT_PULLUP);   // switch cerrado a GND = LOW
  }
}

// b0 = DIP1, b1 = DIP2, b2 = DIP3, b3 = DIP4 (b4..b7 = 0)
uint8_t leerSwitches() {
  uint8_t byteDigital = 0;
  for (uint8_t i = 0; i < NUM_SWITCHES; i++) {
    if (digitalRead(PINES_SWITCH[i]) == LOW) {   // cerrado -> 1
      byteDigital |= (1 << i);
    }
  }
  return byteDigital;
}

// =====================================================================
// TAREA #8: TRAMA 0x7A 0x7B | DIGITAL | 8 ADC | 0x7C
// =====================================================================

void construirTrama(uint8_t byteDigital) {
  uint8_t n = 0;
  trama[n++] = CABECERA_1;
  trama[n++] = CABECERA_2;
  trama[n++] = byteDigital;

  for (uint8_t i = 0; i < NUM_ANALOGICAS; i++) {
#if ADC_10_BITS
    trama[n++] = highByte(lecturasAdc[i]);   // MSB: bits 9..8 (0..3)
    trama[n++] = lowByte(lecturasAdc[i]);    // LSB: bits 7..0
#else
    trama[n++] = lecturasAdc[i] >> 2;        // 10 bits -> 8 bits
#endif
  }

  trama[n] = FIN_TRAMA;
}

void enviarTrama() {
  Serial.write(trama, LONGITUD_TRAMA);
}

// =====================================================================
// TAREA #9: TIMER1 PERIÓDICO
// =====================================================================

void configurarTimer1() {
  noInterrupts();
  TCCR1A = 0;                                          // sin PWM
  TCCR1B = (1 << WGM12) | (1 << CS11) | (1 << CS10);   // modo CTC, prescaler 64
  TCNT1  = 0;
  OCR1A  = OCR1A_1MS;                                  // interrupción cada 1 ms
  TIMSK1 = (1 << OCIE1A);                              // habilita la interrupción por comparación A
  interrupts();
}

// Se ejecuta cada 1 ms. Cuando se completa Ts, levanta la bandera.
ISR(TIMER1_COMPA_vect) {
  contadorMs++;
  if (contadorMs >= periodoMuestreoMs) {
    contadorMs = 0;
    tocaEnviar = true;
  }
}

// =====================================================================
// PROGRAMA PRINCIPAL
// =====================================================================

void setup() {
  configurarUart();
  configurarAdc();
  configurarSwitches();
  configurarTimer1();
}

void loop() {
  if (tocaEnviar) {
    tocaEnviar = false;
    leerAnalogicas();
    construirTrama(leerSwitches());
    enviarTrama();
  }
}