package com.laboratorio;

import com.laboratorio.controller.SerialCommunicationManager;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class App {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            // Instanciar el gestor de comunicacion serial y correr prueba de Tarea #15
            SerialCommunicationManager serialManager = new SerialCommunicationManager();
            serialManager.ejecutarDiagnosticoPuertos();
        });
    }
}
