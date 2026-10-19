package com.laboratorio.controller;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortInvalidPortException;
import com.laboratorio.model.TramaTelemetria;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Controlador de comunicación serial utilizando la API jSerialComm. - Tarea
 * #15: Integración de librería y detección de puertos COM virtuales. - Tarea
 * #16: Configuración de parámetros seriales (9600, 8-N-1), apertura/cierre
 * seguro del descriptor de puerto y manejo de excepciones. - Tarea #17:
 * Escuchador SerialPortDataListener (LISTENING_EVENT_DATA_AVAILABLE) y parser
 * de trama continua (cabecera 0x7A 0x7B, byte digital, 8 ADC y cola 0x7C).
 */
public class SerialCommunicationManager {

    // Puerto virtual por defecto para la aplicación Java (par COM1 <-> COM2)
    public static final String PUERTO_OBJETIVO_DEFAULT = "COM2";

    // Parámetros seriales estandarizados (Tarea #16: 9600 bps, 8-N-1)
    public static final int DEFAULT_BAUD_RATE = 9600;
    public static final int DEFAULT_DATA_BITS = 8;
    public static final int DEFAULT_STOP_BITS = SerialPort.ONE_STOP_BIT;
    public static final int DEFAULT_PARITY = SerialPort.NO_PARITY;

    // Delimitadores de sincronismo de la trama continua (Tarea #17)
    public static final byte HEADER_BYTE_1 = (byte) 0x7A;
    public static final byte HEADER_BYTE_2 = (byte) 0x7B;
    public static final byte TAIL_BYTE = (byte) 0x7C;

    // Dimensiones del protocolo
    public static final int NUM_CANALES_DIGITALES = 4;
    public static final int NUM_CANALES_ANALOGICOS = 8;
    public static final double VOLTAJE_REFERENCIA = 5.0;

    // Longitud total de trama según resolución ADC:
    // - Modo 8 bits (1 byte por canal): 2 (Header) + 1 (Dig) + 8 (ADC) + 1 (Tail) = 12 bytes
    // - Modo 10 bits (2 bytes por canal): 2 (Header) + 1 (Dig) + 16 (ADC) + 1 (Tail) = 20 bytes
    public static final int LONGITUD_TRAMA_8_BITS = 12;
    public static final int LONGITUD_TRAMA_10_BITS = 20;

    // Descriptor del puerto serial actualmente en uso
    private SerialPort puertoActivo;

    // Buffer acumulador para reconstruir tramas fragmentadas en recepción asíncrona
    private final ByteArrayOutputStream bufferRecepcion = new ByteArrayOutputStream();

    // Configuración de resolución ADC (false = 8 bits [12 bytes], true = 10 bits [20 bytes])
    private boolean modoAdc10Bits = false;

    // Contadores de diagnóstico del parser
    private long tramasValidasRecibidas = 0;
    private long bytesDescartadosPorRuido = 0;

    // Callback para entregar cada trama decodificada al controlador / GUI
    private TramaRecibidaListener listenerTrama;

    /**
     * Interfaz funcional para notificar de forma asíncrona cada vez que el
     * parser valida y desempaqueta una trama completa (0x7A 0x7B ... 0x7C).
     */
    @FunctionalInterface
    public interface TramaRecibidaListener {

        void onTramaValida(TramaTelemetria trama);
    }

    public SerialCommunicationManager() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::desconectar));
    }

    public void setTramaRecibidaListener(TramaRecibidaListener listener) {
        this.listenerTrama = listener;
    }

    /**
     * Define si el parser debe esperar 8 bytes analógicos (ADC 8 bits -> trama
     * de 12 bytes) o 16 bytes analógicos (ADC 10 bits -> trama de 20 bytes).
     */
    public synchronized void setModoAdc10Bits(boolean usar10Bits) {
        this.modoAdc10Bits = usar10Bits;
        this.bufferRecepcion.reset();
    }

    public synchronized boolean isModoAdc10Bits() {
        return modoAdc10Bits;
    }

    public synchronized int getLongitudTramaEsperada() {
        return modoAdc10Bits ? LONGITUD_TRAMA_10_BITS : LONGITUD_TRAMA_8_BITS;
    }

    // =========================================================================
    // MÉTODOS DE DETECCIÓN Y ESCANEO (TAREA #15)
    // =========================================================================
    public SerialPort[] obtenerDescriptoresPuertos() {
        return SerialPort.getCommPorts();
    }

    public List<String> obtenerNombresPuertosDisponibles() {
        SerialPort[] puertos = SerialPort.getCommPorts();
        List<String> nombres = new ArrayList<>();
        for (SerialPort puerto : puertos) {
            nombres.add(puerto.getSystemPortName());
        }
        return nombres;
    }

    public boolean existePuerto(String nombrePuerto) {
        if (nombrePuerto == null || nombrePuerto.isBlank()) {
            return false;
        }
        for (SerialPort puerto : SerialPort.getCommPorts()) {
            if (puerto.getSystemPortName().equalsIgnoreCase(nombrePuerto.trim())) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // MÉTODOS DE APERTURA, CONFIGURACIÓN Y CIERRE SEGURO (TAREA #16)
    // =========================================================================
    public synchronized void conectar(String nombrePuerto) throws SerialConnectionException {
        conectar(nombrePuerto, DEFAULT_BAUD_RATE, DEFAULT_DATA_BITS, DEFAULT_STOP_BITS, DEFAULT_PARITY);
    }

    public synchronized void conectar(String nombrePuerto, int baudRate) throws SerialConnectionException {
        conectar(nombrePuerto, baudRate, DEFAULT_DATA_BITS, DEFAULT_STOP_BITS, DEFAULT_PARITY);
    }

    public synchronized void conectar(String nombrePuerto, int baudRate, int dataBits, int stopBits, int parity)
            throws SerialConnectionException {

        if (nombrePuerto == null || nombrePuerto.isBlank()) {
            throw new SerialConnectionException("Debe especificar un nombre de puerto COM válido (ej. COM2).");
        }
        String puertoLimpio = nombrePuerto.trim().toUpperCase();

        if (baudRate <= 0) {
            throw new SerialConnectionException("La velocidad en baudios debe ser mayor que 0 bps.");
        }

        if (isConectado() && puertoActivo.getSystemPortName().equalsIgnoreCase(puertoLimpio)) {
            throw new SerialConnectionException("El puerto " + puertoLimpio + " ya se encuentra abierto en esta sesión.");
        }

        if (isConectado()) {
            desconectar();
        }

        if (!existePuerto(puertoLimpio)) {
            throw new SerialConnectionException(
                    "El puerto '" + puertoLimpio + "' no existe o fue desconectado del sistema.");
        }

        try {
            SerialPort candidato = SerialPort.getCommPort(puertoLimpio);

            boolean paramsOk = candidato.setComPortParameters(baudRate, dataBits, stopBits, parity);
            if (!paramsOk) {
                throw new SerialConnectionException(
                        "No se pudieron aplicar los parámetros seriales (8-N-1) en " + puertoLimpio + ".");
            }

            candidato.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);

            boolean abierto = candidato.openPort();
            if (!abierto) {
                throw new SerialConnectionException(
                        "No fue posible abrir el puerto " + puertoLimpio
                        + ". Verifique que no esté ocupado por otra aplicación.");
            }

            candidato.flushIOBuffers();
            this.puertoActivo = candidato;
            this.bufferRecepcion.reset();

            // Tarea #17: Registrar el escuchador asíncrono al abrir el puerto
            registrarEscuchadorDatosAsincrono();

            System.out.printf("[OK #16/#17] Puerto %s abierto (%d bps, %d-N-%d) con Listener activo.%n",
                    puertoActivo.getSystemPortName(),
                    puertoActivo.getBaudRate(),
                    puertoActivo.getNumDataBits(),
                    (puertoActivo.getNumStopBits() == SerialPort.ONE_STOP_BIT ? 1 : 2));

        } catch (SerialPortInvalidPortException ex) {
            this.puertoActivo = null;
            throw new SerialConnectionException(
                    "Descriptor de puerto inválido para '" + puertoLimpio + "': " + ex.getMessage(), ex);
        } catch (SerialConnectionException ex) {
            this.puertoActivo = null;
            throw ex;
        } catch (Exception ex) {
            this.puertoActivo = null;
            throw new SerialConnectionException(
                    "Error inesperado al intentar abrir el puerto " + puertoLimpio + ": " + ex.getMessage(), ex);
        }
    }

    public synchronized boolean desconectar() {
        if (puertoActivo == null) {
            return false;
        }

        String nombre = puertoActivo.getSystemPortName();
        try {
            puertoActivo.removeDataListener();

            if (puertoActivo.isOpen()) {
                puertoActivo.flushIOBuffers();
                boolean cerrado = puertoActivo.closePort();
                if (cerrado) {
                    System.out.println("[OK #16] Puerto " + nombre + " cerrado y liberado de forma segura.");
                }
                return cerrado;
            }
            return false;
        } catch (Exception ex) {
            System.err.println("[ERROR #16] Excepción al cerrar el puerto " + nombre + ": " + ex.getMessage());
            return false;
        } finally {
            puertoActivo = null;
            bufferRecepcion.reset();
        }
    }

    public synchronized boolean isConectado() {
        return puertoActivo != null && puertoActivo.isOpen();
    }

    public synchronized String getNombrePuertoActivo() {
        return isConectado() ? puertoActivo.getSystemPortName() : "DESCONECTADO";
    }

    public synchronized String obtenerResumenConfiguracion() {
        if (!isConectado()) {
            return "Sin conexión activa.";
        }
        String paridadTxt = switch (puertoActivo.getParity()) {
            case SerialPort.NO_PARITY ->
                "None (N)";
            case SerialPort.EVEN_PARITY ->
                "Even (E)";
            case SerialPort.ODD_PARITY ->
                "Odd (O)";
            case SerialPort.MARK_PARITY ->
                "Mark (M)";
            case SerialPort.SPACE_PARITY ->
                "Space (S)";
            default ->
                "Desconocida";
        };
        int stopTxt = (puertoActivo.getNumStopBits() == SerialPort.ONE_STOP_BIT) ? 1 : 2;

        return String.format("Puerto: %s | BaudRate: %d bps | DataBits: %d | Parity: %s | StopBits: %d",
                puertoActivo.getSystemPortName(),
                puertoActivo.getBaudRate(),
                puertoActivo.getNumDataBits(),
                paridadTxt,
                stopTxt);
    }

    // =========================================================================
    // ESCUCHADOR ASÍNCRONO Y PARSER DE TRAMA CONTINUA (TAREA #17)
    // =========================================================================
    /**
     * Tarea #17: Registra el SerialPortDataListener en modo
     * LISTENING_EVENT_DATA_AVAILABLE. Se ejecuta en un hilo independiente
     * gestionado por jSerialComm sin bloquear el EDT.
     */
    private void registrarEscuchadorDatosAsincrono() {
        puertoActivo.addDataListener(new SerialPortDataListener() {
            @Override
            public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
            }

            @Override
            public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
                    return;
                }
                if (puertoActivo == null || !puertoActivo.isOpen()) {
                    return;
                }

                int disponibles = puertoActivo.bytesAvailable();
                if (disponibles <= 0) {
                    return;
                }

                byte[] bloqueEntrante = new byte[disponibles];
                int leidos = puertoActivo.readBytes(bloqueEntrante, bloqueEntrante.length);
                if (leidos > 0) {
                    procesarFlujoBytes(bloqueEntrante, leidos);
                }
            }
        });
    }

    /**
     * Tarea #17: Algoritmo de búsqueda de delimitadores de sincronismo (0x7A,
     * 0x7B), verificación de cola (0x7C) y tolerancia a fragmentación o ruido
     * en la línea.
     *
     * @param nuevosBytes Arreglo de bytes recién leídos del puerto serial.
     * @param cantidad Número efectivo de bytes leídos.
     */
    public synchronized void procesarFlujoBytes(byte[] nuevosBytes, int cantidad) {
        if (nuevosBytes == null || cantidad <= 0) {
            return;
        }

        // 1. Anexar los nuevos bytes al buffer acumulador
        bufferRecepcion.write(nuevosBytes, 0, cantidad);
        byte[] buffer = bufferRecepcion.toByteArray();

        int longitudTrama = getLongitudTramaEsperada();
        int cursor = 0;

        // 2. Recorrer el buffer mientras existan suficientes bytes para al menos una trama completa
        while (cursor <= buffer.length - longitudTrama) {
            // Verificar coincidencia de la cabecera doble: 0x7A seguido de 0x7B
            if (buffer[cursor] == HEADER_BYTE_1 && buffer[cursor + 1] == HEADER_BYTE_2) {
                int posicionCola = cursor + longitudTrama - 1;

                // Verificar si el byte de cierre en la posición esperada es 0x7C
                if (buffer[posicionCola] == TAIL_BYTE) {
                    byte[] tramaCompleta = Arrays.copyOfRange(buffer, cursor, cursor + longitudTrama);
                    TramaTelemetria objetoTrama = desempaquetarTrama(tramaCompleta);

                    if (listenerTrama != null) {
                        listenerTrama.onTramaValida(objetoTrama);
                    }

                    // Saltar exactamente al final de esta trama válida
                    cursor += longitudTrama;
                } else {
                    // Cabecera falsa (coincidencia de datos con 0x7A 0x7B) o trama corrupta:
                    // avanzar 1 byte para buscar el siguiente sincronismo real
                    bytesDescartadosPorRuido++;
                    cursor++;
                }
            } else {
                // Byte fuera de sincronismo (ruido o residuo previo): descartar y avanzar
                bytesDescartadosPorRuido++;
                cursor++;
            }
        }

        // 3. Conservar en el acumulador únicamente el fragmento final incompleto (si lo hay)
        bufferRecepcion.reset();
        if (cursor < buffer.length) {
            bufferRecepcion.write(buffer, cursor, buffer.length - cursor);
        }
    }

    /**
     * Tarea #17: Desempaqueta una trama validada extrayendo el byte de 4
     * entradas digitales y reconstruyendo las 8 lecturas de voltaje ADC (0.0 V
     * a 5.0 V).
     *
     * @param trama Arreglo de bytes validado con cabecera [0x7A, 0x7B] y fin
     * [0x7C].
     * @return Instancia inmutable de TramaTelemetria con datos crudos y
     * escalados.
     */
    private TramaTelemetria desempaquetarTrama(byte[] trama) {
        tramasValidasRecibidas++;

        // 1. Extraer byte digital (posición 2) y desempaquetar los 4 bits del DIP-Switch (b0..b3)
        byte byteDigital = trama[2];
        int[] estadosDigitales = new int[NUM_CANALES_DIGITALES];
        for (int i = 0; i < NUM_CANALES_DIGITALES; i++) {
            estadosDigitales[i] = (byteDigital >> i) & 0x01;
        }

        // 2. Extraer y escalar los 8 canales analógicos ADC
        int[] cuentasAdc = new int[NUM_CANALES_ANALOGICOS];
        double[] voltajes = new double[NUM_CANALES_ANALOGICOS];

        if (!modoAdc10Bits) {
            // Modo 8 bits: 1 byte sin signo por canal (posiciones 3 a 10), rango 0..255 -> 0.0..5.0 V
            for (int ch = 0; ch < NUM_CANALES_ANALOGICOS; ch++) {
                int valor8Bits = trama[3 + ch] & 0xFF;
                cuentasAdc[ch] = valor8Bits;
                voltajes[ch] = (valor8Bits * VOLTAJE_REFERENCIA) / 255.0;
            }
        } else {
            // Modo 10 bits: 2 bytes por canal [MSB, LSB] (posiciones 3 a 18), rango 0..1023 -> 0.0..5.0 V
            for (int ch = 0; ch < NUM_CANALES_ANALOGICOS; ch++) {
                int offset = 3 + (ch * 2);
                int msb = trama[offset] & 0xFF;
                int lsb = trama[offset + 1] & 0xFF;
                int valor10Bits = ((msb << 8) | lsb) & 0x03FF;
                cuentasAdc[ch] = valor10Bits;
                voltajes[ch] = (valor10Bits * VOLTAJE_REFERENCIA) / 1023.0;
            }
        }

        return new TramaTelemetria(
                tramasValidasRecibidas,
                byteDigital,
                estadosDigitales,
                cuentasAdc,
                voltajes
        );
    }

    public synchronized long getTramasValidasRecibidas() {
        return tramasValidasRecibidas;
    }

    public synchronized long getBytesDescartadosPorRuido() {
        return bytesDescartadosPorRuido;
    }

    // =========================================================================
    // DIAGNÓSTICO DE PUERTOS (TAREA #15)
    // =========================================================================
    public void ejecutarDiagnosticoPuertos() {
        System.out.println("=================================================================");
        System.out.println("  DIAGNOSTICO TAREA #15 - DETECCION DE PUERTOS CON jSerialComm   ");
        System.out.println("=================================================================");

        SerialPort[] puertos = SerialPort.getCommPorts();
        System.out.println(">> Total de puertos seriales detectados: " + puertos.length);

        for (int i = 0; i < puertos.length; i++) {
            SerialPort p = puertos[i];
            System.out.printf("   [%d] Puerto: %-6s | Descripcion: %-45s | Abierto: %s%n",
                    (i + 1),
                    p.getSystemPortName(),
                    p.getDescriptivePortName(),
                    p.isOpen() ? "SI" : "NO");
        }
        System.out.println("=================================================================");
    }
}
