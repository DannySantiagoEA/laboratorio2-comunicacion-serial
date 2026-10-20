package com.laboratorio.controller;

/**
 * Tarea #16 (HU-04): Excepción verificada para gestionar fallos de conexión,
 * puertos inexistentes, puertos ocupados por otra aplicación o desconexiones
 * abruptas en el enlace serial.
 */
public class SerialConnectionException extends Exception {

    public SerialConnectionException(String mensaje) {
        super(mensaje);
    }

    public SerialConnectionException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
