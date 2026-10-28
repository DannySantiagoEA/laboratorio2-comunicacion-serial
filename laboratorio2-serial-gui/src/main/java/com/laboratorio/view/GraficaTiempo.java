/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio.view;

import com.laboratorio.model.Senal;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.JPanel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import java.util.ArrayList;
import java.util.List;
import org.jfree.data.Range;
import java.awt.geom.Ellipse2D;

/**
 * Tarea #13: integración y configuración de la librería de graficación
 * (JFreeChart) para el trazado de series temporales continuas.
 *
 * Es un JPanel reutilizable: se usa una vez para la señal analógica (línea
 * continua) y otra para la señal digital (escalones). Muestra una ventana
 * deslizante con los últimos segundos de la señal, como un osciloscopio.
 */
public class GraficaTiempo extends JPanel {

    public static final double VENTANA_SEGUNDOS = 10.0; // ancho visible del eje de tiempo
    public static final int MAX_PUNTOS = 1000;          // puntos que guarda la serie

    private final XYSeries serie;       // datos (x = tiempo, y = valor)
    private final JFreeChart grafico;   // gráfico completo
    // Tarea #22: en qué instante empezó a mostrarse cada señal (para el archivo)
    private final List<Double> tiemposCambio = new ArrayList<>();
    private final List<String> titulosCambio = new ArrayList<>();
    private String tituloPendiente = null;   // se registra con el siguiente punto

    /**
     * @param ejeY texto del eje vertical, ej. "Voltaje (V)"
     * @param yMin valor mínimo del eje vertical
     * @param yMax valor máximo del eje vertical
     * @param digital true = dibuja en escalones (0/1), false = línea continua
     * @param color color de la línea
     */
    public GraficaTiempo(String ejeY, double yMin, double yMax, boolean digital, Color color) {

        // 1. Serie de datos: guarda como máximo MAX_PUNTOS (borra los más viejos)
        serie = new XYSeries("Señal");
        serie.setMaximumItemCount(MAX_PUNTOS);
        XYSeriesCollection datos = new XYSeriesCollection(serie);

        // 2. Crear el gráfico XY con la fábrica de JFreeChart
        grafico = ChartFactory.createXYLineChart(
                "", // título (se pone al escoger la señal)
                "Tiempo (s)", // eje X
                ejeY, // eje Y
                datos, // datos
                PlotOrientation.VERTICAL,
                false, // leyenda
                true, // tooltips (valor al pasar el mouse)
                false);                     // URLs

        // 3. Configurar el área de dibujo
        XYPlot plot = grafico.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        // 4. Eje X (tiempo): se desplaza solo, mostrando los últimos VENTANA_SEGUNDOS
        NumberAxis ejeX = (NumberAxis) plot.getDomainAxis();
        ejeX.setAutoRange(true);
        ejeX.setFixedAutoRange(VENTANA_SEGUNDOS);

        // 5. Eje Y
        NumberAxis ejeVertical = (NumberAxis) plot.getRangeAxis();
        if (digital) {
            // Se ajusta solo a lo que se ve: 0..1 para la lógica, 0..5 V para las muestreadas
            ejeVertical.setAutoRange(true);
            ejeVertical.setAutoRangeIncludesZero(true);
            ejeVertical.setAutoRangeMinimumSize(1.0);
        } else {
            ejeVertical.setRange(yMin, yMax);   // rango fijo para que la gráfica no "salte"
        }

        // 6. Forma del trazo:
        //    analógica -> línea continua
        //    digital   -> solo puntos (tiempo discreto, una muestra cada Ts)
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(!digital, digital);
        renderer.setSeriesPaint(0, color);
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        if (digital) {
            renderer.setSeriesShape(0, new Ellipse2D.Double(-3, -3, 6, 6));   // punto de 6 px
        }
        plot.setRenderer(renderer);

        // 7. Meter el gráfico en un panel de Swing
        ChartPanel panelGrafico = new ChartPanel(grafico);
        panelGrafico.setPreferredSize(new Dimension(700, 260));
        setLayout(new BorderLayout());
        add(panelGrafico, BorderLayout.CENTER);
    }

    /**
     * Agrega un punto nuevo (se llama en cada ciclo de muestreo).
     */
    /**
     * Agrega un punto nuevo (se llama en cada ciclo de muestreo).
     */
    public void agregarPunto(double tiempo, double valor) {
        if (tituloPendiente != null) {          // primer punto de una señal nueva
            tiemposCambio.add(tiempo);
            titulosCambio.add(tituloPendiente);
            tituloPendiente = null;
        }
        serie.add(tiempo, valor);
    }

    /**
     * Cambia la señal que se muestra: borra la serie y carga su historial
     * reciente.
     */
    public void mostrarSenal(Senal senal) {
        grafico.setTitle(senal.getNombre());
        serie.clear();
        tiemposCambio.clear();
        titulosCambio.clear();
        int n = senal.getCantidad();
        int inicio = Math.max(0, n - MAX_PUNTOS);
        if (n > 0) {                             // ya hay historial: la señal empieza ahí
            tiemposCambio.add(senal.getTiempo(inicio));
            titulosCambio.add(senal.getNombre());
            tituloPendiente = null;
        } else {                                 // sin datos: empieza con el siguiente punto
            tituloPendiente = senal.getNombre();
        }
        for (int i = inicio; i < n; i++) {
            serie.add(senal.getTiempo(i), senal.getValor(i), false); // false = no redibujar aún
        }
        serie.fireSeriesChanged();                                    // redibuja una sola vez
    }

    /**
     * Cambia solo el título, SIN borrar los puntos. Se usa al cambiar de señal
     * para que la gráfica siga desde donde iba.
     */
    public void cambiarTitulo(String titulo) {
        grafico.setTitle(titulo);
        tituloPendiente = titulo;               // la señal nueva empieza en el siguiente punto
    }

    /**
     * Tarea #22: devuelve los puntos {tiempo, valor} que se ven AHORA en la
     * pantalla (los que están dentro del rango visible del eje de tiempo).
     */
    public List<double[]> getPuntosVisibles() {
        Range visible = grafico.getXYPlot().getDomainAxis().getRange();
        List<double[]> puntos = new ArrayList<>();
        for (int i = 0; i < serie.getItemCount(); i++) {
            double t = serie.getX(i).doubleValue();
            if (visible.contains(t)) {
                puntos.add(new double[]{t, serie.getY(i).doubleValue()});
            }
        }
        return puntos;
    }

    /**
     * Tarea #22: describe qué señal(es) se ven en pantalla. Si dentro de la
     * ventana visible hubo un cambio de señal, indica desde qué instante. Ej:
     * "Analógica 1 - Seno (desde t = 3.700 s) -> Analógica 4 - Triangular
     * (desde t = 8.700 s)"
     */
    public String getDescripcionVisible() {
        if (tiemposCambio.isEmpty()) {
            return grafico.getTitle() != null ? grafico.getTitle().getText() : "";
        }
        Range visible = grafico.getXYPlot().getDomainAxis().getRange();

        // Señal que ya estaba activa al inicio de la ventana visible
        int primera = 0;
        for (int i = 0; i < tiemposCambio.size(); i++) {
            if (tiemposCambio.get(i) <= visible.getLowerBound()) {
                primera = i;
            }
        }
        if (primera == tiemposCambio.size() - 1) {
            return titulosCambio.get(primera);   // no hubo cambios en lo visible
        }
        StringBuilder texto = new StringBuilder();
        for (int i = primera; i < tiemposCambio.size(); i++) {
            double desde = (i == primera)
                    ? Math.max(visible.getLowerBound(), serie.getMinX())
                    : tiemposCambio.get(i);
            if (texto.length() > 0) {
                texto.append(" -> ");
            }
            texto.append(String.format(java.util.Locale.US, "%s (desde t = %.3f s)",
                    titulosCambio.get(i), desde));
        }
        return texto.toString();
    }

    /**
     * Borra todos los puntos de la gráfica.
     */
    public void limpiar() {
        serie.clear();
        tiemposCambio.clear();
        titulosCambio.clear();
    }
}
