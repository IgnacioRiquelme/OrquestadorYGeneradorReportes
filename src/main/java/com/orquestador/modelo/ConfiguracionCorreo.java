package com.orquestador.modelo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Configuración de envío de correo por área.
 * Cada área puede tener sus propios destinatarios, plantilla de asunto,
 * cuerpo del correo y ruta donde se depositan los PDFs generados.
 */
public class ConfiguracionCorreo {

    private String id;
    /** Nombre del área, debe coincidir con el campo 'area' de ProyectoAutomatizacion */
    private String area;
    /** Destinatarios en el campo "Para" (separados por ";" en la UI) */
    private List<String> destinatarios = new ArrayList<>();
    /** Destinatarios en el campo "CC" */
    private List<String> cc = new ArrayList<>();
    /**
     * Asunto del correo.
     * Placeholders disponibles: {area}, {fecha}
     */
    private String asunto;
    /**
     * Párrafo de introducción que aparece ANTES de la lista de proyectos.
     */
    private String cuerpoIntroduccion;
    /**
     * Párrafo de cierre que aparece DESPUÉS de la lista de proyectos.
     */
    private String cuerpoFinal;
    /**
     * Ruta de la carpeta donde se depositan los PDF generados para esta área.
     * Solo se adjuntan PDFs cuya fecha de modificación sea posterior a la fecha
     * de la tarea programada.
     */
    private String rutaPDF;

    public ConfiguracionCorreo() {
        this.id = UUID.randomUUID().toString();
    }

    public ConfiguracionCorreo(String area) {
        this.id   = UUID.randomUUID().toString();
        this.area = area;
    }

    // ── Getters / Setters ──────────────────────────────────────────────────────

    public String getId()               { return id; }
    public void   setId(String id)      { this.id = id; }

    public String getArea()              { return area; }
    public void   setArea(String area)   { this.area = area; }

    public List<String> getDestinatarios()                      { return destinatarios; }
    public void         setDestinatarios(List<String> d)        { this.destinatarios = d != null ? d : new ArrayList<>(); }

    public List<String> getCc()                                 { return cc; }
    public void         setCc(List<String> cc)                  { this.cc = cc != null ? cc : new ArrayList<>(); }

    public String getAsunto()                                   { return asunto; }
    public void   setAsunto(String asunto)                      { this.asunto = asunto; }

    public String getCuerpoIntroduccion()                       { return cuerpoIntroduccion; }
    public void   setCuerpoIntroduccion(String cuerpoIntro)     { this.cuerpoIntroduccion = cuerpoIntro; }

    public String getCuerpoFinal()                              { return cuerpoFinal; }
    public void   setCuerpoFinal(String cuerpoFinal)            { this.cuerpoFinal = cuerpoFinal; }

    public String getRutaPDF()                                  { return rutaPDF; }
    public void   setRutaPDF(String rutaPDF)                    { this.rutaPDF = rutaPDF; }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Devuelve el asunto resolviendo los placeholders {area} y {fecha}. */
    public String getAsuntoResuelto(String fechaFormateada) {
        if (asunto == null) return "";
        return asunto
                .replace("{area}",  area  != null ? area  : "")
                .replace("{fecha}", fechaFormateada != null ? fechaFormateada : "");
    }

    /** Convierte la lista de destinatarios en un String separado por ";" */
    public String getDestinatariosString() {
        return String.join("; ", destinatarios);
    }

    /** Convierte la lista CC en un String separado por ";" */
    public String getCcString() {
        return String.join("; ", cc);
    }

    /** Parsea un string con ";" como separador y actualiza la lista de destinatarios */
    public void setDestinatariosDesdeString(String texto) {
        destinatarios = parsearEmails(texto);
    }

    /** Parsea un string con ";" como separador y actualiza la lista CC */
    public void setCcDesdeString(String texto) {
        cc = parsearEmails(texto);
    }

    private static List<String> parsearEmails(String texto) {
        List<String> lista = new ArrayList<>();
        if (texto == null || texto.isBlank()) return lista;
        for (String email : texto.split(";")) {
            String e = email.trim();
            if (!e.isEmpty()) lista.add(e);
        }
        return lista;
    }

    @Override
    public String toString() {
        return "Área: " + area;
    }
}
