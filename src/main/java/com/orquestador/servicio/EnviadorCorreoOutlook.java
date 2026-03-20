package com.orquestador.servicio;

import com.orquestador.modelo.ConfiguracionCorreo;
import com.orquestador.modelo.ProyectoAutomatizacion;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Envía correos a través de Outlook (COM) usando un script PowerShell temporal.
 * Requiere que Outlook esté instalado y configurado en el equipo.
 */
public class EnviadorCorreoOutlook {

    private static final DateTimeFormatter FMT_ASUNTO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FMT_CUERPO  = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy 'a las' HH:mm",
            new Locale("es", "CL"));

    /**
     * Envía el correo automáticamente para el area indicada.
     *
     * @param config           Configuración de correo del área
     * @param proyectosExito   Proyectos que ejecutaron con éxito (deben tener PDF)
     * @param proyectosFallido Proyectos que fallaron (no generan PDF, se informan como error)
     * @param fechaTareaDesde  Fecha desde la cual se aceptan PDFs (hora de inicio de la tarea)
     * @return Resultado del envío con mensaje descriptivo
     */
    public static ResultadoEnvio enviar(ConfiguracionCorreo config,
                                        List<ProyectoAutomatizacion> proyectosExito,
                                        List<ProyectoAutomatizacion> proyectosFallido,
                                        LocalDateTime fechaTareaDesde) {
        try {
            // 1. Buscar PDFs nuevos en la carpeta configurada
            List<File> pdfs = buscarPDFsNuevos(config.getRutaPDF(), fechaTareaDesde);

            // 2. Construir cuerpo HTML
            String htmlBody = construirCuerpoHTML(config, proyectosExito, proyectosFallido);

            // 3. Generar script PowerShell temporal
            File scriptFile = generarScriptPowerShell(config, fechaTareaDesde, htmlBody, pdfs);

            // 4. Ejecutar
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-File", scriptFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process proceso = pb.start();

            String salida = new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = proceso.waitFor();

            // 5. Limpiar script temporal
            try { scriptFile.delete(); } catch (Exception ignored) {}

            if (exitCode == 0) {
                int nPdfs = pdfs.size();
                int nFail = proyectosFallido.size();
                String resumen = "✅ Correo enviado al área \"" + config.getArea() + "\" | "
                        + "Adjuntos: " + nPdfs + " PDF(s)";
                if (nFail > 0) resumen += " | " + nFail + " proyecto(s) con error informados";
                return new ResultadoEnvio(true, resumen, salida);
            } else {
                return new ResultadoEnvio(false,
                        "❌ Error al enviar correo del área \"" + config.getArea() + "\": código " + exitCode,
                        salida);
            }

        } catch (Exception e) {
            return new ResultadoEnvio(false,
                    "❌ Excepción al enviar correo: " + e.getMessage(), e.toString());
        }
    }

    // ── Búsqueda de PDFs ─────────────────────────────────────────────────────

    private static List<File> buscarPDFsNuevos(String rutaCarpeta, LocalDateTime desde) {
        List<File> resultado = new ArrayList<>();
        if (rutaCarpeta == null || rutaCarpeta.isBlank()) return resultado;

        File carpeta = new File(rutaCarpeta);
        if (!carpeta.exists() || !carpeta.isDirectory()) return resultado;

        File[] archivos = carpeta.listFiles(f -> f.isFile()
                && f.getName().toLowerCase().endsWith(".pdf"));
        if (archivos == null) return resultado;

        long desdeMs = desde.atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli();

        for (File f : archivos) {
            if (f.lastModified() >= desdeMs) {
                resultado.add(f);
            }
        }

        // Ordenar por fecha de modificación más reciente primero
        resultado.sort(Comparator.comparingLong(File::lastModified).reversed());
        return resultado;
    }

    // ── Cuerpo HTML ──────────────────────────────────────────────────────────

    private static String construirCuerpoHTML(ConfiguracionCorreo config,
                                               List<ProyectoAutomatizacion> exitosos,
                                               List<ProyectoAutomatizacion> fallidos) {

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:Calibri,Arial,sans-serif;font-size:14px;'>");

        // Introducción
        String intro = config.getCuerpoIntroduccion();
        if (intro != null && !intro.isBlank()) {
            // Preservar saltos de línea como <br>
            for (String linea : intro.split("\\n")) {
                sb.append("<p>").append(escapeHtml(linea.trim())).append("</p>");
            }
        }

        // Lista de proyectos
        sb.append("<ul>");
        for (ProyectoAutomatizacion p : exitosos) {
            sb.append("<li>").append(escapeHtml(limpiarNombreProyecto(p.getNombre()))).append("</li>");
        }
        for (ProyectoAutomatizacion p : fallidos) {
            sb.append("<li style='color:#CC0000;'>")
              .append(escapeHtml(limpiarNombreProyecto(p.getNombre())))
              .append(" &nbsp;<b>&#9888; EJECUCIÓN CON ERROR</b> (sin informe)")
              .append("</li>");
        }
        sb.append("</ul>");

        // Cierre
        String cierre = config.getCuerpoFinal();
        if (cierre != null && !cierre.isBlank()) {
            for (String linea : cierre.split("\\n")) {
                sb.append("<p>").append(escapeHtml(linea.trim())).append("</p>");
            }
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    /** Quita el prefijo numérico "01- " del nombre del proyecto para mostrarlo más limpio */
    private static String limpiarNombreProyecto(String nombre) {
        if (nombre == null) return "";
        return nombre.replaceAll("^\\d+[-.]?\\s*", "").trim();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // ── Script PowerShell ─────────────────────────────────────────────────────

    private static File generarScriptPowerShell(ConfiguracionCorreo config,
                                                 LocalDateTime fechaTarea,
                                                 String htmlBody,
                                                 List<File> pdfs) throws IOException {
        String asunto = config.getAsuntoResuelto(fechaTarea.format(FMT_ASUNTO));

        StringBuilder ps = new StringBuilder();
        ps.append("# Script generado automáticamente - OrquestadorAutomatizaciones\n");
        ps.append("$ErrorActionPreference = 'Stop'\n\n");

        // Crear objeto Outlook
        ps.append("try {\n");
        ps.append("    $outlook = New-Object -ComObject Outlook.Application\n");
        ps.append("    $mail = $outlook.CreateItem(0)\n\n");

        // Destinatarios
        ps.append("    $mail.To = ").append(psString(config.getDestinatariosString())).append("\n");
        if (!config.getCc().isEmpty()) {
            ps.append("    $mail.CC = ").append(psString(config.getCcString())).append("\n");
        }

        // Asunto
        ps.append("    $mail.Subject = ").append(psString(asunto)).append("\n");

        // Cuerpo HTML (se escribe en archivo temp para evitar problemas de escaping)
        File htmlTemp = File.createTempFile("orq_mail_body_", ".html");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(htmlTemp), StandardCharsets.UTF_8)) {
            w.write(htmlBody);
        }
        ps.append("    $mail.HTMLBody = [System.IO.File]::ReadAllText(")
          .append(psString(htmlTemp.getAbsolutePath()))
          .append(", [System.Text.Encoding]::UTF8)\n\n");

        // Adjuntos PDF
        for (File pdf : pdfs) {
            ps.append("    $mail.Attachments.Add(").append(psString(pdf.getAbsolutePath())).append(")\n");
        }

        // Enviar
        ps.append("\n    $mail.Send()\n");
        ps.append("    Write-Host 'CORREO_ENVIADO_OK'\n");
        ps.append("} catch {\n");
        ps.append("    Write-Host \"ERROR: $_\"\n");
        ps.append("    exit 1\n");
        ps.append("} finally {\n");
        ps.append("    # Limpiar HTML temp\n");
        ps.append("    if (Test-Path ").append(psString(htmlTemp.getAbsolutePath())).append(") {\n");
        ps.append("        Remove-Item ").append(psString(htmlTemp.getAbsolutePath())).append(" -Force\n");
        ps.append("    }\n");
        ps.append("}\n");

        // Escribir script
        File scriptFile = File.createTempFile("orq_mail_", ".ps1");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(scriptFile), StandardCharsets.UTF_8)) {
            w.write(ps.toString());
        }
        return scriptFile;
    }

    /** Envuelve un string en comillas simples para PowerShell (escapa las internas) */
    private static String psString(String value) {
        if (value == null) value = "";
        return "'" + value.replace("'", "''") + "'";
    }

    // ── Clase resultado ───────────────────────────────────────────────────────

    public static class ResultadoEnvio {
        private final boolean exito;
        private final String mensaje;
        private final String detalle;

        public ResultadoEnvio(boolean exito, String mensaje, String detalle) {
            this.exito   = exito;
            this.mensaje = mensaje;
            this.detalle = detalle;
        }

        public boolean isExito()    { return exito; }
        public String  getMensaje() { return mensaje; }
        public String  getDetalle() { return detalle; }
    }
}
