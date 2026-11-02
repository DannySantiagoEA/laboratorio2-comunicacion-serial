package com.laboratorio.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

/**
 * Vista principal para el Laboratorio 2. Expone la API pública para que
 * ControladorAdquisicion gestione el enlace serial, los selectores de canales
 * analógicos/digitales (Tarea #21), los 4 actuadores LED (Tarea #22) y la
 * actualización de tiempo de muestreo Ts (Tarea #23).
 */
public class VentanaPrincipal extends javax.swing.JFrame {

    // ===================== ATRIBUTOS DE ACTUADORES (TAREA #22) =====================
    private final IndicadorLed[] ledsSalidas = new IndicadorLed[4];
    private final JToggleButton[] botonesSalidas = new JToggleButton[4];
    private JPanel panelContenedorActuadores;

    // ===================== ATRIBUTOS DE ENLACE SERIAL (HU-01 / HU-05) =====================
    private JPanel panelConexionSerial;
    private JComboBox<String> comboPuertosCom;
    private JButton btnRefrescarPuertos;
    private JButton btnConectarSerial;
    private IndicadorLed ledEstadoSerial;
    private JLabel lblEstadoSerial;

    public VentanaPrincipal() {
        initComponents();
        jLayeredPane1.setPreferredSize(new Dimension(307, 235));
        jLayeredPane6.setPreferredSize(new Dimension(323, 235));
        setTiempoMuestreoActual(100);
        Tiempo_muestreo_nuevo.setText("100");

        inicializarPanelConexionSerial();
        inicializarModuloActuadores();

        pack();
    }

    // ===================== API PÚBLICA =====================
    public void cargarNombresCanales(String[] analogicas, String[] digitales) {
        Selector_analogo.setModel(new DefaultComboBoxModel<>(analogicas));
        Selector_digital.setModel(new DefaultComboBoxModel<>(digitales));
    }

    public int getCanalAnalogico() {
        return Selector_analogo.getSelectedIndex();
    }

    public int getCanalDigital() {
        return Selector_digital.getSelectedIndex();
    }

    public void addListenerCanalAnalogico(ActionListener l) {
        Selector_analogo.addActionListener(l);
    }

    public void addListenerCanalDigital(ActionListener l) {
        Selector_digital.addActionListener(l);
    }

    public void setTiempoMuestreoActual(int ms) {
        Tiempo_muestreo_actual.setText(String.valueOf(ms));
    }

    public String getTiempoMuestreoTexto() {
        return Tiempo_muestreo_nuevo.getText();
    }

    public void addListenerCambiarMuestreo(ActionListener l) {
        Button_nuevo_tiempo_muestreo.addActionListener(l);
    }

    public void addListenerGuardarAnalogica(ActionListener l) {
        Button_almacenar_analoga.addActionListener(l);
    }

    public void addListenerGuardarDigital(ActionListener l) {
        Button_almacenar_digital.addActionListener(l);
    }

    public void montarGraficaAnalogica(Component grafica) {
        montarEn(jLayeredPane1, grafica);
    }

    public void montarGraficaDigital(Component grafica) {
        montarEn(jLayeredPane6, grafica);
    }

    // ===================== INTERNOS Y ENLACE SERIAL =====================
    private void montarEn(JLayeredPane contenedor, Component grafica) {
        contenedor.removeAll();
        contenedor.setLayout(new BorderLayout());
        contenedor.add(grafica, BorderLayout.CENTER);
        contenedor.revalidate();
        contenedor.repaint();
    }

    private void inicializarPanelConexionSerial() {
        panelConexionSerial = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        panelConexionSerial.setBackground(new Color(215, 222, 228));
        panelConexionSerial.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Enlace Serial RS-232 (jSerialComm - 9600 bps, 8-N-1)"));

        JLabel lblPuerto = new JLabel("Puerto COM:");
        lblPuerto.setFont(new Font("Segoe UI", Font.BOLD, 12));

        comboPuertosCom = new JComboBox<>(new String[]{"COM2", "COM1"});
        comboPuertosCom.setPreferredSize(new Dimension(100, 26));

        btnRefrescarPuertos = new JButton("Refrescar");
        btnConectarSerial = new JButton("Conectar");
        btnConectarSerial.setFont(new Font("Segoe UI", Font.BOLD, 12));

        ledEstadoSerial = new IndicadorLed("ENLACE", false);
        ledEstadoSerial.setEstado(false);

        lblEstadoSerial = new JLabel("Desconectado (Modo Simulación Local)");
        lblEstadoSerial.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        panelConexionSerial.add(lblPuerto);
        panelConexionSerial.add(comboPuertosCom);
        panelConexionSerial.add(btnRefrescarPuertos);
        panelConexionSerial.add(btnConectarSerial);
        panelConexionSerial.add(ledEstadoSerial);
        panelConexionSerial.add(lblEstadoSerial);
    }

    private void inicializarModuloActuadores() {
        panelContenedorActuadores = new JPanel(new GridLayout(1, 4, 15, 0));
        panelContenedorActuadores.setBackground(new Color(221, 221, 221));
        panelContenedorActuadores.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Salidas Digitales y Telecontrol de LEDs (0xAA 0x01 ... 0x55)"));

        Color[] coloresLeds = {
            new Color(0, 220, 90),
            new Color(255, 190, 0),
            new Color(240, 45, 45),
            new Color(30, 144, 255)
        };

        for (int i = 0; i < 4; i++) {
            JPanel celda = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
            celda.setOpaque(false);

            ledsSalidas[i] = new IndicadorLed("Salida D" + (i + 1), false);
            botonesSalidas[i] = new JToggleButton("Out " + (i + 1));

            celda.add(ledsSalidas[i]);
            celda.add(botonesSalidas[i]);
            panelContenedorActuadores.add(celda);
        }

        getContentPane().removeAll();
        getContentPane().setLayout(new BorderLayout(0, 4));
        if (panelConexionSerial != null) {
            getContentPane().add(panelConexionSerial, BorderLayout.NORTH);
        }
        getContentPane().add(jPanel1, BorderLayout.CENTER);
        getContentPane().add(panelContenedorActuadores, BorderLayout.SOUTH);
        revalidate();
        repaint();
    }

    public void cargarPuertosDisponibles(String[] puertos) {
        comboPuertosCom.setModel(new DefaultComboBoxModel<>(puertos));
        for (String p : puertos) {
            if ("COM2".equalsIgnoreCase(p)) {
                comboPuertosCom.setSelectedItem(p);
                break;
            }
        }
    }

    public String getPuertoSeleccionado() {
        Object sel = comboPuertosCom.getSelectedItem();
        return sel != null ? sel.toString() : "COM2";
    }

    public void addListenerRefrescarPuertos(ActionListener l) {
        btnRefrescarPuertos.addActionListener(l);
    }

    public void addListenerConectarSerial(ActionListener l) {
        btnConectarSerial.addActionListener(l);
    }

    public void actualizarEstadoConexionSerial(boolean conectado, String mensaje) {
        ledEstadoSerial.setEstado(conectado);
        lblEstadoSerial.setText(mensaje);
        btnConectarSerial.setText(conectado ? "Desconectar" : "Conectar");
        comboPuertosCom.setEnabled(!conectado);
        btnRefrescarPuertos.setEnabled(!conectado);
    }

    public IndicadorLed getLedSalida(int canal) {
        return (canal >= 0 && canal < ledsSalidas.length) ? ledsSalidas[canal] : null;
    }

    public JToggleButton getBotonSalida(int canal) {
        return (canal >= 0 && canal < botonesSalidas.length) ? botonesSalidas[canal] : null;
    }

    public void addListenerSalida(int canal, ActionListener l) {
        if (canal >= 0 && canal < botonesSalidas.length) {
            botonesSalidas[canal].addActionListener(l);
        }
    }

    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jPanel4 = new javax.swing.JPanel();
        Selector_analogo = new javax.swing.JComboBox<>();
        jLabel1 = new javax.swing.JLabel();
        jLayeredPane1 = new javax.swing.JLayeredPane();
        Selector_digital = new javax.swing.JComboBox<>();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();
        jLabel5 = new javax.swing.JLabel();
        Tiempo_muestreo_nuevo = new javax.swing.JTextField();
        Tiempo_muestreo_actual = new javax.swing.JTextField();
        jLabel7 = new javax.swing.JLabel();
        Button_nuevo_tiempo_muestreo = new javax.swing.JButton();
        jLayeredPane6 = new javax.swing.JLayeredPane();
        Button_almacenar_digital = new javax.swing.JButton();
        Button_almacenar_analoga = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setLayout(new java.awt.GridLayout(1, 1));

        jPanel4.setBackground(new java.awt.Color(255, 255, 255));

        Selector_analogo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Seno", "Coseno", "Cuadrada", "Triangular", "Rampa subida", "Rampa bajada", "Escalón", "Ruido" }));
        Selector_analogo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Selector_analogoActionPerformed(evt);
            }
        });

        jLabel1.setText("Seleccione una señal :");

        jLayeredPane1.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        javax.swing.GroupLayout jLayeredPane1Layout = new javax.swing.GroupLayout(jLayeredPane1);
        jLayeredPane1.setLayout(jLayeredPane1Layout);
        jLayeredPane1Layout.setHorizontalGroup(
            jLayeredPane1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 307, Short.MAX_VALUE)
        );
        jLayeredPane1Layout.setVerticalGroup(
            jLayeredPane1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 235, Short.MAX_VALUE)
        );

        Selector_digital.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Seno", "Coseno", "Triangular", "Escalón" }));
        Selector_digital.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Selector_digitalActionPerformed(evt);
            }
        });

        jLabel3.setText("Seleccione una señal :");

        jLabel4.setText("Actual:");

        jLabel5.setText("Tiempo de muestreo");

        Tiempo_muestreo_nuevo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Tiempo_muestreo_nuevoActionPerformed(evt);
            }
        });

        Tiempo_muestreo_actual.setEditable(false);

        jLabel7.setText("Nuevo:");

        Button_nuevo_tiempo_muestreo.setText("Aplicar");
        Button_nuevo_tiempo_muestreo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Button_nuevo_tiempo_muestreoActionPerformed(evt);
            }
        });

        jLayeredPane6.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        javax.swing.GroupLayout jLayeredPane6Layout = new javax.swing.GroupLayout(jLayeredPane6);
        jLayeredPane6.setLayout(jLayeredPane6Layout);
        jLayeredPane6Layout.setHorizontalGroup(
            jLayeredPane6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 323, Short.MAX_VALUE)
        );
        jLayeredPane6Layout.setVerticalGroup(
            jLayeredPane6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        Button_almacenar_digital.setText("Guardar señal digital");
        Button_almacenar_digital.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Button_almacenar_digitalActionPerformed(evt);
            }
        });

        Button_almacenar_analoga.setText("Guardar señal análogica");
        Button_almacenar_analoga.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Button_almacenar_analogaActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(11, 11, 11)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGap(15, 15, 15)
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel5, javax.swing.GroupLayout.PREFERRED_SIZE, 122, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(jPanel4Layout.createSequentialGroup()
                                .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, 44, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(Tiempo_muestreo_actual, javax.swing.GroupLayout.PREFERRED_SIZE, 75, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(56, 56, 56)
                                .addComponent(jLabel7, javax.swing.GroupLayout.PREFERRED_SIZE, 44, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(Tiempo_muestreo_nuevo, javax.swing.GroupLayout.PREFERRED_SIZE, 75, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(Button_nuevo_tiempo_muestreo))))
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLayeredPane1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(jPanel4Layout.createSequentialGroup()
                                .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(Selector_analogo, javax.swing.GroupLayout.PREFERRED_SIZE, 182, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addGroup(jPanel4Layout.createSequentialGroup()
                                .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 159, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(Selector_digital, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addComponent(jLayeredPane6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(53, 53, 53))
                    .addComponent(jSeparator1)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                        .addGap(75, 75, 75)
                        .addComponent(Button_almacenar_analoga)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(Button_almacenar_digital)
                        .addGap(118, 118, 118)))
                .addContainerGap())
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(15, 15, 15)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(Selector_digital, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3)
                    .addComponent(Selector_analogo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1))
                .addGap(15, 15, 15)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLayeredPane6)
                    .addComponent(jLayeredPane1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(Button_almacenar_digital)
                    .addComponent(Button_almacenar_analoga))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 19, Short.MAX_VALUE)
                .addComponent(jLabel5)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(Tiempo_muestreo_actual, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(Tiempo_muestreo_nuevo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel7)
                    .addComponent(Button_nuevo_tiempo_muestreo))
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, 702, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        getContentPane().add(jPanel1);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void Selector_analogoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Selector_analogoActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_Selector_analogoActionPerformed

    private void Selector_digitalActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Selector_digitalActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_Selector_digitalActionPerformed

    private void Button_nuevo_tiempo_muestreoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Button_nuevo_tiempo_muestreoActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_Button_nuevo_tiempo_muestreoActionPerformed

    private void Button_almacenar_digitalActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Button_almacenar_digitalActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_Button_almacenar_digitalActionPerformed

    private void Button_almacenar_analogaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Button_almacenar_analogaActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_Button_almacenar_analogaActionPerformed

    private void Tiempo_muestreo_nuevoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Tiempo_muestreo_nuevoActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_Tiempo_muestreo_nuevoActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(VentanaPrincipal.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(VentanaPrincipal.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(VentanaPrincipal.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(VentanaPrincipal.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new VentanaPrincipal().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton Button_almacenar_analoga;
    private javax.swing.JButton Button_almacenar_digital;
    private javax.swing.JButton Button_nuevo_tiempo_muestreo;
    private javax.swing.JComboBox<String> Selector_analogo;
    private javax.swing.JComboBox<String> Selector_digital;
    private javax.swing.JTextField Tiempo_muestreo_actual;
    private javax.swing.JTextField Tiempo_muestreo_nuevo;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLayeredPane jLayeredPane1;
    private javax.swing.JLayeredPane jLayeredPane6;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JSeparator jSeparator1;
    // End of variables declaration//GEN-END:variables
}
