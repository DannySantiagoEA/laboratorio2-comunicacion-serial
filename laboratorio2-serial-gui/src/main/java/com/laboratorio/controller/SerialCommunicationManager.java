package com.laboratorio.controller;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortInvalidPortException;
import java.util.ArrayList;
import java.util.List;

/**
 * Controlador de comunicación serial utilizando la API jSerialComm. - Tarea
 * #15: Integración de librería y detección de puertos COM virtuales. - Tarea
 * #16: Configuración de parámetros seriales (9600, 8-N-1), apertura/cierre
 * seguro del descriptor de puerto y manejo de excepciones.
 */
public class SerialCommunicationManager {

    // Puerto virtual por defecto para la aplicación Java (par COM1 <-> COM2)
    public static final String PUERTO_OBJETIVO_DEFAULT = "COM2";

    // Parámetros seriales estandarizados (Tarea #16: 9600 bps, 8-N-1)
    public static final int DEFAULT_BAUD_RATE = 9600;
    public static final int DEFAULT_DATA_BITS = 8;
    public static final int DEFAULT_STOP_BITS = SerialPort.ONE_STOP_BIT;
    public static final int DEFAULT_PARITY = SerialPort.NO_PARITY;

    // Descriptor del puerto serial actualmente en uso
    private SerialPort puertoActivo;

    public SerialCommunicationManager() {
        // Gancho de seguridad (Shutdown Hook): asegura que el puerto COM se libere
        // en Windows incluso si la aplicación se cierra abruptamente.
        Runtime.getRuntime().addShutdownHook(new Thread(this::desconectar));
    }

    // =========================================================================
    // MÉTODOS DE DETECCIÓN Y ESCANEO (TAREA #15)
    // =========================================================================
    /**
     * Obtiene el arreglo nativo de descriptores SerialPort detectados en el SO.
     */
    public SerialPort[] obtenerDescriptoresPuertos() {
        return SerialPort.getCommPorts();
    }

    /**
     * Retorna una lista con los nombres de sistema de todos los puertos COM
     * activos (ej. ["COM1", "COM2"]).
     */
    public List<String> obtenerNombresPuertosDisponibles() {
        SerialPort[] puertos = SerialPort.getCommPorts();
        List<String> nombres = new ArrayList<>();
        for (SerialPort puerto : puertos) {
            nombres.add(puerto.getSystemPortName());
        }
        return nombres;
    }

    /**
     * Comprueba si un puerto específico (ej. "COM2") se encuentra activo y
     * reconocido por el sistema operativo.
     */
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
    /**
     * Sobrecarga que abre el puerto indicado usando los parámetros estándar del
     * laboratorio (9600 bps, 8 bits de datos, 1 bit de parada, sin paridad).
     *
     * @param nombrePuerto Identificador del puerto (ej. "COM2").
     * @throws SerialConnectionException Si el puerto no existe, está ocupado o
     * falla su apertura.
     */
    public synchronized void conectar(String nombrePuerto) throws SerialConnectionException {
        conectar(nombrePuerto, DEFAULT_BAUD_RATE, DEFAULT_DATA_BITS, DEFAULT_STOP_BITS, DEFAULT_PARITY);
    }

    /**
     * Sobrecarga que permite especificar el puerto y la velocidad en baudios,
     * manteniendo la trama 8-N-1.
     *
     * @param nombrePuerto Identificador del puerto (ej. "COM2").
     * @param baudRate Velocidad de transmisión (ej. 9600 o 115200).
     * @throws SerialConnectionException Si el puerto no existe, está ocupado o
     * falla su apertura.
     */
    public synchronized void conectar(String nombrePuerto, int baudRate) throws SerialConnectionException {
        conectar(nombrePuerto, baudRate, DEFAULT_DATA_BITS, DEFAULT_STOP_BITS, DEFAULT_PARITY);
    }

    /**
     * Tarea #16: Configura los parámetros de comunicación serial y abre de
     * forma segura el descriptor del puerto manejando posibles excepciones.
     *
     * @param nombrePuerto Identificador del sistema (ej. "COM2").
     * @param baudRate Tasa de baudios (ej. 9600).
     * @param dataBits Bits de datos por trama (8).
     * @param stopBits Bits de parada (SerialPort.ONE_STOP_BIT).
     * @param parity Paridad (SerialPort.NO_PARITY).
     * @throws SerialConnectionException Si ocurre un error de validación o
     * acceso nativo.
     */
    public synchronized void conectar(String nombrePuerto, int baudRate, int dataBits, int stopBits, int parity)
            throws SerialConnectionException {

        // 1. Validar entrada nula o vacía
        if (nombrePuerto == null || nombrePuerto.isBlank()) {
            throw new SerialConnectionException("Debe especificar un nombre de puerto COM válido (ej. COM2).");
        }
        String puertoLimpio = nombrePuerto.trim().toUpperCase();

        // 2. Validar tasa de baudios
        if (baudRate <= 0) {
            throw new SerialConnectionException("La velocidad en baudios debe ser mayor que 0 bps.");
        }

        // 3. Verificar si ya nos encontramos conectados a ese mismo puerto
        if (isConectado() && puertoActivo.getSystemPortName().equalsIgnoreCase(puertoLimpio)) {
            throw new SerialConnectionException("El puerto " + puertoLimpio + " ya se encuentra abierto en esta sesión.");
        }

        // 4. Si había otro puerto distinto abierto previamente, cerrarlo con seguridad
        if (isConectado()) {
            desconectar();
        }

        // 5. Verificar que el puerto realmente exista en la enumeración del sistema operativo
        if (!existePuerto(puertoLimpio)) {
            throw new SerialConnectionException(
                    "El puerto '" + puertoLimpio + "' no existe o fue desconectado del sistema.");
        }

        try {
            // 6. Obtener el descriptor nativo del puerto
            SerialPort candidato = SerialPort.getCommPort(puertoLimpio);

            // 7. Configurar parámetros seriales: 9600 bps, 8 DataBits, 1 StopBit, Parity None
            boolean paramsOk = candidato.setComPortParameters(baudRate, dataBits, stopBits, parity);
            if (!paramsOk) {
                throw new SerialConnectionException(
                        "No se pudieron aplicar los parámetros seriales (8-N-1) en " + puertoLimpio + ".");
            }

            // 8. Configurar Timeouts en modo no bloqueante para lectura asíncrona por eventos
            candidato.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);

            // 9. Limpiar buffers de hardware (RX/TX) antes de abrir y abrir el puerto
            boolean abierto = candidato.openPort();
            if (!abierto) {
                throw new SerialConnectionException(
                        "No fue posible abrir el puerto " + puertoLimpio
                        + ". Verifique que no esté ocupado por otra aplicación (ej. Proteus u otro monitor serial).");
            }

            candidato.flushIOBuffers();
            this.puertoActivo = candidato;

            System.out.printf("[OK #16] Puerto %s abierto exitosamente (%d bps, %d-N-%d).%n",
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

    /**
     * Tarea #16: Cierra de manera segura el descriptor del puerto activo,
     * remueve listeners y libera el recurso en el sistema operativo.
     *
     * @return true si el puerto estaba abierto y se cerró correctamente; false
     * si ya estaba cerrado.
     */
    public synchronized boolean desconectar() {
        if (puertoActivo == null) {
            return false;
        }

        String nombre = puertoActivo.getSystemPortName();
        try {
            // Remover cualquier listener asíncrono registrado antes de cerrar
            puertoActivo.removeDataListener();

            if (puertoActivo.isOpen()) {
                puertoActivo.flushIOBuffers();
                boolean cerrado = puertoActivo.closePort();
                if (cerrado) {
                    System.out.println("[OK #16] Puerto " + nombre + " cerrado y liberado de forma segura.");
                } else {
                    System.err.println("[AVISO #16] El sistema reportó demora al liberar el puerto " + nombre + ".");
                }
                return cerrado;
            }
            return false;
        } catch (Exception ex) {
            System.err.println("[ERROR #16] Excepción al cerrar el puerto " + nombre + ": " + ex.getMessage());
            return false;
        } finally {
            puertoActivo = null;
        }
    }

    /**
     * Verifica en tiempo real si el puerto sigue abierto y disponible.
     *
     * @return true si el descriptor es válido y permanece abierto.
     */
    public synchronized boolean isConectado() {
        return puertoActivo != null && puertoActivo.isOpen();
    }

    /**
     * Retorna el nombre del puerto actualmente conectado o "DESCONECTADO" si no
     * hay sesión.
     */
    public synchronized String getNombrePuertoActivo() {
        return isConectado() ? puertoActivo.getSystemPortName() : "DESCONECTADO";
    }

    /**
     * Retorna un resumen técnico de la configuración actual del puerto.
     */
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
    // DIAGNÓSTICO INTEGRAL DE PRUEBA (TAREAS #15 y #16)
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
