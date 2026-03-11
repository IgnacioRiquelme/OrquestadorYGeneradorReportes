package com.orquestador.modelo;

import java.time.LocalDateTime;

/**
 * Modelo que representa un proyecto de automatizacin
 */
public class ProyectoAutomatizacion {
    private String nombre;
    private String empresa;
    private String ruta;
    private String area; // Clientes, Comercial, Integraciones, Siniestros
    private TipoVPN tipoVPN; // SIN_VPN, VPN_BCI, VPN_CLIP
    private TipoEjecucion tipoEjecucion; // MAVEN, NEWMAN
    private boolean seleccionado;
    private EstadoEjecucion estado;
    private LocalDateTime ultimaEjecucion;
    private Integer duracionSegundos;
    private String mensajeError;
    private String rutaLogEjecucion; // Ruta del archivo .log de la última ejecución

    // Campos para RETRY - reintento de ejecución hasta 3 veces
    private boolean retryHabilitado = false; // Si es true, permite reintentos (por defecto deshabilitado)
    private int intentoActual = 0; // Intento actual (0 = no iniciado, 1 = primer intento, 2 = segundo, 3 = tercero)
    private int totalReintentos = 3; // Total de reintentos permitidos

    // Campos para generacion de informes
    private String rutaImagenes;
    private String rutaTemplateWord;
    private String rutaSalidaWord;
    private String rutaSalidaPdf;
    private java.util.List<String> imagenesSeleccionadas = new java.util.ArrayList<>();
    private boolean reporteGenerado; // Estado de la generación del informe
    private boolean esProyectoManual = false; // Si es true, imagenesSeleccionadas contiene rutas absolutas
    private java.util.List<ConfiguracionInforme> informes = new java.util.ArrayList<>(); // Lista de informes a generar

    public enum TipoVPN {
        SIN_VPN("Sin VPN"),
        HIBRIDO("Hibrido"),
        VPN_BCI("VPN BCI"),
        VPN_CLIP("VPN Clip");

        private final String descripcion;

        TipoVPN(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }

        @Override
        public String toString() {
            return descripcion;
        }
    }

    public enum TipoEjecucion {
        MAVEN("Maven Test"),
        NEWMAN("Newman (Postman)"),
        MAVEN_NEWMAN("Maven + Newman");

        private final String descripcion;

        TipoEjecucion(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }

        @Override
        public String toString() {
            return descripcion;
        }
    }

    public enum EstadoEjecucion {
        PENDIENTE(" Pendiente"),
        EJECUTANDO(" Ejecutando"),
        EXITOSO(" Exitoso"),
        FALLIDO(" Fallido"),
        CANCELADO(" Cancelado");

        private final String descripcion;

        EstadoEjecucion(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }

        @Override
        public String toString() {
            return descripcion;
        }
    }

    // Constructor
    public ProyectoAutomatizacion() {
        this.empresa = "BCI Seguros";
        this.seleccionado = false; // Por defecto NO seleccionado
        this.estado = EstadoEjecucion.PENDIENTE;
        this.tipoVPN = TipoVPN.SIN_VPN;
        this.tipoEjecucion = TipoEjecucion.MAVEN;
    }

    public ProyectoAutomatizacion(String nombre, String ruta, String area, TipoVPN tipoVPN, TipoEjecucion tipoEjecucion) {
        this();
        this.nombre = nombre;
        this.ruta = ruta;
        this.area = area;
        this.tipoVPN = tipoVPN;
        this.tipoEjecucion = tipoEjecucion;
    }

    public ProyectoAutomatizacion(String nombre, String empresa, String ruta, String area, TipoVPN tipoVPN, TipoEjecucion tipoEjecucion) {
        this(nombre, ruta, area, tipoVPN, tipoEjecucion);
        this.empresa = (empresa == null || empresa.trim().isEmpty()) ? "BCI Seguros" : empresa.trim();
    }

    // Getters y Setters
    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getRuta() {
        return ruta;
    }

    public String getEmpresa() {
        return empresa;
    }

    public void setEmpresa(String empresa) {
        this.empresa = (empresa == null || empresa.trim().isEmpty()) ? "BCI Seguros" : empresa.trim();
    }

    public void setRuta(String ruta) {
        this.ruta = ruta;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public TipoVPN getTipoVPN() {
        return tipoVPN;
    }

    public void setTipoVPN(TipoVPN tipoVPN) {
        this.tipoVPN = tipoVPN;
    }

    public TipoEjecucion getTipoEjecucion() {
        return tipoEjecucion;
    }

    public void setTipoEjecucion(TipoEjecucion tipoEjecucion) {
        this.tipoEjecucion = tipoEjecucion;
    }

    public boolean isSeleccionado() {
        return seleccionado;
    }

    public void setSeleccionado(boolean seleccionado) {
        this.seleccionado = seleccionado;
    }

    public EstadoEjecucion getEstado() {
        return estado;
    }

    public void setEstado(EstadoEjecucion estado) {
        this.estado = estado;
    }

    public LocalDateTime getUltimaEjecucion() {
        return ultimaEjecucion;
    }

    public void setUltimaEjecucion(LocalDateTime ultimaEjecucion) {
        this.ultimaEjecucion = ultimaEjecucion;
    }

    public Integer getDuracionSegundos() {
        return duracionSegundos;
    }

    public void setDuracionSegundos(Integer duracionSegundos) {
        this.duracionSegundos = duracionSegundos;
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

    public String getRutaSalidaWord() {
        return rutaSalidaWord;
    }

    public void setRutaSalidaWord(String rutaSalidaWord) {
        this.rutaSalidaWord = rutaSalidaWord;
    }

    public String getRutaSalidaPdf() {
        return rutaSalidaPdf;
    }

    public void setRutaSalidaPdf(String rutaSalidaPdf) {
        this.rutaSalidaPdf = rutaSalidaPdf;
    }

    public java.util.List<String> getImagenesSeleccionadas() {
        return imagenesSeleccionadas;
    }

    public void setImagenesSeleccionadas(java.util.List<String> imagenesSeleccionadas) {
        this.imagenesSeleccionadas = imagenesSeleccionadas;
    }

    public boolean isReporteGenerado() {
        return reporteGenerado;
    }

    public void setReporteGenerado(boolean reporteGenerado) {
        this.reporteGenerado = reporteGenerado;
    }

    public String getMensajeError() {
        return mensajeError;
    }

    public void setMensajeError(String mensajeError) {
        this.mensajeError = mensajeError;
    }

    public String getRutaLogEjecucion() {
        return rutaLogEjecucion;
    }

    public void setRutaLogEjecucion(String rutaLogEjecucion) {
        this.rutaLogEjecucion = rutaLogEjecucion;
    }

    public boolean isEsProyectoManual() {
        return esProyectoManual;
    }

    public void setEsProyectoManual(boolean esProyectoManual) {
        this.esProyectoManual = esProyectoManual;
    }

    public java.util.List<ConfiguracionInforme> getInformes() {
        return informes;
    }

    public void setInformes(java.util.List<ConfiguracionInforme> informes) {
        this.informes = informes;
    }

    // Getters y Setters para RETRY
    public boolean isRetryHabilitado() {
        return retryHabilitado;
    }

    public void setRetryHabilitado(boolean retryHabilitado) {
        this.retryHabilitado = retryHabilitado;
    }

    public int getIntentoActual() {
        return intentoActual;
    }

    public void setIntentoActual(int intentoActual) {
        this.intentoActual = intentoActual;
    }

    public int getTotalReintentos() {
        return totalReintentos;
    }

    public void setTotalReintentos(int totalReintentos) {
        this.totalReintentos = totalReintentos;
    }

    /**
     * Obtiene la representación de reintentos en formato "X/3"
     * @return String con formato "X/3" si hay reintentos, "" si no
     */
    public String getFormatoRetry() {
        if (!retryHabilitado || intentoActual == 0) {
            return "";
        }
        return intentoActual + "/" + totalReintentos;
    }

    /**
     * Resetea los contadores de reintentos
     */
    public void resetearReintentos() {
        this.intentoActual = 0;
        this.retryHabilitado = false;
    }
}

