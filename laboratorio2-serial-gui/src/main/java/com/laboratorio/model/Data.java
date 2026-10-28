package com.laboratorio.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Tarea #8: estructuras de datos en memoria para 8 canales analógicos y 4
 * canales digitales.
 *
 * - Las ANALÓGICAS se calculan en cada ciclo del timer (cada
 * PERIODO_ANALOGICO_MS), así se ven continuas. El tiempo de muestreo NO las
 * afecta. - Las DIGITALES solo se muestrean cada Ts (periodoMuestreoMs): son
 * señales en tiempo discreto x[n] = x(n·Ts). · La "Cuadrada" es una señal
 * lógica: vale 0 o 1. · Las demás son señales analógicas muestreadas (valor en
 * V en cada n·Ts).
 */
public class Data {

    public static final int NUM_ANALOGICAS = 8;
    public static final int NUM_DIGITALES = 4;
    public static final double V_MIN = 0.0;    // rango de las analógicas
    public static final double V_MAX = 5.0;
    public static final double UMBRAL = 2.5;   // V: por encima es 1, por debajo es 0

    /**
     * Paso fijo de la simulación analógica (no depende de Ts).
     */
    public static final int PERIODO_ANALOGICO_MS = 20;

    private double frecuencia = 1.0;           // Hz: un solo valor para todas las señales

    // 1. Estado instantáneo: el valor ACTUAL de cada canal
    private double[] analogicas = new double[NUM_ANALOGICAS];
    private double[] digitales = new double[NUM_DIGITALES];   // 0/1 (lógica) o V (muestreada)

    // 2. Historial de cada canal (para graficar y guardar en archivo)
    private Senal[] historialAnalogicas = new Senal[NUM_ANALOGICAS];
    private Senal[] historialDigitales = new Senal[NUM_DIGITALES];

    // 3. Un generador de funciones por canal
    private GeneradorFuncion[] generadoresAnalogicos = new GeneradorFuncion[NUM_ANALOGICAS];
    private GeneradorFuncion[] generadoresDigitales = new GeneradorFuncion[NUM_DIGITALES];

    // 4. Nombres para menús
    private String[] nombresAnalogicas = new String[NUM_ANALOGICAS];
    private String[] nombresDigitales = new String[NUM_DIGITALES];

    // 5. Tiempo
    private int periodoMuestreoMs = 100;          // Ts: SOLO para las digitales
    private double tiempo = 0;                    // tiempo de la simulación (s)
    private double proximoMuestreoDigital = 0;    // instante de la próxima muestra digital (s)

    // Muestras digitales tomadas en el último ciclo: {t, d1, d2, d3, d4}
    private final List<double[]> muestrasDigitalesNuevas = new ArrayList<>();

    // 6. Salidas digitales (Tarea #35)
    private final SalidasDigitales salidas = new SalidasDigitales();

    public Data() {
        for (int i = 0; i < NUM_ANALOGICAS; i++) {
            String tipo = TipoOnda.ANALOGICAS[i];
            nombresAnalogicas[i] = "Analógica " + (i + 1) + " - " + tipo;
            historialAnalogicas[i] = new Senal(nombresAnalogicas[i]);
            generadoresAnalogicos[i] = new GeneradorFuncion(tipo, frecuencia);
        }
        for (int i = 0; i < NUM_DIGITALES; i++) {
            String tipo = TipoOnda.DIGITALES[i];
            String clase = esLogica(i) ? " (lógica)" : " (muestreada)";
            nombresDigitales[i] = "Digital " + (i + 1) + " - " + tipo + clase;
            historialDigitales[i] = new Senal(nombresDigitales[i]);
            generadoresDigitales[i] = new GeneradorFuncion(tipo, frecuencia);
        }
    }

    /**
     * Un ciclo de simulación: calcula las 8 analógicas en el instante actual y,
     * si ya llegó el momento, toma las muestras digitales en los instantes
     * exactos n·Ts. Luego avanza el tiempo un paso analógico.
     */
    public void tomarMuestra() {
        // a) Analógicas: en cada ciclo (continuas, no dependen de Ts)
        for (int i = 0; i < NUM_ANALOGICAS; i++) {
            double v = generadoresAnalogicos[i].valor(tiempo);
            analogicas[i] = limitar(v);
            historialAnalogicas[i].agregarMuestra(tiempo, analogicas[i]);
        }

        // b) Digitales: solo en los instantes n·Ts que ya se alcanzaron
        while (proximoMuestreoDigital <= tiempo + 1e-9) {
            double tn = proximoMuestreoDigital;
            double[] fila = new double[1 + NUM_DIGITALES];
            fila[0] = tn;
            for (int i = 0; i < NUM_DIGITALES; i++) {
                digitales[i] = valorDigital(i, tn);
                historialDigitales[i].agregarMuestra(tn, digitales[i]);
                fila[i + 1] = digitales[i];
            }
            muestrasDigitalesNuevas.add(fila);
            proximoMuestreoDigital += periodoMuestreoMs / 1000.0;
        }

        tiempo += PERIODO_ANALOGICO_MS / 1000.0;
    }

    /**
     * Valor de la digital i en el instante t: 0/1 si es lógica, V si es
     * muestreada.
     */
    private double valorDigital(int i, double t) {
        double v = generadoresDigitales[i].valor(t);
        if (esLogica(i)) {
            return (v >= UMBRAL) ? 1 : 0;   // señal lógica (onda cuadrada 0/1)
        }
        return limitar(v);                  // señal analógica muestreada
    }

    /**
     * true si la digital i es la señal lógica (cuadrada 0/1).
     */
    public boolean esLogica(int canal) {
        return TipoOnda.CUADRADA.equals(TipoOnda.DIGITALES[canal]);
    }

    private double limitar(double v) {
        return Math.max(V_MIN, Math.min(V_MAX, v));   // 0 - 5 V
    }

    /**
     * Devuelve (y vacía) las muestras digitales tomadas desde la última
     * llamada. Cada fila es {t, d1, d2, d3, d4}.
     */
    public List<double[]> extraerMuestrasDigitalesNuevas() {
        List<double[]> copia = new ArrayList<>(muestrasDigitalesNuevas);
        muestrasDigitalesNuevas.clear();
        return copia;
    }

    // ---------- Al escoger una señal en el menú ----------
    public void reiniciarAnalogica(int canal) {
        validar(canal, NUM_ANALOGICAS);
        generadoresAnalogicos[canal].reiniciar(tiempo);
    }

    public void reiniciarDigital(int canal) {
        validar(canal, NUM_DIGITALES);
        generadoresDigitales[canal].reiniciar(tiempo);
    }

    // ---------- Nombres para los menús ----------
    public String[] getNombresAnalogicas() {
        return nombresAnalogicas;
    }

    public String[] getNombresDigitales() {
        return nombresDigitales;
    }

    // ---------- Valores individuales actuales ----------
    public double getAnalogica(int canal) {
        validar(canal, NUM_ANALOGICAS);
        return analogicas[canal];
    }

    public double getDigital(int canal) {
        validar(canal, NUM_DIGITALES);
        return digitales[canal];
    }

    // ---------- Historiales ----------
    public Senal getHistorialAnalogica(int canal) {
        validar(canal, NUM_ANALOGICAS);
        return historialAnalogicas[canal];
    }

    public Senal getHistorialDigital(int canal) {
        validar(canal, NUM_DIGITALES);
        return historialDigitales[canal];
    }

    // ---------- Salidas digitales (Tarea #35) ----------
    public SalidasDigitales getSalidas() {
        return salidas;
    }

    // ---------- Frecuencia ----------
    public double getFrecuencia() {
        return frecuencia;
    }

    // ---------- Tiempo ----------
    public double getTiempo() {
        return tiempo;
    }

    /**
     * Ts de las señales digitales, en ms.
     */
    public int getPeriodoMuestreoMs() {
        return periodoMuestreoMs;
    }

    /**
     * Cambia Ts (solo digitales). La siguiente muestra digital se toma ya.
     */
    public void setPeriodoMuestreoMs(int ms) {
        if (ms <= 0) {
            throw new IllegalArgumentException("El tiempo de muestreo debe ser mayor que 0");
        }
        periodoMuestreoMs = ms;
        proximoMuestreoDigital = tiempo;
    }

    /**
     * Evita pedir un canal que no existe (ej. analógica 9).
     */
    private void validar(int canal, int limite) {
        if (canal < 0 || canal >= limite) {
            throw new IndexOutOfBoundsException(
                    "El canal " + canal + " no existe (válidos: 0 a " + (limite - 1) + ")");
        }
    }

    // ---------- Métodos para el Controlador ----------
    /**
     * Copia de las 8 lecturas analógicas actuales.
     */
    public double[] getTodasLasAnalogicas() {
        return analogicas.clone();
    }

    /**
     * Copia de los 4 valores digitales de la última muestra.
     */
    public double[] getTodasLasDigitales() {
        return digitales.clone();
    }
}
