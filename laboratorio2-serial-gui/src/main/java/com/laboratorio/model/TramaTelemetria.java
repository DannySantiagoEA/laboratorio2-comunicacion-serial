package com.laboratorio.model;

import java.util.Arrays;

/**
 * Tarea #17 (HU-04 y HU-05): Representa un paquete de telemetría desempaquetado
 * y validado tras verificar la cabecera (0x7A, 0x7B) y el delimitador de cola
 * (0x7C).
 */
public class TramaTelemetria {

    public static final int NUM_CANALES_ADC = 8;
    public static final int NUM_SWITCHES_DIP = 4;

    private final long numeroSecuencia;
    private final long timestampRecepcionMs;
    private final byte byteDigitalCrudo;
    private final int[] estadosDigitales;      // 4 canales digitales (0 o 1)
    private final boolean[] estadosSwitchesBool;
    private final int[] cuentasAdc;            // 8 lecturas crudas del ADC (0..255 o 0..1023)
    private final double[] voltajesAnalogicos; // 8 canales escalados de 0.00 V a 5.00 V

    /**
     * Constructor principal de 5 argumentos invocado por
     * SerialCommunicationManager (línea 372).
     */
    public TramaTelemetria(
            long numeroSecuencia,
            byte byteDigitalCrudo,
            int[] estadosDigitales,
            int[] cuentasAdc,
            double[] voltajesAnalogicos) {

        this.numeroSecuencia = numeroSecuencia;
        this.timestampRecepcionMs = System.currentTimeMillis();
        this.byteDigitalCrudo = byteDigitalCrudo;
        this.estadosDigitales = Arrays.copyOf(estadosDigitales, NUM_SWITCHES_DIP);
        this.estadosSwitchesBool = new boolean[NUM_SWITCHES_DIP];
        for (int i = 0; i < NUM_SWITCHES_DIP; i++) {
            this.estadosSwitchesBool[i] = (this.estadosDigitales[i] == 1);
        }
        this.cuentasAdc = Arrays.copyOf(cuentasAdc, NUM_CANALES_ADC);
        this.voltajesAnalogicos = Arrays.copyOf(voltajesAnalogicos, NUM_CANALES_ADC);
    }

    /**
     * Constructor sobrecargado de 6 argumentos para compatibilidad extendida.
     */
    public TramaTelemetria(
            long numeroSecuencia,
            int byteDigitalCrudo,
            int[] estadosSwitchesInt,
            boolean[] estadosSwitchesBool,
            int[] cuentasAdcCrudas,
            double[] voltajesAnalogicos) {

        this.numeroSecuencia = numeroSecuencia;
        this.timestampRecepcionMs = System.currentTimeMillis();
        this.byteDigitalCrudo = (byte) (byteDigitalCrudo & 0xFF);
        this.estadosDigitales = Arrays.copyOf(estadosSwitchesInt, NUM_SWITCHES_DIP);
        this.estadosSwitchesBool = Arrays.copyOf(estadosSwitchesBool, NUM_SWITCHES_DIP);
        this.cuentasAdc = Arrays.copyOf(cuentasAdcCrudas, NUM_CANALES_ADC);
        this.voltajesAnalogicos = Arrays.copyOf(voltajesAnalogicos, NUM_CANALES_ADC);
    }

    public long getNumeroSecuencia() {
        return numeroSecuencia;
    }

    public long getTimestampRecepcionMs() {
        return timestampRecepcionMs;
    }

    public byte getByteDigitalCrudo() {
        return byteDigitalCrudo;
    }

    public int[] getEstadosDigitales() {
        return estadosDigitales.clone();
    }

    public int[] getEstadosSwitchesInt() {
        return estadosDigitales.clone();
    }

    public boolean[] getEstadosDigitalesBoolean() {
        return estadosSwitchesBool.clone();
    }

    public boolean[] getEstadosSwitchesBool() {
        return estadosSwitchesBool.clone();
    }

    public int[] getCuentasAdc() {
        return cuentasAdc.clone();
    }

    public int[] getCuentasAdcCrudas() {
        return cuentasAdc.clone();
    }

    public double[] getVoltajesAnalogicos() {
        return voltajesAnalogicos.clone();
    }

    public double getVoltajeCanal(int canal) {
        if (canal >= 0 && canal < voltajesAnalogicos.length) {
            return voltajesAnalogicos[canal];
        }
        return 0.0;
    }

    public int getEstadoSwitchInt(int indiceSwitch) {
        if (indiceSwitch >= 0 && indiceSwitch < estadosDigitales.length) {
            return estadosDigitales[indiceSwitch];
        }
        return (byteDigitalCrudo >> indiceSwitch) & 0x01;
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
