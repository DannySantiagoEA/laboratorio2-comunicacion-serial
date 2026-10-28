package com.laboratorio.model;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Subtarea #33 (HU-07): Estructura de datos en memoria para salidas digitales y
 * actuadores virtuales (Out 1 a Out 4). Gestiona de forma thread-safe los
 * estados lógicos (0 / 1), la marca temporal de conmutación y la notificación a
 * disparadores (listeners).
 */
public class SalidasDigitales {

    public static final int NUM_SALIDAS = 4;
    private final boolean[] estados = new boolean[NUM_SALIDAS]; // Por defecto: false (0 / OFF)

    // Interfaces funcionales para permitir expresiones lambda en agregarDisparador
    @FunctionalInterface
    public interface Disparador {

        void onCambio(int canal, boolean estado);
    }

    @FunctionalInterface
    public interface DisparadorConTiempo {

        void onCambio(int canal, boolean estado, double tiempo);
    }

    // Listas seguras para concurrencia de manejadores de eventos
    private final List<Disparador> disparadores = new CopyOnWriteArrayList<>();
    private final List<DisparadorConTiempo> disparadoresConTiempo = new CopyOnWriteArrayList<>();

    public SalidasDigitales() {
        reiniciar();
    }

    // =========================================================================
    // REGISTRO DE DISPARADORES / LISTENERS (Línea 97 de ControladorAdquisicion)
    // =========================================================================
    /**
     * Registra un disparador que recibe (canal, estado).
     */
    public void agregarDisparador(Disparador disparador) {
        if (disparador != null) {
            this.disparadores.add(disparador);
        }
    }

    /**
     * Sobrecarga que permite registrar un disparador con estampa temporal
     * (canal, estado, tiempo).
     */
    public void agregarDisparador(DisparadorConTiempo disparador) {
        if (disparador != null) {
            this.disparadoresConTiempo.add(disparador);
        }
    }

    // =========================================================================
    // MUTADORES DE ESTADO (Línea 195 de ControladorAdquisicion)
    // =========================================================================
    /**
     * Modifica el nivel lógico asociando la marca temporal en segundos.
     *
     * @param canal Índice de la salida (0 a 3).
     * @param nuevoEstado true = 1 (ON), false = 0 (OFF).
     * @param tiempo Tiempo transcurrido en segundos al momento del cambio.
     */
    public synchronized void setEstado(int canal, boolean nuevoEstado, double tiempo) {
        validarIndice(canal);
        this.estados[canal] = nuevoEstado;

        // Notificación a todos los observadores registrados
        for (Disparador d : disparadores) {
            d.onCambio(canal, nuevoEstado);
        }
        for (DisparadorConTiempo d : disparadoresConTiempo) {
            d.onCambio(canal, nuevoEstado, tiempo);
        }
    }

    /**
     * Sobrecarga de dos parámetros delegando con marca temporal en cero.
     */
    public synchronized void setEstado(int canal, boolean nuevoEstado) {
        setEstado(canal, nuevoEstado, 0.0);
    }

    /**
     * Conmuta (invierte) el estado actual del canal especificado.
     */
    public synchronized boolean conmutar(int canal, double tiempo) {
        validarIndice(canal);
        boolean nuevo = !this.estados[canal];
        setEstado(canal, nuevo, tiempo);
        return nuevo;
    }

    public synchronized boolean conmutar(int canal) {
        return conmutar(canal, 0.0);
    }

    // =========================================================================
    // ACCESORES DE CONSULTA (Línea 207 de ControladorAdquisicion)
    // =========================================================================
    /**
     * Retorna una copia defensiva del arreglo de estados (requerido por
     * ControladorAdquisicion).
     */
    public synchronized boolean[] getEstados() {
        return this.estados.clone();
    }

    /**
     * Alias de consulta completa de estados.
     */
    public synchronized boolean[] getTodosLosEstados() {
        return getEstados();
    }

    /**
     * Consulta el nivel lógico de un canal individual.
     */
    public synchronized boolean getEstado(int canal) {
        validarIndice(canal);
        return this.estados[canal];
    }

    /**
     * Restablece todas las salidas al nivel bajo (0 / OFF).
     */
    public synchronized void reiniciar() {
        for (int i = 0; i < NUM_SALIDAS; i++) {
            this.estados[i] = false;
        }
    }

    public String getNombreSalida(int canal) {
        validarIndice(canal);
        return "Salida D" + (canal + 1);
    }

    private void validarIndice(int canal) {
        if (canal < 0 || canal >= NUM_SALIDAS) {
            throw new IllegalArgumentException(
                    "Canal de salida digital fuera de rango [0, " + (NUM_SALIDAS - 1) + "]: " + canal
            );
        }
    }
}
