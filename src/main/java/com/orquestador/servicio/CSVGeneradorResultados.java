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
            // Cabecera
            pw.println("Nombre,Estado,DuracionMs,DocumentoWord,DocumentoPdf,MensajeError,RutaLog");

            DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            for (ProyectoAutomatizacion p : proyectos) {
                String nombre = escape(p.getNombre());
                String estado = p.getEstado() != null ? escape(p.getEstado().toString()) : "";
                String duracion = p.getDuracionSegundos() != null ? String.valueOf(p.getDuracionSegundos()) : "";
                String reporteGenerado = String.valueOf(p.isReporteGenerado());
                String mensaje = p.getMensajeError() != null ? escape(p.getMensajeError()) : "";
                String log = p.getRutaLogEjecucion() != null ? escape(p.getRutaLogEjecucion()) : "";

                pw.printf("%s,%s,%s,%s,%s,%s\n", nombre, estado, duracion, reporteGenerado, mensaje, log);
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
