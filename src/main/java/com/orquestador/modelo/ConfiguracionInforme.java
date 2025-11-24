package com.orquestador.modelo;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa la configuración de un informe individual dentro de un proyecto.
 * Permite generar múltiples informes desde una sola ejecución de automatización.
 */
public class ConfiguracionInforme {
    private String templateWord;
    private String patronImagenes;
    private List<String> imagenesSeleccionadas;
    private String nombreArchivo;
    
    public ConfiguracionInforme() {
        this.imagenesSeleccionadas = new ArrayList<>();
    }
    
    public ConfiguracionInforme(String templateWord, String patronImagenes) {
        this.templateWord = templateWord;
        this.patronImagenes = patronImagenes;
        this.imagenesSeleccionadas = new ArrayList<>();
    }
    
    public String getTemplateWord() {
        return templateWord;
    }
    
    public void setTemplateWord(String templateWord) {
        this.templateWord = templateWord;
    }
    
    public String getPatronImagenes() {
        return patronImagenes;
    }
    
    public void setPatronImagenes(String patronImagenes) {
        this.patronImagenes = patronImagenes;
    }
    
    public List<String> getImagenesSeleccionadas() {
        return imagenesSeleccionadas;
    }
    
    public void setImagenesSeleccionadas(List<String> imagenesSeleccionadas) {
        this.imagenesSeleccionadas = imagenesSeleccionadas;
    }
    
    public String getNombreArchivo() {
        return nombreArchivo;
    }
    
    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }
    
    @Override
    public String toString() {
        return "ConfiguracionInforme{" +
                "templateWord='" + templateWord + '\'' +
                ", patronImagenes='" + patronImagenes + '\'' +
                ", imagenesSeleccionadas=" + (imagenesSeleccionadas != null ? imagenesSeleccionadas.size() : 0) +
                ", nombreArchivo='" + nombreArchivo + '\'' +
                '}';
    }
}
