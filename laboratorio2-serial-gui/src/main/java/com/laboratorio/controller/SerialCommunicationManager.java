package com.laboratorio.controller;

import com.fazecast.jSerialComm.SerialPort;
import java.util.ArrayList;
import java.util.List;

/**
 * Controlador de comunicación serial utilizando la API jSerialComm. Tarea #15:
 * Integración de librería y detección de puertos COM virtuales (COM2).
 */
public class SerialCommunicationManager {

    public static final String PUERTO_OBJETIVO_DEFAULT = "COM2";

    /**
     * Obtiene el arreglo nativo de descriptores SerialPort detectados en el SO.
     *
     * @return Arreglo de objetos SerialPort disponibles.
     */
    public SerialPort[] obtenerDescriptoresPuertos() {
        return SerialPort.getCommPorts();
    }

    /**
     * Retorna una lista con los nombres de sistema de todos los puertos COM
     * activos (ej. ["COM1", "COM2"]).
     *
     * @return Lista de nombres de puertos disponibles.
     */
    public List<String> obtenerNombresPuertosDisponibles() {
        SerialPort[] puertos = SerialPort.getCommPorts();
        List<String> nombres = new ArrayList<>();
        for (SerialPort puerto : puertos) {
            nombres.add(puerto.getSystemPortName());
        }
        return nombres;
    }

    /**
     * Comprueba si un puerto específico (ej. "COM2") se encuentra activo y
     * reconocido por el sistema operativo.
     *
     * @param nombrePuerto Nombre del puerto a buscar (ej. "COM2").
     * @return true si el puerto existe en la enumeración de jSerialComm.
     */
    public boolean existePuerto(String nombrePuerto) {
        if (nombrePuerto == null || nombrePuerto.isBlank()) {
            return false;
        }
        for (SerialPort puerto : SerialPort.getCommPorts()) {
            if (puerto.getSystemPortName().equalsIgnoreCase(nombrePuerto.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tarea #15: Método exploratorio de diagnóstico que ejecuta
     * SerialPort.getCommPorts(), imprime el detalle técnico de cada puerto y
     * valida la presencia de COM2.
     */
    public void ejecutarDiagnosticoPuertos() {
        System.out.println("=================================================================");
        System.out.println("  DIAGNOSTICO TAREA #15 - DETECCION DE PUERTOS CON jSerialComm   ");
        System.out.println("=================================================================");

        SerialPort[] puertos = SerialPort.getCommPorts();
        System.out.println(">> Total de puertos seriales detectados: " + puertos.length);

        if (puertos.length == 0) {
            System.out.println("   [!] No se detectaron puertos COM activos en el sistema.");
        } else {
            for (int i = 0; i < puertos.length; i++) {
                SerialPort p = puertos[i];
                System.out.printf("   [%d] Puerto: %-6s | Descripcion: %-30s | Abierto: %s%n",
                        (i + 1),
                        p.getSystemPortName(),
                        p.getDescriptivePortName(),
                        p.isOpen() ? "SI" : "NO");
            }
        }

        System.out.println("-----------------------------------------------------------------");
        if (existePuerto(PUERTO_OBJETIVO_DEFAULT)) {
            System.out.println(">> [EXITO #15] El puerto virtual '" + PUERTO_OBJETIVO_DEFAULT
                    + "' fue identificado correctamente para enlazar Java.");
        } else {
            System.out.println(">> [AVISO #15] La libreria jSerialComm funciona correctamente,");
            System.out.println("   pero el puerto '" + PUERTO_OBJETIVO_DEFAULT + "' aun no aparece en Windows.");
            System.out.println("   Verifica que el par virtual COM1 <-> COM2 este activo en com0com/VSPD.");
        }
        System.out.println("=================================================================");
    }
}
