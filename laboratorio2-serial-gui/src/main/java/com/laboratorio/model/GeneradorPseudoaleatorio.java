/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio.model;

import java.util.Random;

public class GeneradorPseudoaleatorio {

    private Random random = new Random();

    /**
     * Número decimal al azar entre min y max para las señales analogicas*
     */
    public double siguienteAnalogico(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    /**
     * 0 o 1 al azar para las señales digitales*
     */
    public boolean siguienteBooleano() {
        return random.nextBoolean();
    }
}
