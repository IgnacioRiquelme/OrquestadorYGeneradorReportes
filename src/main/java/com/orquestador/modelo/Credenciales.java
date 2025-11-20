package com.orquestador.modelo;

import com.google.gson.annotations.SerializedName;

/**
 * Modelo para almacenar credenciales de los 4 proyectos especiales (Contactenos BCI, Zenit, Corredores)
 */
public class Credenciales {
    
    // Campos para proyectos 15 (BCI), 16 (Zenit), 17 (Corredores Generales)
    @SerializedName("user")
    private String user = "";
    
    @SerializedName("pasword")
    private String pasword = "";
    
    @SerializedName("nAtencionBci")
    private String nAtencionBci = "";
    
    @SerializedName("nAtencionZenit")
    private String nAtencionZenit = "";
    
    @SerializedName("user2")
    private String user2 = "";
    
    @SerializedName("pasword2")
    private String pasword2 = "";
    
    // Campos para proyecto 18 (Corredores VIDA)
    @SerializedName("numeroTicket")
    private String numeroTicket = "";
    
    // Campos para imágenes (comunes a todos)
    @SerializedName("rutaImagenSolicitud")
    private String rutaImagenSolicitud = "";
    
    @SerializedName("rutaImagenCorreo")
    private String rutaImagenCorreo = "";
    
    // Constructores
    public Credenciales() {}
    
    // Getters y Setters
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user != null ? user.trim() : ""; }
    
    public String getPasword() { return pasword; }
    public void setPasword(String pasword) { this.pasword = pasword != null ? pasword.trim() : ""; }
    
    public String getNAtencionBci() { return nAtencionBci; }
    public void setNAtencionBci(String nAtencionBci) { this.nAtencionBci = nAtencionBci != null ? nAtencionBci.trim() : ""; }
    
    public String getNAtencionZenit() { return nAtencionZenit; }
    public void setNAtencionZenit(String nAtencionZenit) { this.nAtencionZenit = nAtencionZenit != null ? nAtencionZenit.trim() : ""; }
    
    public String getUser2() { return user2; }
    public void setUser2(String user2) { this.user2 = user2 != null ? user2.trim() : ""; }
    
    public String getPasword2() { return pasword2; }
    public void setPasword2(String pasword2) { this.pasword2 = pasword2 != null ? pasword2.trim() : ""; }
    
    public String getNumeroTicket() { return numeroTicket; }
    public void setNumeroTicket(String numeroTicket) { this.numeroTicket = numeroTicket != null ? numeroTicket.trim() : ""; }
    
    public String getRutaImagenSolicitud() { return rutaImagenSolicitud; }
    public void setRutaImagenSolicitud(String rutaImagenSolicitud) { this.rutaImagenSolicitud = rutaImagenSolicitud != null ? rutaImagenSolicitud.trim() : ""; }
    
    public String getRutaImagenCorreo() { return rutaImagenCorreo; }
    public void setRutaImagenCorreo(String rutaImagenCorreo) { this.rutaImagenCorreo = rutaImagenCorreo != null ? rutaImagenCorreo.trim() : ""; }
}
