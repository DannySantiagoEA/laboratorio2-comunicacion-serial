package com.laboratorio.model;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Tarea #25: persistencia en disco en dos columnas delimitadas por
 * tabulador, como pide la Práctica 2 (numeral 3.e).
 *
 * Ejemplo del archivo que se genera (señal analógica, 4 decimales):
 *
 * Tiempo	Dato
 * 0.000	2.4902
 * 0.100	2.5098
 *
 * Señal digital (0 decimales):
 *
 * Tiempo	Dato
 * 0.000	1
 * 0.100	0
 */
public class EscritorArchivo {

    public static final String SEPARADOR = "\t";
    public static final String ENCABEZADO = "Tiempo" + SEPARADOR + "Dato";
    public static final int DECIMALES_ANALOGICA = 4;
    public static final int DECIMALES_DIGITAL = 0;
    private static final int DECIMALES_TIEMPO = 3;

    /**
     * Escribe las muestras (t, dato) en dos columnas separadas por tabulador.
     *
     * @param muestras  lista de pares {tiempo (s), dato}
     * @param archivo   archivo destino (se crea o se sobrescribe)
     * @param decimales decimales de la columna Dato (4 analógica, 0 digital)
     * @return cantidad de muestras escritas
     * @throws IOException si no se puede crear o escribir el archivo
     */
    public static int guardarTiempoDato(List<double[]> muestras, File archivo,
            int decimales) throws IOException {

        if (muestras == null || muestras.isEmpty()) {
            throw new IllegalArgumentException("No hay muestras para guardar.");
        }
        if (archivo == null) {
            throw new IllegalArgumentException("No se indicó el archivo de destino.");
        }

        String formatoFila = "%." + DECIMALES_TIEMPO + "f" + SEPARADOR + "%." + decimales + "f";

        // try-with-resources: cierra el archivo aunque ocurra un error
        try (BufferedWriter escritor = new BufferedWriter(
                new FileWriter(archivo, StandardCharsets.UTF_8))) {

            escritor.write(ENCABEZADO);
            escritor.newLine();

            // Locale.US -> punto decimal (2.5000 y no 2,5000)
            for (double[] punto : muestras) {
                escritor.write(String.format(Locale.US, formatoFila, punto[0], punto[1]));
                escritor.newLine();
            }
        }
        return muestras.size();
    }
}