package com.orquestador.servicio;

import com.orquestador.modelo.TareaProgramada;
import com.orquestador.modelo.ProyectoAutomatizacion;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Gestor sencillo de tareas programadas. Persiste en JSON en el home del usuario.
 */
public class ProgramadorTareas {
    private static final File STORAGE = new File(System.getProperty("user.home"), ".orquestador_tasks.json");
    private final List<TareaProgramada> tareas = new ArrayList<>();
    private final Gson gson = new GsonBuilder().registerTypeAdapter(LocalDateTime.class, new com.google.gson.JsonDeserializer<LocalDateTime>() {
        @Override
        public LocalDateTime deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type typeOfT, com.google.gson.JsonDeserializationContext context) {
            return LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }).registerTypeAdapter(LocalDateTime.class, new com.google.gson.JsonSerializer<LocalDateTime>() {
        @Override
        public com.google.gson.JsonElement serialize(LocalDateTime src, java.lang.reflect.Type typeOfSrc, com.google.gson.JsonSerializationContext context) {
            return new com.google.gson.JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
    }).setPrettyPrinting().create();

    private java.util.function.Consumer<com.orquestador.modelo.TareaProgramada> ejecucionHandler; // recibe la tarea completa

    public ProgramadorTareas() {
        cargar();
        iniciarChequeoPeriódico();
    }

    public void setEjecucionHandler(java.util.function.Consumer<com.orquestador.modelo.TareaProgramada> handler) {
        this.ejecucionHandler = handler;
    }

    public synchronized List<TareaProgramada> listarTareas() {
        return new ArrayList<>(tareas);
    }

    public synchronized void agregarTarea(TareaProgramada tarea) {
        tareas.add(tarea);
        guardar();
    }

    public synchronized void eliminarTarea(String id) {
        Iterator<TareaProgramada> it = tareas.iterator();
        while (it.hasNext()) {
            TareaProgramada t = it.next();
            if (t.getId().equals(id)) {
                it.remove();
                guardar();
                break;
            }
        }
    }

    private void cargar() {
        try {
            if (!STORAGE.exists()) return;
            Type listType = new TypeToken<List<TareaProgramada>>(){}.getType();
            try (FileReader fr = new FileReader(STORAGE)) {
                List<TareaProgramada> loaded = gson.fromJson(fr, listType);
                if (loaded != null) tareas.addAll(loaded);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void guardar() {
        try (FileWriter fw = new FileWriter(STORAGE)) {
            gson.toJson(tareas, fw);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void iniciarChequeoPeriódico() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(30_000); // cada 30s
                } catch (InterruptedException e) { break; }

                List<TareaProgramada> aEjecutar = new ArrayList<>();
                synchronized (this) {
                    LocalDateTime ahora = LocalDateTime.now();
                    for (TareaProgramada tarea : tareas) {
                        if (!tarea.isEjecutada() && !tarea.getFechaHora().isAfter(ahora)) {
                            aEjecutar.add(tarea);
                        }
                    }
                }

                for (TareaProgramada tarea : aEjecutar) {
                    // marcar como ejecutada
                    tarea.setEjecutada(true);
                    guardar();
                    if (ejecucionHandler != null) {
                        Platform.runLater(() -> ejecucionHandler.accept(tarea));
                    }
                }
            }
        }, "ProgramadorTareas-Chequeo");
        t.setDaemon(true);
        t.start();
    }
}
