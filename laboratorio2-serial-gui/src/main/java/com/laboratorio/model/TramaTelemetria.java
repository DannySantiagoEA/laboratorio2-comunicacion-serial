package com.laboratorio.model;

import java.util.Arrays;

/**
 * Tarea #17 (HU-04): Representa un paquete de telemetría desempaquetado y
 * validado tras verificar la cabecera (0x7A, 0x7B) y el delimitador de cola
 * (0x7C).
 */
public class TramaTelemetria {

    private final long numeroSecuencia;
    private final byte byteDigitalCrudo;
    private final int[] estadosDigitales;      // 4 canales digitales (0 o 1)
    private final int[] cuentasAdc;            // 8 lecturas crudas del ADC (0..255 o 0..1023)
    private final double[] voltajesAnalogicos; // 8 canales escalados de 0.00 V a 5.00 V

    public TramaTelemetria(long numeroSecuencia, byte byteDigitalCrudo,
            int[] estadosDigitales, int[] cuentasAdc, double[] voltajesAnalogicos) {
        this.numeroSecuencia = numeroSecuencia;
        this.byteDigitalCrudo = byteDigitalCrudo;
        this.estadosDigitales = estadosDigitales.clone();
        this.cuentasAdc = cuentasAdc.clone();
        this.voltajesAnalogicos = voltajesAnalogicos.clone();
    }

    public long getNumeroSecuencia() {
        return numeroSecuencia;
    }

    public byte getByteDigitalCrudo() {
        return byteDigitalCrudo;
    }

    /**
     * Retorna los 4 estados del DIP-Switch como arreglo de enteros (0 o 1),
     * directamente compatible con GraficaTiempo y Data.java.
     */
    public int[] getEstadosDigitales() {
        return estadosDigitales.clone();
    }

    /**
     * Retorna los 4 estados del DIP-Switch como arreglo booleano (true = ON,
     * false = OFF).
     */
    public boolean[] getEstadosDigitalesBoolean() {
        boolean[] resultado = new boolean[estadosDigitales.length];
        for (int i = 0; i < estadosDigitales.length; i++) {
            resultado[i] = (estadosDigitales[i] == 1);
        }
        return resultado;
    }

    public int[] getCuentasAdc() {
        return cuentasAdc.clone();
    }

    /**
     * Retorna los 8 voltajes analógicos reconstruidos en el rango [0.0, 5.0] V.
     */
    public double[] getVoltajesAnalogicos() {
        return voltajesAnalogicos.clone();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Trama #%d | Dig(0x%02X): %s | ADC(V): [",
                numeroSecuencia, byteDigitalCrudo & 0xFF, Arrays.toString(estadosDigitales)));
        for (int i = 0; i < voltajesAnalogicos.length; i++) {
            sb.append(String.format("%.2fV", voltajesAnalogicos[i]));
            if (i < voltajesAnalogicos.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
