/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import javax.swing.JPanel;

/**
 * Subtarea #34 (HU-07): Indicador gráfico tipo LED para instrumentación
 * virtual. Renderizado vectorial mediante Graphics2D con gradientes radiales,
 * bisel protector y conmutación de estado visual (Verde / "ON" frente a Gris /
 * "OFF").
 */
public class IndicadorLed extends JPanel {

    private boolean encendido = false;
    private String textoIdentificador = "";

    // Paleta cromática de alta visibilidad para instrumentación
    private static final Color COLOR_ON_BASE = new Color(46, 204, 113);     // Verde esmeralda vivo
    private static final Color COLOR_ON_BRILLO = new Color(169, 255, 195);  // Reflejo esférico superior
    private static final Color COLOR_OFF_BASE = new Color(73, 80, 87);      // Gris oscuro inactivo
    private static final Color COLOR_OFF_BRILLO = new Color(134, 142, 150); // Relieve tenue apagado
    private static final Color COLOR_BISEL_EXTERIOR = new Color(33, 37, 41); // Aro metálico oscuro
    private static final Color COLOR_BORDE = new Color(15, 23, 42);

    public IndicadorLed() {
        this("", false);
    }

    public IndicadorLed(String textoIdentificador) {
        this(textoIdentificador, false);
    }

    public IndicadorLed(String textoIdentificador, boolean estadoInicial) {
        this.textoIdentificador = textoIdentificador;
        this.encendido = estadoInicial;
        setPreferredSize(new Dimension(50, 50));
        setMinimumSize(new Dimension(40, 40));
        setOpaque(false);
        actualizarTooltip();
    }

    /**
     * Modifica el nivel lógico del LED y repinta inmediatamente el componente
     * en el EDT.
     *
     * @param nuevoEstado true para nivel alto (ON), false para nivel bajo
     * (OFF).
     */
    public void setEstado(boolean nuevoEstado) {
        if (this.encendido != nuevoEstado) {
            this.encendido = nuevoEstado;
            actualizarTooltip();
            repaint();
        }
    }

    public boolean isEncendido() {
        return encendido;
    }

    public void conmutar() {
        setEstado(!this.encendido);
    }

    public void setTextoIdentificador(String textoIdentificador) {
        this.textoIdentificador = textoIdentificador;
        actualizarTooltip();
        repaint();
    }

    private void actualizarTooltip() {
        String estadoStr = encendido ? "ACTIVO (1 / ON)" : "INACTIVO (0 / OFF)";
        setToolTipText(textoIdentificador.isEmpty() ? estadoStr : textoIdentificador + ": " + estadoStr);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();

        // 1. Antialiasing para formas vectoriales y tipografía
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int ancho = getWidth();
        int alto = getHeight();
        int diametro = Math.min(ancho, alto) - 6;
        int x = (ancho - diametro) / 2;
        int y = (alto - diametro) / 2;

        // 2. Bisel exterior (aro contenedor)
        g2.setColor(COLOR_BISEL_EXTERIOR);
        g2.fillOval(x, y, diametro, diametro);

        // 3. Gradiente radial para simular lente esférica luminosa
        int margenInterior = Math.max(2, diametro / 10);
        int diametroInterior = diametro - (margenInterior * 2);
        int xInterior = x + margenInterior;
        int yInterior = y + margenInterior;

        Color base = encendido ? COLOR_ON_BASE : COLOR_OFF_BASE;
        Color brillo = encendido ? COLOR_ON_BRILLO : COLOR_OFF_BRILLO;

        Point2D centroLuz = new Point2D.Float(
                xInterior + diametroInterior * 0.35f,
                yInterior + diametroInterior * 0.35f
        );
        float radioLuz = diametroInterior * 0.75f;
        float[] fracciones = {0.0f, 1.0f};
        Color[] tonos = {brillo, base};

        RadialGradientPaint gradiente = new RadialGradientPaint(centroLuz, radioLuz, fracciones, tonos);
        g2.setPaint(gradiente);
        g2.fillOval(xInterior, yInterior, diametroInterior, diametroInterior);

        // 4. Borde de delimitación circular
        g2.setColor(COLOR_BORDE);
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawOval(x, y, diametro, diametro);

        // 5. Rótulo de texto dinámico centrado ("ON" / "OFF")
        String etiquetaEstado = encendido ? "ON" : "OFF";
        int tamanoFuente = Math.max(9, diametroInterior / 3);
        Font fuente = new Font("Segoe UI", Font.BOLD, tamanoFuente);
        g2.setFont(fuente);

        FontMetrics fm = g2.getFontMetrics();
        int anchoTexto = fm.stringWidth(etiquetaEstado);
        int alturaTexto = fm.getAscent();
        int xTexto = (ancho - anchoTexto) / 2;
        int yTexto = (alto + alturaTexto) / 2 - 2;

        // Sombra de contraste
        g2.setColor(new Color(0, 0, 0, 150));
        g2.drawString(etiquetaEstado, xTexto + 1, yTexto + 1);

        // Texto frontal en blanco puro
        g2.setColor(Color.WHITE);
        g2.drawString(etiquetaEstado, xTexto, yTexto);

        g2.dispose();
    }

    /**
     * Prueba unitaria visual ejecutable con Shift + F6 en NetBeans.
     */
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            javax.swing.JFrame frame = new javax.swing.JFrame("Test Unitario - IndicadorLed (HU-07)");
            frame.setDefaultCloseOperation(javax.swing.JFrame.DISPOSE_ON_CLOSE);
            frame.setLayout(new java.awt.GridLayout(1, 4, 15, 15));

            for (int i = 0; i < 4; i++) {
                int ch = i + 1;
                javax.swing.JPanel p = new javax.swing.JPanel(new java.awt.BorderLayout(5, 5));
                IndicadorLed led = new IndicadorLed("D" + ch, false);
                javax.swing.JToggleButton btn = new javax.swing.JToggleButton("Out " + ch);
                btn.addActionListener(e -> led.setEstado(btn.isSelected()));

                p.add(new javax.swing.JLabel("Salida " + ch, javax.swing.SwingConstants.CENTER), java.awt.BorderLayout.NORTH);
                p.add(led, java.awt.BorderLayout.CENTER);
                p.add(btn, java.awt.BorderLayout.SOUTH);
                frame.add(p);
            }

            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
