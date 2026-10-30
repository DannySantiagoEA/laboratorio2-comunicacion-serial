package com.laboratorio.controller;

import com.laboratorio.model.Data;
import com.laboratorio.model.EscritorArchivo;
import com.laboratorio.model.SalidasDigitales;
import com.laboratorio.model.TramaTelemetria;
import com.laboratorio.view.GraficaTiempo;
import com.laboratorio.view.IndicadorLed;
import com.laboratorio.view.VentanaPrincipal;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Controlador principal de la arquitectura MVC para el Laboratorio 2. Tarea #21
 * (HU-05): Vincula los selectores de canales analógicos (0..7) y digitales
 * (0..3) con el flujo de telemetría serial (0x7A 0x7B ... 0x7C) para refrescar
 * en tiempo real los lienzos de GraficaTiempo.
 */
public class ControladorAdquisicion {

    private final VentanaPrincipal vista;
    private final Data modelo;
    private final SerialCommunicationManager gestorSerial;

    // Timer de respaldo para simulación local únicamente cuando el puerto COM está desconectado
    private Timer timerMuestreoLocal;
    private int periodoMuestreoMs = 100; // Ts inicial: 100 ms
    private long contadorTicks = 0;

    // Reloj acumulado para las tramas recibidas por puerto serial (en segundos)
    private double tiempoSerialSegundos = 0.0;

    // Componentes gráficos JFreeChart encapsulados en GraficaTiempo
    private GraficaTiempo graficaAnalogica;
    private GraficaTiempo graficaDigital;

    // Estado de selección de canales activos en la GUI (0..7 analógicos, 0..3 digitales)
    private int canalAnalogicoActivo = 0;
    private int canalDigitalActivo = 0;

    // Etiquetas de los canales físicos del ATmega328P en Proteus (Tarea #21)
    private static final String[] NOMBRES_ADC_SERIAL = {
        "Canal ADC 1 (A0 - Pot 1)", "Canal ADC 2 (A1 - Pot 2)",
        "Canal ADC 3 (A2 - Pot 3)", "Canal ADC 4 (A3 - Pot 4)",
        "Canal ADC 5 (A4 - Pot 5)", "Canal ADC 6 (A5 - Pot 6)",
        "Canal ADC 7 (A6 - Pot 7)", "Canal ADC 8 (A7 - Pot 8)"
    };
    private static final String[] NOMBRES_DIP_SERIAL = {
        "Entrada Digital 1 (DSW1-1 / Pin PD4)",
        "Entrada Digital 2 (DSW1-2 / Pin PD5)",
        "Entrada Digital 3 (DSW1-3 / Pin PD6)",
        "Entrada Digital 4 (DSW1-4 / Pin PD7)"
    };

    // Búferes sincronizados en memoria para exportación tabular (Tiempo\tDato)
    private final List<double[]> bufferAnalogicoActivo = Collections.synchronizedList(new ArrayList<>());
    private final List<double[]> bufferDigitalActivo = Collections.synchronizedList(new ArrayList<>());

    public static final int NUM_SALIDAS = SalidasDigitales.NUM_SALIDAS;

    public ControladorAdquisicion(VentanaPrincipal vista, Data modelo) {
        this(vista, modelo, new SerialCommunicationManager());
    }

    public ControladorAdquisicion(VentanaPrincipal vista, Data modelo, SerialCommunicationManager gestorSerial) {
        this.vista = vista;
        this.modelo = modelo;
        this.gestorSerial = gestorSerial;

        this.modelo.setPeriodoMuestreoMs(this.periodoMuestreoMs);

        inicializarGraficas();
        inicializarVista();
        configurarTimerLocal();
        configurarRecepcionSerialAsincrona();
        conectarEventos();
        refrescarListaPuertosCom();
    }

    private void inicializarGraficas() {
        // Señal analógica: trazo continuo, rango 0.0 V a 5.0 V, color rojo
        this.graficaAnalogica = new GraficaTiempo("Voltaje (V)", 0.0, 5.0, false, new Color(200, 30, 30));

        // Señal digital: puntos discretos, rango -0.2 a 1.5, color azul
        this.graficaDigital = new GraficaTiempo("Nivel Lógico (0/1)", -0.2, 1.5, true, new Color(30, 110, 200));
    }

    private void inicializarVista() {
        vista.montarGraficaAnalogica(this.graficaAnalogica);
        vista.montarGraficaDigital(this.graficaDigital);

        // Poblar combos con los canales del enlace serial
        vista.cargarNombresCanales(NOMBRES_ADC_SERIAL, NOMBRES_DIP_SERIAL);
        vista.setTiempoMuestreoActual(this.periodoMuestreoMs);

        graficaAnalogica.cambiarTitulo(NOMBRES_ADC_SERIAL[canalAnalogicoActivo]);
        graficaDigital.cambiarTitulo(NOMBRES_DIP_SERIAL[canalDigitalActivo]);

        // Estado inicial de los 4 LEDs de salida
        SalidasDigitales salidas = modelo.getSalidas();
        for (int i = 0; i < NUM_SALIDAS; i++) {
            IndicadorLed led = vista.getLedSalida(i);
            if (led != null) {
                led.setEstado(salidas.getEstado(i));
            }
        }

        // Disparador del modelo: actualiza LED visual y despacha trama 0xAA 0x01 si está conectado
        salidas.agregarDisparador((canal, estado, tiempo) -> {
            IndicadorLed led = vista.getLedSalida(canal);
            if (led != null) {
                led.setEstado(estado);
            }
            if (vista.getBotonSalida(canal) != null) {
                vista.getBotonSalida(canal).setSelected(estado);
            }
            System.out.printf(">> [Actuador] Salida D%d -> %s%n",
                    (canal + 1), (estado ? "1 (ON)" : "0 (OFF)"));

            if (gestorSerial.isConectado()) {
                try {
                    boolean[] estadosActuales = salidas.getEstados();
                    gestorSerial.enviarComandoLeds(estadosActuales);
                } catch (SerialConnectionException ex) {
                    System.err.println("[ERROR TX LEDs] No se pudo enviar comando de LEDs: " + ex.getMessage());
                }
            }
        });
    }

    private void configurarTimerLocal() {
        timerMuestreoLocal = new Timer(Data.PERIODO_ANALOGICO_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Solo opera como respaldo si el puerto serial NO está conectado
                if (!gestorSerial.isConectado()) {
                    ejecutarCicloMuestreoSintetico();
                }
            }
        });
    }

    // =========================================================================
    // TAREA #21: ENLACE DE TELEMETRÍA SERIAL CON LIENZOS GRAFICATIEMPO
    // =========================================================================
    /**
     * Escucha las tramas [0x7A, 0x7B, Digital, ADC0..7, 0x7C] validadas por
     * SerialCommunicationManager y las publica en el EDT de Swing.
     */
    private void configurarRecepcionSerialAsincrona() {
        gestorSerial.setTramaRecibidaListener((TramaTelemetria trama) -> {
            SwingUtilities.invokeLater(() -> procesarTramaSerialEnEdt(trama));
        });
    }

    /**
     * Deserializa el canal analógico y digital actualmente seleccionados en los
     * JComboBox y refresca ambos lienzos de GraficaTiempo.
     */
    private void procesarTramaSerialEnEdt(TramaTelemetria trama) {
        double tActual = this.tiempoSerialSegundos;
        this.tiempoSerialSegundos += (this.periodoMuestreoMs / 1000.0);

        // 1. Extraer voltaje escalado (0.00 V a 5.00 V) del canal analógico activo (0..7)
        double vAnalogico = trama.getVoltajesAnalogicos()[canalAnalogicoActivo];
        graficaAnalogica.agregarPunto(tActual, vAnalogico);
        bufferAnalogicoActivo.add(new double[]{tActual, vAnalogico});

        // 2. Extraer nivel lógico (0 o 1) del interruptor digital activo (0..3)
        int vDigital = trama.getEstadosDigitales()[canalDigitalActivo];
        graficaDigital.agregarPunto(tActual, vDigital);
        bufferDigitalActivo.add(new double[]{tActual, (double) vDigital});
    }

    private void ejecutarCicloMuestreoSintetico() {
        contadorTicks++;
        double tiempoActual = modelo.getTiempo();
        modelo.tomarMuestra();

        double vAnalogico = modelo.getTodasLasAnalogicas()[canalAnalogicoActivo];
        graficaAnalogica.agregarPunto(tiempoActual, vAnalogico);
        bufferAnalogicoActivo.add(new double[]{tiempoActual, vAnalogico});

        for (double[] fila : modelo.extraerMuestrasDigitalesNuevas()) {
            double tn = fila[0];
            double vDigital = fila[1 + canalDigitalActivo];
            graficaDigital.agregarPunto(tn, vDigital);
            bufferDigitalActivo.add(new double[]{tn, vDigital});
        }
    }

    private void conectarEventos() {
        // 1. Botón Refrescar puertos COM
        vista.addListenerRefrescarPuertos(e -> refrescarListaPuertosCom());

        // 2. Botón Conectar / Desconectar puerto serial
        vista.addListenerConectarSerial(e -> alternarConexionSerial());

        // 3. Tarea #21: Selector de canal analógico (0 a 7) conectado al flujo serial
        vista.addListenerCanalAnalogico(e -> {
            int nuevoCanal = vista.getCanalAnalogico();
            if (nuevoCanal >= 0 && nuevoCanal < Data.NUM_ANALOGICAS && nuevoCanal != canalAnalogicoActivo) {
                this.canalAnalogicoActivo = nuevoCanal;
                this.graficaAnalogica.cambiarTitulo(NOMBRES_ADC_SERIAL[nuevoCanal]);
                limpiarBufferAnalogico();
                System.out.println(">> [Tarea #21] Canal analógico conmutado a: " + NOMBRES_ADC_SERIAL[nuevoCanal]);
            }
        });

        // 4. Tarea #21: Selector de canal digital (0 a 3) conectado al flujo serial
        vista.addListenerCanalDigital(e -> {
            int nuevoCanal = vista.getCanalDigital();
            if (nuevoCanal >= 0 && nuevoCanal < Data.NUM_DIGITALES && nuevoCanal != canalDigitalActivo) {
                this.canalDigitalActivo = nuevoCanal;
                this.graficaDigital.cambiarTitulo(NOMBRES_DIP_SERIAL[nuevoCanal]);
                limpiarBufferDigital();
                System.out.println(">> [Tarea #21] Canal digital conmutado a: " + NOMBRES_DIP_SERIAL[nuevoCanal]);
            }
        });

        // 5. Modificación de Ts en caliente y envío de comando serial 0xAA 0x02
        vista.addListenerCambiarMuestreo(e -> {
            String textoEntrada = vista.getTiempoMuestreoTexto();
            actualizarPeriodo(textoEntrada);
        });

        // 6. Exportación de datos analógicos y digitales a disco (Tiempo\tDato)
        vista.addListenerGuardarAnalogica(e -> exportarDatosCanalAnalogico());
        vista.addListenerGuardarDigital(e -> exportarDatosCanalDigital());

        // 7. Conexión de los 4 botones de salidas digitales (LEDs hacia microcontrolador)
        for (int i = 0; i < NUM_SALIDAS; i++) {
            final int canal = i;
            vista.addListenerSalida(canal, e -> {
                boolean estadoActual = vista.getBotonSalida(canal).isSelected();
                establecerEstadoSalida(canal, estadoActual);
            });
        }
    }

    // =========================================================================
    // GESTIÓN DE CONEXIÓN SERIAL (HU-01 / HU-04 / HU-05)
    // =========================================================================
    public final void refrescarListaPuertosCom() {
        List<String> puertos = gestorSerial.obtenerNombresPuertosDisponibles();
        if (puertos.isEmpty()) {
            vista.cargarPuertosDisponibles(new String[]{"COM2", "COM1"});
        } else {
            vista.cargarPuertosDisponibles(puertos.toArray(new String[0]));
        }
    }

    private void alternarConexionSerial() {
        if (gestorSerial.isConectado()) {
            try {
                gestorSerial.enviarComandoEstadoFlujo(false);
            } catch (SerialConnectionException ex) {
                System.err.println("[WARN] No se pudo enviar comando de pausa antes de desconectar: " + ex.getMessage());
            }
            gestorSerial.desconectar();
            vista.actualizarEstadoConexionSerial(false, "Desconectado (Modo Simulación Local)");
            iniciarAdquisicion();
        } else {
            String puerto = vista.getPuertoSeleccionado();
            try {
                detenerAdquisicion(); // Detiene el generador de la Práctica 1
                gestorSerial.conectar(puerto);

                // Reiniciar gráficas y reloj serial para mostrar exclusivamente telemetría de Proteus
                this.tiempoSerialSegundos = 0.0;
                this.graficaAnalogica.limpiar();
                this.graficaDigital.limpiar();
                limpiarBufferAnalogico();
                limpiarBufferDigital();

                // Sincronizar Ts actual, estado de LEDs e iniciar transmisión en el ATmega328P
                gestorSerial.enviarComandoMuestreo(this.periodoMuestreoMs);
                gestorSerial.enviarComandoLeds(modelo.getSalidas().getEstados());
                gestorSerial.enviarComandoEstadoFlujo(true);

                vista.actualizarEstadoConexionSerial(true,
                        "Conectado a " + puerto + " (9600 bps, 8-N-1 | Recibiendo telemetría)");
            } catch (SerialConnectionException ex) {
                vista.actualizarEstadoConexionSerial(false, "Error en " + puerto + " (Modo Simulación Local)");
                JOptionPane.showMessageDialog(
                        vista,
                        ex.getMessage(),
                        "Error de Conexión Serial (" + puerto + ")",
                        JOptionPane.ERROR_MESSAGE
                );
                iniciarAdquisicion();
            }
        }
    }

    // =========================================================================
    // SALIDAS DIGITALES Y ACTUADORES (TELECOMANDO DE LEDS 0xAA 0x01)
    // =========================================================================
    public void establecerEstadoSalida(int canal, boolean nuevoEstado) {
        if (canal >= 0 && canal < NUM_SALIDAS) {
            modelo.getSalidas().setEstado(canal, nuevoEstado, modelo.getTiempo());
        }
    }

    public boolean getEstadoSalida(int canal) {
        if (canal >= 0 && canal < NUM_SALIDAS) {
            return modelo.getSalidas().getEstado(canal);
        }
        return false;
    }

    public boolean[] getTodosLosEstadosSalidas() {
        return modelo.getSalidas().getEstados();
    }

    // =========================================================================
    // SANITIZACIÓN Y CONFIGURACIÓN DEL PERIODO TS (TELECOMANDO 0xAA 0x02)
    // =========================================================================
    public boolean actualizarPeriodo(String entradaTexto) {
        try {
            int nuevoTs = ValidadorMuestreo.validarPeriodo(entradaTexto);
            this.periodoMuestreoMs = nuevoTs;

            if (modelo != null) {
                modelo.setPeriodoMuestreoMs(nuevoTs);
            }

            if (gestorSerial.isConectado()) {
                try {
                    gestorSerial.enviarComandoMuestreo(nuevoTs);
                } catch (SerialConnectionException ex) {
                    JOptionPane.showMessageDialog(
                            vista,
                            "Se actualizó Ts localmente, pero falló el envío serial:\n" + ex.getMessage(),
                            "Error de Transmisión Serial",
                            JOptionPane.WARNING_MESSAGE
                    );
                }
            }

            vista.setTiempoMuestreoActual(nuevoTs);
            System.out.println(">> [Controlador] Periodo de muestreo reconfigurado a: " + nuevoTs + " ms");
            return true;

        } catch (PeriodoInvalidoException ex) {
            JOptionPane.showMessageDialog(
                    vista,
                    ex.getMessage(),
                    "Tiempo de Muestreo Inválido",
                    JOptionPane.WARNING_MESSAGE
            );
            return false;
        }
    }

    // =========================================================================
    // PERSISTENCIA Y EXPORTACIÓN A DISCO (HU-06 - Tiempo\tDato)
    // =========================================================================
    private void exportarDatosCanalAnalogico() {
        List<double[]> datos = graficaAnalogica.getPuntosVisibles();
        if (datos.isEmpty()) {
            JOptionPane.showMessageDialog(vista,
                    "No hay muestras analógicas en memoria para exportar.",
                    "Búfer Vacío",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        File archivo = solicitarRutaGuardado("Exportar Señal Analógica a Archivo");
        if (archivo != null) {
            try {
                int total = EscritorArchivo.guardarValorVsTiempo(
                        datos,
                        archivo,
                        graficaAnalogica.getDescripcionVisible(),
                        "V"
                );
                JOptionPane.showMessageDialog(vista,
                        "Archivo analógico guardado con " + total + " muestras:\n" + archivo.getAbsolutePath(),
                        "Exportación Exitosa",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(vista,
                        "Error al escribir el archivo: " + ex.getMessage(),
                        "Error I/O",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportarDatosCanalDigital() {
        List<double[]> datos = graficaDigital.getPuntosVisibles();
        if (datos.isEmpty()) {
            JOptionPane.showMessageDialog(vista,
                    "No hay muestras digitales en memoria para exportar.",
                    "Búfer Vacío",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        File archivo = solicitarRutaGuardado("Exportar Señal Digital a Archivo");
        if (archivo != null) {
            try {
                int total = EscritorArchivo.guardarValorVsTiempo(
                        datos,
                        archivo,
                        graficaDigital.getDescripcionVisible(),
                        "0/1"
                );
                JOptionPane.showMessageDialog(vista,
                        "Archivo digital guardado con " + total + " muestras:\n" + archivo.getAbsolutePath(),
                        "Exportación Exitosa",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(vista,
                        "Error al escribir el archivo digital: " + ex.getMessage(),
                        "Error I/O",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private File solicitarRutaGuardado(String titulo) {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle(titulo);
        selector.setFileFilter(new FileNameExtensionFilter("Archivos de texto (*.txt)", "txt"));

        int seleccion = selector.showSaveDialog(vista);
        if (seleccion == JFileChooser.APPROVE_OPTION) {
            File f = selector.getSelectedFile();
            if (!f.getName().toLowerCase().endsWith(".txt")) {
                f = new File(f.getAbsolutePath() + ".txt");
            }
            return f;
        }
        return null;
    }

    public synchronized List<double[]> obtenerBufferAnalogicoActivo() {
        synchronized (bufferAnalogicoActivo) {
            return new ArrayList<>(bufferAnalogicoActivo);
        }
    }

    public synchronized List<double[]> obtenerBufferDigitalActivo() {
        synchronized (bufferDigitalActivo) {
            return new ArrayList<>(bufferDigitalActivo);
        }
    }

    public void limpiarBufferAnalogico() {
        bufferAnalogicoActivo.clear();
    }

    public void limpiarBufferDigital() {
        bufferDigitalActivo.clear();
    }

    public void iniciarAdquisicion() {
        if (timerMuestreoLocal != null && !timerMuestreoLocal.isRunning()) {
            timerMuestreoLocal.start();
            System.out.println(">> Simulación local de respaldo iniciada.");
        }
    }

    public void detenerAdquisicion() {
        if (timerMuestreoLocal != null && timerMuestreoLocal.isRunning()) {
            timerMuestreoLocal.stop();
            System.out.println(">> Simulación local detenida.");
        }
    }

    public SerialCommunicationManager getGestorSerial() {
        return gestorSerial;
    }
}
