package com.orquestador.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.orquestador.modelo.ProyectoAutomatizacion;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestor de configuración y persistencia de proyectos
 */
public class GestorConfiguracion {
    private static final String DIRECTORIO_CONFIG = System.getProperty("user.home") 
        + File.separator + "AppData" 
        + File.separator + "Local" 
        + File.separator + "OrquestadorAutomatizaciones";
    
    private static final String ARCHIVO_PROYECTOS = "proyectos.json";
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    /**
     * Guarda la lista de proyectos en JSON
     */
    public static void guardarProyectos(List<ProyectoAutomatizacion> proyectos) throws IOException {
        crearDirectorioConfiguracion();
        
        String rutaArchivo = DIRECTORIO_CONFIG + File.separator + ARCHIVO_PROYECTOS;
        try (FileWriter writer = new FileWriter(rutaArchivo)) {
            gson.toJson(proyectos, writer);
        }
    }

    /**
     * Carga la lista de proyectos desde JSON
     */
    public static List<ProyectoAutomatizacion> cargarProyectos() {
        String rutaArchivo = DIRECTORIO_CONFIG + File.separator + ARCHIVO_PROYECTOS;
        File archivo = new File(rutaArchivo);
        
        if (!archivo.exists()) {
            return new ArrayList<>();
        }

        try (FileReader reader = new FileReader(archivo)) {
            Type listType = new TypeToken<ArrayList<ProyectoAutomatizacion>>(){}.getType();
            List<ProyectoAutomatizacion> proyectos = gson.fromJson(reader, listType);
            if (proyectos != null) {
                for (ProyectoAutomatizacion p : proyectos) normalizarRutas(p);
                return proyectos;
            }
            return new ArrayList<>();
        } catch (Exception e) {
            System.err.println("Error al cargar proyectos: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Normaliza las rutas de un proyecto: colapsa separadores duplicados
     * "\\\\" -> "\\" en todos los campos de ruta (defecto histórico del
     * instalador que duplicaba los backslashes). Conserva prefijos UNC.
     */
    public static void normalizarRutas(ProyectoAutomatizacion p) {
        if (p == null) return;
        p.setRuta(colapsarSeparadores(p.getRuta()));
        p.setRutaImagenes(colapsarSeparadores(p.getRutaImagenes()));
        p.setRutaTemplateWord(colapsarSeparadores(p.getRutaTemplateWord()));
        p.setRutaSalidaWord(colapsarSeparadores(p.getRutaSalidaWord()));
        p.setRutaSalidaPdf(colapsarSeparadores(p.getRutaSalidaPdf()));
        p.setRutaLogEjecucion(colapsarSeparadores(p.getRutaLogEjecucion()));
        if (p.getImagenesSeleccionadas() != null) {
            p.getImagenesSeleccionadas().replaceAll(GestorConfiguracion::colapsarSeparadores);
        }
        if (p.getInformes() != null) {
            for (com.orquestador.modelo.ConfiguracionInforme inf : p.getInformes()) {
                if (inf == null) continue;
                inf.setTemplateWord(colapsarSeparadores(inf.getTemplateWord()));
                inf.setPatronImagenes(colapsarSeparadores(inf.getPatronImagenes()));
                if (inf.getImagenesSeleccionadas() != null) {
                    inf.getImagenesSeleccionadas().replaceAll(GestorConfiguracion::colapsarSeparadores);
                }
            }
        }
    }

    /**
     * Colapsa secuencias de dos o más backslashes a uno solo.
     * No toca el prefijo "\\\\" inicial (ruta UNC \\servidor\recurso).
     */
    private static String colapsarSeparadores(String ruta) {
        if (ruta == null || ruta.isEmpty()) return ruta;
        String prefijo = ruta.startsWith("\\\\") ? "\\\\" : "";
        String resto = prefijo.isEmpty() ? ruta : ruta.substring(2);
        while (resto.contains("\\\\")) {
            resto = resto.replace("\\\\", "\\");
        }
        return prefijo + resto;
    }

    /**
     * Crea el directorio de configuración si no existe
     */
    private static void crearDirectorioConfiguracion() throws IOException {
        Path path = Paths.get(DIRECTORIO_CONFIG);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }

    /**
     * Obtiene la ruta del directorio de configuración
     */
    public static String getDirectorioConfiguracion() {
        return DIRECTORIO_CONFIG;
    }
}
