package com.orquestador.modelo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Modelo para representar una vista guardada de proyectos seleccionados.
 * Permite guardar y restaurar configuraciones de selección para diferentes validaciones.
 */
public class VistaGuardada {
    private String nombre;
    private String descripcion;
    private List<String> proyectosSeleccionados; // Lista de claves "empresa::nombre"
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;

    public VistaGuardada() {
        this.proyectosSeleccionados = new ArrayList<>();
        this.fechaCreacion = LocalDateTime.now();
        this.fechaModificacion = LocalDateTime.now();
    }

    public VistaGuardada(String nombre) {
        this();
        this.nombre = nombre;
    }

    public VistaGuardada(String nombre, String descripcion) {
        this(nombre);
        this.descripcion = descripcion;
    }

    // Getters y Setters
    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
        this.fechaModificacion = LocalDateTime.now();
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
        this.fechaModificacion = LocalDateTime.now();
    }

    public List<String> getProyectosSeleccionados() {
        return proyectosSeleccionados;
    }

    public void setProyectosSeleccionados(List<String> proyectosSeleccionados) {
        this.proyectosSeleccionados = proyectosSeleccionados;
        this.fechaModificacion = LocalDateTime.now();
    }

    public void agregarProyecto(String claveProyecto) {
        if (!proyectosSeleccionados.contains(claveProyecto)) {
            proyectosSeleccionados.add(claveProyecto);
            this.fechaModificacion = LocalDateTime.now();
        }
    }

    public void removerProyecto(String claveProyecto) {
        proyectosSeleccionados.remove(claveProyecto);
        this.fechaModificacion = LocalDateTime.now();
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public LocalDateTime getFechaModificacion() {
        return fechaModificacion;
    }

    public void setFechaModificacion(LocalDateTime fechaModificacion) {
        this.fechaModificacion = fechaModificacion;
    }

    public boolean contieneProyecto(String claveProyecto) {
        return proyectosSeleccionados.contains(claveProyecto);
    }

    public int getCantidadProyectos() {
        return proyectosSeleccionados.size();
    }

    @Override
    public String toString() {
        return nombre + " (" + getCantidadProyectos() + " proyectos)";
    }
}
