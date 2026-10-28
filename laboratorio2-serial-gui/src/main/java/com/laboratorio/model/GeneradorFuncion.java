/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio.model;

/**
 * Generador de funciones de la forma:
 *
 * v(t) = offset + amplitud * forma(t)
 *
 * donde forma(t) es la función escogida, normalizada entre -1 y 1.
 */
public class GeneradorFuncion {

    public static final double RETARDO_ESCALON = 1.0;   // s: el escalón sube 1 s después de escoger la señal

    private String tipo;                // nombre de la función (ver clase TipoOnda)
    private double frecuencia;          // Hz
    private double amplitud = 2.5;      // V (valor pico)
    private double offset = 2.5;        // V (nivel DC)  ->  la señal va de 0 a 5 V
    private double tiempoInicio = 0;    // s: instante desde el que se cuenta el escalón

    private GeneradorPseudoaleatorio aleatorio = new GeneradorPseudoaleatorio();

    public GeneradorFuncion(String tipo, double frecuencia) {
        this.tipo = tipo;
        this.frecuencia = frecuencia;
    }

    /**
     * Hace que la señal "arranque" de nuevo desde el tiempo actual.
     */
    public void reiniciar(double tiempoActual) {
        this.tiempoInicio = tiempoActual;
    }

    /**
     * Voltaje en el instante t (segundos).
     */
    public double valor(double t) {
        return offset + amplitud * forma(t - tiempoInicio);
    }

    /**
     * Función normalizada entre -1 y 1.
     */
    private double forma(double t) {
        double fase = fase(t);   // posición dentro del periodo, de 0 a 1

        switch (tipo) {
            case TipoOnda.SENO:
                return Math.sin(2 * Math.PI * frecuencia * t);
            case TipoOnda.COSENO:
                return Math.cos(2 * Math.PI * frecuencia * t);
            case TipoOnda.CUADRADA:
                return (fase < 0.5) ? 1 : -1;
            case TipoOnda.TRIANGULAR:
                return 1 - 4 * Math.abs(fase - 0.5);
            case TipoOnda.RAMPA_SUBIDA:
                return 2 * fase - 1;
            case TipoOnda.RAMPA_BAJADA:
                return 1 - 2 * fase;
            case TipoOnda.ESCALON:
                return (t >= RETARDO_ESCALON) ? 1 : -1;
            case TipoOnda.RUIDO:
                return aleatorio.siguienteAnalogico(-1, 1);   // tarea #9
            default:
                return 0;
        }
    }

    /**
     * Parte decimal de f·t: en qué punto del periodo estamos (0 a 1).
     */
    private double fase(double t) {
        double x = frecuencia * t;
        return x - Math.floor(x);
    }

    public String getTipo() {
        return tipo;
    }

    public double getFrecuencia() {
        return frecuencia;
    }

    public double getAmplitud() {
        return amplitud;
    }

    public double getOffset() {
        return offset;
    }

    public void setFrecuencia(double frecuencia) {
        if (frecuencia <= 0) {
            throw new IllegalArgumentException("La frecuencia debe ser mayor que 0");
        }
        this.frecuencia = frecuencia;
    }

    public void setAmplitud(double amplitud) {
        this.amplitud = amplitud;
    }

    public void setOffset(double offset) {
        this.offset = offset;
    }
}
