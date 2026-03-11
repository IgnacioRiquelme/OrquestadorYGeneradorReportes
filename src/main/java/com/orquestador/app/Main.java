package com.orquestador.app;

import com.orquestador.ui.ControladorPrincipal;
import com.orquestador.ui.LicenciaDialog;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/**
 * Clase principal de la aplicacion
 */
public class Main extends Application {

    private ControladorPrincipal controlador;
    private static final Path CRASH_LOG = Paths.get(System.getProperty("user.home"), "orquestador_crash.log");

    @Override
    public void start(Stage primaryStage) {
        try {
            Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> {
                registrarErrorFatal("Excepción no controlada en JavaFX", throwable);
                mostrarAlertaFatal("La aplicación encontró un error inesperado y se cerrará.");
            });

            // ── Verificar licencia antes de mostrar la aplicación ──────────────
            boolean licenciaOk = LicenciaDialog.mostrarActivacionRequerida();
            if (!licenciaOk) {
                Platform.exit();
                return;
            }
            // ───────────────────────────────────────────────────────────────────

            controlador = new ControladorPrincipal();

            // Detectar resolución real del monitor donde se ejecuta la app
            Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
            double screenW = screenBounds.getWidth();
            double screenH = screenBounds.getHeight();

            // Tamaño inicial: 90% del monitor, mínimo 1024x640, máximo 1600x900
            double initW = Math.min(Math.max(screenW * 0.90, 1024), 1600);
            double initH = Math.min(Math.max(screenH * 0.90, 640), 900);

            // Mínimos: 60% del monitor pero no menos de 900x580
            double minW = Math.max(screenW * 0.60, 900);
            double minH = Math.max(screenH * 0.60, 580);

            Scene scene = new Scene(controlador.getRoot(), initW, initH);

            primaryStage.setTitle("Orquestador de Automatizaciones BCI Seguros");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(minW);
            primaryStage.setMinHeight(minH);

            // Centrar en el monitor al arrancar
            primaryStage.setX(screenBounds.getMinX() + (screenW - initW) / 2);
            primaryStage.setY(screenBounds.getMinY() + (screenH - initH) / 2);

            // Detener automatización al cerrar
            primaryStage.setOnCloseRequest(e -> {
                if (controlador != null) {
                    controlador.detenerAutomatizacionAlCerrar();
                }
            });

            primaryStage.show();

        } catch (Exception e) {
            registrarErrorFatal("Error durante el inicio de la aplicación", e);
            mostrarAlertaFatal("No se pudo iniciar el orquestador. Revisa el archivo de log de errores en tu carpeta de usuario.");
            Platform.exit();
        }
    }

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            registrarErrorFatal("Excepción no controlada en hilo " + thread.getName(), throwable);
        });
        launch(args);
    }

    private static void registrarErrorFatal(String mensaje, Throwable error) {
        try {
            StringWriter sw = new StringWriter();
            error.printStackTrace(new PrintWriter(sw));
            String contenido = "[" + LocalDateTime.now() + "] " + mensaje + System.lineSeparator()
                    + sw + System.lineSeparator();
            Files.writeString(CRASH_LOG, contenido,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    private static void mostrarAlertaFatal(String mensaje) {
        try {
            Platform.runLater(() -> {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                alert.setTitle("Error inesperado");
                alert.setHeaderText("La aplicación se cerró por un error");
                alert.setContentText(mensaje + "\n\nLog: " + CRASH_LOG.toString());
                alert.showAndWait();
            });
        } catch (Exception ignored) {
        }
    }
}
