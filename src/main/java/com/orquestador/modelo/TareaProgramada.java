package com.orquestador.modelo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class TareaProgramada {
    public static enum Modo { CSV_ONLY, EXEC_AND_REPORT }

    private String id;
    private String nombre;
    private List<String> proyectos; // nombres de proyectos
    private LocalDateTime fechaHora;
    private boolean ejecutada;
    private Modo modo = Modo.EXEC_AND_REPORT;

    public TareaProgramada() {
        // para serialización
    }

    public TareaProgramada(String nombre, List<String> proyectos, LocalDateTime fechaHora) {
        this.id = UUID.randomUUID().toString();
        this.nombre = nombre;
        this.proyectos = proyectos;
        this.fechaHora = fechaHora;
        this.ejecutada = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public List<String> getProyectos() { return proyectos; }
    public void setProyectos(List<String> proyectos) { this.proyectos = proyectos; }

    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }

    public boolean isEjecutada() { return ejecutada; }
    public void setEjecutada(boolean ejecutada) { this.ejecutada = ejecutada; }

    public Modo getModo() { return modo; }
    public void setModo(Modo modo) { this.modo = modo; }
}