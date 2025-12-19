package com.orquestador.servicio;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;

/**
 * Servicio mínimo para activar/validar licencias contra el servidor local.
 * Persistencia en ~/.orquestador_license.properties
 */
public class LicenciaService {

    private static final String SERVER_URL = "http://localhost:3000";
    private static final File LIC_FILE = new File(System.getProperty("user.home"), ".orquestador_license.properties");
    private static final Gson gson = new Gson();

    private String installationId;
    private String token;

    public LicenciaService() {
        load();
        if (installationId == null) {
            installationId = generateInstallationId();
            save();
        }
    }

    private String generateInstallationId() {
        return UUID.randomUUID().toString();
    }

    private void load() {
        try {
            if (!LIC_FILE.exists()) return;
            Properties p = new Properties();
            try (FileInputStream fis = new FileInputStream(LIC_FILE)) {
                p.load(fis);
            }
            installationId = p.getProperty("installationId");
            token = p.getProperty("token");
        } catch (Exception e) {
            // ignore
        }
    }

    private void save() {
        try {
            Properties p = new Properties();
            if (installationId != null) p.setProperty("installationId", installationId);
            if (token != null) p.setProperty("token", token);
            try (FileOutputStream fos = new FileOutputStream(LIC_FILE)) {
                p.store(fos, "Orquestador licencia");
            }
        } catch (Exception e) {
            // ignore
        }
    }

    public boolean isActivated() {
        return token != null && !token.trim().isEmpty();
    }

    public String getInstallationId() {
        return installationId;
    }

    public boolean activateInteractive() {
        final boolean[] result = {false};
        // Run on JavaFX thread to show dialogs
        Platform.runLater(() -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Activación requerida");
            dialog.setHeaderText("Introduce el código de activación");
            dialog.setContentText("Código:");

            dialog.showAndWait().ifPresent(code -> {
                try {
                    boolean ok = activate(code.trim());
                    if (ok) {
                        Alert a = new Alert(Alert.AlertType.INFORMATION);
                        a.setTitle("Activación correcta");
                        a.setHeaderText(null);
                        a.setContentText("La aplicación ha sido activada correctamente.");
                        a.showAndWait();
                        result[0] = true;
                    } else {
                        Alert a = new Alert(Alert.AlertType.ERROR);
                        a.setTitle("Activación fallida");
                        a.setHeaderText(null);
                        a.setContentText("Código inválido o error al activar.");
                        a.showAndWait();
                        result[0] = false;
                    }
                } catch (Exception e) {
                    Alert a = new Alert(Alert.AlertType.ERROR);
                    a.setTitle("Error");
                    a.setHeaderText(null);
                    a.setContentText("Error al comunicarse con el servidor: " + e.getMessage());
                    a.showAndWait();
                    result[0] = false;
                }
            });
        });

        // Wait until dialog handled (simple spin) — acceptable here because it's a one-time interactive flow
        while (Platform.isImplicitExit() == false && !result[0] && !isActivated()) {
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            // If user closed dialog without entering code, break to allow user to quit
            // But keep loop short — in practice user will enter code or close app
            break;
        }

        return isActivated();
    }

    public boolean activate(String code) throws Exception {
        URL url = new URL(SERVER_URL + "/activate");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        con.setDoOutput(true);

        String body = gson.toJson(new java.util.HashMap<String, String>() {{ put("code", code); put("installationId", installationId); }});
        byte[] out = body.getBytes(StandardCharsets.UTF_8);
        con.getOutputStream().write(out);

        int status = con.getResponseCode();
        if (status == 200) {
            java.io.InputStream is = con.getInputStream();
            java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
            String resp = s.hasNext() ? s.next() : "";
            java.util.Map map = gson.fromJson(resp, java.util.Map.class);
            Object t = map.get("token");
            if (t != null) {
                token = t.toString();
                save();
                return true;
            }
        }
        return false;
    }

    public boolean checkStatus() {
        try {
            URL url = new URL(SERVER_URL + "/status");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            con.setDoOutput(true);
            String body = gson.toJson(new java.util.HashMap<String, String>() {{ put("installationId", installationId); }});
            con.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            int status = con.getResponseCode();
            if (status == 200) {
                java.util.Scanner s = new java.util.Scanner(con.getInputStream()).useDelimiter("\\A");
                String resp = s.hasNext() ? s.next() : "";
                java.util.Map map = gson.fromJson(resp, java.util.Map.class);
                Object st = map.get("status");
                if ("active".equals(st)) return true;
                if ("revoked".equals(st)) {
                    token = null; save(); return false;
                }
            }
        } catch (Exception e) {
            // network error — assume offline; permit running (client handles grace period separately)
            return true;
        }
        return false;
    }
}
