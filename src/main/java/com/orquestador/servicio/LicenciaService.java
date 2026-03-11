package com.orquestador.servicio;

import com.orquestador.util.LicenseKeyUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;

/**
 * Servicio de licencias completamente OFFLINE.
 * Valida claves firmadas con HMAC-SHA256 generadas por la App Maestra.
 * No requiere conexion a ningun servidor.
 *
 * Persistencia: %USERPROFILE%\.orquestador_license.properties
 *   - installationId  (derivado del hardware -- reproducible)
 *   - licenseKey      (clave emitida por el administrador)
 */
public class LicenciaService {

    private static final Path LIC_FILE = Paths.get(
            System.getProperty("user.home"), ".orquestador_license.properties");

    private String installationId;
    private String licenseKey;

    public LicenciaService() {
        cargar();
        if (installationId == null || installationId.isBlank()) {
            installationId = LicenseKeyUtil.generarInstallationId();
            guardar();
        }
    }

    // Estado
    public boolean isActivated() {
        if (licenseKey == null || licenseKey.isBlank()) return false;
        return LicenseKeyUtil.validarClave(licenseKey, installationId).valida;
    }

    public LicenseKeyUtil.ResultadoValidacion getValidacion() {
        if (licenseKey == null || licenseKey.isBlank()) {
            return new LicenseKeyUtil.ResultadoValidacion(
                false, "No hay clave de licencia registrada", null, null, null);
        }
        return LicenseKeyUtil.validarClave(licenseKey, installationId);
    }

    public String getInstallationId() { return installationId; }
    public String getLicenseKey()     { return licenseKey; }

    // Activacion
    public boolean activate(String clave) {
        if (clave == null || clave.isBlank()) return false;
        LicenseKeyUtil.ResultadoValidacion r = LicenseKeyUtil.validarClave(clave.trim(), installationId);
        if (r.valida) {
            licenseKey = clave.trim();
            guardar();
            return true;
        }
        return false;
    }

    public boolean activateInteractive() {
        final boolean[] resultado = {false};

        Platform.runLater(() -> {
            Alert infoId = new Alert(Alert.AlertType.INFORMATION);
            infoId.setTitle("Activacion requerida");
            infoId.setHeaderText("Para activar, envia el siguiente Installation ID al administrador:");
            infoId.setContentText(installationId);
            infoId.showAndWait();

            TextInputDialog dlg = new TextInputDialog();
            dlg.setTitle("Activar licencia");
            dlg.setHeaderText("Ingresa la clave de activacion proporcionada por el administrador:");
            dlg.setContentText("Clave:");
            dlg.getEditor().setPrefColumnCount(50);

            dlg.showAndWait().ifPresent(clave -> {
                if (activate(clave)) {
                    LicenseKeyUtil.ResultadoValidacion r = getValidacion();
                    String expira = r.expiracion != null
                        ? r.expiracion.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "N/A";
                    Alert ok = new Alert(Alert.AlertType.INFORMATION);
                    ok.setTitle("Activada correctamente");
                    ok.setHeaderText("Licencia activada");
                    ok.setContentText("Valida hasta: " + expira
                        + (r.nombre != null ? "\nTitular: " + r.nombre : ""));
                    ok.showAndWait();
                    resultado[0] = true;
                } else {
                    LicenseKeyUtil.ResultadoValidacion r =
                        LicenseKeyUtil.validarClave(clave, installationId);
                    Alert err = new Alert(Alert.AlertType.ERROR);
                    err.setTitle("Activacion fallida");
                    err.setHeaderText("La clave no es valida");
                    err.setContentText(r.mensajeError != null ? r.mensajeError : "Verifica la clave.");
                    err.showAndWait();
                }
            });
        });

        long deadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < deadline) {
            try { Thread.sleep(300); } catch (InterruptedException e) { break; }
            if (isActivated()) return true;
        }
        return isActivated();
    }

    public void revocarLocal() {
        licenseKey = null;
        guardar();
    }

    private void cargar() {
        try {
            if (!Files.exists(LIC_FILE)) return;
            Properties p = new Properties();
            try (InputStream is = new FileInputStream(LIC_FILE.toFile())) {
                p.load(is);
            }
            installationId = p.getProperty("installationId");
            licenseKey     = p.getProperty("licenseKey");
        } catch (Exception e) {
            System.err.println("[LicenciaService] Error al cargar: " + e.getMessage());
        }
    }

    private void guardar() {
        try {
            Properties p = new Properties();
            if (installationId != null) p.setProperty("installationId", installationId);
            if (licenseKey     != null) p.setProperty("licenseKey",     licenseKey);
            try (OutputStream os = new FileOutputStream(LIC_FILE.toFile())) {
                p.store(os, "Orquestador Automatizaciones - Licencia");
            }
        } catch (Exception e) {
            System.err.println("[LicenciaService] Error al guardar: " + e.getMessage());
        }
    }

    // Compatibilidad con llamadas existentes
    public boolean checkStatus() { return isActivated(); }
}
