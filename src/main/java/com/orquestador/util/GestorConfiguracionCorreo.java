package com.orquestador.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.orquestador.modelo.ConfiguracionCorreo;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persiste las configuraciones de correo por área en un archivo JSON dentro de AppData.
 */
public class GestorConfiguracionCorreo {

    private static final Path CONFIG_FILE = Paths.get(
            System.getProperty("user.home"),
            "AppData", "Local", "OrquestadorAutomatizaciones",
            "configuracion_correo.json"
    );

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── Carga ─────────────────────────────────────────────────────────────────

    public static List<ConfiguracionCorreo> cargar() {
        if (!Files.exists(CONFIG_FILE)) return new ArrayList<>();
        try (Reader reader = new InputStreamReader(
                new FileInputStream(CONFIG_FILE.toFile()), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<ConfiguracionCorreo>>() {}.getType();
            List<ConfiguracionCorreo> lista = GSON.fromJson(reader, listType);
            return lista != null ? lista : new ArrayList<>();
        } catch (Exception e) {
            System.err.println("[GestorConfiguracionCorreo] Error al cargar: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // ── Guardado ──────────────────────────────────────────────────────────────

    public static void guardar(List<ConfiguracionCorreo> configs) {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(CONFIG_FILE.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(configs, writer);
            }
        } catch (Exception e) {
            System.err.println("[GestorConfiguracionCorreo] Error al guardar: " + e.getMessage());
        }
    }

    // ── Búsqueda ──────────────────────────────────────────────────────────────

    /** Devuelve la configuración para un área específica (case-insensitive). */
    public static Optional<ConfiguracionCorreo> buscarPorArea(List<ConfiguracionCorreo> configs, String area) {
        if (area == null) return Optional.empty();
        return configs.stream()
                .filter(c -> area.equalsIgnoreCase(c.getArea()))
                .findFirst();
    }
}
