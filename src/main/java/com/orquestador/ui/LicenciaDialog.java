package com.orquestador.ui;

import com.orquestador.servicio.LicenciaService;
import com.orquestador.util.LicenseKeyUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.time.format.DateTimeFormatter;

public class LicenciaDialog {

    private final LicenciaService servicio;

    public LicenciaDialog() {
        this.servicio = new LicenciaService();
    }

    /** Ruta del archivo que marca este equipo como equipo MAESTRO (exento de licencia). */
    private static final java.nio.file.Path MASTER_BYPASS =
        java.nio.file.Paths.get(System.getProperty("user.home"), ".orquestador_master.key");

    /** Devuelve true si este equipo está marcado como maestro (nunca pide licencia). */
    public static boolean esMaestro() {
        try {
            if (!java.nio.file.Files.exists(MASTER_BYPASS)) return false;
            String contenido = new String(java.nio.file.Files.readAllBytes(MASTER_BYPASS)).trim();
            // El contenido debe ser el HMAC del texto fijo "MASTER" con la clave secreta
            return com.orquestador.util.LicenseKeyUtil.esBypassMaestroValido(contenido);
        } catch (Exception e) { return false; }
    }

    /** Crea el archivo de bypass maestro en este equipo. */
    public static void marcarComoMaestro() throws Exception {
        String token = com.orquestador.util.LicenseKeyUtil.generarBypassMaestro();
        java.nio.file.Files.writeString(MASTER_BYPASS, token);
    }

    /**
     * Muestra una ventana BLOQUEANTE que pide activación de licencia.
     * Se debe llamar al inicio de la app si la licencia no está activa.
     *
     * @return true si el usuario activó correctamente, false si cerró sin activar (la app debe salir).
     */
    public static boolean mostrarActivacionRequerida() {
        // Equipo maestro: nunca bloquear
        if (esMaestro()) return true;

        LicenciaService svc = new LicenciaService();
        if (svc.isActivated()) return true;

        final boolean[] activado = {false};

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Activación requerida — Orquestador de Automatizaciones");
        stage.setWidth(620);
        stage.setResizable(false);

        VBox root = new VBox(14);
        root.setPadding(new Insets(22));

        // Cabecera
        Label lblTitulo = new Label("⚠  Licencia no activada");
        lblTitulo.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        lblTitulo.setStyle("-fx-text-fill: #c62828;");

        Label lblDesc = new Label(
            "Este equipo no tiene una licencia válida.\n" +
            "Comparte el Installation ID con el administrador para obtener tu clave de activación.");
        lblDesc.setWrapText(true);
        lblDesc.setStyle("-fx-font-size: 12px;");

        // Installation ID
        Label lblIdTitle = new Label("Tu Installation ID:");
        lblIdTitle.setStyle("-fx-font-weight: bold;");
        TextField txtId = new TextField(svc.getInstallationId());
        txtId.setEditable(false);
        txtId.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        Button btnCopiar = new Button("Copiar");
        btnCopiar.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(svc.getInstallationId());
            Clipboard.getSystemClipboard().setContent(cc);
        });
        HBox idBox = new HBox(8, txtId, btnCopiar);
        HBox.setHgrow(txtId, Priority.ALWAYS);

        // Campo de clave
        Label lblClaveTitle = new Label("Clave de activación:");
        lblClaveTitle.setStyle("-fx-font-weight: bold;");
        TextField txtClave = new TextField();
        txtClave.setPromptText("Pega aquí la clave proporcionada por el administrador...");
        txtClave.setStyle("-fx-font-family: monospace;");

        // Mensaje de error
        Label lblError = new Label();
        lblError.setStyle("-fx-text-fill: #c62828; -fx-font-size: 11px;");
        lblError.setWrapText(true);

        // Botones
        Button btnActivar = new Button("Activar");
        btnActivar.setStyle("-fx-background-color: #1565C0; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 18;");
        btnActivar.setDefaultButton(true);

        Button btnSalir = new Button("Salir de la aplicación");
        btnSalir.setStyle("-fx-text-fill: #c62828;");

        btnActivar.setOnAction(e -> {
            String clave = txtClave.getText().trim();
            if (clave.isEmpty()) {
                lblError.setText("Ingresa la clave de activación antes de continuar.");
                return;
            }
            if (svc.activate(clave)) {
                activado[0] = true;
                stage.close();
            } else {
                LicenseKeyUtil.ResultadoValidacion r = LicenseKeyUtil.validarClave(clave, svc.getInstallationId());
                lblError.setText(r.mensajeError != null
                    ? "Error: " + r.mensajeError
                    : "Clave inválida. Verifica la clave e intenta nuevamente.");
            }
        });

        btnSalir.setOnAction(e -> stage.close());

        HBox botones = new HBox(10, btnSalir, new javafx.scene.layout.Region(), btnActivar);
        HBox.setHgrow(botones.getChildren().get(1), Priority.ALWAYS);
        botones.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(
            lblTitulo, lblDesc,
            new Separator(),
            lblIdTitle, idBox,
            new Separator(),
            lblClaveTitle, txtClave, lblError,
            botones
        );

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.showAndWait();

        return activado[0];
    }

    public void showAndWait() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Gestion de Licencia");
        stage.setWidth(600);

        VBox root = new VBox(12);
        root.setPadding(new Insets(18));

        // Titulo
        Label titulo = new Label("Estado de Licencia");
        titulo.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        root.getChildren().add(titulo);

        // Installation ID
        Label lblIdTitle = new Label("Installation ID (comparte este codigo con el administrador para activar):");
        lblIdTitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
        TextField txtId = new TextField(servicio.getInstallationId());
        txtId.setEditable(false);
        txtId.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        Button btnCopiarId = new Button("Copiar ID");
        btnCopiarId.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(servicio.getInstallationId());
            Clipboard.getSystemClipboard().setContent(cc);
        });
        HBox idBox = new HBox(8, txtId, btnCopiarId);
        HBox.setHgrow(txtId, Priority.ALWAYS);
        root.getChildren().addAll(lblIdTitle, idBox);

        // Separador
        root.getChildren().add(new Separator());

        // Estado actual
        LicenseKeyUtil.ResultadoValidacion val = servicio.getValidacion();
        Label lblEstadoTitle = new Label("Estado actual:");
        lblEstadoTitle.setStyle("-fx-font-weight: bold;");
        Label lblEstado = new Label();
        if (val.valida) {
            String expira = val.expiracion != null
                ? val.expiracion.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "N/A";
            lblEstado.setText("ACTIVA - Valida hasta: " + expira
                + (val.nombre != null && !val.nombre.isBlank() ? "\nTitular: " + val.nombre : "")
                + (val.empresa != null && !val.empresa.isBlank() ? " / " + val.empresa : ""));
            lblEstado.setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
        } else {
            lblEstado.setText(val.mensajeError != null ? val.mensajeError : "No activada");
            lblEstado.setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
        }
        root.getChildren().addAll(lblEstadoTitle, lblEstado);

        // Separador
        root.getChildren().add(new Separator());

        // Activar con nueva clave
        Label lblClaveTitle = new Label("Activar / Renovar con una nueva clave:");
        lblClaveTitle.setStyle("-fx-font-weight: bold;");
        TextField txtClave = new TextField();
        txtClave.setPromptText("Pega aqui la clave proporcionada por el administrador...");
        Button btnActivar = new Button("Activar / Renovar");
        btnActivar.setStyle("-fx-background-color: #1565C0; -fx-text-fill: white; -fx-font-weight: bold;");
        btnActivar.setOnAction(e -> {
            String clave = txtClave.getText().trim();
            if (clave.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Ingresa una clave.", ButtonType.OK).showAndWait();
                return;
            }
            if (servicio.activate(clave)) {
                LicenseKeyUtil.ResultadoValidacion r = servicio.getValidacion();
                String expira = r.expiracion != null
                    ? r.expiracion.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "N/A";
                lblEstado.setText("ACTIVA - Valida hasta: " + expira
                    + (r.nombre != null && !r.nombre.isBlank() ? "\nTitular: " + r.nombre : ""));
                lblEstado.setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
                new Alert(Alert.AlertType.INFORMATION, "Licencia activada correctamente.", ButtonType.OK).showAndWait();
            } else {
                LicenseKeyUtil.ResultadoValidacion r = LicenseKeyUtil.validarClave(clave, servicio.getInstallationId());
                new Alert(Alert.AlertType.ERROR,
                    r.mensajeError != null ? r.mensajeError : "Clave invalida.",
                    ButtonType.OK).showAndWait();
            }
        });
        HBox claveBox = new HBox(8, txtClave, btnActivar);
        HBox.setHgrow(txtClave, Priority.ALWAYS);
        root.getChildren().addAll(lblClaveTitle, claveBox);

        // Botones inferiores
        Button btnRevocar = new Button("Eliminar licencia de este equipo");
        btnRevocar.setStyle("-fx-text-fill: #c62828;");
        btnRevocar.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Esto eliminara la licencia guardada en este equipo.\n" +
                "Necesitaras una nueva clave para volver a activar.\n\n" +
                "Confirmar?",
                ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.YES) {
                    servicio.revocarLocal();
                    lblEstado.setText("Licencia eliminada de este equipo.");
                    lblEstado.setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
                }
            });
        });

        Button btnCerrar = new Button("Cerrar");
        btnCerrar.setOnAction(e -> stage.close());

        HBox botonesInf = new HBox(10, btnRevocar, new javafx.scene.layout.Region(), btnCerrar);
        HBox.setHgrow(botonesInf.getChildren().get(1), Priority.ALWAYS);
        botonesInf.setAlignment(Pos.CENTER_RIGHT);
        root.getChildren().add(botonesInf);

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.showAndWait();
    }
}
