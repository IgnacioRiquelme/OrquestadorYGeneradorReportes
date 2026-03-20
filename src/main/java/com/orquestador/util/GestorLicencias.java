package com.orquestador.util;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.orquestador.modelo.LicenciaRegistro;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Persiste el historial de licencias emitidas (solo en la app maestra).
 * Archivo: %USERPROFILE%\AppData\Local\OrquestadorMaestro\licencias.json
 */
public class GestorLicencias {

    private static final Path STORAGE = Paths.get(
            System.getProperty("user.home"),
            "AppData", "Local", "OrquestadorMaestro", "licencias.json");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDate.class,
                (JsonSerializer<LocalDate>) (src, t, ctx) ->
                    new JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE)))
            .registerTypeAdapter(LocalDate.class,
                (JsonDeserializer<LocalDate>) (json, t, ctx) ->
                    LocalDate.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE))
            .registerTypeAdapter(LocalDateTime.class,
                (JsonSerializer<LocalDateTime>) (src, t, ctx) ->
                    new JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
            .registerTypeAdapter(LocalDateTime.class,
                (JsonDeserializer<LocalDateTime>) (json, t, ctx) ->
                    LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .create();

    public static List<LicenciaRegistro> cargar() {
        if (!Files.exists(STORAGE)) return new ArrayList<>();
        try (Reader r = new InputStreamReader(
                new FileInputStream(STORAGE.toFile()), StandardCharsets.UTF_8)) {
            Type t = new TypeToken<List<LicenciaRegistro>>(){}.getType();
            List<LicenciaRegistro> lista = GSON.fromJson(r, t);
            return lista != null ? lista : new ArrayList<>();
        } catch (Exception e) {
            System.err.println("[GestorLicencias] Error al cargar: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static void guardar(List<LicenciaRegistro> lista) {
        try {
            Files.createDirectories(STORAGE.getParent());
            try (Writer w = new OutputStreamWriter(
                    new FileOutputStream(STORAGE.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(lista, w);
            }
        } catch (Exception e) {
            System.err.println("[GestorLicencias] Error al guardar: " + e.getMessage());
        }
    }
}
