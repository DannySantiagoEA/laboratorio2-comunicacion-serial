/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio.model;

/**
 * Clase que solo guarda constantes: los nombres de las funciones y qué función
 * tiene cada canal.
 */
public class TipoOnda {

    public static final String SENO = "Seno";
    public static final String COSENO = "Coseno";
    public static final String CUADRADA = "Cuadrada";
    public static final String TRIANGULAR = "Triangular";
    public static final String RAMPA_SUBIDA = "Rampa subida";
    public static final String RAMPA_BAJADA = "Rampa bajada";
    public static final String ESCALON = "Escalón";
    public static final String RUIDO = "Ruido";
    public static final String PWM = "PWM";
    public static final String PRBS = "PRBS";

    // Función de cada una de las 8 entradas analógicas (en orden)
    public static final String[] ANALOGICAS = {
        SENO, COSENO, CUADRADA, TRIANGULAR, RAMPA_SUBIDA, RAMPA_BAJADA, ESCALON, RUIDO
    };

    // Función de cada una de las 4 entradas digitales (en orden)
    public static final String[] DIGITALES = {
        CUADRADA, SENO, TRIANGULAR, RAMPA_SUBIDA
    };
}
