package com.laboratorio;

import com.laboratorio.controller.SerialCommunicationManager;
import com.laboratorio.controller.SerialConnectionException;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class App {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            SerialCommunicationManager serialManager = new SerialCommunicationManager();

            // 1. Verificar detección de puertos (Tarea #15)
            serialManager.ejecutarDiagnosticoPuertos();

            System.out.println("\n=================================================================");
            System.out.println("  PRUEBA TAREA #16 - APERTURA, CONFIGURACION 8-N-1 Y EXCEPCIONES ");
            System.out.println("=================================================================");

            // Caso 1: Prueba de excepción al intentar abrir un puerto inexistente (COM99)
            try {
                System.out.println(">> [Prueba 1] Intentando abrir puerto inexistente 'COM99'...");
                serialManager.conectar("COM99");
            } catch (SerialConnectionException ex) {
                System.out.println("   [EXCEPCION CAPTURADA OK]: " + ex.getMessage());
            }

            // Caso 2: Apertura exitosa de COM2 con parámetros 9600 bps, 8-N-1
            try {
                System.out.println("\n>> [Prueba 2] Abriendo puerto virtual 'COM2' a 9600 bps (8-N-1)...");
                serialManager.conectar("COM2");
                System.out.println("   -> Estado conectado: " + serialManager.isConectado());
                System.out.println("   -> Resumen: " + serialManager.obtenerResumenConfiguracion());
            } catch (SerialConnectionException ex) {
                System.err.println("   [ERROR]: " + ex.getMessage());
            }

            // Caso 3: Prueba de excepción por puerto ocupado / sesión duplicada
            try {
                System.out.println("\n>> [Prueba 3] Intentando abrir 'COM2' nuevamente estando ya activo...");
                serialManager.conectar("COM2");
            } catch (SerialConnectionException ex) {
                System.out.println("   [EXCEPCION CAPTURADA OK]: " + ex.getMessage());
            }

            // Caso 4: Cierre seguro del descriptor de puerto
            System.out.println("\n>> [Prueba 4] Cerrando el puerto 'COM2' de forma segura...");
            serialManager.desconectar();
            System.out.println("   -> Estado conectado tras cierre: " + serialManager.isConectado());
            System.out.println("=================================================================");
        });
    }
}
