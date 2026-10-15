package com.laboratorio;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class App {

    public static void main(String[] args) {
        // Look & Feel nativo del sistema operativo
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        // Ejecucion en el hilo de interfaz Swing (EDT)
        SwingUtilities.invokeLater(() -> {
            System.out.println("=================================================");
            System.out.println(" Laboratorio 2 - Comunicacion Serial Iniciada    ");
            System.out.println("=================================================");
            // Aqui se instanciara VentanaPrincipal y SerialCommunicationManager
        });
    }
}
