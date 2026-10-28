/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio.controller;

/**
 * Tarea #17: sanitización y validación numérica del tiempo de muestreo.
 *
 * 1. Sanitiza: quita espacios, la unidad "ms" y cambia coma por punto. 2.
 * Convierte a número dentro de un try-catch (NumberFormatException). 3. Valida
 * que sea entero y que esté dentro del rango permitido.
 */
public class ValidadorMuestreo {

    public static final int MIN_MS = 10;     // más rápido satura la GUI
    public static final int MAX_MS = 5000;   // 5 s entre muestras

    /**
     * Recibe el texto escrito por el usuario y devuelve el periodo en ms.
     *
     * @throws PeriodoInvalidoException si el texto no es un periodo válido
     */
    public static int validarPeriodo(String entrada) throws PeriodoInvalidoException {

        // 1. Sanitización
        if (entrada == null) {
            throw new PeriodoInvalidoException("Debe escribir un tiempo de muestreo.");
        }
        String limpio = entrada.trim()
                .toLowerCase()
                .replace("ms", "") // acepta "100 ms"
                .replace(" ", "") // acepta "1 000"
                .replace(",", ".");   // acepta "100,5" para poder avisar que no es entero

        if (limpio.isEmpty()) {
            throw new PeriodoInvalidoException("El campo está vacío. Escriba un tiempo en milisegundos.");
        }

        // 2. Conversión numérica con try-catch
        int periodo;
        try {
            periodo = Integer.parseInt(limpio);
        } catch (NumberFormatException ex) {
            if (limpio.matches("[+-]?\\d+\\.\\d*")) {
                throw new PeriodoInvalidoException(
                        "El tiempo de muestreo debe ser un número entero de milisegundos (ej. 100).", ex);
            }
            if (limpio.matches("[+-]?\\d+")) {
                throw new PeriodoInvalidoException(
                        "El número es demasiado grande. Máximo " + MAX_MS + " ms.", ex);
            }
            throw new PeriodoInvalidoException(
                    "\"" + entrada.trim() + "\" no es un número válido. Escriba solo dígitos (ej. 100).", ex);
        }

        // 3. Validación de rango
        if (periodo <= 0) {
            throw new PeriodoInvalidoException("El tiempo de muestreo debe ser mayor que 0 ms.");
        }
        if (periodo < MIN_MS || periodo > MAX_MS) {
            throw new PeriodoInvalidoException(
                    "El tiempo de muestreo debe estar entre " + MIN_MS + " y " + MAX_MS + " ms.");
        }
        return periodo;
    }
}
