package com.orquestador.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.orquestador.modelo.ConfiguracionInforme;
import com.orquestador.modelo.ProyectoAutomatizacion;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestiona la exportación e importación de la configuración completa de proyectos.
 *
 * <p><b>EXPORTAR:</b> Relativiza todas las rutas absolutas respecto a tres raíces:
 * <ol>
 *   <li>Raíz de proyectos (ej: C:\Automatizaciones_V2) → para ruta del proyecto, imágenes
 *   <li>Raíz de informes  (ej: C:\Entregas Documentos Parchado) → para rutaSalidaWord/Pdf
 *   <li>Raíz de templates (ej: C:\Nuevos esqueletos) → para rutaTemplateWord
 * </ol>
 *
 * <p><b>IMPORTAR:</b> Reconstruye rutas absolutas con las nuevas raíces proporcionadas
 * por el usuario, y crea automáticamente la estructura de carpetas de informes.
 */
public class GestorExportImport {

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    // -----------------------------------------------------------------------
    // Modelo de paquete de exportación
    // -----------------------------------------------------------------------

    /** Wrapper JSON que envuelve los proyectos relativizados y los metadatos. */
    public static class PaqueteExportacion {
        public String version = "1.0";
        public String fecha;
        /** Nombre de la carpeta raíz de proyectos en el equipo de origen (solo informativo). */
        public String infoRaizProyectos;
        /** Nombre de la carpeta raíz de informes en el equipo de origen (solo informativo). */
        public String infoRaizInformes;
        /** Nombre de la carpeta raíz de templates en el equipo de origen (solo informativo). */
        public String infoRaizTemplates;
        public List<ProyectoAutomatizacion> proyectos;
    }

    // -----------------------------------------------------------------------
    // EXPORTAR
    // -----------------------------------------------------------------------

    /**
     * Exporta la configuración de proyectos a un archivo JSON con rutas relativizadas.
     *
     * @param proyectos       Lista completa de proyectos a exportar.
     * @param destino         Archivo .json de destino.
     * @param rutaBaseProyectos Raíz donde están las 4 carpetas de áreas con los proyectos.
     * @param rutaBaseInformes  Carpeta raíz donde se almacenan los informes PDF/WORD.
     * @param rutaBaseTemplates Carpeta raíz donde están los templates Word base.
     */
    public static void exportar(
            List<ProyectoAutomatizacion> proyectos,
            File destino,
            String rutaBaseProyectos,
            String rutaBaseInformes,
            String rutaBaseTemplates) throws IOException {

        String baseProyectos = normalizar(rutaBaseProyectos);
        String baseInformes  = normalizar(rutaBaseInformes);
        String baseTemplates = normalizar(rutaBaseTemplates);

        List<ProyectoAutomatizacion> relativizados = new ArrayList<>();

        for (ProyectoAutomatizacion p : proyectos) {
            // Deep copy via Gson para no mutar el original en memoria
            ProyectoAutomatizacion copia = copiarProyecto(p);

            // --- Rutas relativas a baseProyectos ---
            copia.setRuta(relativizar(p.getRuta(), baseProyectos));
            copia.setRutaImagenes(relativizar(p.getRutaImagenes(), baseProyectos));

            // --- Rutas relativas a baseInformes ---
            copia.setRutaSalidaWord(relativizar(p.getRutaSalidaWord(), baseInformes));
            copia.setRutaSalidaPdf(relativizar(p.getRutaSalidaPdf(), baseInformes));

            // --- Rutas relativas a baseTemplates ---
            copia.setRutaTemplateWord(relativizar(p.getRutaTemplateWord(), baseTemplates));

            // --- imagenesSeleccionadas (relativas a baseProyectos) ---
            if (p.getImagenesSeleccionadas() != null) {
                List<String> imagenesRel = new ArrayList<>();
                for (String img : p.getImagenesSeleccionadas()) {
                    imagenesRel.add(relativizar(img, baseProyectos));
                }
                copia.setImagenesSeleccionadas(imagenesRel);
            }

            // --- ConfiguracionInforme (multi-informe por proyecto) ---
            if (p.getInformes() != null) {
                List<ConfiguracionInforme> informesRel = new ArrayList<>();
                for (ConfiguracionInforme inf : p.getInformes()) {
                    ConfiguracionInforme ci = new ConfiguracionInforme();
                    ci.setNombreArchivo(inf.getNombreArchivo());
                    ci.setTemplateWord(relativizar(inf.getTemplateWord(), baseTemplates));
                    ci.setPatronImagenes(relativizar(inf.getPatronImagenes(), baseProyectos));
                    if (inf.getImagenesSeleccionadas() != null) {
                        List<String> imgs = new ArrayList<>();
                        for (String img : inf.getImagenesSeleccionadas()) {
                            imgs.add(relativizar(img, baseProyectos));
                        }
                        ci.setImagenesSeleccionadas(imgs);
                    }
                    informesRel.add(ci);
                }
                copia.setInformes(informesRel);
            }

            // Limpiar campos que no deben viajar (log, estado temporal, log path)
            copia.setEstado(ProyectoAutomatizacion.EstadoEjecucion.PENDIENTE);
            copia.setUltimaEjecucion(null);
            copia.setMensajeError(null);
            copia.setRutaLogEjecucion(null);
            copia.setSeleccionado(false);
            copia.setIntentoActual(0);

            relativizados.add(copia);
        }

        PaqueteExportacion paquete = new PaqueteExportacion();
        paquete.fecha = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        paquete.infoRaizProyectos = new File(baseProyectos).getName();
        paquete.infoRaizInformes  = new File(baseInformes).getName();
        paquete.infoRaizTemplates = new File(baseTemplates).getName();
        paquete.proyectos = relativizados;

        try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(destino), StandardCharsets.UTF_8)) {
            gson.toJson(paquete, writer);
        }
    }

    // -----------------------------------------------------------------------
    // IMPORTAR
    // -----------------------------------------------------------------------

    /**
     * Importa la configuración desde un archivo JSON, reconstruyendo rutas absolutas
     * con las nuevas raíces proporcionadas. También crea la estructura de carpetas
     * PDF/WORD con las subcarpetas de cada área.
     *
     * @param archivoImport            Archivo .json exportado desde el equipo maestro.
     * @param rutaBaseProyectosDestino Carpeta raíz donde están las 4 áreas en el equipo destino.
     * @param rutaBaseInformesDestino  Carpeta raíz donde se almacenarán los informes en el destino.
     * @param rutaBaseTemplatesDestino Carpeta raíz donde están los templates Word en el destino.
     * @return Lista de proyectos ya con rutas absolutas reconstruidas.
     */
    public static List<ProyectoAutomatizacion> importar(
            File archivoImport,
            String rutaBaseProyectosDestino,
            String rutaBaseInformesDestino,
            String rutaBaseTemplatesDestino) throws IOException {

        String baseProyectos = normalizar(rutaBaseProyectosDestino);
        String baseInformes  = normalizar(rutaBaseInformesDestino);
        String baseTemplates = normalizar(rutaBaseTemplatesDestino);

        PaqueteExportacion paquete = parsearPaquete(archivoImport);

        // Crear estructura de carpetas de informes en el destino
        crearEstructuraInformes(baseInformes);

        List<ProyectoAutomatizacion> resultado = new ArrayList<>();

        for (ProyectoAutomatizacion p : paquete.proyectos) {
            // --- Reconstruir rutas con baseProyectos ---
            p.setRuta(reconstruir(p.getRuta(), baseProyectos));
            p.setRutaImagenes(reconstruir(p.getRutaImagenes(), baseProyectos));

            // --- Reconstruir rutas con baseInformes ---
            p.setRutaSalidaWord(reconstruir(p.getRutaSalidaWord(), baseInformes));
            p.setRutaSalidaPdf(reconstruir(p.getRutaSalidaPdf(), baseInformes));

            // --- Reconstruir rutas con baseTemplates ---
            p.setRutaTemplateWord(reconstruir(p.getRutaTemplateWord(), baseTemplates));

            // --- imagenesSeleccionadas ---
            if (p.getImagenesSeleccionadas() != null) {
                List<String> imgs = new ArrayList<>();
                for (String img : p.getImagenesSeleccionadas()) {
                    imgs.add(reconstruir(img, baseProyectos));
                }
                p.setImagenesSeleccionadas(imgs);
            }

            // --- ConfiguracionInforme ---
            if (p.getInformes() != null) {
                for (ConfiguracionInforme ci : p.getInformes()) {
                    ci.setTemplateWord(reconstruir(ci.getTemplateWord(), baseTemplates));
                    ci.setPatronImagenes(reconstruir(ci.getPatronImagenes(), baseProyectos));
                    if (ci.getImagenesSeleccionadas() != null) {
                        List<String> imgs = new ArrayList<>();
                        for (String img : ci.getImagenesSeleccionadas()) {
                            imgs.add(reconstruir(img, baseProyectos));
                        }
                        ci.setImagenesSeleccionadas(imgs);
                    }
                }
            }

            // Asegurar estado limpio en el equipo nuevo
            p.setEstado(ProyectoAutomatizacion.EstadoEjecucion.PENDIENTE);
            p.setUltimaEjecucion(null);
            p.setMensajeError(null);
            p.setRutaLogEjecucion(null);
            p.setSeleccionado(false);
            p.setIntentoActual(0);

            resultado.add(p);
        }

        return resultado;
    }

    /**
     * Lee solo los metadatos del paquete sin cargar todos los proyectos.
     * Útil para mostrar información antes de confirmar la importación.
     */
    public static PaqueteExportacion leerMetadatos(File archivoImport) throws IOException {
        return parsearPaquete(archivoImport);
    }

    /**
     * Parsea un archivo JSON aceptando dos formatos:
     * <ol>
     *   <li><b>Formato exportación</b>: objeto raíz con {@code version}, {@code fecha}, {@code proyectos}.</li>
     *   <li><b>Formato interno</b>: array JSON plano de {@code ProyectoAutomatizacion}.</li>
     * </ol>
     * Siempre devuelve un {@code PaqueteExportacion} válido o lanza {@code IOException}.
     */
    private static PaqueteExportacion parsearPaquete(File archivo) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(archivo), StandardCharsets.UTF_8)) {

            JsonElement root = JsonParser.parseReader(reader);

            if (root == null || root.isJsonNull()) {
                throw new IOException("El archivo está vacío o tiene contenido nulo.");
            }

            // ── Formato 1: array plano de proyectos (archivo interno proyectos.json) ──
            if (root.isJsonArray()) {
                Type listType = new TypeToken<List<ProyectoAutomatizacion>>(){}.getType();
                List<ProyectoAutomatizacion> lista = gson.fromJson(root, listType);
                if (lista == null) {
                    throw new IOException("No se pudo leer la lista de proyectos del archivo.");
                }
                PaqueteExportacion paquete = new PaqueteExportacion();
                paquete.proyectos = lista;
                paquete.fecha = "";
                paquete.infoRaizProyectos = "";
                paquete.infoRaizInformes  = "";
                paquete.infoRaizTemplates = "";
                return paquete;
            }

            // ── Formato 2: objeto PaqueteExportacion con wrapper ──
            if (root.isJsonObject()) {
                PaqueteExportacion paquete = gson.fromJson(root, PaqueteExportacion.class);
                if (paquete == null) {
                    throw new IOException("El archivo no tiene un formato de exportación válido (objeto nulo).");
                }
                if (paquete.proyectos == null) {
                    // Intentar leer campo alternativo o dar error descriptivo
                    throw new IOException(
                        "El archivo no contiene una lista de proyectos válida.\n" +
                        "Asegúrate de usar un archivo exportado desde 'Exportar configuración'.");
                }
                return paquete;
            }

            throw new IOException("El archivo no tiene un formato JSON reconocido (se esperaba objeto o array).");

        } catch (com.google.gson.JsonParseException e) {
            throw new IOException("Error al leer el JSON: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers internos
    // -----------------------------------------------------------------------

    /**
     * Detecta automáticamente la raíz común más larga entre una lista de rutas absolutas.
     * Por ejemplo, de ["C:\Auto\Siniestros\P1", "C:\Auto\Clientes\P2"] devuelve "C:\Auto".
     *
     * @param rutas Lista de rutas absolutas.
     * @return La carpeta padre común más larga, o cadena vacía si no se puede determinar.
     */
    public static String detectarRaizComun(List<String> rutas) {
        if (rutas == null || rutas.isEmpty()) return "";
        // Filtrar nulos y no-absolutas
        List<String> absolutas = rutas.stream()
            .filter(r -> r != null && !r.isBlank() && new File(r).isAbsolute())
            .map(GestorExportImport::normalizar)
            .distinct()
            .collect(java.util.stream.Collectors.toList());
        if (absolutas.isEmpty()) return "";
        if (absolutas.size() == 1) {
            // Si es un archivo devolver su carpeta padre; si es directorio devolverlo tal cual
            File f = new File(absolutas.get(0));
            return f.isDirectory() ? f.getAbsolutePath() : (f.getParentFile() != null ? f.getParentFile().getAbsolutePath() : absolutas.get(0));
        }
        // Partir cada ruta en segmentos
        String sep = File.separator.equals("\\") ? "\\\\" : File.separator;
        String[][] partes = absolutas.stream()
            .map(r -> r.split(sep))
            .toArray(String[][]::new);
        int minLen = Integer.MAX_VALUE;
        for (String[] p : partes) minLen = Math.min(minLen, p.length);
        int comun = 0;
        outer:
        for (int i = 0; i < minLen; i++) {
            String seg = partes[0][i];
            for (String[] p : partes) {
                if (!p[i].equalsIgnoreCase(seg)) break outer;
            }
            comun = i + 1;
        }
        if (comun == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < comun; i++) {
            if (i > 0) sb.append(File.separator);
            sb.append(partes[0][i]);
        }
        return sb.toString();
    }

    /**
     * Crea la estructura de carpetas de informes:
     * <pre>
     * baseInformes/
     *   PDF/  Comercial/ Clientes/ Integraciones/ Siniestros/
     *   WORD/ Comercial/ Clientes/ Integraciones/ Siniestros/
     * </pre>
     */
    private static void crearEstructuraInformes(String baseInformes) {
        String[] areas = {"Comercial", "Clientes", "Integraciones", "Siniestros"};
        String[] tipos = {"PDF", "WORD"};
        for (String tipo : tipos) {
            for (String area : areas) {
                new File(baseInformes + File.separator + tipo + File.separator + area).mkdirs();
            }
        }
    }

    /**
     * Convierte una ruta absoluta en relativa respecto a una base.
     * Si la ruta no empieza por la base, la devuelve sin cambios.
     */
    static String relativizar(String rutaAbsoluta, String base) {
        if (rutaAbsoluta == null || rutaAbsoluta.isBlank()) return rutaAbsoluta;
        String ruta = normalizar(rutaAbsoluta);
        String baseNorm = normalizar(base);
        // Comparación case-insensitive (Windows)
        if (ruta.toLowerCase().startsWith(baseNorm.toLowerCase())) {
            String rel = ruta.substring(baseNorm.length());
            if (!rel.isEmpty() && !rel.startsWith(File.separator)) {
                rel = File.separator + rel;
            }
            return rel;
        }
        return rutaAbsoluta; // No coincide, dejar tal cual
    }

    /**
     * Reconstruye una ruta absoluta a partir de una relativa y una nueva base.
     * Si ya es absoluta, la devuelve sin modificar.
     */
    static String reconstruir(String rutaRelativa, String base) {
        if (rutaRelativa == null || rutaRelativa.isBlank()) return rutaRelativa;
        File f = new File(rutaRelativa);
        if (f.isAbsolute()) return rutaRelativa; // Ya es absoluta, no tocar
        String sep = rutaRelativa.startsWith(File.separator) ? "" : File.separator;
        return normalizar(base) + sep + rutaRelativa;
    }

    /** Normaliza separadores de rutas al separador del sistema operativo actual. */
    static String normalizar(String ruta) {
        if (ruta == null) return "";
        // Normalizar primero a '/' y luego al separador del SO
        return ruta.replace("\\", "/").replace("/", File.separator);
    }

    /** Deep copy de un ProyectoAutomatizacion vía Gson (no muta el original). */
    private static ProyectoAutomatizacion copiarProyecto(ProyectoAutomatizacion p) {
        String json = gson.toJson(p);
        return gson.fromJson(json, ProyectoAutomatizacion.class);
    }
}
