package com.orquestador.modelo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Modelo que representa un Proyecto de generaciÃ³n de documentos
 */
public class Proyecto implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String nombre;
    private String rutaImagenes;
    private String rutaTemplateWord;
    private String rutaSalida; // Mantener por compatibilidad - deprecated
    private String rutaSalidaWord;
    private String rutaSalidaPdf;
    private List<String> imagenesSeleccionadas;
    private String estado; // PENDIENTE, EN_PROCESO, EXITOSO, FALLIDO
    private String mensajeError;
    private long tiempoGeneracion; // en milisegundos
    private String documentoWordGenerado;
    private String documentoPdfGenerado;
    private boolean seleccionado = true; // Por defecto todos seleccionados
    private String area; // Ãrea del proyecto: Clientes, Comercial, Integraciones, Siniestros, etc.
    
    public Proyecto() {
        this.imagenesSeleccionadas = new ArrayList<>();
        this.estado = "PENDIENTE";
        this.seleccionado = true;
    }
    
    public Proyecto(String nombre, String rutaImagenes, String rutaTemplateWord, String rutaSalida) {
        this();
        this.nombre = nombre;
        this.rutaImagenes = rutaImagenes;
        this.rutaTemplateWord = rutaTemplateWord;
        this.rutaSalida = rutaSalida;
    }
    
    // Getters y Setters
    public String getNombre() {
        return nombre;
    }
    
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }
    
    public String getRutaImagenes() {
        return rutaImagenes;
    }
    
    public void setRutaImagenes(String rutaImagenes) {
        this.rutaImagenes = rutaImagenes;
    }
    
    public String getRutaTemplateWord() {
        return rutaTemplateWord;
    }
    
    public void setRutaTemplateWord(String rutaTemplateWord) {
        this.rutaTemplateWord = rutaTemplateWord;
    }
    
    public String getRutaSalida() {
        // Por compatibilidad, si no hay rutas especÃ­ficas retorna la general
        return rutaSalida != null ? rutaSalida : rutaSalidaWord;
    }
    
    public void setRutaSalida(String rutaSalida) {
        this.rutaSalida = rutaSalida;
    }
    
    public String getRutaSalidaWord() {
        return rutaSalidaWord != null ? rutaSalidaWord : rutaSalida;
    }
    
    public void setRutaSalidaWord(String rutaSalidaWord) {
        this.rutaSalidaWord = rutaSalidaWord;
    }
    
    public String getRutaSalidaPdf() {
        return rutaSalidaPdf != null ? rutaSalidaPdf : rutaSalida;
    }
    
    public void setRutaSalidaPdf(String rutaSalidaPdf) {
        this.rutaSalidaPdf = rutaSalidaPdf;
    }
    
    public List<String> getImagenesSeleccionadas() {
        return imagenesSeleccionadas;
    }
    
    public void setImagenesSeleccionadas(List<String> imagenesSeleccionadas) {
        this.imagenesSeleccionadas = imagenesSeleccionadas;
    }
    
    public String getEstado() {
        return estado;
    }
    
    public void setEstado(String estado) {
        this.estado = estado;
    }
    
    public String getMensajeError() {
        return mensajeError;
    }
    
    public void setMensajeError(String mensajeError) {
        this.mensajeError = mensajeError;
    }
    
    public long getTiempoGeneracion() {
        return tiempoGeneracion;
    }
    
    public void setTiempoGeneracion(long tiempoGeneracion) {
        this.tiempoGeneracion = tiempoGeneracion;
    }
    
    public String getDocumentoWordGenerado() {
        return documentoWordGenerado;
    }
    
    public void setDocumentoWordGenerado(String documentoWordGenerado) {
        this.documentoWordGenerado = documentoWordGenerado;
    }
    
    public String getDocumentoPdfGenerado() {
        return documentoPdfGenerado;
    }
    
    public void setDocumentoPdfGenerado(String documentoPdfGenerado) {
        this.documentoPdfGenerado = documentoPdfGenerado;
    }
    
    public boolean isSeleccionado() {
        return seleccionado;
    }
    
    public void setSeleccionado(boolean seleccionado) {
        this.seleccionado = seleccionado;
    }
    
    public String getArea() {
        return area != null ? area : "Sin Ã¡rea";
    }
    
    public void setArea(String area) {
        this.area = area;
    }
    
    @Override
    public String toString() {
        return "Proyecto{" +
                "nombre='" + nombre + '\'' +
                ", estado='" + estado + '\'' +
                ", imagenesSeleccionadas=" + imagenesSeleccionadas.size() +
                '}';
    }
}

