package com.orquestador.maestro;

import com.orquestador.modelo.LicenciaRegistro;
import com.orquestador.util.GestorLicencias;
import com.orquestador.util.LicenseKeyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Controlador principal de la App Maestra.
 * Gestiona la generación, visualización y revocación de licencias offline.
 */
public class LicenciaManagerController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final GestorLicencias gestor = new GestorLicencias();
    private final ObservableList<LicenciaRegistro> allItems = FXCollections.observableArrayList();
    private FilteredList<LicenciaRegistro> filtered;
    private TableView<LicenciaRegistro> tabla;

    // Campos del formulario
    private TextField txtInstId, txtNombre, txtEmpresa, txtRut, txtTelefono, txtEmail;
    private Spinner<Integer> spinnerDias;
    private TextArea txtNotas;
    private Label lblClaveGenerada;
    private ToggleGroup filtroGroup;

    public Node buildRoot() {
        cargarDatos();

        BorderPane root = new BorderPane();
        root.setTop(buildHeader());
        root.setCenter(buildCenter());
        root.setBottom(buildStatusBar());
        return root;
    }

    // -------------------------------------------------------------------------
    // Header
    // -------------------------------------------------------------------------
    private Node buildHeader() {
        Label titulo = new Label("App Maestra — Gestión de Licencias");
        titulo.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        titulo.setStyle("-fx-text-fill: #1565C0;");

        // Botón para marcar este equipo como Maestro (exento de chequeo de licencia)
        Button btnMaestro = new Button("⭐  Marcar este equipo como Maestro");
        btnMaestro.setStyle("-fx-background-color: #F57F17; -fx-text-fill: white; -fx-font-weight: bold;");
        btnMaestro.setTooltip(new Tooltip(
            "Marca este equipo como Maestro.\nEste equipo no requerirá licencia para abrir la app."));
        btnMaestro.setOnAction(e -> {
            try {
                if (com.orquestador.ui.LicenciaDialog.esMaestro()) {
                    Alert a = new Alert(Alert.AlertType.INFORMATION,
                        "Este equipo ya está marcado como Maestro.\nNo requiere licencia para abrir la app.",
                        ButtonType.OK);
                    a.setHeaderText("Equipo Maestro activo");
                    a.showAndWait();
                    return;
                }
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Esto marcará este equipo como Maestro.\n" +
                    "El Orquestador abrirá sin pedir licencia en este equipo.\n\nConfirmar?",
                    ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText("Marcar equipo como Maestro");
                confirm.showAndWait().ifPresent(bt -> {
                    if (bt == ButtonType.YES) {
                        try {
                            com.orquestador.ui.LicenciaDialog.marcarComoMaestro();
                            btnMaestro.setText("✅  Este equipo es Maestro");
                            btnMaestro.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold;");
                            new Alert(Alert.AlertType.INFORMATION,
                                "¡Listo! Este equipo ya no pedirá licencia al abrir el Orquestador.",
                                ButtonType.OK).showAndWait();
                        } catch (Exception ex) {
                            new Alert(Alert.AlertType.ERROR,
                                "Error al marcar el equipo: " + ex.getMessage(),
                                ButtonType.OK).showAndWait();
                        }
                    }
                });
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK).showAndWait();
            }
        });
        // Actualizar label si ya es maestro al abrir
        if (com.orquestador.ui.LicenciaDialog.esMaestro()) {
            btnMaestro.setText("✅  Este equipo es Maestro");
            btnMaestro.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold;");
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox box = new HBox(titulo, spacer, btnMaestro);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(14, 16, 10, 16));
        box.setStyle("-fx-background-color: #E3F2FD; -fx-border-color: #90CAF9; -fx-border-width: 0 0 1 0;");
        return box;
    }

    // -------------------------------------------------------------------------
    // Centro principal
    // -------------------------------------------------------------------------
    private Node buildCenter() {
        SplitPane split = new SplitPane();

        split.getItems().addAll(buildPanelLista(), buildPanelFormulario());
        split.setDividerPositions(0.55);
        return split;
    }

    // -------------------------------------------------------------------------
    // Panel izquierdo — tabla de licencias
    // -------------------------------------------------------------------------
    private Node buildPanelLista() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(12));

        // Filtros
        ToggleButton btnTodas     = new ToggleButton("Todas");
        ToggleButton btnActivas   = new ToggleButton("Activas");
        ToggleButton btnExpiradas = new ToggleButton("Expiradas");
        ToggleButton btnRevocadas = new ToggleButton("Revocadas");

        filtroGroup = new ToggleGroup();
        btnTodas.setToggleGroup(filtroGroup);
        btnActivas.setToggleGroup(filtroGroup);
        btnExpiradas.setToggleGroup(filtroGroup);
        btnRevocadas.setToggleGroup(filtroGroup);
        btnTodas.setSelected(true);

        filtroGroup.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n == null) { btnTodas.setSelected(true); return; }
            aplicarFiltro(((ToggleButton) n).getText());
        });

        HBox filtros = new HBox(6, btnTodas, btnActivas, btnExpiradas, btnRevocadas);
        filtros.setAlignment(Pos.CENTER_LEFT);

        // Tabla
        tabla = new TableView<>();
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<LicenciaRegistro, String> colNombre   = new TableColumn<>("Nombre");
        TableColumn<LicenciaRegistro, String> colEmpresa  = new TableColumn<>("Empresa");
        TableColumn<LicenciaRegistro, String> colInstId   = new TableColumn<>("Installation ID");
        TableColumn<LicenciaRegistro, String> colExpira   = new TableColumn<>("Expira");
        TableColumn<LicenciaRegistro, String> colEstado   = new TableColumn<>("Estado");

        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreDisplay()));
        colEmpresa.setCellValueFactory(c -> new SimpleStringProperty(nvl(c.getValue().getEmpresa())));
        colInstId.setCellValueFactory(c -> {
            String id = c.getValue().getInstallationId();
            return new SimpleStringProperty(id != null && id.length() > 14 ? id.substring(0, 14) + "…" : nvl(id));
        });
        colExpira.setCellValueFactory(c -> {
            LocalDate d = c.getValue().getFechaExpiracion();
            return new SimpleStringProperty(d != null ? d.format(FMT) : "—");
        });
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstadoCalculado().name()));
        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                switch (item) {
                    case "ACTIVA"    -> setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
                    case "EXPIRADA"  -> setStyle("-fx-text-fill: #e65100; -fx-font-weight: bold;");
                    case "REVOCADA"  -> setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
                    default          -> setStyle("");
                }
            }
        });

        colNombre.setPrefWidth(130);
        colEmpresa.setPrefWidth(110);
        colInstId.setPrefWidth(120);
        colExpira.setPrefWidth(80);
        colEstado.setPrefWidth(80);

        tabla.getColumns().addAll(colNombre, colEmpresa, colInstId, colExpira, colEstado);

        filtered = new FilteredList<>(allItems, r -> true);
        tabla.setItems(filtered);

        tabla.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) cargarEnFormulario(sel);
        });

        // Botones de acción sobre la selección
        Button btnRevocar  = new Button("Revocar seleccionada");
        Button btnEliminar = new Button("Eliminar seleccionada");
        Button btnRefresh  = new Button("⟳ Refrescar");

        btnRevocar.setStyle("-fx-text-fill: #e65100;");
        btnEliminar.setStyle("-fx-text-fill: #c62828;");

        btnRevocar.setOnAction(e -> revocarSeleccionada());
        btnEliminar.setOnAction(e -> eliminarSeleccionada());
        btnRefresh.setOnAction(e -> { cargarDatos(); aplicarFiltro("Todas"); });

        HBox acciones = new HBox(8, btnRevocar, btnEliminar, new Region(), btnRefresh);
        HBox.setHgrow(acciones.getChildren().get(2), Priority.ALWAYS);

        VBox.setVgrow(tabla, Priority.ALWAYS);
        panel.getChildren().addAll(filtros, tabla, acciones);
        return panel;
    }

    // -------------------------------------------------------------------------
    // Panel derecho — formulario generación de claves
    // -------------------------------------------------------------------------
    private Node buildPanelFormulario() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(12));
        panel.setStyle("-fx-background-color: #FAFAFA;");

        Label titulo = new Label("Generar nueva licencia");
        titulo.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        // Installation ID
        txtInstId = new TextField();
        txtInstId.setPromptText("Pega el Installation ID del cliente…");
        txtInstId.setStyle("-fx-font-family: monospace;");
        Button btnLimpiarId = new Button("✕");
        btnLimpiarId.setOnAction(e -> txtInstId.clear());
        HBox instIdBox = new HBox(6, txtInstId, btnLimpiarId);
        HBox.setHgrow(txtInstId, Priority.ALWAYS);

        // Días de vigencia
        spinnerDias = new Spinner<>(1, 3650, 365);
        spinnerDias.setEditable(true);
        spinnerDias.setPrefWidth(120);

        // Datos del titular
        txtNombre   = new TextField(); txtNombre.setPromptText("Nombre completo");
        txtEmpresa  = new TextField(); txtEmpresa.setPromptText("Empresa / Organización");
        txtRut      = new TextField(); txtRut.setPromptText("RUT");
        txtTelefono = new TextField(); txtTelefono.setPromptText("Teléfono");
        txtEmail    = new TextField(); txtEmail.setPromptText("E-mail");
        txtNotas    = new TextArea();  txtNotas.setPromptText("Notas internas (opcionales)");
        txtNotas.setPrefRowCount(3);

        // Botón generar
        Button btnGenerar = new Button("▶  Generar Clave");
        btnGenerar.setStyle("-fx-background-color: #1565C0; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
        btnGenerar.setMaxWidth(Double.MAX_VALUE);
        btnGenerar.setOnAction(e -> generarClave());

        // Resultado
        lblClaveGenerada = new Label("(la clave generada aparecerá aquí)");
        lblClaveGenerada.setWrapText(true);
        lblClaveGenerada.setStyle("-fx-font-family: monospace; -fx-font-size: 11px; -fx-text-fill: #444; "
                + "-fx-background-color: #F5F5F5; -fx-padding: 8; -fx-border-color: #CCC; -fx-border-radius: 4;");
        lblClaveGenerada.setMinHeight(60);
        lblClaveGenerada.setMaxWidth(Double.MAX_VALUE);

        Button btnCopiarClave = new Button("Copiar clave");
        btnCopiarClave.setMaxWidth(Double.MAX_VALUE);
        btnCopiarClave.setOnAction(e -> {
            String txt = lblClaveGenerada.getText();
            if (!txt.startsWith("(")) {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(txt);
                Clipboard.getSystemClipboard().setContent(cc);
            }
        });

        Button btnNuevo = new Button("Nuevo / Limpiar formulario");
        btnNuevo.setMaxWidth(Double.MAX_VALUE);
        btnNuevo.setOnAction(e -> limpiarFormulario());

        panel.getChildren().addAll(
            titulo,
            label("Installation ID del cliente:"), instIdBox,
            label("Días de vigencia:"), spinnerDias,
            new Separator(),
            label("Datos del titular:"),
            txtNombre, txtEmpresa, txtRut, txtTelefono, txtEmail,
            txtNotas,
            new Separator(),
            btnGenerar,
            label("Clave generada:"),
            lblClaveGenerada,
            btnCopiarClave,
            new Region(),
            btnNuevo
        );

        return new ScrollPane(panel) {{ setFitToWidth(true); }};
    }

    // -------------------------------------------------------------------------
    // Status bar
    // -------------------------------------------------------------------------
    private Node buildStatusBar() {
        Label lbl = new Label("Las licencias se guardan en: %APPDATA%\\Local\\OrquestadorMaestro\\licencias.json");
        lbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #777;");
        HBox bar = new HBox(lbl);
        bar.setPadding(new Insets(4, 10, 4, 10));
        bar.setStyle("-fx-background-color: #ECEFF1; -fx-border-color: #CFD8DC; -fx-border-width: 1 0 0 0;");
        return bar;
    }

    // -------------------------------------------------------------------------
    // Lógica de negocio
    // -------------------------------------------------------------------------
    private void cargarDatos() {
        List<LicenciaRegistro> lista = gestor.cargar();
        allItems.setAll(lista);
    }

    private void aplicarFiltro(String filtro) {
        filtered.setPredicate(r -> switch (filtro) {
            case "Activas"   -> r.getEstadoCalculado() == LicenciaRegistro.EstadoLicencia.ACTIVA;
            case "Expiradas" -> r.getEstadoCalculado() == LicenciaRegistro.EstadoLicencia.EXPIRADA;
            case "Revocadas" -> r.getEstadoCalculado() == LicenciaRegistro.EstadoLicencia.REVOCADA;
            default          -> true;
        });
    }

    private void generarClave() {
        String instId = txtInstId.getText().trim();
        if (instId.isEmpty()) {
            alerta(Alert.AlertType.WARNING, "Falta el Installation ID", "Pega el Installation ID del cliente.");
            return;
        }
        int dias = spinnerDias.getValue();
        String nombre   = txtNombre.getText().trim();
        String empresa  = txtEmpresa.getText().trim();
        String rut      = txtRut.getText().trim();
        String telefono = txtTelefono.getText().trim();
        String email    = txtEmail.getText().trim();

        try {
            String clave = LicenseKeyUtil.generarClave(instId, dias, nombre, empresa, rut, telefono, email);
            lblClaveGenerada.setText(clave);
            lblClaveGenerada.setStyle("-fx-font-family: monospace; -fx-font-size: 11px; -fx-text-fill: #1B5E20; "
                    + "-fx-background-color: #E8F5E9; -fx-padding: 8; -fx-border-color: #81C784; -fx-border-radius: 4;");

            // Guardar registro
            LicenciaRegistro reg = new LicenciaRegistro();
            reg.setId(UUID.randomUUID().toString());
            reg.setInstallationId(instId);
            reg.setClave(clave);
            reg.setNombre(nombre);
            reg.setEmpresa(empresa);
            reg.setRut(rut);
            reg.setTelefono(telefono);
            reg.setEmail(email);
            reg.setNotas(txtNotas.getText().trim());
            reg.setFechaExpiracion(LocalDate.now().plusDays(dias));
            reg.setFechaEmision(java.time.LocalDateTime.now());
            reg.setRevocada(false);

            List<LicenciaRegistro> lista = gestor.cargar();
            lista.add(reg);
            gestor.guardar(lista);
            allItems.setAll(lista);

        } catch (Exception ex) {
            alerta(Alert.AlertType.ERROR, "Error al generar clave", ex.getMessage());
        }
    }

    private void cargarEnFormulario(LicenciaRegistro r) {
        txtInstId.setText(nvl(r.getInstallationId()));
        txtNombre.setText(nvl(r.getNombre()));
        txtEmpresa.setText(nvl(r.getEmpresa()));
        txtRut.setText(nvl(r.getRut()));
        txtTelefono.setText(nvl(r.getTelefono()));
        txtEmail.setText(nvl(r.getEmail()));
        txtNotas.setText(nvl(r.getNotas()));
        if (r.getClave() != null) {
            lblClaveGenerada.setText(r.getClave());
            lblClaveGenerada.setStyle("-fx-font-family: monospace; -fx-font-size: 11px; -fx-text-fill: #444; "
                    + "-fx-background-color: #F5F5F5; -fx-padding: 8; -fx-border-color: #CCC; -fx-border-radius: 4;");
        }
        if (r.getFechaExpiracion() != null) {
            long dias = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), r.getFechaExpiracion());
            spinnerDias.getValueFactory().setValue((int) Math.max(1, dias));
        }
    }

    private void revocarSeleccionada() {
        LicenciaRegistro sel = tabla.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "¿Revocar la licencia de \"" + sel.getNombreDisplay() + "\"?\n\n"
            + "La licencia quedará marcada como REVOCADA en el registro.", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                List<LicenciaRegistro> lista = gestor.cargar();
                lista.stream().filter(r -> r.getId() != null && r.getId().equals(sel.getId()))
                     .findFirst().ifPresent(r -> r.setRevocada(true));
                gestor.guardar(lista);
                allItems.setAll(lista);
            }
        });
    }

    private void eliminarSeleccionada() {
        LicenciaRegistro sel = tabla.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "¿Eliminar permanentemente el registro de \"" + sel.getNombreDisplay() + "\"?",
            ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                List<LicenciaRegistro> lista = gestor.cargar();
                lista.removeIf(r -> r.getId() != null && r.getId().equals(sel.getId()));
                gestor.guardar(lista);
                allItems.setAll(lista);
                limpiarFormulario();
            }
        });
    }

    private void limpiarFormulario() {
        txtInstId.clear(); txtNombre.clear(); txtEmpresa.clear();
        txtRut.clear(); txtTelefono.clear(); txtEmail.clear(); txtNotas.clear();
        spinnerDias.getValueFactory().setValue(365);
        lblClaveGenerada.setText("(la clave generada aparecerá aquí)");
        lblClaveGenerada.setStyle("-fx-font-family: monospace; -fx-font-size: 11px; -fx-text-fill: #444; "
                + "-fx-background-color: #F5F5F5; -fx-padding: 8; -fx-border-color: #CCC; -fx-border-radius: 4;");
        tabla.getSelectionModel().clearSelection();
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------
    private Label label(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 11px; -fx-text-fill: #555; -fx-font-weight: bold;");
        return l;
    }

    private void alerta(Alert.AlertType type, String titulo, String mensaje) {
        Alert a = new Alert(type, mensaje, ButtonType.OK);
        a.setHeaderText(titulo);
        a.showAndWait();
    }

    private String nvl(String s) { return s != null ? s : ""; }
}
