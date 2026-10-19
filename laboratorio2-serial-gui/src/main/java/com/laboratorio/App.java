package com.laboratorio;

import com.fazecast.jSerialComm.SerialPort;
import com.laboratorio.controller.SerialCommunicationManager;
import com.laboratorio.controller.SerialConnectionException;
import java.util.Arrays;
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
            System.out.println("  PRUEBA TAREA #17 - LISTENER ASINCRONO Y PARSER (COM1 -> COM2)  ");
            System.out.println("=================================================================");

            // 1. Registrar el callback que recibirá las tramas ya desempaquetadas
            serialManager.setTramaRecibidaListener(trama -> {
                System.out.println("\n   >>> [EVENTO ASINCRONO RECIBIDO EN COM2] <<<");
                System.out.println("   " + trama);
                System.out.println("   -> Estados DIP-Switch (SW1..SW4): "
                        + Arrays.toString(trama.getEstadosDigitalesBoolean()));
                System.out.println("   -> Cuentas ADC crudas (AN0..AN7): "
                        + Arrays.toString(trama.getCuentasAdc()));
            });

            SerialPort emuladorProteusCOM1 = null;
            try {
                // 2. Abrir COM2 con nuestro SerialCommunicationManager (Receptor Java)
                serialManager.conectar("COM2", 9600);

                // 3. Abrir COM1 para simular el envío desde Proteus a través del puente HHD
                emuladorProteusCOM1 = SerialPort.getCommPort("COM1");
                emuladorProteusCOM1.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
                if (emuladorProteusCOM1.openPort()) {
                    System.out.println("[SIMULADOR] COM1 abierto como transmisor (emulando Proteus).");

                    // --- PRUEBA A: Enviar basura previa + Trama #1 completa ---
                    // Switches: 0x05 (0000 0101 -> SW1=ON, SW2=OFF, SW3=ON, SW4=OFF)
                    // 8 Canales ADC: 0 (0.0V), 51 (1.0V), 102 (2.0V), 128 (2.51V),
                    //                153 (3.0V), 204 (4.0V), 230 (4.51V), 255 (5.0V)
                    byte[] rafaga1 = new byte[]{
                        (byte) 0xFF, (byte) 0x00, (byte) 0x13, // 3 bytes de ruido previo
                        (byte) 0x7A, (byte) 0x7B, // Cabecera oficial
                        (byte) 0x05, // Byte digital (SW1 y SW3 en 1)
                        (byte) 0, (byte) 51, (byte) 102, (byte) 128,
                        (byte) 153, (byte) 204, (byte) 230, (byte) 255, // 8 bytes ADC
                        (byte) 0x7C // Fin de trama
                    };
                    System.out.println("\n>> [Envio 1] Transmitiendo 3 bytes de ruido + Trama #1 por COM1...");
                    emuladorProteusCOM1.writeBytes(rafaga1, rafaga1.length);
                    Thread.sleep(250); // Dar tiempo al hilo asíncrono de jSerialComm

                    // --- PRUEBA B: Enviar Trama #2 FRAGMENTADA en dos partes ---
                    // Switches: 0x0A (0000 1010 -> SW1=OFF, SW2=ON, SW3=OFF, SW4=ON)
                    byte[] parte1 = new byte[]{
                        (byte) 0x7A, (byte) 0x7B, (byte) 0x0A, (byte) 255, (byte) 128
                    };
                    byte[] parte2 = new byte[]{
                        (byte) 64, (byte) 32, (byte) 16, (byte) 8, (byte) 4, (byte) 0, (byte) 0x7C
                    };
                    System.out.println("\n>> [Envio 2] Transmitiendo Trama #2 fragmentada en 2 partes por COM1...");
                    emuladorProteusCOM1.writeBytes(parte1, parte1.length);
                    Thread.sleep(100);
                    emuladorProteusCOM1.writeBytes(parte2, parte2.length);
                    Thread.sleep(250);

                    System.out.println("\n-----------------------------------------------------------------");
                    System.out.println(">> Resumen Diagnostico Parser (#17):");
                    System.out.println("   - Tramas validas decodificadas : " + serialManager.getTramasValidasRecibidas());
                    System.out.println("   - Bytes de ruido descartados   : " + serialManager.getBytesDescartadosPorRuido());
                    System.out.println("-----------------------------------------------------------------");
                } else {
                    System.err.println("[AVISO] No se pudo abrir COM1 para el autodiagnóstico.");
                }

            } catch (SerialConnectionException | InterruptedException ex) {
                System.err.println("[ERROR]: " + ex.getMessage());
            } finally {
                if (emuladorProteusCOM1 != null && emuladorProteusCOM1.isOpen()) {
                    emuladorProteusCOM1.closePort();
                }
                serialManager.desconectar();
                System.out.println("=================================================================");
            }
        });
    }
}
