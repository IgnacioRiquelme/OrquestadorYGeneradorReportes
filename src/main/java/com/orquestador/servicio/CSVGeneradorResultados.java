package com.orquestador.servicio;

import com.orquestador.modelo.ProyectoAutomatizacion;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CSVGeneradorResultados {

    public static File generarCSV(List<ProyectoAutomatizacion> proyectos, File destino) throws Exception {
        if (destino == null) {
            throw new IllegalArgumentException("Destino no puede ser null");
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(destino))) {
            // Cabecera: Nombre, Área, Retry, Estado, Última Ejecución, Duración
            pw.println("Nombre,Área,Retry,Estado,Última Ejecución,Duración");

            DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

            for (ProyectoAutomatizacion p : proyectos) {
                String nombre = escape(p.getNombre());
                String area = escape(p.getArea() != null ? p.getArea() : "");
                String retry = escape(p.getFormatoRetry() != null ? p.getFormatoRetry() : "-");
                String estado = p.getEstado() != null ? escape(p.getEstado().toString()) : "";
                String ultimaEjecucion = "";
                if (p.getUltimaEjecucion() != null) {
                    ultimaEjecucion = p.getUltimaEjecucion().format(df);
                }
                
                // Convertir duración a formato legible
                int totalSegundos = p.getDuracionSegundos() != null ? p.getDuracionSegundos() : 0;
                int minutos = totalSegundos / 60;
                int segundos = totalSegundos % 60;
                String duracionFormato;
                if (minutos > 0 && segundos > 0) {
                    duracionFormato = minutos + " min " + segundos + " seg";
                } else if (minutos > 0) {
                    duracionFormato = minutos + " min";
                } else {
                    duracionFormato = segundos + " seg";
                }

                pw.printf("%s,%s,%s,%s,%s,%s\n", nombre, area, retry, estado, ultimaEjecucion, duracionFormato);
            }

            pw.flush();
        }

        return destino;
    }

    private static String escape(String s) {
        if (s == null) return "";
        String out = s.replace("\"", "\"\"");
        if (out.contains(",") || out.contains("\n") || out.contains("\r")) {
            out = "\"" + out + "\"";
        }
        return out;
    }
}
