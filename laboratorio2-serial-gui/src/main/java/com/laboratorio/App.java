package com.laboratorio;

import com.fazecast.jSerialComm.SerialPort;
import com.laboratorio.controller.ControladorAdquisicion;
import com.laboratorio.controller.SerialCommunicationManager;
import com.laboratorio.model.Data;
import com.laboratorio.view.VentanaPrincipal;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Punto de entrada principal para el Laboratorio 2: Comunicación Serial con
 * Java (jSerialComm). Inicializa la arquitectura MVC en el Event Dispatch
 * Thread (EDT), ajusta las dimensiones ampliadas de la ventana (960x620) y cede
 * COM1 exclusivamente a Proteus.
 */
public class App {

    // En false para que Proteus sea dueño exclusivo de COM1
    private static final boolean ACTIVAR_EMULADOR_INTERNO_COM1 = false;

    private static volatile int tsEmuladoMs = 100;
    private static volatile boolean flujoActivo = true;

    public static void main(String[] args) {
        try {
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SerialCommunicationManager gestor = new SerialCommunicationManager();
        gestor.ejecutarDiagnosticoPuertos();

        if (ACTIVAR_EMULADOR_INTERNO_COM1) {
            iniciarEmuladorArduinoEnCom1SiEstaLibre();
        } else {
            System.out.println("[Modo Proteus] Emulador interno en COM1 desactivado. COM1 libre para COMPIM de Proteus.");
        }

        SwingUtilities.invokeLater(() -> {
            VentanaPrincipal vista = new VentanaPrincipal();
            Data modelo = new Data();
            ControladorAdquisicion controlador = new ControladorAdquisicion(vista, modelo, gestor);

            vista.setTitle("Laboratorio 2 - Adquisición y Telecontrol Serial RS-232 (jSerialComm)");

            vista.setMinimumSize(new Dimension(920, 580));
            vista.setSize(960, 620);
            vista.setLocationRelativeTo(null);

            vista.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    controlador.detenerAdquisicion();
                    if (gestor.isConectado()) {
                        gestor.desconectar();
                    }
                }
            });

            vista.setVisible(true);
            controlador.iniciarAdquisicion();
        });
    }

    private static void iniciarEmuladorArduinoEnCom1SiEstaLibre() {
        Thread hiloEmulador = new Thread(() -> {
            SerialPort com1 = SerialPort.getCommPort("COM1");
            com1.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
            com1.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);

            if (!com1.openPort()) {
                System.out.println("[Enlace COM1] COM1 ocupado por simulador externo (Proteus).");
                return;
            }

            System.out.println("[Emulador COM1] Emulador activo en COM1 -> transmitiendo hacia COM2.");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (com1.isOpen()) {
                    com1.closePort();
                }
            }));

            double fase = 0.0;
            byte[] rxBuf = new byte[64];

            while (com1.isOpen()) {
                try {
                    if (com1.bytesAvailable() > 0) {
                        int leidos = com1.readBytes(rxBuf, Math.min(com1.bytesAvailable(), rxBuf.length));
                        procesarComandosEnEmulador(rxBuf, leidos);
                    }

                    if (flujoActivo) {
                        byte[] trama = new byte[12];
                        trama[0] = 0x7A;
                        trama[1] = 0x7B;

                        int sw1 = (Math.sin(fase) >= 0) ? 1 : 0;
                        int sw2 = (Math.cos(fase * 0.5) >= 0) ? 1 : 0;
                        int sw3 = ((int) (fase * 2) % 2 == 0) ? 1 : 0;
                        int sw4 = 1;
                        trama[2] = (byte) ((sw1) | (sw2 << 1) | (sw3 << 2) | (sw4 << 3));

                        for (int ch = 0; ch < 8; ch++) {
                            double v = 2.5 + 2.4 * Math.sin(fase + ch * 0.6);
                            int adc8 = (int) Math.round((v / 5.0) * 255.0);
                            adc8 = Math.max(0, Math.min(255, adc8));
                            trama[3 + ch] = (byte) adc8;
                        }

                        trama[11] = 0x7C;
                        com1.writeBytes(trama, trama.length);
                        fase += (tsEmuladoMs / 1000.0) * 2.0 * Math.PI * 0.5;
                    }

                    Thread.sleep(Math.max(10, tsEmuladoMs));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "Hilo-Emulador-Arduino-COM1");

        hiloEmulador.setDaemon(true);
        hiloEmulador.start();
    }

    private static void procesarComandosEnEmulador(byte[] buf, int len) {
        for (int i = 0; i < len; i++) {
            if ((buf[i] & 0xFF) == 0xAA && i + 3 < len) {
                int cmd = buf[i + 1] & 0xFF;
                if (cmd == 0x01 && (buf[i + 3] & 0xFF) == 0x55) {
                    int mascara = buf[i + 2] & 0x0F;
                    System.out.printf("[Emulador COM1 <- Java COM2] Comando LEDs recibido: máscara = 0x%02X%n", mascara);
                } else if (cmd == 0x02 && i + 4 < len && (buf[i + 4] & 0xFF) == 0x55) {
                    int nuevoTs = ((buf[i + 2] & 0xFF) << 8) | (buf[i + 3] & 0xFF);
                    if (nuevoTs >= 10) {
                        tsEmuladoMs = nuevoTs;
                        System.out.println("[Emulador COM1 <- Java COM2] Comando Ts recibido: " + tsEmuladoMs + " ms");
                    }
                } else if (cmd == 0x03 && (buf[i + 3] & 0xFF) == 0x55) {
                    flujoActivo = (buf[i + 2] == 0x01);
                }
            }
        }
    }
}
