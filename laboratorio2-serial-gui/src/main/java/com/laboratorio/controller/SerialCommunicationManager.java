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
 * Controlador de comunicación serial utilizando la API jSerialComm (HU-04). -
 * Tarea #15: Integración de librería y detección de puertos COM virtuales. -
 * Tarea #16: Configuración de parámetros seriales (9600, 8-N-1),
 * apertura/cierre seguro del descriptor de puerto y manejo de excepciones. -
 * Tarea #17: Escuchador SerialPortDataListener (LISTENING_EVENT_DATA_AVAILABLE)
 * y parser de trama continua (cabecera 0x7A 0x7B, byte digital, 8 ADC y cola
 * 0x7C). - Tarea #18: Métodos de despacho de comandos hacia el microcontrolador
 * (LEDs y Ts) delimitados con [0xAA ... 0x55].
 */
public class SerialCommunicationManager {

    // Puerto virtual por defecto para la aplicación Java (par COM1 <-> COM2)
    public static final String PUERTO_OBJETIVO_DEFAULT = "COM2";

    // Parámetros seriales estandarizados (Tarea #16: 9600 bps, 8-N-1)
    public static final int DEFAULT_BAUD_RATE = 9600;
    public static final int DEFAULT_DATA_BITS = 8;
    public static final int DEFAULT_STOP_BITS = SerialPort.ONE_STOP_BIT;
    public static final int DEFAULT_PARITY = SerialPort.NO_PARITY;

    // Delimitadores de sincronismo de la trama continua Micro -> PC (Tarea #17)
    public static final byte HEADER_BYTE_1 = (byte) 0x7A;
    public static final byte HEADER_BYTE_2 = (byte) 0x7B;
    public static final byte TAIL_BYTE = (byte) 0x7C;

    // Delimitadores e identificadores de comandos PC -> Micro (Tarea #18)
    public static final byte CMD_HEADER = (byte) 0xAA;
    public static final byte CMD_TIPO_LEDS = (byte) 0x01;
    public static final byte CMD_TIPO_MUESTREO = (byte) 0x02;
    public static final byte CMD_TIPO_FLUJO = (byte) 0x03;
    public static final byte CMD_TAIL = (byte) 0x55;

    // Cotas de seguridad para el periodo de muestreo Ts en milisegundos (16 bits unsigned)
    public static final int MIN_PERIODO_MS = 10;
    public static final int MAX_PERIODO_MS = 65535;

    // Dimensiones del protocolo
    public static final int NUM_CANALES_DIGITALES = 4;
    public static final int NUM_CANALES_ANALOGICOS = 8;
    public static final double VOLTAJE_REFERENCIA = 5.0;

    // Longitud total de trama según resolución ADC:
    // - Modo 8 bits: 2 (Header) + 1 (Dig) + 8 (ADC) + 1 (Tail) = 12 bytes
    // - Modo 10 bits: 2 (Header) + 1 (Dig) + 16 (ADC) + 1 (Tail) = 20 bytes
    public static final int LONGITUD_TRAMA_8_BITS = 12;
    public static final int LONGITUD_TRAMA_10_BITS = 20;

    private SerialPort puertoActivo;
    private final ByteArrayOutputStream bufferRecepcion = new ByteArrayOutputStream();
    private boolean modoAdc10Bits = false;

    // Contadores de diagnóstico de recepción y transmisión
    private long tramasValidasRecibidas = 0;
    private long bytesDescartadosPorRuido = 0;
    private long comandosEnviados = 0;

    private TramaRecibidaListener listenerTrama;

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

    public synchronized void procesarFlujoBytes(byte[] nuevosBytes, int cantidad) {
        if (nuevosBytes == null || cantidad <= 0) {
            return;
        }

        bufferRecepcion.write(nuevosBytes, 0, cantidad);
        byte[] buffer = bufferRecepcion.toByteArray();

        int longitudTrama = getLongitudTramaEsperada();
        int cursor = 0;

        while (cursor <= buffer.length - longitudTrama) {
            if (buffer[cursor] == HEADER_BYTE_1 && buffer[cursor + 1] == HEADER_BYTE_2) {
                int posicionCola = cursor + longitudTrama - 1;

                if (buffer[posicionCola] == TAIL_BYTE) {
                    byte[] tramaCompleta = Arrays.copyOfRange(buffer, cursor, cursor + longitudTrama);
                    TramaTelemetria objetoTrama = desempaquetarTrama(tramaCompleta);

                    if (listenerTrama != null) {
                        listenerTrama.onTramaValida(objetoTrama);
                    }

                    cursor += longitudTrama;
                } else {
                    bytesDescartadosPorRuido++;
                    cursor++;
                }
            } else {
                bytesDescartadosPorRuido++;
                cursor++;
            }
        }

        bufferRecepcion.reset();
        if (cursor < buffer.length) {
            bufferRecepcion.write(buffer, cursor, buffer.length - cursor);
        }
    }

    private TramaTelemetria desempaquetarTrama(byte[] trama) {
        tramasValidasRecibidas++;

        byte byteDigital = trama[2];
        int[] estadosDigitales = new int[NUM_CANALES_DIGITALES];
        for (int i = 0; i < NUM_CANALES_DIGITALES; i++) {
            estadosDigitales[i] = (byteDigital >> i) & 0x01;
        }

        int[] cuentasAdc = new int[NUM_CANALES_ANALOGICOS];
        double[] voltajes = new double[NUM_CANALES_ANALOGICOS];

        if (!modoAdc10Bits) {
            for (int ch = 0; ch < NUM_CANALES_ANALOGICOS; ch++) {
                int valor8Bits = trama[3 + ch] & 0xFF;
                cuentasAdc[ch] = valor8Bits;
                voltajes[ch] = (valor8Bits * VOLTAJE_REFERENCIA) / 255.0;
            }
        } else {
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

    // =========================================================================
    // DESPACHO DE COMANDOS DE CONTROL HACIA EL MICROCONTROLADOR (TAREA #18)
    // =========================================================================
    /**
     * Tarea #18: Construye la trama binaria de 4 bytes para conmutar los 4
     * LEDs: [0xAA] [0x01] [MASCARA_LEDS (0x00..0x0F)] [0x55]
     *
     * @param mascara Byte cuyos 4 bits menos significativos (b0..b3) definen el
     * estado de LED1..LED4.
     * @return Arreglo de 4 bytes listo para transmitir.
     */
    public byte[] construirTramaComandoLeds(byte mascara) {
        byte mascaraLimpia = (byte) (mascara & 0x0F); // Garantizar que solo b0..b3 estén activos
        return new byte[]{
            CMD_HEADER,
            CMD_TIPO_LEDS,
            mascaraLimpia,
            CMD_TAIL
        };
    }

    /**
     * Tarea #18: Envía por el puerto serial activo el comando de
     * encendido/apagado de los 4 LEDs en Proteus/SimulIDE. Estructura: [0xAA]
     * [0x01] [MASCARA_LEDS] [0x55]
     *
     * @param mascara Byte de máscara (bit 0 = LED 1, bit 1 = LED 2, bit 2 = LED
     * 3, bit 3 = LED 4).
     * @return true si los 4 bytes se transmitieron íntegramente por el puerto.
     * @throws SerialConnectionException Si el puerto está cerrado o falla la
     * escritura.
     */
    public synchronized boolean enviarComandoLeds(byte mascara) throws SerialConnectionException {
        verificarConexionParaEnvio();

        byte[] trama = construirTramaComandoLeds(mascara);
        int enviados = puertoActivo.writeBytes(trama, trama.length);

        if (enviados != trama.length) {
            throw new SerialConnectionException(
                    "Fallo al transmitir comando de LEDs: se enviaron " + enviados + " de " + trama.length + " bytes.");
        }

        comandosEnviados++;
        System.out.printf("[TX #18 LEDs] Trama enviada (%d bytes): %s | Mascara binaria: %s%n",
                trama.length,
                formatearHex(trama),
                String.format("%4s", Integer.toBinaryString(mascara & 0x0F)).replace(' ', '0'));
        return true;
    }

    /**
     * Sobrecarga auxiliar que convierte un arreglo de 4 booleanos (ej. desde
     * SalidasDigitales o los botones Out 1..Out 4 de la GUI) en el byte de
     * máscara y lo despacha al microcontrolador.
     *
     * @param estadosLeds Arreglo booleano de longitud 4 (índice 0 = LED 1 ...
     * índice 3 = LED 4).
     * @return true si el comando se transmitió correctamente.
     * @throws SerialConnectionException Si el arreglo es inválido o el puerto
     * no está abierto.
     */
    public synchronized boolean enviarComandoLeds(boolean[] estadosLeds) throws SerialConnectionException {
        if (estadosLeds == null || estadosLeds.length < NUM_CANALES_DIGITALES) {
            throw new SerialConnectionException("Se requiere el estado de las 4 salidas digitales para armar la máscara.");
        }
        byte mascara = 0x00;
        for (int i = 0; i < NUM_CANALES_DIGITALES; i++) {
            if (estadosLeds[i]) {
                mascara |= (byte) (1 << i);
            }
        }
        return enviarComandoLeds(mascara);
    }

    /**
     * Tarea #18: Construye la trama binaria de 5 bytes para cambiar el periodo
     * de muestreo Ts: [0xAA] [0x02] [MSB_Ts] [LSB_Ts] [0x55]
     *
     * @param periodoMs Nuevo periodo de muestreo en milisegundos (10 ms a 65535
     * ms).
     * @return Arreglo de 5 bytes listo para transmitir.
     */
    public byte[] construirTramaComandoMuestreo(int periodoMs) {
        byte msb = (byte) ((periodoMs >> 8) & 0xFF);
        byte lsb = (byte) (periodoMs & 0xFF);
        return new byte[]{
            CMD_HEADER,
            CMD_TIPO_MUESTREO,
            msb,
            lsb,
            CMD_TAIL
        };
    }

    /**
     * Tarea #18: Envía por el puerto serial activo el comando para reconfigurar
     * en caliente el periodo de muestreo (Ts) del Timer en el microcontrolador.
     * Estructura: [0xAA] [0x02] [MSB_Ts] [LSB_Ts] [0x55]
     *
     * @param periodoMs Periodo de muestreo deseado en milisegundos (entre 10 y
     * 65535 ms).
     * @return true si los 5 bytes se transmitieron íntegramente por el puerto.
     * @throws SerialConnectionException Si el periodo está fuera de rango, el
     * puerto está cerrado o falla el envío.
     */
    public synchronized boolean enviarComandoMuestreo(int periodoMs) throws SerialConnectionException {
        if (periodoMs < MIN_PERIODO_MS || periodoMs > MAX_PERIODO_MS) {
            throw new SerialConnectionException(String.format(
                    "El periodo de muestreo Ts (%d ms) está fuera del rango permitido [%d ms - %d ms].",
                    periodoMs, MIN_PERIODO_MS, MAX_PERIODO_MS));
        }
        verificarConexionParaEnvio();

        byte[] trama = construirTramaComandoMuestreo(periodoMs);
        int enviados = puertoActivo.writeBytes(trama, trama.length);

        if (enviados != trama.length) {
            throw new SerialConnectionException(
                    "Fallo al transmitir comando de muestreo Ts: se enviaron " + enviados + " de " + trama.length + " bytes.");
        }

        comandosEnviados++;
        System.out.printf("[TX #18 Ts]   Trama enviada (%d bytes): %s | Nuevo Ts = %d ms (MSB=0x%02X, LSB=0x%02X)%n",
                trama.length,
                formatearHex(trama),
                periodoMs,
                trama[2] & 0xFF,
                trama[3] & 0xFF);
        return true;
    }

    /**
     * Método complementario del protocolo (Comando 0x03) para ordenar al
     * microcontrolador iniciar (0x01) o pausar (0x00) la emisión continua de
     * telemetría. Estructura: [0xAA] [0x03] [0x01 / 0x00] [0x55]
     */
    public synchronized boolean enviarComandoEstadoFlujo(boolean iniciar) throws SerialConnectionException {
        verificarConexionParaEnvio();

        byte[] trama = new byte[]{
            CMD_HEADER,
            CMD_TIPO_FLUJO,
            (byte) (iniciar ? 0x01 : 0x00),
            CMD_TAIL
        };
        int enviados = puertoActivo.writeBytes(trama, trama.length);
        if (enviados != trama.length) {
            throw new SerialConnectionException("No se pudo enviar el comando de control de flujo.");
        }
        comandosEnviados++;
        System.out.printf("[TX #18 Flujo] Trama enviada (%d bytes): %s | Accion: %s%n",
                trama.length, formatearHex(trama), iniciar ? "INICIAR" : "DETENER");
        return true;
    }

    private void verificarConexionParaEnvio() throws SerialConnectionException {
        if (!isConectado()) {
            throw new SerialConnectionException(
                    "No hay una conexión serial activa. Conecte el puerto COM antes de enviar comandos.");
        }
    }

    /**
     * Convierte un arreglo de bytes a su representación hexadecimal legible
     * (ej. "[0xAA, 0x01, 0x05, 0x55]").
     */
    public static String formatearHex(byte[] trama) {
        if (trama == null || trama.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < trama.length; i++) {
            sb.append(String.format("0x%02X", trama[i] & 0xFF));
            if (i < trama.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public synchronized long getTramasValidasRecibidas() {
        return tramasValidasRecibidas;
    }

    public synchronized long getBytesDescartadosPorRuido() {
        return bytesDescartadosPorRuido;
    }

    public synchronized long getComandosEnviados() {
        return comandosEnviados;
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
