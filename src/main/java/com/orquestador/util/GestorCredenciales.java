package com.orquestador.util;

import com.google.gson.*;
import com.orquestador.modelo.Credenciales;
import com.orquestador.modelo.ProyectoAutomatizacion;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

/**
 * Gestor para cargar y guardar credenciales de los proyectos especiales
 * Actualmente maneja: 15 - Contactenos BCI Seguros, 16 - Contactenos Zenit Seguros, 17 - Contactenos Corredores Generales
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
        
        // Todos los proyectos especiales usan la misma ubicación relativa del Principal.json
        rutaRelativa = "src/main/resources/testdata/Principal.json";
        
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
        // Excluir explícitamente proyectos que contienen 'vida'
        if (nombre.contains("vida")) return false;

        // Detectar proyectos especiales por palabras clave de los proyectos que todavía son especiales
        boolean esContactenos = nombre.contains("contactenos") || nombre.contains("contáctenos");
        boolean esTipoValido = nombre.contains("bci") || nombre.contains("zenit") || nombre.contains("corredores");
        return esContactenos && esTipoValido;
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
            
            // Extraer sólo usuario/contraseña (estandarizado)
            // Preferir estructura anidada utilizada por algunos proyectos: "datos1": { "user": ..., "pasword": ... }
            if (jsonObject.has("datos1") && jsonObject.get("datos1").isJsonObject()) {
                JsonObject datos1 = jsonObject.getAsJsonObject("datos1");
                if (datos1.has("user")) {
                    cred.setUser(datos1.get("user").getAsString());
                }
                if (datos1.has("pasword")) {
                    cred.setPasword(datos1.get("pasword").getAsString());
                }
                if (datos1.has("nAtencionBci")) {
                    cred.setNAtencionBci(datos1.get("nAtencionBci").getAsString());
                }
            } else {
                if (jsonObject.has("user")) {
                    cred.setUser(jsonObject.get("user").getAsString());
                }
                if (jsonObject.has("pasword")) {
                    cred.setPasword(jsonObject.get("pasword").getAsString());
                }
            }

            // Cuentas alternativas
            // Cuentas alternativas: también soportar cuando están dentro de datos2 (estructura de Corredores)
            if (jsonObject.has("user2")) {
                cred.setUser2(jsonObject.get("user2").getAsString());
            }
            if (jsonObject.has("pasword2")) {
                cred.setPasword2(jsonObject.get("pasword2").getAsString());
            }
            if (jsonObject.has("datos2") && jsonObject.get("datos2").isJsonObject()) {
                JsonObject datos2 = jsonObject.getAsJsonObject("datos2");
                if (datos2.has("user2")) {
                    cred.setUser2(datos2.get("user2").getAsString());
                }
                if (datos2.has("pasword2")) {
                    cred.setPasword2(datos2.get("pasword2").getAsString());
                }
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

        // Crear directorios si no existen
        if (!archivo.exists()) {
            archivo.getParentFile().mkdirs();
        }

        JsonObject jsonObject;

        // Si el archivo existe, leerlo; si no, crear un objeto vacío
        if (archivo.exists()) {
            try (FileReader reader = new FileReader(archivo)) {
                jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                // Si hay error leyendo, crear objeto vacío
                jsonObject = new JsonObject();
            }
        } else {
            jsonObject = new JsonObject();
        }

        // Guardar sólo campos de usuario/contraseña.
        // Para proyectos tipo BCI o Zenit preferimos siempre usar el objeto "datos1" (crear si es necesario),
        // así replicamos la lógica del proyecto 15 para el proyecto 16.
        String nombreProyecto = proyecto.getNombre() != null ? proyecto.getNombre().toLowerCase() : "";
        boolean usarDatos1 = nombreProyecto.contains("bci") || nombreProyecto.contains("zenit");

        if (usarDatos1) {
            // Solo actualizar campos existentes dentro de datos1. No crear campos nuevos.
            if (jsonObject.has("datos1") && jsonObject.get("datos1").isJsonObject()) {
                JsonObject datos1 = jsonObject.getAsJsonObject("datos1");
                if (cred.getUser() != null && datos1.has("user")) {
                    datos1.addProperty("user", cred.getUser());
                }
                if (cred.getPasword() != null && datos1.has("pasword")) {
                    datos1.addProperty("pasword", cred.getPasword());
                }
                // dejar intactos otros campos dentro de datos1
            } else {
                // datos1 no existe: no crear ni escribir nada para respetar la regla de "solo actualizar"
            }
        } else {
            // Mantener compatibilidad: actualizar en la raíz solo si las propiedades ya existen
            if (cred.getUser() != null && jsonObject.has("user")) {
                jsonObject.addProperty("user", cred.getUser());
            }
            if (cred.getPasword() != null && jsonObject.has("pasword")) {
                jsonObject.addProperty("pasword", cred.getPasword());
            }
        }

        // Cuentas alternativas: para proyectos 'corredores' actualizamos dentro de datos2 si existe
        String nombreProyectoLower = proyecto.getNombre() != null ? proyecto.getNombre().toLowerCase() : "";
        boolean esCorredores = nombreProyectoLower.contains("corredores");
        if (esCorredores) {
            if (jsonObject.has("datos2") && jsonObject.get("datos2").isJsonObject()) {
                JsonObject datos2 = jsonObject.getAsJsonObject("datos2");
                if (cred.getUser2() != null && datos2.has("user2")) datos2.addProperty("user2", cred.getUser2());
                if (cred.getPasword2() != null && datos2.has("pasword2")) datos2.addProperty("pasword2", cred.getPasword2());
            }
        } else {
            // Mantener comportamiento anterior para otros proyectos: actualizar en la raíz solo si las propiedades ya existen
            String[] camposEsperados = obtenerCamposEsperados(proyecto);
            boolean esperaUser2 = Arrays.asList(camposEsperados).contains("user2");
            if (esperaUser2) {
                if (cred.getUser2() != null && jsonObject.has("user2")) jsonObject.addProperty("user2", cred.getUser2());
                if (cred.getPasword2() != null && jsonObject.has("pasword2")) jsonObject.addProperty("pasword2", cred.getPasword2());
            }
        }

        // Escribir JSON actualizado
        try (FileWriter writer = new FileWriter(archivo)) {
            writer.write(gson.toJson(jsonObject));
            writer.flush();
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
            return new String[]{"user", "pasword"};
        } else if (nombre.contains("corredores")) {
            // No usar cuentas alternativas por defecto: devolver user/pasword
            return new String[]{"user", "pasword"};
        } else {
            // BCI por defecto
            return new String[]{"user", "pasword"};
        }
    }
}
