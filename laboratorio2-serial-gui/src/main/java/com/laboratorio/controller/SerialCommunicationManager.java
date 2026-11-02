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
 * de trama continua (cabecera 0x7A 0x7B, byte digital, 8 ADC y cola 0x7C). -
 * Tarea #18 / #22 / #23: Despacho de comandos binarios [0xAA ... 0x55] hacia el
 * microcontrolador.
 */
public class SerialCommunicationManager {

    public static final String PUERTO_OBJETIVO_DEFAULT = "COM2";

    // Parámetros seriales estandarizados (9600 bps, 8-N-1)
    public static final int DEFAULT_BAUD_RATE = 9600;
    public static final int DEFAULT_DATA_BITS = 8;
    public static final int DEFAULT_STOP_BITS = SerialPort.ONE_STOP_BIT;
    public static final int DEFAULT_PARITY = SerialPort.NO_PARITY;

    // Delimitadores de sincronismo de la trama continua (Micro -> PC)
    public static final byte HEADER_BYTE_1 = (byte) 0x7A;
    public static final byte HEADER_BYTE_2 = (byte) 0x7B;
    public static final byte TAIL_BYTE = (byte) 0x7C;

    // Delimitadores y códigos de comando (PC -> Micro)
    public static final byte CMD_START_BYTE = (byte) 0xAA;
    public static final byte CMD_END_BYTE = (byte) 0x55;
    public static final byte CMD_TIPO_LEDS = (byte) 0x01;
    public static final byte CMD_TIPO_TS = (byte) 0x02;
    public static final byte CMD_TIPO_FLUJO = (byte) 0x03;

    // Dimensiones del protocolo
    public static final int NUM_CANALES_DIGITALES = 4;
    public static final int NUM_CANALES_ANALOGICOS = 8;
    public static final double VOLTAJE_REFERENCIA = 5.0;

    public static final int LONGITUD_TRAMA_8_BITS = 12;
    public static final int LONGITUD_TRAMA_10_BITS = 20;

    private SerialPort puertoActivo;
    private final ByteArrayOutputStream bufferRecepcion = new ByteArrayOutputStream();
    private boolean modoAdc10Bits = false;

    private long tramasValidasRecibidas = 0;
    private long bytesDescartadosPorRuido = 0;

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

    // =========================================================================
    // ESCUCHADOR ASÍNCRONO Y PARSER DE TRAMA CONTINUA (TAREA #17 / #21)
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
    // DESPACHO DE COMANDOS AL MICROCONTROLADOR (TAREAS #18, #22 y #23)
    // =========================================================================
    private void verificarConexionActiva() throws SerialConnectionException {
        if (!isConectado()) {
            throw new SerialConnectionException("No hay un puerto serial conectado para enviar comandos.");
        }
    }

    public synchronized void enviarBytes(byte[] datos) throws SerialConnectionException {
        verificarConexionActiva();
        if (datos == null || datos.length == 0) {
            return;
        }
        int escritos = puertoActivo.writeBytes(datos, datos.length);
        if (escritos != datos.length) {
            throw new SerialConnectionException("Error de escritura serial: se enviaron "
                    + escritos + " de " + datos.length + " bytes.");
        }
    }

    /**
     * Tarea #18 y #22: Envía el comando [0xAA, 0x01, Mascara, 0x55] a partir
     * del arreglo booleano de los 4 LEDs.
     */
    public synchronized void enviarComandoLeds(boolean[] estadosLeds) throws SerialConnectionException {
        if (estadosLeds == null || estadosLeds.length < NUM_CANALES_DIGITALES) {
            throw new IllegalArgumentException("El arreglo de estados debe contener al menos 4 elementos.");
        }
        byte mascara = 0;
        for (int i = 0; i < NUM_CANALES_DIGITALES; i++) {
            if (estadosLeds[i]) {
                mascara |= (1 << i);
            }
        }
        enviarComandoLeds(mascara);
    }

    /**
     * Tarea #18 y #22: Envía la trama [0xAA, 0x01, MASCARA_LEDS, 0x55].
     */
    public synchronized void enviarComandoLeds(byte mascaraLeds) throws SerialConnectionException {
        byte mascaraLimpia = (byte) (mascaraLeds & 0x0F);
        byte[] tramaComando = new byte[]{
            CMD_START_BYTE,
            CMD_TIPO_LEDS,
            mascaraLimpia,
            CMD_END_BYTE
        };
        enviarBytes(tramaComando);
        System.out.printf(">> [TX Serial 0x01] Comando LEDs enviado: [0xAA, 0x01, 0x%02X, 0x55]%n", mascaraLimpia);
    }

    /**
     * Tarea #18 y #23: Envía la trama [0xAA, 0x02, MSB_Ts, LSB_Ts, 0x55] con el
     * nuevo periodo Ts (ms).
     */
    public synchronized void enviarComandoMuestreo(int periodoMs) throws SerialConnectionException {
        byte msb = (byte) ((periodoMs >> 8) & 0xFF);
        byte lsb = (byte) (periodoMs & 0xFF);

        byte[] tramaComando = new byte[]{
            CMD_START_BYTE,
            CMD_TIPO_TS,
            msb,
            lsb,
            CMD_END_BYTE
        };
        enviarBytes(tramaComando);
        System.out.printf(">> [TX Serial 0x02] Comando Ts = %d ms enviado: [0xAA, 0x02, 0x%02X, 0x%02X, 0x55]%n",
                periodoMs, msb & 0xFF, lsb & 0xFF);
    }

    /**
     * Envía la trama [0xAA, 0x03, 0x01=Iniciar / 0x00=Pausar, 0x55].
     */
    public synchronized void enviarComandoEstadoFlujo(boolean iniciar) throws SerialConnectionException {
        byte estadoByte = (byte) (iniciar ? 0x01 : 0x00);
        byte[] tramaComando = new byte[]{
            CMD_START_BYTE,
            CMD_TIPO_FLUJO,
            estadoByte,
            CMD_END_BYTE
        };
        enviarBytes(tramaComando);
        System.out.printf(">> [TX Serial 0x03] Comando Flujo (%s) enviado: [0xAA, 0x03, 0x%02X, 0x55]%n",
                (iniciar ? "INICIAR" : "PAUSAR"), estadoByte);
    }

    public synchronized long getTramasValidasRecibidas() {
        return tramasValidasRecibidas;
    }

    public synchronized long getBytesDescartadosPorRuido() {
        return bytesDescartadosPorRuido;
    }

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
