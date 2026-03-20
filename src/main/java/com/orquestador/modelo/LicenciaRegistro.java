package com.orquestador.modelo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Registro de una licencia emitida. Solo existe en la app maestra (tu PC).
 */
public class LicenciaRegistro {

    public enum EstadoLicencia { ACTIVA, EXPIRADA, REVOCADA }

    private String id;               // ID interno del registro
    private String installationId;   // ID del equipo destino
    private String clave;            // Clave generada para entregar al usuario

    // Datos del titular (opcionales)
    private String nombre;
    private String empresa;
    private String rut;
    private String telefono;
    private String email;
    private String notas;

    // Fechas
    private LocalDate fechaExpiracion;
    private LocalDateTime fechaEmision;

    // Estado
    private EstadoLicencia estado; // calculado al consultar
    private boolean revocada;

    public LicenciaRegistro() {}

    public LicenciaRegistro(String installationId, String clave,
                             LocalDate fechaExpiracion, LocalDateTime fechaEmision,
                             String nombre, String empresa, String rut,
                             String telefono, String email, String notas) {
        this.id              = UUID.randomUUID().toString();
        this.installationId  = installationId;
        this.clave           = clave;
        this.fechaExpiracion = fechaExpiracion;
        this.fechaEmision    = fechaEmision;
        this.nombre          = nombre;
        this.empresa         = empresa;
        this.rut             = rut;
        this.telefono        = telefono;
        this.email           = email;
        this.notas           = notas;
        this.revocada        = false;
    }

    /** Calcula el estado real considerando fecha actual y bandera de revocación */
    public EstadoLicencia getEstadoCalculado() {
        if (revocada) return EstadoLicencia.REVOCADA;
        if (fechaExpiracion != null && LocalDate.now().isAfter(fechaExpiracion))
            return EstadoLicencia.EXPIRADA;
        return EstadoLicencia.ACTIVA;
    }

    /** Días restantes (negativo si ya expiró) */
    public long getDiasRestantes() {
        if (fechaExpiracion == null) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), fechaExpiracion);
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getId()                        { return id; }
    public void   setId(String id)               { this.id = id; }

    public String getInstallationId()                         { return installationId; }
    public void   setInstallationId(String installationId)    { this.installationId = installationId; }

    public String getClave()                     { return clave; }
    public void   setClave(String clave)         { this.clave = clave; }

    public String getNombre()                    { return nombre != null ? nombre : ""; }
    public void   setNombre(String nombre)       { this.nombre = nombre; }

    public String getEmpresa()                   { return empresa != null ? empresa : ""; }
    public void   setEmpresa(String empresa)     { this.empresa = empresa; }

    public String getRut()                       { return rut != null ? rut : ""; }
    public void   setRut(String rut)             { this.rut = rut; }

    public String getTelefono()                  { return telefono != null ? telefono : ""; }
    public void   setTelefono(String telefono)   { this.telefono = telefono; }

    public String getEmail()                     { return email != null ? email : ""; }
    public void   setEmail(String email)         { this.email = email; }

    public String getNotas()                     { return notas != null ? notas : ""; }
    public void   setNotas(String notas)         { this.notas = notas; }

    public LocalDate     getFechaExpiracion()                        { return fechaExpiracion; }
    public void          setFechaExpiracion(LocalDate v)             { this.fechaExpiracion = v; }

    public LocalDateTime getFechaEmision()                           { return fechaEmision; }
    public void          setFechaEmision(LocalDateTime v)            { this.fechaEmision = v; }

    public boolean isRevocada()                  { return revocada; }
    public void    setRevocada(boolean revocada) { this.revocada = revocada; }

    /** Para mostrar en tabla */
    public String getNombreDisplay() {
        if (nombre != null && !nombre.isBlank()) return nombre;
        if (empresa != null && !empresa.isBlank()) return empresa;
        return "(Sin nombre)";
    }
}
