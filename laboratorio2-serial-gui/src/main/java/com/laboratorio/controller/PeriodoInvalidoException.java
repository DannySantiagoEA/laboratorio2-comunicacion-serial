/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio.controller;

/**
 * Tarea #17: excepción propia que indica que el tiempo de muestreo escrito por
 * el usuario no es válido. El mensaje explica el motivo.
 */
public class PeriodoInvalidoException extends Exception {

    public PeriodoInvalidoException(String mensaje) {
        super(mensaje);
    }

    public PeriodoInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
