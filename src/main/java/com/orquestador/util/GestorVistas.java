package com.orquestador.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.orquestador.modelo.VistaGuardada;

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
import java.util.stream.Collectors;

/**
 * Gestor de persistencia para vistas guardadas de proyectos seleccionados.
 */
public class GestorVistas {
    private static final String DIRECTORIO_CONFIG = System.getProperty("user.home") 
        + File.separator + "AppData" 
        + File.separator + "Local" 
        + File.separator + "OrquestadorAutomatizaciones";
    
    private static final String ARCHIVO_VISTAS = "vistas_guardadas.json";
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    /**
     * Guarda la lista de vistas guardadas en JSON
     */
    public static void guardarVistas(List<VistaGuardada> vistas) throws IOException {
        crearDirectorioConfiguracion();
        
        String rutaArchivo = DIRECTORIO_CONFIG + File.separator + ARCHIVO_VISTAS;
        try (FileWriter writer = new FileWriter(rutaArchivo)) {
            gson.toJson(vistas, writer);
        }
    }

    /**
     * Carga la lista de vistas guardadas desde JSON
     */
    public static List<VistaGuardada> cargarVistas() {
        String rutaArchivo = DIRECTORIO_CONFIG + File.separator + ARCHIVO_VISTAS;
        File archivo = new File(rutaArchivo);
        
        if (!archivo.exists()) {
            return new ArrayList<>();
        }

        try (FileReader reader = new FileReader(archivo)) {
            Type listType = new TypeToken<ArrayList<VistaGuardada>>(){}.getType();
            List<VistaGuardada> vistas = gson.fromJson(reader, listType);
            return vistas != null ? vistas : new ArrayList<>();
        } catch (Exception e) {
            System.err.println("Error al cargar vistas: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Guarda una vista individual (actualiza la lista completa)
     */
    public static void guardarVista(VistaGuardada vista) throws IOException {
        List<VistaGuardada> vistas = cargarVistas();
        
        // Buscar si ya existe una vista con el mismo nombre y actualizarla
        boolean encontrada = false;
        for (int i = 0; i < vistas.size(); i++) {
            if (vistas.get(i).getNombre().equals(vista.getNombre())) {
                vistas.set(i, vista);
                encontrada = true;
                break;
            }
        }
        
        // Si no existe, agregarla
        if (!encontrada) {
            vistas.add(vista);
        }
        
        guardarVistas(vistas);
    }

    /**
     * Elimina una vista por su nombre
     */
    public static void eliminarVista(String nombre) throws IOException {
        List<VistaGuardada> vistas = cargarVistas();
        vistas.removeIf(v -> v.getNombre().equals(nombre));
        guardarVistas(vistas);
    }

    /**
     * Obtiene una vista por su nombre
     */
    public static VistaGuardada obtenerVista(String nombre) {
        List<VistaGuardada> vistas = cargarVistas();
        return vistas.stream()
                .filter(v -> v.getNombre().equals(nombre))
                .findFirst()
                .orElse(null);
    }

    /**
     * Verifica si existe una vista con el nombre dado
     */
    public static boolean existeVista(String nombre) {
        List<VistaGuardada> vistas = cargarVistas();
        return vistas.stream().anyMatch(v -> v.getNombre().equals(nombre));
    }

    /**
     * Obtiene la lista de nombres de vistas guardadas
     */
    public static List<String> obtenerNombresVistas() {
        return cargarVistas().stream()
                .map(VistaGuardada::getNombre)
                .collect(Collectors.toList());
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
