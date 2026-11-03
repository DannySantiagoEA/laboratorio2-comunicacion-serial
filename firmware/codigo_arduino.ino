// =====================================================================
// LABORATORIO 2 - INTERFACES (UNIVERSIDAD DEL VALLE)
// Microcontrolador: ATmega328P a 16 MHz (Esquemático de Proteus Lab2Interfaces.pdsprj)
// Cumple HU-02 (Telemetría 0x7A 0x7B ... 0x7C) y HU-03 (Comandos 0xAA ... 0x55)
// Tareas #11 (RX ISR con MEF), #12 (Conmutación 4 LEDs) y #13 (Precarga Timer1 Ts)
// =====================================================================

const unsigned long BAUDIOS = 9600;
// UBRR = F_CPU / (16 * baudios) - 1 = 16 000 000 / 153 600 - 1 = 103
const uint16_t VALOR_UBRR = (F_CPU / (16UL * BAUDIOS)) - 1;

// --- 1. ADC: 8 entradas analógicas (PC0..PC5 = A0..A5, más ADC6 = A6 y ADC7 = A7) ---
const uint8_t NUM_ANALOGICAS = 8;
const uint8_t PINES_ADC[NUM_ANALOGICAS] = { A0, A1, A2, A3, A4, A5, A6, A7 };

// Modo de resolución: 0 -> 8 bits (12 bytes por trama), 1 -> 10 bits (20 bytes)
#define ADC_10_BITS 0

// --- 2. DIP-Switch de 4 bits en PD4, PD5, PD6, PD7 (Pines Arduino 4, 5, 6, 7) ---
const uint8_t NUM_SWITCHES = 4;
const uint8_t PINES_SWITCH[NUM_SWITCHES] = { 4, 5, 6, 7 };

// --- 3. 4 LEDs de salida en PB0, PB1, PB2, PB3 (Pines Arduino 8, 9, 10, 11) ---
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
const uint8_t MAX_DATOS_CMD = 2;  // el comando más largo (Ts) trae 2 datos
const uint8_t TAM_COLA_CMD = 4;   // comandos que pueden esperar turno en loop()

#if ADC_10_BITS
const uint8_t BYTES_POR_CANAL = 2;
#else
const uint8_t BYTES_POR_CANAL = 1;
#endif

const uint8_t LONGITUD_TRAMA = 2 + 1 + (NUM_ANALOGICAS * BYTES_POR_CANAL) + 1;

// --- Timer1 y tiempo de muestreo ---
// Prescaler 64: 16 MHz / 64 = 250 kHz -> una cuenta cada 4 us -> 250 cuentas por ms
const uint16_t CUENTAS_POR_MS = 250;
const uint16_t TS_INICIAL_MS = 100;  // igual que en Java
const uint16_t TS_MIN_MS = 10;       // mismo mínimo que ValidadorMuestreo

// =====================================================================
// VARIABLES GLOBALES
// =====================================================================

// --- Temporización (las usa la ISR del Timer1 -> volatile) ---
volatile uint8_t interrupcionesPorPeriodo = 1;  // veces que TCNT1 debe llegar a OCR1A para completar Ts
volatile uint8_t contadorInterrupciones = 0;
volatile bool tocaEnviar = false;
bool flujoHabilitado = true;  // se envía desde el arranque

// --- Recepción de comandos (las usa la ISR de la UART -> volatile) ---
enum EstadoRx { ESPERA_CABECERA,
                ESPERA_TIPO,
                ESPERA_DATOS,
                ESPERA_COLA };
volatile EstadoRx estadoRx = ESPERA_CABECERA;
volatile uint8_t tipoRx = 0;
volatile uint8_t datosRx[MAX_DATOS_CMD];
volatile uint8_t datosEsperados = 0;
volatile uint8_t datosRecibidos = 0;

// Cola circular de comandos válidos, listos para ejecutarse en loop().
// Al conectar, Java envía 3 comandos seguidos (Ts, LEDs e iniciar flujo); si
// llegan mientras sale una trama (12,5 ms), la cola evita que se pisen.
volatile uint8_t colaTipo[TAM_COLA_CMD];
volatile uint8_t colaDatos[TAM_COLA_CMD][MAX_DATOS_CMD];
volatile uint8_t indiceEscritura = 0;  // lo avanza la ISR
volatile uint8_t indiceLectura = 0;    // lo avanza loop()

// --- Telemetría ---
uint16_t lecturasAdc[NUM_ANALOGICAS];
uint8_t trama[LONGITUD_TRAMA];

// =====================================================================
// CONFIGURACIÓN DE PERIFÉRICOS (UART, ADC, SWITCHES, LEDS)
// =====================================================================

// La UART0 se configura por registros y no con Serial.begin(), porque la
// librería Serial ya tiene su propia ISR(USART_RX_vect) y no deja definir
// la de la Tarea #11 (el compilador marca "multiple definition of __vector_18").
void configurarUart() {
  UBRR0 = VALOR_UBRR;                                    // 9600 bps (error 0,16 %)
  UCSR0A &= ~(1 << U2X0);                                // velocidad normal, sin tocar UDRE0
  UCSR0B = (1 << RXEN0) | (1 << TXEN0) | (1 << RXCIE0);  // RX, TX e interrupción de RX
  UCSR0C = (1 << UCSZ01) | (1 << UCSZ00);                // 8 datos, sin paridad, 1 stop
}

void enviarByte(uint8_t dato) {
  while (!(UCSR0A & (1 << UDRE0))) {
    // espera a que el registro de transmisión quede libre
  }
  UDR0 = dato;
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

// =====================================================================
// TAREA #12: CONMUTACIÓN DE LOS 4 PINES DE SALIDA HACIA LOS LEDs
// =====================================================================

void configurarLeds() {
  for (uint8_t i = 0; i < NUM_LEDS; i++) {
    pinMode(PINES_LEDS[i], OUTPUT);
    digitalWrite(PINES_LEDS[i], LOW);
  }
}

// bit0 -> LED1 (PB0, pin 8) ... bit3 -> LED4 (PB3, pin 11)
void aplicarMascaraLeds(uint8_t mascara) {
  for (uint8_t i = 0; i < NUM_LEDS; i++) {
    bool encender = ((mascara >> i) & 0x01) != 0;
    digitalWrite(PINES_LEDS[i], encender ? HIGH : LOW);
  }
}

// =====================================================================
// TIMER1 PERIÓDICO (modo CTC, prescaler 64)
// =====================================================================

void configurarTimer1() {
  noInterrupts();
  TCCR1A = 0;                                         // sin PWM
  TCCR1B = (1 << WGM12) | (1 << CS11) | (1 << CS10);  // modo CTC, prescaler 64
  TIMSK1 = (1 << OCIE1A);                             // interrupción por comparación A
  interrupts();
  ajustarPrecargaTimer(TS_INICIAL_MS);  // Tarea #13
}

// Cada vez que TCNT1 llega a OCR1A. Tras N interrupciones se completa Ts.
ISR(TIMER1_COMPA_vect) {
  contadorInterrupciones++;
  if (contadorInterrupciones >= interrupcionesPorPeriodo) {
    contadorInterrupciones = 0;
    tocaEnviar = true;
  }
}

// =====================================================================
// TAREA #13: AJUSTE DINÁMICO DE LA PRECARGA DEL TIMER1
// =====================================================================

// cuentas por interrupción = Ts * (250 / N) -> OCR1A = cuentas - 1
void ajustarPrecargaTimer(uint16_t nuevoTsMs) {
  const uint8_t DIVISORES[] = { 1, 2, 5, 10, 25, 50, 125, 250 };
  uint8_t n = 250;
  uint32_t cuentas = nuevoTsMs;  // valor con N = 250 (sirve hasta Ts = 65535)

  for (uint8_t i = 0; i < sizeof(DIVISORES); i++) {
    uint32_t c = (uint32_t)nuevoTsMs * (CUENTAS_POR_MS / DIVISORES[i]);
    if (c <= 65536UL) {
      n = DIVISORES[i];
      cuentas = c;
      break;
    }
  }

  noInterrupts();                   // cambio atómico: la ISR no ve valores a medias
  OCR1A = (uint16_t)(cuentas - 1);  // nueva precarga del comparador
  TCNT1 = 0;                        // el nuevo periodo arranca desde cero
  interrupcionesPorPeriodo = n;
  contadorInterrupciones = 0;
  interrupts();
}

// =====================================================================
// TAREA #11: RX ISR CON MÁQUINA DE ESTADOS FINITA
// =====================================================================

// Cuántos datos trae cada tipo de comando (0 = tipo desconocido)
uint8_t datosDelTipo(uint8_t tipo) {
  switch (tipo) {
    case CMD_TIPO_LEDS: return 1;
    case CMD_TIPO_TS: return 2;
    case CMD_TIPO_FLUJO: return 1;
    default: return 0;
  }
}

// Se ejecuta cada vez que llega un byte por RX (PD0).
// Solo valida el comando y lo pone en la cola; lo ejecuta loop().
ISR(USART_RX_vect) {
  uint8_t byteRx = UDR0;  // leer UDR0 limpia la bandera de la interrupción

  switch (estadoRx) {
    case ESPERA_CABECERA:
      if (byteRx == CMD_HEADER) {
        estadoRx = ESPERA_TIPO;
      }
      break;

    case ESPERA_TIPO:
      datosEsperados = datosDelTipo(byteRx);
      if (datosEsperados > 0) {
        tipoRx = byteRx;
        datosRecibidos = 0;
        estadoRx = ESPERA_DATOS;
      } else if (byteRx != CMD_HEADER) {  // si llega otro 0xAA se queda esperando el tipo
        estadoRx = ESPERA_CABECERA;
      }
      break;

    case ESPERA_DATOS:
      datosRx[datosRecibidos++] = byteRx;
      if (datosRecibidos >= datosEsperados) {
        estadoRx = ESPERA_COLA;
      }
      break;

    case ESPERA_COLA:
      if (byteRx == CMD_TAIL) {  // trama completa y válida -> a la cola
        uint8_t siguiente = (indiceEscritura + 1) % TAM_COLA_CMD;
        if (siguiente != indiceLectura) {  // si la cola está llena, el comando se pierde
          colaTipo[indiceEscritura] = tipoRx;
          for (uint8_t i = 0; i < datosEsperados; i++) {
            colaDatos[indiceEscritura][i] = datosRx[i];
          }
          indiceEscritura = siguiente;
        }
        estadoRx = ESPERA_CABECERA;
      } else if (byteRx == CMD_HEADER) {  // trama corrupta, pero empieza otra
        estadoRx = ESPERA_TIPO;
      } else {  // trama corrupta: se descarta
        estadoRx = ESPERA_CABECERA;
      }
      break;
  }
}

// Saca el comando más antiguo de la cola. Devuelve false si está vacía.
bool tomarComando(uint8_t &tipo, uint8_t datos[]) {
  if (indiceLectura == indiceEscritura) {
    return false;
  }
  tipo = colaTipo[indiceLectura];
  for (uint8_t i = 0; i < MAX_DATOS_CMD; i++) {
    datos[i] = colaDatos[indiceLectura][i];
  }
  indiceLectura = (indiceLectura + 1) % TAM_COLA_CMD;
  return true;
}

void ejecutarComando(uint8_t tipo, const uint8_t datos[]) {
  switch (tipo) {
    case CMD_TIPO_LEDS:
      aplicarMascaraLeds(datos[0] & 0x0F);  // Tarea #12
      break;

    case CMD_TIPO_TS:
      {
        uint16_t nuevoTs = ((uint16_t)datos[0] << 8) | datos[1];  // MSB, LSB
        if (nuevoTs >= TS_MIN_MS) {                               // fuera de rango: se ignora
          ajustarPrecargaTimer(nuevoTs);                          // Tarea #13
        }
        break;
      }

    case CMD_TIPO_FLUJO:
      flujoHabilitado = (datos[0] == 0x01);
      break;
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
    trama[n++] = (uint8_t)(lecturasAdc[i] >> 2);  // 10 bits (0..1023) -> 8 bits (0..255)
#endif
  }

  trama[n] = FIN_TRAMA;

  for (uint8_t i = 0; i < LONGITUD_TRAMA; i++) {
    enviarByte(trama[i]);
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
  // 1. Ejecutar los comandos que dejó en cola la RX ISR (HU-03)
  uint8_t tipo;
  uint8_t datos[MAX_DATOS_CMD];
  while (tomarComando(tipo, datos)) {
    ejecutarComando(tipo, datos);
  }

  // 2. Si se cumplió el periodo Ts, muestrear y transmitir trama continua (HU-02)
  if (tocaEnviar) {
    tocaEnviar = false;
    if (flujoHabilitado) {
      leerAnalogicas();
      construirYEnviarTrama();
    }
  }
}