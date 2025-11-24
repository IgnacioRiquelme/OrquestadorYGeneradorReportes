package com.orquestador.util;

import com.google.gson.*;
import com.orquestador.modelo.Credenciales;
import com.orquestador.modelo.ProyectoAutomatizacion;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Gestor para cargar y guardar credenciales de los 4 proyectos especiales
 * 15 - Contactenos BCI Seguros
 * 16 - Contactenos Zenit Seguros
 * 17 - Contactenos Corredores Generales
 * 18 - Contactenos Corredores VIDA
 */
public class GestorCredenciales {
    
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    /**
     * Obtiene la ruta del archivo Principal.json para cada proyecto
     */
    private static String obtenerRutaPrincipalJson(ProyectoAutomatizacion proyecto) {
        String ruta = proyecto.getRuta();
        String nombre = proyecto.getNombre().toLowerCase();
        
        // Determinar la ruta relativa según el nombre del proyecto
        String rutaRelativa = null;
        
        if (nombre.contains("bci") && nombre.contains("zenit")) {
            // Proyecto 16 - Zenit
            rutaRelativa = "src/main/resources/testdata/Principal.json";
        } else if (nombre.contains("bci") && nombre.contains("vida")) {
            // Proyecto 18 - Corredores VIDA
            rutaRelativa = "src/main/resources/testdata/Principal.json";
        } else if (nombre.contains("corredores") && !nombre.contains("vida")) {
            // Proyecto 17 - Corredores Generales
            rutaRelativa = "src/main/resources/testdata/Principal.json";
        } else {
            // Proyecto 15 - BCI Seguros (predeterminado)
            rutaRelativa = "src/main/resources/testdata/Principal.json";
        }
        
        // Normalizar separadores
        ruta = ruta.replace('/', '\\');
        if (!ruta.endsWith("\\")) {
            ruta += "\\";
        }
        
        String pathFull = ruta + rutaRelativa.replace('/', '\\');
        return pathFull;
    }
    
    /**
     * Verifica si un proyecto es uno de los 4 especiales
     */
    public static boolean esProyectoEspecial(ProyectoAutomatizacion proyecto) {
        if (proyecto == null) return false;
        String nombre = proyecto.getNombre().toLowerCase();
        return nombre.contains("contactenos") || nombre.contains("contáctenos");
    }
    
    /**
     * Carga credenciales desde el archivo Principal.json del proyecto
     */
    public static Credenciales cargarCredenciales(ProyectoAutomatizacion proyecto) throws IOException {
        if (!esProyectoEspecial(proyecto)) {
            return new Credenciales();
        }
        
        String ruta = obtenerRutaPrincipalJson(proyecto);
        File archivo = new File(ruta);
        
        if (!archivo.exists()) {
            return new Credenciales();
        }
        
        try (FileReader reader = new FileReader(archivo)) {
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            Credenciales cred = new Credenciales();
            
            // Extraer credenciales si existen
            if (jsonObject.has("user")) {
                cred.setUser(jsonObject.get("user").getAsString());
            }
            if (jsonObject.has("pasword")) {
                cred.setPasword(jsonObject.get("pasword").getAsString());
            }
            if (jsonObject.has("nAtencionBci")) {
                cred.setNAtencionBci(jsonObject.get("nAtencionBci").getAsString());
            }
            if (jsonObject.has("nAtencionZenit")) {
                cred.setNAtencionZenit(jsonObject.get("nAtencionZenit").getAsString());
            }
            if (jsonObject.has("user2")) {
                cred.setUser2(jsonObject.get("user2").getAsString());
            }
            if (jsonObject.has("pasword2")) {
                cred.setPasword2(jsonObject.get("pasword2").getAsString());
            }
            if (jsonObject.has("numeroTicket")) {
                cred.setNumeroTicket(jsonObject.get("numeroTicket").getAsString());
            }
            if (jsonObject.has("rutaImagenSolicitud")) {
                cred.setRutaImagenSolicitud(jsonObject.get("rutaImagenSolicitud").getAsString());
            }
            if (jsonObject.has("rutaImagenCorreo")) {
                cred.setRutaImagenCorreo(jsonObject.get("rutaImagenCorreo").getAsString());
            }
            
            return cred;
        } catch (Exception e) {
            System.err.println("Error cargando credenciales: " + e.getMessage());
            return new Credenciales();
        }
    }
    
    /**
     * Guarda credenciales en el archivo Principal.json del proyecto
     */
    public static void guardarCredenciales(ProyectoAutomatizacion proyecto, Credenciales cred) throws IOException {
        if (!esProyectoEspecial(proyecto)) {
            return;
        }
        
        String ruta = obtenerRutaPrincipalJson(proyecto);
        File archivo = new File(ruta);
        
        if (!archivo.exists()) {
            archivo.getParentFile().mkdirs();
        }
        
        try (FileReader reader = new FileReader(archivo)) {
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            
            // Actualizar campos de credenciales (solo si no están vacíos)
            if (!cred.getUser().isEmpty()) {
                jsonObject.addProperty("user", cred.getUser());
            }
            if (!cred.getPasword().isEmpty()) {
                jsonObject.addProperty("pasword", cred.getPasword());
            }
            if (!cred.getNAtencionBci().isEmpty()) {
                jsonObject.addProperty("nAtencionBci", cred.getNAtencionBci());
            }
            if (!cred.getNAtencionZenit().isEmpty()) {
                jsonObject.addProperty("nAtencionZenit", cred.getNAtencionZenit());
            }
            if (!cred.getUser2().isEmpty()) {
                jsonObject.addProperty("user2", cred.getUser2());
            }
            if (!cred.getPasword2().isEmpty()) {
                jsonObject.addProperty("pasword2", cred.getPasword2());
            }
            if (!cred.getNumeroTicket().isEmpty()) {
                jsonObject.addProperty("numeroTicket", cred.getNumeroTicket());
            }
            if (!cred.getRutaImagenSolicitud().isEmpty()) {
                jsonObject.addProperty("rutaImagenSolicitud", cred.getRutaImagenSolicitud());
            }
            if (!cred.getRutaImagenCorreo().isEmpty()) {
                jsonObject.addProperty("rutaImagenCorreo", cred.getRutaImagenCorreo());
            }
            
            // Escribir JSON actualizado
            try (FileWriter writer = new FileWriter(archivo)) {
                writer.write(gson.toJson(jsonObject));
                writer.flush();
            }
        } catch (Exception e) {
            System.err.println("Error guardando credenciales: " + e.getMessage());
            throw new IOException("No se pudieron guardar las credenciales", e);
        }
    }
    
    /**
     * Obtiene una descripción de qué campos espera cada proyecto
     */
    public static String[] obtenerCamposEsperados(ProyectoAutomatizacion proyecto) {
        String nombre = proyecto.getNombre().toLowerCase();
        
        if (nombre.contains("zenit")) {
            return new String[]{"user", "pasword", "nAtencionZenit", "rutaImagenSolicitud", "rutaImagenCorreo"};
        } else if (nombre.contains("vida")) {
            return new String[]{"numeroTicket", "rutaImagenSolicitud", "rutaImagenCorreo"};
        } else if (nombre.contains("corredores") && !nombre.contains("vida")) {
            return new String[]{"user2", "pasword2", "rutaImagenCorreo"};
        } else {
            // BCI por defecto
            return new String[]{"user", "pasword", "nAtencionBci", "rutaImagenSolicitud", "rutaImagenCorreo"};
        }
    }
}
