/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio.model;

import java.util.ArrayList;

/**
 * Historial de UNA señal: guarda cada muestra como (tiempo, valor). Se usará
 * después para graficar y para guardar en archivo.
 */
public class Senal {

    public static final int MAX_MUESTRAS = 10000;   // límite para no llenar la memoria

    private String nombre;
    private ArrayList<Double> tiempos = new ArrayList<>();
    private ArrayList<Double> valores = new ArrayList<>();

    public Senal(String nombre) {
        this.nombre = nombre;
    }

    /**
     * Agrega una muestra. Si se supera el límite, borra la más antigua.
     */
    public void agregarMuestra(double tiempo, double valor) {
        tiempos.add(tiempo);
        valores.add(valor);
        if (tiempos.size() > MAX_MUESTRAS) {
            tiempos.remove(0);
            valores.remove(0);
        }
    }

    public int getCantidad() {
        return valores.size();
    }

    public double getTiempo(int i) {
        return tiempos.get(i);
    }

    public double getValor(int i) {
        return valores.get(i);
    }

    public String getNombre() {
        return nombre;
    }

    public void limpiar() {
        tiempos.clear();
        valores.clear();
    }

    @Override
    public String toString() {
        return nombre;
    }
}
