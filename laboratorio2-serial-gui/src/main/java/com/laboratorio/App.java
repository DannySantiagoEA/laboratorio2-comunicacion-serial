package com.laboratorio;

import com.fazecast.jSerialComm.SerialPort;
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
            serialManager.ejecutarDiagnosticoPuertos();

            System.out.println("\n=================================================================");
            System.out.println("  PRUEBA TAREA #18 - DESPACHO DE COMANDOS (COM2 -> COM1)         ");
            System.out.println("=================================================================");

            SerialPort receptorProteusCOM1 = null;
            try {
                // 1. Abrir COM2 desde nuestro controlador Java
                serialManager.conectar("COM2", 9600);

                // 2. Abrir COM1 simulando el buffer UART RX del microcontrolador en Proteus
                receptorProteusCOM1 = SerialPort.getCommPort("COM1");
                receptorProteusCOM1.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
                receptorProteusCOM1.openPort();

                // --- PRUEBA 1: Enviar comando de LEDs con máscara byte (LED 1 y LED 3 encendidos -> 0b0101 = 0x05) ---
                System.out.println("\n>> [Prueba 1] Enviando comando LEDs (Mascara 0x05 -> LED1=ON, LED2=OFF, LED3=ON, LED4=OFF)...");
                serialManager.enviarComandoLeds((byte) 0b00000101);
                Thread.sleep(100);
                leerYVerificarEnCOM1(receptorProteusCOM1);

                // --- PRUEBA 2: Enviar comando de LEDs con arreglo booleano (LED 2 y LED 4 encendidos -> 0b1010 = 0x0A) ---
                System.out.println("\n>> [Prueba 2] Enviando comando LEDs desde arreglo booleano [false, true, false, true]...");
                serialManager.enviarComandoLeds(new boolean[]{false, true, false, true});
                Thread.sleep(100);
                leerYVerificarEnCOM1(receptorProteusCOM1);

                // --- PRUEBA 3: Enviar comando de tiempo de muestreo Ts = 250 ms (0x00FA) ---
                System.out.println("\n>> [Prueba 3] Enviando comando de cambio de muestreo Ts = 250 ms...");
                serialManager.enviarComandoMuestreo(250);
                Thread.sleep(100);
                leerYVerificarEnCOM1(receptorProteusCOM1);

                // --- PRUEBA 4: Enviar comando de tiempo de muestreo Ts = 1000 ms (0x03E8) ---
                System.out.println("\n>> [Prueba 4] Enviando comando de cambio de muestreo Ts = 1000 ms...");
                serialManager.enviarComandoMuestreo(1000);
                Thread.sleep(100);
                leerYVerificarEnCOM1(receptorProteusCOM1);

                // --- PRUEBA 5: Validación defensiva ante un Ts inválido (ej. -50 ms) ---
                System.out.println("\n>> [Prueba 5] Probando rechazo defensivo con Ts invalido (-50 ms)...");
                try {
                    serialManager.enviarComandoMuestreo(-50);
                } catch (SerialConnectionException ex) {
                    System.out.println("   [EXCEPCION CAPTURADA OK]: " + ex.getMessage());
                }

                System.out.println("\n-----------------------------------------------------------------");
                System.out.println(">> Total de tramas de comando despachadas con exito (#18): "
                        + serialManager.getComandosEnviados());
                System.out.println("-----------------------------------------------------------------");

            } catch (SerialConnectionException | InterruptedException ex) {
                System.err.println("[ERROR]: " + ex.getMessage());
            } finally {
                if (receptorProteusCOM1 != null && receptorProteusCOM1.isOpen()) {
                    receptorProteusCOM1.closePort();
                }
                serialManager.desconectar();
                System.out.println("=================================================================");
            }
        });
    }

    /**
     * Lee los bytes que llegaron al puerto COM1 (extremo de Proteus) y
     * comprueba la integridad del protocolo [0xAA ... 0x55].
     */
    private static void leerYVerificarEnCOM1(SerialPort com1) {
        int disponibles = com1.bytesAvailable();
        if (disponibles <= 0) {
            System.out.println("   [COM1 RX] Sin bytes recibidos.");
            return;
        }
        byte[] recibidos = new byte[disponibles];
        com1.readBytes(recibidos, recibidos.length);

        System.out.println("   [COM1 RX <- Microcontrolador recibe]: "
                + SerialCommunicationManager.formatearHex(recibidos));

        // Si es un comando de muestreo (0x02), reconstruir el Ts de 16 bits como lo hará Brayan en C
        if (recibidos.length == 5 && recibidos[0] == (byte) 0xAA
                && recibidos[1] == (byte) 0x02 && recibidos[4] == (byte) 0x55) {
            int tsReconstruido = ((recibidos[2] & 0xFF) << 8) | (recibidos[3] & 0xFF);
            System.out.println("   [COM1 RX] -> Ts reconstruido en 16 bits por el micro: " + tsReconstruido + " ms");
        }
    }
}
