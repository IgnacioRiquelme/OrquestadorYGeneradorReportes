package com.orquestador.ui;

import com.orquestador.modelo.ProyectoAutomatizacion;
import com.orquestador.modelo.ProyectoAutomatizacion.*;
import com.orquestador.modelo.Proyecto;
import com.orquestador.servicio.EjecutorAutomatizaciones;
import com.orquestador.servicio.GeneradorDocumentos;
import com.orquestador.util.GestorConfiguracion;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controlador principal de la interfaz
 */
public class ControladorPrincipal {
    
    private BorderPane root;
    private TableView<ProyectoAutomatizacion> tablaProyectos;
    private ObservableList<ProyectoAutomatizacion> proyectos;
    private TextArea logArea;
    private Label lblEstadisticas;
    private Button btnEjecutarSeleccionados, btnEjecutarPorArea, btnDetener, btnVerCapturas, btnGenerarInformes, btnAgregar, btnEditar, btnEliminar;
    private ComboBox<String> cboFiltroArea;
    private EjecutorAutomatizaciones ejecutor;
    private boolean ejecutando = false;
    
    public ControladorPrincipal() {
        ejecutor = new EjecutorAutomatizaciones();
        proyectos = FXCollections.observableArrayList(GestorConfiguracion.cargarProyectos());
        inicializarUI();
    }
    
    private void inicializarUI() {
        root = new BorderPane();
        root.setPadding(new Insets(15));
        
        // Header
        VBox header = crearHeader();
        root.setTop(header);
        
        // Centro: Tabla + Log
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        splitPane.setDividerPositions(0.6);
        
        VBox tablaContainer = crearSeccionTabla();
        VBox logContainer = crearSeccionLog();
        
        splitPane.getItems().addAll(tablaContainer, logContainer);
        root.setCenter(splitPane);
        
        // Footer: Estadsticas
        HBox footer = crearFooter();
        root.setBottom(footer);
        
        actualizarEstadisticas();
    }
    
    private VBox crearHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(0, 0, 15, 0));
        
        // Ttulo
        Label titulo = new Label(" Orquestador de Automatizaciones");
        titulo.setFont(Font.font("System", FontWeight.BOLD, 24));
        
        // Botones de accin
        HBox botonesAccion = new HBox(10);
        botonesAccion.setAlignment(Pos.CENTER_LEFT);
        
        btnAgregar = new Button(" Agregar Proyecto");
        btnAgregar.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        btnAgregar.setOnAction(e -> agregarProyecto());
        
        btnEditar = new Button(" Editar Proyecto");
        btnEditar.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        btnEditar.setOnAction(e -> editarProyecto());
        
        btnEliminar = new Button(" Eliminar Seleccionados");
        btnEliminar.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold;");
        btnEliminar.setOnAction(e -> eliminarSeleccionados());
        
        cboFiltroArea = new ComboBox<>();
        cboFiltroArea.setPromptText("Filtrar por Area");
        cboFiltroArea.setEditable(false);
        cboFiltroArea.getItems().addAll("Todas", "Clientes", "Comercial", "Integraciones", "Siniestros");
        cboFiltroArea.setValue("Todas");
        cboFiltroArea.setOnAction(e -> aplicarFiltro());
        
        Button btnRefrescar = new Button(" Refrescar");
        btnRefrescar.setOnAction(e -> refrescarTabla());
        
        botonesAccion.getChildren().addAll(btnAgregar, btnEditar, btnEliminar, new Separator(javafx.geometry.Orientation.VERTICAL), 
                                           new Label("Area:"), cboFiltroArea, btnRefrescar);
        
        // Botones de ejecucin
        HBox botonesEjecucion = new HBox(10);
        botonesEjecucion.setAlignment(Pos.CENTER_LEFT);
        
        btnEjecutarSeleccionados = new Button(" Ejecutar Seleccionados");
        btnEjecutarSeleccionados.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        btnEjecutarSeleccionados.setOnAction(e -> ejecutarSeleccionados());
        
        btnEjecutarPorArea = new Button(" Ejecutar por Area");
        btnEjecutarPorArea.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        btnEjecutarPorArea.setOnAction(e -> ejecutarPorArea());
        
        btnDetener = new Button(" Detener");
        btnDetener.setStyle("-fx-background-color: #9E9E9E; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        btnDetener.setDisable(true);
        btnDetener.setOnAction(e -> detenerEjecucion());
        
        btnVerCapturas = new Button(" Ver Capturas");
        btnVerCapturas.setStyle("-fx-background-color: #9C27B0; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        btnVerCapturas.setOnAction(e -> mostrarCapturas());
        
        btnGenerarInformes = new Button(" Generar Informes");
        btnGenerarInformes.setStyle("-fx-background-color: #FF6F00; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        btnGenerarInformes.setOnAction(e -> generarInformes());
        
        botonesEjecucion.getChildren().addAll(btnEjecutarSeleccionados, btnEjecutarPorArea, btnDetener, btnVerCapturas, btnGenerarInformes);
        
        header.getChildren().addAll(titulo, botonesAccion, botonesEjecucion);
        return header;
    }
    
    @SuppressWarnings("unchecked")
    private VBox crearSeccionTabla() {
        VBox container = new VBox(5);
        container.setPadding(new Insets(5));
        
        Label lblTabla = new Label(" Proyectos de Automatizacion");
        lblTabla.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        tablaProyectos = new TableView<>();
        tablaProyectos.setEditable(true);
        tablaProyectos.setItems(proyectos);
        
        // Columna Seleccionar
        TableColumn<ProyectoAutomatizacion, Boolean> colSeleccionar = new TableColumn<>("");
        
        // Checkbox en header para seleccionar/deseleccionar todos
        CheckBox headerCheckBox = new CheckBox();
        headerCheckBox.setOnAction(e -> {
            boolean selected = headerCheckBox.isSelected();
            for (ProyectoAutomatizacion p : proyectos) {
                p.setSeleccionado(selected);
            }
            tablaProyectos.refresh();
            guardarProyectos();
        });
        colSeleccionar.setGraphic(headerCheckBox);
        
        colSeleccionar.setCellValueFactory(cellData -> {
            ProyectoAutomatizacion proyecto = cellData.getValue();
            javafx.beans.property.SimpleBooleanProperty prop = new javafx.beans.property.SimpleBooleanProperty(proyecto.isSeleccionado());
            prop.addListener((obs, oldVal, newVal) -> {
                proyecto.setSeleccionado(newVal);
                guardarProyectos();
            });
            return prop;
        });
        colSeleccionar.setCellFactory(CheckBoxTableCell.forTableColumn(colSeleccionar));
        colSeleccionar.setEditable(true);
        colSeleccionar.setMinWidth(40);
        colSeleccionar.setMaxWidth(40);
        
        // Columna Nombre
        TableColumn<ProyectoAutomatizacion, String> colNombre = new TableColumn<>("Nombre");
        colNombre.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getNombre()));
        colNombre.setCellFactory(TextFieldTableCell.forTableColumn());
        colNombre.setOnEditCommit(e -> {
            e.getRowValue().setNombre(e.getNewValue());
            guardarProyectos();
        });
        colNombre.setMinWidth(200);
        
        // Columna Ruta (Editable)
        TableColumn<ProyectoAutomatizacion, String> colRuta = new TableColumn<>("Ruta");
        colRuta.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getRuta()));
        colRuta.setCellFactory(TextFieldTableCell.forTableColumn());
        colRuta.setOnEditCommit(e -> {
            e.getRowValue().setRuta(e.getNewValue());
            guardarProyectos();
        });
        colRuta.setMinWidth(300);
        
        // Columna Area
        TableColumn<ProyectoAutomatizacion, String> colArea = new TableColumn<>("Area");
        colArea.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getArea()));
        colArea.setCellFactory(ComboBoxTableCell.forTableColumn("Clientes", "Comercial", "Integraciones", "Siniestros"));
        colArea.setOnEditCommit(e -> {
            e.getRowValue().setArea(e.getNewValue());
            guardarProyectos();
        });
        colArea.setMinWidth(120);
        
        // Columna Tipo VPN
        TableColumn<ProyectoAutomatizacion, TipoVPN> colVPN = new TableColumn<>("VPN");
        colVPN.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getTipoVPN()));
        colVPN.setCellFactory(ComboBoxTableCell.forTableColumn(TipoVPN.values()));
        colVPN.setOnEditCommit(e -> {
            e.getRowValue().setTipoVPN(e.getNewValue());
            guardarProyectos();
        });
        colVPN.setMinWidth(100);
        
        // Columna Tipo Ejecucin
        TableColumn<ProyectoAutomatizacion, TipoEjecucion> colTipo = new TableColumn<>("Tipo");
        colTipo.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getTipoEjecucion()));
        colTipo.setCellFactory(ComboBoxTableCell.forTableColumn(TipoEjecucion.values()));
        colTipo.setOnEditCommit(e -> {
            e.getRowValue().setTipoEjecucion(e.getNewValue());
            guardarProyectos();
        });
        colTipo.setMinWidth(120);
        
        // Columna Estado
        TableColumn<ProyectoAutomatizacion, String> colEstado = new TableColumn<>("Estado");
        colEstado.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getEstado().getDescripcion()));
        colEstado.setMinWidth(120);
        
        // Columna Ultima Ejecucion
        TableColumn<ProyectoAutomatizacion, String> colUltima = new TableColumn<>("Ultima Ejecucion");
        colUltima.setCellValueFactory(cellData -> {
            if (cellData.getValue().getUltimaEjecucion() != null) {
                String fecha = cellData.getValue().getUltimaEjecucion().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
                return new javafx.beans.property.SimpleStringProperty(fecha);
            }
            return new javafx.beans.property.SimpleStringProperty("-");
        });
        colUltima.setMinWidth(150);
        
        // Columna Duracion
        TableColumn<ProyectoAutomatizacion, String> colDuracion = new TableColumn<>("Duracion");
        colDuracion.setCellValueFactory(cellData -> {
            if (cellData.getValue().getDuracionSegundos() != null) {
                return new javafx.beans.property.SimpleStringProperty(cellData.getValue().getDuracionSegundos() + "s");
            }
            return new javafx.beans.property.SimpleStringProperty("-");
        });
        colDuracion.setMinWidth(80);
        
        // Columna Reporte
        TableColumn<ProyectoAutomatizacion, String> colReporte = new TableColumn<>("Reporte");
        colReporte.setCellValueFactory(cellData -> {
            if (cellData.getValue().isReporteGenerado()) {
                return new javafx.beans.property.SimpleStringProperty("✅ Generado");
            }
            return new javafx.beans.property.SimpleStringProperty("-");
        });
        colReporte.setMinWidth(100);
        
        // Columna Configurar (para proyectos especiales con credenciales O proyectos manuales sin ruta)
        TableColumn<ProyectoAutomatizacion, Void> colConfigurar = new TableColumn<>("Configurar");
        colConfigurar.setCellFactory(param -> new javafx.scene.control.TableCell<ProyectoAutomatizacion, Void>() {
            private final Button btnConfigurar = new Button("⚙️ Config");
            private final Button btnCargarImagenes = new Button("📁 Cargar Imágenes");
            {
                btnConfigurar.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-weight: bold;");
                btnConfigurar.setOnAction(event -> {
                    ProyectoAutomatizacion proyecto = getTableView().getItems().get(getIndex());
                    abrirDialogoCredenciales(proyecto);
                });
                
                btnCargarImagenes.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
                btnCargarImagenes.setOnAction(event -> {
                    ProyectoAutomatizacion proyecto = getTableView().getItems().get(getIndex());
                    abrirDialogoCargaImagenesManual(proyecto);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableView().getItems().get(getIndex()) == null) {
                    setGraphic(null);
                } else {
                    ProyectoAutomatizacion proyecto = getTableView().getItems().get(getIndex());
                    // Proyecto manual: sin ruta de automatización
                    boolean esManual = proyecto.getRuta() == null || proyecto.getRuta().trim().isEmpty();
                    
                    if (esManual) {
                        setGraphic(btnCargarImagenes);
                    } else if (com.orquestador.util.GestorCredenciales.esProyectoEspecial(proyecto)) {
                        setGraphic(btnConfigurar);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });
        colConfigurar.setMinWidth(150);
        
        tablaProyectos.getColumns().addAll(colSeleccionar, colNombre, colRuta, colArea, colVPN, colTipo, colEstado, colUltima, colDuracion, colReporte, colConfigurar);
        
        container.getChildren().addAll(lblTabla, tablaProyectos);
        VBox.setVgrow(tablaProyectos, Priority.ALWAYS);
        
        return container;
    }
    
    private VBox crearSeccionLog() {
        VBox container = new VBox(5);
        container.setPadding(new Insets(5));
        
        Label lblLog = new Label(" Log de Ejecucion");
        lblLog.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12px;");
        
        Button btnLimpiarLog = new Button(" Limpiar Log");
        btnLimpiarLog.setOnAction(e -> logArea.clear());
        
        container.getChildren().addAll(lblLog, logArea, btnLimpiarLog);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        
        return container;
    }
    
    private HBox crearFooter() {
        HBox footer = new HBox(10);
        footer.setPadding(new Insets(10, 0, 0, 0));
        footer.setAlignment(Pos.CENTER_LEFT);
        
        lblEstadisticas = new Label();
        lblEstadisticas.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        footer.getChildren().add(lblEstadisticas);
        return footer;
    }
    
    private void agregarProyecto() {
        Dialog<ProyectoAutomatizacion> dialog = new Dialog<>();
        dialog.setTitle("Agregar Proyecto");
        dialog.setHeaderText("Nuevo Proyecto de Automatización");
        
        ButtonType btnAceptar = new ButtonType("Agregar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnAceptar, ButtonType.CANCEL);
        
        // Contenedor principal con scroll
        VBox contenido = new VBox(10);
        contenido.setPadding(new Insets(15));
        contenido.setMinWidth(800);
        contenido.setPrefWidth(850);
        
        TextField txtNombre = new TextField();
        txtNombre.setPromptText("Nombre del proyecto");
        
        TextField txtRuta = new TextField();
        txtRuta.setPromptText("C:\\ruta\\al\\proyecto");
        
        Button btnExplorarRuta = new Button("Examinar...");
        btnExplorarRuta.setOnAction(e -> {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle("Seleccionar carpeta del proyecto");
            java.io.File folder = chooser.showDialog(dialog.getOwner());
            if (folder != null) {
                txtRuta.setText(folder.getAbsolutePath());
            }
        });
        
        ComboBox<String> cboArea = new ComboBox<>();
        cboArea.getItems().addAll("Clientes", "Comercial", "Integraciones", "Siniestros");
        cboArea.setValue("Clientes");
        
        ComboBox<TipoVPN> cboVPN = new ComboBox<>();
        cboVPN.getItems().addAll(TipoVPN.values());
        cboVPN.setValue(TipoVPN.SIN_VPN);
        
        ComboBox<TipoEjecucion> cboTipo = new ComboBox<>();
        cboTipo.getItems().addAll(TipoEjecucion.values());
        cboTipo.setValue(TipoEjecucion.MAVEN);
        
        // Separador
        javafx.scene.control.Separator sep1 = new javafx.scene.control.Separator();
        Label lblGeneracion = new Label("📄 Configuración para Generación de Informes");
        lblGeneracion.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        
        // Campos para generación de informes
        TextField txtRutaImagenes = new TextField();
        txtRutaImagenes.setPromptText("Ruta de imágenes");
        
        Button btnExplorarImagenes = new Button("Examinar...");
        btnExplorarImagenes.setOnAction(e -> {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle("Seleccionar carpeta de imágenes");
            java.io.File folder = chooser.showDialog(dialog.getOwner());
            if (folder != null) {
                txtRutaImagenes.setText(folder.getAbsolutePath());
            }
        });
        
        TextField txtTemplate = new TextField();
        txtTemplate.setPromptText("Ruta del template Word");
        
        Button btnExplorarTemplate = new Button("Examinar...");
        btnExplorarTemplate.setOnAction(e -> {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Seleccionar template Word");
            chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Word", "*.docx"));
            java.io.File file = chooser.showOpenDialog(dialog.getOwner());
            if (file != null) {
                txtTemplate.setText(file.getAbsolutePath());
            }
        });
        
        TextField txtSalidaWord = new TextField();
        txtSalidaWord.setPromptText("Carpeta de salida Word");
        
        Button btnExplorarSalidaWord = new Button("Examinar...");
        btnExplorarSalidaWord.setOnAction(e -> {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle("Seleccionar carpeta de salida Word");
            java.io.File folder = chooser.showDialog(dialog.getOwner());
            if (folder != null) {
                txtSalidaWord.setText(folder.getAbsolutePath());
            }
        });
        
        TextField txtSalidaPdf = new TextField();
        txtSalidaPdf.setPromptText("Carpeta de salida PDF");
        
        Button btnExplorarSalidaPdf = new Button("Examinar...");
        btnExplorarSalidaPdf.setOnAction(e -> {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle("Seleccionar carpeta de salida PDF");
            java.io.File folder = chooser.showDialog(dialog.getOwner());
            if (folder != null) {
                txtSalidaPdf.setText(folder.getAbsolutePath());
            }
        });
        
        // Autocompletar rutas basadas en el área seleccionada
        cboArea.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                txtSalidaWord.setText("C:\\Users\\IARC\\Desktop\\Entregas Documentos Parchado\\WORD\\" + newVal);
                txtSalidaPdf.setText("C:\\Users\\IARC\\Desktop\\Entregas Documentos Parchado\\PDF\\" + newVal);
            }
        });
        
        // Inicializar rutas con el área por defecto
        txtSalidaWord.setText("C:\\Users\\IARC\\Desktop\\Entregas Documentos Parchado\\WORD\\" + cboArea.getValue());
        txtSalidaPdf.setText("C:\\Users\\IARC\\Desktop\\Entregas Documentos Parchado\\PDF\\" + cboArea.getValue());
        
        // Checkbox y botón para selector visual
        CheckBox chkSeleccionar = new CheckBox("Seleccionar imágenes manualmente");
        
        Button btnSelectorVisual = new Button("🖼️ Abrir Selector de Imágenes");
        btnSelectorVisual.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        btnSelectorVisual.setVisible(false);
        btnSelectorVisual.setManaged(false);
        
        List<String> imagenesSeleccionadasManualmente = new ArrayList<>();
        
        chkSeleccionar.selectedProperty().addListener((obs, oldVal, newVal) -> {
            btnSelectorVisual.setVisible(newVal);
            btnSelectorVisual.setManaged(newVal);
        });
        
        btnSelectorVisual.setOnAction(e -> {
            String ruta = txtRutaImagenes.getText();
            if (ruta == null || ruta.trim().isEmpty()) {
                // Permitir elegir carpeta si no hay ruta definida
                javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
                chooser.setTitle("Seleccionar carpeta de imágenes");
                java.io.File folder = chooser.showDialog(dialog.getOwner());
                if (folder != null) {
                    txtRutaImagenes.setText(folder.getAbsolutePath());
                    ruta = folder.getAbsolutePath();
                } else {
                    // El usuario canceló, no abrir el selector
                    return;
                }
            }

            List<String> seleccionadas = mostrarSelectorImagenesVisual(ruta, imagenesSeleccionadasManualmente);
            imagenesSeleccionadasManualmente.clear();
            imagenesSeleccionadasManualmente.addAll(seleccionadas);
            if (!seleccionadas.isEmpty()) {
                mostrarAlerta("Imágenes seleccionadas", "Se seleccionaron " + seleccionadas.size() + " imágenes en orden", Alert.AlertType.INFORMATION);
            }
        });
        
        // Construir interfaz
        contenido.getChildren().add(new Label("Nombre del proyecto:"));
        contenido.getChildren().add(txtNombre);
        
        HBox hboxRuta = new HBox(10);
        hboxRuta.getChildren().addAll(txtRuta, btnExplorarRuta);
        HBox.setHgrow(txtRuta, Priority.ALWAYS);
        contenido.getChildren().add(new Label("Ruta del proyecto:"));
        contenido.getChildren().add(hboxRuta);
        // Detectar automáticamente el área a partir de la ruta (p.ej. ...Automatizaciones_V2\Integraciones\...)
        txtRuta.textProperty().addListener((obs, oldVal, newVal) -> {
            String areaDetectada = detectarAreaDesdeRuta(newVal);
            if (areaDetectada != null) {
                for (String item : cboArea.getItems()) {
                    if (item.equalsIgnoreCase(areaDetectada)) {
                        cboArea.setValue(item);
                        break;
                    }
                }
            }
        });
        // Autodetectar ruta de imágenes si el campo está vacío
        txtRuta.textProperty().addListener((obs, oldVal, newVal) -> {
            if (txtRutaImagenes.getText() == null || txtRutaImagenes.getText().isEmpty()) {
                String rutaDetectada = detectarRutaImagenesDesdeRuta(newVal);
                if (rutaDetectada != null) {
                    txtRutaImagenes.setText(rutaDetectada);
                }
            }
        });
        // Detección inicial al abrir el diálogo
        if (txtRutaImagenes.getText() == null || txtRutaImagenes.getText().isEmpty()) {
            String inicialImg = detectarRutaImagenesDesdeRuta(txtRuta.getText());
            if (inicialImg != null) txtRutaImagenes.setText(inicialImg);
        }
        
        contenido.getChildren().add(new Label("Área:"));
        contenido.getChildren().add(cboArea);
        
        contenido.getChildren().add(new Label("VPN:"));
        contenido.getChildren().add(cboVPN);
        
        contenido.getChildren().add(new Label("Tipo de ejecución:"));
        contenido.getChildren().add(cboTipo);
        
        contenido.getChildren().addAll(sep1, lblGeneracion);
        
        HBox hboxImagenes = new HBox(10);
        hboxImagenes.getChildren().addAll(txtRutaImagenes, btnExplorarImagenes);
        HBox.setHgrow(txtRutaImagenes, Priority.ALWAYS);
        contenido.getChildren().add(new Label("Ruta de imágenes:"));
        contenido.getChildren().add(hboxImagenes);
        
        HBox hboxTemplate = new HBox(10);
        hboxTemplate.getChildren().addAll(txtTemplate, btnExplorarTemplate);
        HBox.setHgrow(txtTemplate, Priority.ALWAYS);
        contenido.getChildren().add(new Label("Template Word:"));
        contenido.getChildren().add(hboxTemplate);
        
        HBox hboxSalidaWord = new HBox(10);
        hboxSalidaWord.getChildren().addAll(txtSalidaWord, btnExplorarSalidaWord);
        HBox.setHgrow(txtSalidaWord, Priority.ALWAYS);
        contenido.getChildren().add(new Label("Carpeta Word:"));
        contenido.getChildren().add(hboxSalidaWord);
        
        HBox hboxSalidaPdf = new HBox(10);
        hboxSalidaPdf.getChildren().addAll(txtSalidaPdf, btnExplorarSalidaPdf);
        HBox.setHgrow(txtSalidaPdf, Priority.ALWAYS);
        contenido.getChildren().add(new Label("Carpeta PDF:"));
        contenido.getChildren().add(hboxSalidaPdf);
        
        contenido.getChildren().add(chkSeleccionar);
        contenido.getChildren().add(btnSelectorVisual);
        
        javafx.scene.control.ScrollPane scrollContenido = new javafx.scene.control.ScrollPane(contenido);
        scrollContenido.setFitToWidth(true);
        scrollContenido.setPrefHeight(700);
        scrollContenido.setPrefWidth(870);
        scrollContenido.setMinHeight(700);
        
        dialog.getDialogPane().setContent(scrollContenido);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnAceptar) {
                // Nombre es obligatorio
                if (txtNombre.getText().isEmpty()) {
                    mostrarAlerta("Error", "El nombre es obligatorio", Alert.AlertType.ERROR);
                    return null;
                }

                // Permitir ruta vacía si:
                // 1) Se marcó checkbox de selección manual
                // 2) Se proporcionó ruta de imágenes
                // 3) Se proporcionó template Word (indica intención de generar informes)
                boolean rutaVacia = txtRuta.getText() == null || txtRuta.getText().trim().isEmpty();
                boolean tieneRutaImagenes = txtRutaImagenes.getText() != null && !txtRutaImagenes.getText().trim().isEmpty();
                boolean tieneTemplate = txtTemplate.getText() != null && !txtTemplate.getText().trim().isEmpty();
                
                if (rutaVacia && !chkSeleccionar.isSelected() && !tieneRutaImagenes && !tieneTemplate) {
                    mostrarAlerta("Error", "Debe proporcionar:\n- Ruta del proyecto, o\n- Marcar 'Seleccionar imágenes manualmente', o\n- Especificar 'Ruta de imágenes' o 'Template Word'", Alert.AlertType.ERROR);
                    return null;
                }

                ProyectoAutomatizacion proyecto = new ProyectoAutomatizacion(
                    txtNombre.getText(),
                    txtRuta.getText(),
                    cboArea.getValue(),
                    cboVPN.getValue(),
                    cboTipo.getValue()
                );

                // Si no hay ruta al proyecto, marcar como proyecto manual y avisar
                if (rutaVacia) {
                    proyecto.setEsProyectoManual(true);
                    mostrarAlerta("Advertencia", "No se especificó la ruta del proyecto. El proyecto será generado en modo manual.", Alert.AlertType.WARNING);
                }

                // Configuración para generación de informes
                proyecto.setRutaImagenes(txtRutaImagenes.getText());
                proyecto.setRutaTemplateWord(txtTemplate.getText());
                proyecto.setRutaSalidaWord(txtSalidaWord.getText());
                proyecto.setRutaSalidaPdf(txtSalidaPdf.getText());

                // Si usó selector manual, guardar esas imágenes
                if (chkSeleccionar.isSelected() && !imagenesSeleccionadasManualmente.isEmpty()) {
                    proyecto.setImagenesSeleccionadas(imagenesSeleccionadasManualmente);
                }

                return proyecto;
            }
            return null;
        });
        
        Optional<ProyectoAutomatizacion> resultado = dialog.showAndWait();
        resultado.ifPresent(proyecto -> {
            proyectos.add(proyecto);
            guardarProyectos();
            actualizarEstadisticas();
            agregarLog("✅ Proyecto agregado: " + proyecto.getNombre());
        });
    }
    
    private void eliminarSeleccionados() {
        List<ProyectoAutomatizacion> seleccionados = proyectos.stream()
            .filter(ProyectoAutomatizacion::isSeleccionado)
            .collect(Collectors.toList());
        
        if (seleccionados.isEmpty()) {
            mostrarAlerta("Advertencia", "No hay proyectos seleccionados", Alert.AlertType.WARNING);
            return;
        }
        
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Confirmar eliminacin");
        confirmacion.setHeaderText("Eliminar " + seleccionados.size() + " proyecto(s)?");
        confirmacion.setContentText("Esta accin no se puede deshacer.");
        
        Optional<ButtonType> resultado = confirmacion.showAndWait();
        if (resultado.isPresent() && resultado.get() == ButtonType.OK) {
            proyectos.removeAll(seleccionados);
            guardarProyectos();
            actualizarEstadisticas();
            agregarLog(" Eliminados " + seleccionados.size() + " proyecto(s)");
        }
    }
    
    private void editarProyecto() {
        ProyectoAutomatizacion seleccionado = tablaProyectos.getSelectionModel().getSelectedItem();
        
        if (seleccionado == null) {
            mostrarAlerta("Sin selección", "Selecciona un proyecto de la tabla para editar", Alert.AlertType.WARNING);
            return;
        }
        
        Dialog<ProyectoAutomatizacion> dialog = new Dialog<>();
        dialog.setTitle("Editar Proyecto");
        dialog.setHeaderText("Editar: " + seleccionado.getNombre());
        
        ButtonType btnGuardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, ButtonType.CANCEL);
        
        // Contenedor principal con scroll
        VBox contenido = new VBox(10);
        contenido.setPadding(new Insets(15));
        contenido.setMinWidth(800);
        contenido.setPrefWidth(850);
        
        TextField txtNombre = new TextField(seleccionado.getNombre());
        txtNombre.setPromptText("Nombre del proyecto");
        
        TextField txtRuta = new TextField(seleccionado.getRuta());
        txtRuta.setPromptText("C:\\ruta\\al\\proyecto");
        
        Button btnExplorarRuta = new Button("Examinar...");
        btnExplorarRuta.setOnAction(e -> {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle("Seleccionar carpeta del proyecto");
            java.io.File folder = chooser.showDialog(dialog.getOwner());
            if (folder != null) {
                txtRuta.setText(folder.getAbsolutePath());
            }
        });
        
        ComboBox<String> cboArea = new ComboBox<>();
        cboArea.getItems().addAll("Clientes", "Comercial", "Integraciones", "Siniestros");
        cboArea.setValue(seleccionado.getArea());
        
        ComboBox<TipoVPN> cboVPN = new ComboBox<>();
        cboVPN.getItems().addAll(TipoVPN.values());
        cboVPN.setValue(seleccionado.getTipoVPN());
        
        ComboBox<TipoEjecucion> cboTipo = new ComboBox<>();
        cboTipo.getItems().addAll(TipoEjecucion.values());
        cboTipo.setValue(seleccionado.getTipoEjecucion());
        
        // Separador
        javafx.scene.control.Separator sep1 = new javafx.scene.control.Separator();
        Label lblGeneracion = new Label("📄 Configuración para Generación de Informes");
        lblGeneracion.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        
        // Campos para generación de informes
        TextField txtRutaImagenes = new TextField(seleccionado.getRutaImagenes() != null ? seleccionado.getRutaImagenes() : "");
        txtRutaImagenes.setPromptText("Ruta de imágenes");
        
        Button btnExplorarImagenes = new Button("Examinar...");
        btnExplorarImagenes.setOnAction(e -> {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle("Seleccionar carpeta de imágenes");
            java.io.File folder = chooser.showDialog(dialog.getOwner());
            if (folder != null) {
                txtRutaImagenes.setText(folder.getAbsolutePath());
            }
        });
        
        TextField txtTemplate = new TextField(seleccionado.getRutaTemplateWord() != null ? seleccionado.getRutaTemplateWord() : "");
        txtTemplate.setPromptText("Ruta del template Word");
        
        Button btnExplorarTemplate = new Button("Examinar...");
        btnExplorarTemplate.setOnAction(e -> {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Seleccionar template Word");
            chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Word", "*.docx"));
            java.io.File file = chooser.showOpenDialog(dialog.getOwner());
            if (file != null) {
                txtTemplate.setText(file.getAbsolutePath());
            }
        });
        
        TextField txtSalidaWord = new TextField(seleccionado.getRutaSalidaWord() != null ? seleccionado.getRutaSalidaWord() : "");
        txtSalidaWord.setPromptText("Carpeta de salida Word");
        
        Button btnExplorarSalidaWord = new Button("Examinar...");
        btnExplorarSalidaWord.setOnAction(e -> {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle("Seleccionar carpeta de salida Word");
            java.io.File folder = chooser.showDialog(dialog.getOwner());
            if (folder != null) {
                txtSalidaWord.setText(folder.getAbsolutePath());
            }
        });
        
        TextField txtSalidaPdf = new TextField(seleccionado.getRutaSalidaPdf() != null ? seleccionado.getRutaSalidaPdf() : "");
        txtSalidaPdf.setPromptText("Carpeta de salida PDF");
        
        Button btnExplorarSalidaPdf = new Button("Examinar...");
        btnExplorarSalidaPdf.setOnAction(e -> {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle("Seleccionar carpeta de salida PDF");
            java.io.File folder = chooser.showDialog(dialog.getOwner());
            if (folder != null) {
                txtSalidaPdf.setText(folder.getAbsolutePath());
            }
        });
        
        // Autocompletar rutas basadas en el área seleccionada
        cboArea.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // Solo autocompletar si los campos están vacíos o tienen el patrón por defecto
                String currentWord = txtSalidaWord.getText();
                String currentPdf = txtSalidaPdf.getText();
                if (currentWord.isEmpty() || currentWord.contains("Entregas Documentos Parchado\\WORD\\")) {
                    txtSalidaWord.setText("C:\\Users\\IARC\\Desktop\\Entregas Documentos Parchado\\WORD\\" + newVal);
                }
                if (currentPdf.isEmpty() || currentPdf.contains("Entregas Documentos Parchado\\PDF\\")) {
                    txtSalidaPdf.setText("C:\\Users\\IARC\\Desktop\\Entregas Documentos Parchado\\PDF\\" + newVal);
                }
            }
        });
        
        // Inicializar rutas si están vacías
        if (txtSalidaWord.getText().isEmpty()) {
            txtSalidaWord.setText("C:\\Users\\IARC\\Desktop\\Entregas Documentos Parchado\\WORD\\" + cboArea.getValue());
        }
        if (txtSalidaPdf.getText().isEmpty()) {
            txtSalidaPdf.setText("C:\\Users\\IARC\\Desktop\\Entregas Documentos Parchado\\PDF\\" + cboArea.getValue());
        }
        
        // Checkbox y botón para selector visual
        CheckBox chkSeleccionar = new CheckBox("Seleccionar imágenes manualmente");
        
        Button btnSelectorVisual = new Button("🖼️ Abrir Selector de Imágenes");
        btnSelectorVisual.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        btnSelectorVisual.setVisible(false);
        btnSelectorVisual.setManaged(false);
        
        // Mantener las imágenes seleccionadas en formato de patrón (hasta '_' antes del timestamp)
        List<String> imagenesSeleccionadasManualmente = new ArrayList<>();
        if (seleccionado.getImagenesSeleccionadas() != null) {
            imagenesSeleccionadasManualmente.addAll(seleccionado.getImagenesSeleccionadas());
        }

            // Tabla pequeña que muestra el orden de las imágenes seleccionadas (mandatoria cuando se activa el checkbox)
            javafx.scene.control.TableView<String> tableSeleccionadas = new javafx.scene.control.TableView<>();
            javafx.scene.control.TableColumn<String, String> colOrden = new javafx.scene.control.TableColumn<>("Imágenes (orden)");
            colOrden.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyStringWrapper(cell.getValue()));
            colOrden.setPrefWidth(600);
            tableSeleccionadas.getColumns().add(colOrden);
            tableSeleccionadas.setItems(javafx.collections.FXCollections.observableArrayList(imagenesSeleccionadasManualmente));
            tableSeleccionadas.setPrefHeight(120);
        
        chkSeleccionar.selectedProperty().addListener((obs, oldVal, newVal) -> {
            btnSelectorVisual.setVisible(newVal);
            btnSelectorVisual.setManaged(newVal);
        });
        
        btnSelectorVisual.setOnAction(e -> {
            String ruta = txtRutaImagenes.getText();
            if (ruta == null || ruta.trim().isEmpty()) {
                // Permitir elegir carpeta si no hay ruta definida
                javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
                chooser.setTitle("Seleccionar carpeta de imágenes");
                java.io.File folder = chooser.showDialog(dialog.getOwner());
                if (folder != null) {
                    txtRutaImagenes.setText(folder.getAbsolutePath());
                    ruta = folder.getAbsolutePath();
                } else {
                    // El usuario canceló, no abrir el selector
                    return;
                }
            }

            List<String> seleccionadas = mostrarSelectorImagenesVisual(ruta, imagenesSeleccionadasManualmente);
            imagenesSeleccionadasManualmente.clear();
            // Convertir cada nombre de archivo a su patrón (hasta '_' antes del timestamp)
            for (String nombreArchivo : seleccionadas) {
                String patron = com.orquestador.utilidades.GestorImagenes.extraerPatron(nombreArchivo);
                if (patron == null || patron.isEmpty()) {
                    // si no fue posible extraer patrón (p.ej. nombre no sigue formato), guardar el nombre tal cual
                    patron = nombreArchivo;
                }
                imagenesSeleccionadasManualmente.add(patron);
            }
            // Actualizar tabla mostrando los patrones
            tableSeleccionadas.getItems().setAll(imagenesSeleccionadasManualmente);
            if (!seleccionadas.isEmpty()) {
                mostrarAlerta("Imágenes seleccionadas", "Se seleccionaron " + seleccionadas.size() + " imágenes en orden (guardadas como patrones)", Alert.AlertType.INFORMATION);
            }
        });
        
        // Construir interfaz
        contenido.getChildren().add(new Label("Nombre del proyecto:"));
        contenido.getChildren().add(txtNombre);
        
        HBox hboxRuta = new HBox(10);
        hboxRuta.getChildren().addAll(txtRuta, btnExplorarRuta);
        HBox.setHgrow(txtRuta, Priority.ALWAYS);
        contenido.getChildren().add(new Label("Ruta del proyecto:"));
        contenido.getChildren().add(hboxRuta);
        // Detectar automáticamente el área a partir de la ruta al abrir el diálogo de edición
        txtRuta.textProperty().addListener((obs, oldVal, newVal) -> {
            String areaDetectada = detectarAreaDesdeRuta(newVal);
            if (areaDetectada != null) {
                for (String item : cboArea.getItems()) {
                    if (item.equalsIgnoreCase(areaDetectada)) {
                        cboArea.setValue(item);
                        break;
                    }
                }
            }
        });
        // Autodetectar ruta de imágenes si el campo está vacío (edición)
        txtRuta.textProperty().addListener((obs, oldVal, newVal) -> {
            if (txtRutaImagenes.getText() == null || txtRutaImagenes.getText().isEmpty()) {
                String rutaDetectada = detectarRutaImagenesDesdeRuta(newVal);
                if (rutaDetectada != null) {
                    txtRutaImagenes.setText(rutaDetectada);
                }
            }
        });
        // Detección inicial al abrir el diálogo de edición
        if (txtRutaImagenes.getText() == null || txtRutaImagenes.getText().isEmpty()) {
            String inicialImg = detectarRutaImagenesDesdeRuta(txtRuta.getText());
            if (inicialImg != null) txtRutaImagenes.setText(inicialImg);
        }
        // Ejecutar detección inicial en caso de que la ruta ya contenga el área
        String areaInicial = detectarAreaDesdeRuta(txtRuta.getText());
        if (areaInicial != null) {
            for (String item : cboArea.getItems()) {
                if (item.equalsIgnoreCase(areaInicial)) {
                    cboArea.setValue(item);
                    break;
                }
            }
        }
        
        contenido.getChildren().add(new Label("Área:"));
        contenido.getChildren().add(cboArea);
        
        contenido.getChildren().add(new Label("VPN:"));
        contenido.getChildren().add(cboVPN);
        
        contenido.getChildren().add(new Label("Tipo de ejecución:"));
        contenido.getChildren().add(cboTipo);
        
        contenido.getChildren().addAll(sep1, lblGeneracion);
        
        HBox hboxImagenes = new HBox(10);
        hboxImagenes.getChildren().addAll(txtRutaImagenes, btnExplorarImagenes);
        HBox.setHgrow(txtRutaImagenes, Priority.ALWAYS);
        contenido.getChildren().add(new Label("Ruta de imágenes:"));
        contenido.getChildren().add(hboxImagenes);
        
        HBox hboxTemplate = new HBox(10);
        hboxTemplate.getChildren().addAll(txtTemplate, btnExplorarTemplate);
        HBox.setHgrow(txtTemplate, Priority.ALWAYS);
        contenido.getChildren().add(new Label("Template Word:"));
        contenido.getChildren().add(hboxTemplate);
        
        HBox hboxSalidaWord = new HBox(10);
        hboxSalidaWord.getChildren().addAll(txtSalidaWord, btnExplorarSalidaWord);
        HBox.setHgrow(txtSalidaWord, Priority.ALWAYS);
        contenido.getChildren().add(new Label("Carpeta Word:"));
        contenido.getChildren().add(hboxSalidaWord);
        
        HBox hboxSalidaPdf = new HBox(10);
        hboxSalidaPdf.getChildren().addAll(txtSalidaPdf, btnExplorarSalidaPdf);
        HBox.setHgrow(txtSalidaPdf, Priority.ALWAYS);
        contenido.getChildren().add(new Label("Carpeta PDF:"));
        contenido.getChildren().add(hboxSalidaPdf);
        
        contenido.getChildren().add(chkSeleccionar);
        contenido.getChildren().add(btnSelectorVisual);
        contenido.getChildren().add(new Label("Lista de imágenes seleccionadas (orden):"));
        contenido.getChildren().add(tableSeleccionadas);
        
        // Nota: el botón de "Limpiar Configuración" se muestra solo en la segunda pantalla
        // (selector visual de imágenes). Se eliminó de esta primera pantalla intencionalmente.
        
        javafx.scene.control.ScrollPane scrollContenido = new javafx.scene.control.ScrollPane(contenido);
        scrollContenido.setFitToWidth(true);
        scrollContenido.setPrefHeight(700);
        scrollContenido.setPrefWidth(870);
        scrollContenido.setMinHeight(700);
        
        dialog.getDialogPane().setContent(scrollContenido);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnGuardar) {
                // Nombre es obligatorio
                if (txtNombre.getText().isEmpty()) {
                    mostrarAlerta("Error", "El nombre es obligatorio", Alert.AlertType.ERROR);
                    return null;
                }

                boolean rutaVacia = txtRuta.getText() == null || txtRuta.getText().trim().isEmpty();
                boolean tieneRutaImagenes = txtRutaImagenes.getText() != null && !txtRutaImagenes.getText().trim().isEmpty();
                boolean tieneTemplate = txtTemplate.getText() != null && !txtTemplate.getText().trim().isEmpty();

                if (rutaVacia && !chkSeleccionar.isSelected() && !tieneRutaImagenes && !tieneTemplate) {
                    mostrarAlerta("Error", "Debe proporcionar:\n- Ruta del proyecto, o\n- Marcar 'Seleccionar imágenes manualmente', o\n- Especificar 'Ruta de imágenes' o 'Template Word'", Alert.AlertType.ERROR);
                    return null;
                }

                // Actualizar proyecto existente
                seleccionado.setNombre(txtNombre.getText());
                seleccionado.setRuta(txtRuta.getText());
                seleccionado.setArea(cboArea.getValue());
                seleccionado.setTipoVPN(cboVPN.getValue());
                seleccionado.setTipoEjecucion(cboTipo.getValue());

                // Si no hay ruta al proyecto, marcar como proyecto manual y avisar
                if (rutaVacia) {
                    seleccionado.setEsProyectoManual(true);
                    mostrarAlerta("Advertencia", "No se especificó la ruta del proyecto. El proyecto será generado en modo manual.", Alert.AlertType.WARNING);
                } else {
                    seleccionado.setEsProyectoManual(false);
                }

                // Configuración para generación de informes
                seleccionado.setRutaImagenes(txtRutaImagenes.getText());
                seleccionado.setRutaTemplateWord(txtTemplate.getText());
                seleccionado.setRutaSalidaWord(txtSalidaWord.getText());
                seleccionado.setRutaSalidaPdf(txtSalidaPdf.getText());

                // Si usó selector manual, validar que la tabla no esté vacía y convertir los nombres seleccionados a patrones y guardar
                if (chkSeleccionar.isSelected()) {
                    if (imagenesSeleccionadasManualmente.isEmpty()) {
                        mostrarAlerta("Error", "Debes seleccionar al menos una imagen en la tabla antes de guardar la configuración.", Alert.AlertType.ERROR);
                        return null;
                    }
                    
                    // convertir y guardar
                    List<String> patronesGuardar = new ArrayList<>();
                    for (String nombre : imagenesSeleccionadasManualmente) {
                        String patron = com.orquestador.utilidades.GestorImagenes.extraerPatron(nombre);
                        if (patron == null || patron.isEmpty()) {
                            patron = nombre;
                        }
                        patronesGuardar.add(patron);
                    }
                    seleccionado.setImagenesSeleccionadas(patronesGuardar);
                }
                
                
                return seleccionado;
            }
            return null;
        });
        
        Optional<ProyectoAutomatizacion> resultado = dialog.showAndWait();
        resultado.ifPresent(proyecto -> {
            tablaProyectos.refresh();
            guardarProyectos();
            agregarLog("✏️ Proyecto editado: " + proyecto.getNombre());
        });
    }
    
    // Detectar el área a partir de la ruta del proyecto
    private String detectarAreaDesdeRuta(String ruta) {
        if (ruta == null || ruta.isEmpty()) return null;
        String marker = "Automatizaciones_V2";
        // normalizar separadores
        String normalized = ruta.replace('/', '\\');
        int idx = normalized.indexOf(marker + "\\");
        if (idx == -1) {
            // si no tiene barra después, buscar solo el marcador
            idx = normalized.indexOf(marker);
            if (idx == -1) return null;
            int start = idx + marker.length();
            if (start >= normalized.length()) return null;
            String rest = normalized.substring(start);
            if (rest.startsWith("\\")) rest = rest.substring(1);
            String[] parts = rest.split("[\\\\/]");
            if (parts.length > 0) return parts[0];
            return null;
        }
        String rest = normalized.substring(idx + marker.length() + 1);
        if (rest.isEmpty()) return null;
        String[] parts = rest.split("\\\\");
        if (parts.length > 0) return parts[0];
        return null;
    }

    // Detectar la ruta de imágenes probando rutas candidatas dentro del proyecto
    private String detectarRutaImagenesDesdeRuta(String ruta) {
        if (ruta == null || ruta.isEmpty()) return null;
        String[] candidatos = new String[] {
            "test-output\\capturaPantalla",
            "Archivos\\screenshots\\evidencia"
        };
        // normalizar separadores
        String normalized = ruta.replace('/', '\\');
        java.io.File base = new java.io.File(normalized);
        for (String rel : candidatos) {
            java.io.File cand = new java.io.File(base, rel);
            if (cand.exists() && cand.isDirectory()) {
                try {
                    return cand.getCanonicalPath();
                } catch (java.io.IOException e) {
                    return cand.getAbsolutePath();
                }
            }
        }
        return null;
    }

    // Diálogo modal para configurar credenciales de proyectos especiales
    private void abrirDialogoCredenciales(ProyectoAutomatizacion proyecto) {
        try {
            // Cargar credenciales actuales
            com.orquestador.modelo.Credenciales cred = com.orquestador.util.GestorCredenciales.cargarCredenciales(proyecto);
            
            Dialog<com.orquestador.modelo.Credenciales> dialog = new Dialog<>();
            dialog.setTitle("Configurar Credenciales - " + proyecto.getNombre());
            dialog.setHeaderText("Actualizar datos y imágenes para: " + proyecto.getNombre());
            
            ButtonType btnGuardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, ButtonType.CANCEL);
            
            VBox contenido = new VBox(15);
            contenido.setPadding(new Insets(20));
            contenido.setMinWidth(700);
            contenido.setPrefWidth(750);
            
            String nombre = proyecto.getNombre().toLowerCase();
            
            // SECCIÓN 1: CREDENCIALES (campos de texto)
            Label lblCredenciales = new Label("📝 Credenciales");
            lblCredenciales.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            contenido.getChildren().add(lblCredenciales);
            
            // Usar contenedor para referencias mutables
            java.util.Map<String, javafx.scene.control.Control> campos = new java.util.HashMap<>();
            
            VBox vboxCred = new VBox(10);
            vboxCred.setPadding(new Insets(10));
            vboxCred.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 5;");
            
            if (nombre.contains("zenit")) {
                // Proyecto 16: user, pasword, nAtencionZenit
                vboxCred.getChildren().add(new Label("Usuario:"));
                TextField txtUser = new TextField(cred.getUser());
                campos.put("user", txtUser);
                vboxCred.getChildren().add(txtUser);
                
                vboxCred.getChildren().add(new Label("Contraseña:"));
                PasswordField txtPassword = new PasswordField();
                txtPassword.setText(cred.getPasword());
                campos.put("password", txtPassword);
                vboxCred.getChildren().add(txtPassword);
                
                vboxCred.getChildren().add(new Label("Número Solicitud (Zenit):"));
                TextField txtNAtencion = new TextField(cred.getNAtencionZenit());
                campos.put("natencion", txtNAtencion);
                vboxCred.getChildren().add(txtNAtencion);
                
            } else if (nombre.contains("vida")) {
                // Proyecto 18: numeroTicket
                vboxCred.getChildren().add(new Label("Número Ticket:"));
                TextField txtNumeroTicket = new TextField(cred.getNumeroTicket());
                campos.put("numeroticket", txtNumeroTicket);
                vboxCred.getChildren().add(txtNumeroTicket);
                
            } else if (nombre.contains("corredores") && !nombre.contains("vida")) {
                // Proyecto 17: user2, pasword2
                vboxCred.getChildren().add(new Label("Usuario:"));
                TextField txtUser2 = new TextField(cred.getUser2());
                campos.put("user2", txtUser2);
                vboxCred.getChildren().add(txtUser2);
                
                vboxCred.getChildren().add(new Label("Contraseña:"));
                PasswordField txtPassword2 = new PasswordField();
                txtPassword2.setText(cred.getPasword2());
                campos.put("password2", txtPassword2);
                vboxCred.getChildren().add(txtPassword2);
                
            } else {
                // Proyecto 15 (BCI): user, pasword, nAtencionBci
                vboxCred.getChildren().add(new Label("Usuario:"));
                TextField txtUser = new TextField(cred.getUser());
                campos.put("user", txtUser);
                vboxCred.getChildren().add(txtUser);
                
                vboxCred.getChildren().add(new Label("Contraseña:"));
                PasswordField txtPassword = new PasswordField();
                txtPassword.setText(cred.getPasword());
                campos.put("password", txtPassword);
                vboxCred.getChildren().add(txtPassword);
                
                vboxCred.getChildren().add(new Label("Número Solicitud (BCI):"));
                TextField txtNAtencion = new TextField(cred.getNAtencionBci());
                campos.put("natencion", txtNAtencion);
                vboxCred.getChildren().add(txtNAtencion);
            }
            
            contenido.getChildren().add(vboxCred);
            
            // SECCIÓN 2: IMÁGENES
            Label lblImagenes = new Label("🖼️ Imágenes");
            lblImagenes.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            contenido.getChildren().add(lblImagenes);
            
            VBox vboxImg = new VBox(15);
            vboxImg.setPadding(new Insets(10));
            vboxImg.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 5;");
            
            // Imagen de Solicitud (solo para proyectos 15, 16, 18 - híbridos)
            if (!nombre.contains("corredores") || nombre.contains("vida")) {
                vboxImg.getChildren().add(new Label("📄 Imagen de la Solicitud (PRIMERA imagen del informe):"));
                
                TextField txtImgSolicitud = new TextField(cred.getRutaImagenSolicitud());
                txtImgSolicitud.setPromptText("Ruta de la imagen de solicitud");
                txtImgSolicitud.setEditable(false);
                campos.put("imgSolicitud", txtImgSolicitud);
                
                Button btnExaminarSolicitud = new Button("Examinar...");
                btnExaminarSolicitud.setOnAction(e -> {
                    javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
                    chooser.setTitle("Seleccionar imagen de solicitud");
                    chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"));
                    java.io.File file = chooser.showOpenDialog(dialog.getOwner());
                    if (file != null) {
                        txtImgSolicitud.setText(file.getAbsolutePath());
                    }
                });
                
                HBox hboxSolicitud = new HBox(10);
                hboxSolicitud.getChildren().addAll(txtImgSolicitud, btnExaminarSolicitud);
                HBox.setHgrow(txtImgSolicitud, Priority.ALWAYS);
                
                // Drag & Drop para Solicitud
                configurarDragDropTextField(txtImgSolicitud);
                
                vboxImg.getChildren().add(hboxSolicitud);
            }
            
            // Imagen de Correo (para los 4 proyectos)
            vboxImg.getChildren().add(new Label("📧 Imagen del Correo (ÚLTIMA imagen del informe):"));
            
            TextField txtImgCorreo = new TextField(cred.getRutaImagenCorreo());
            txtImgCorreo.setPromptText("Ruta de la imagen del correo");
            txtImgCorreo.setEditable(false);
            campos.put("imgCorreo", txtImgCorreo);
            
            Button btnExaminarCorreo = new Button("Examinar...");
            btnExaminarCorreo.setOnAction(e -> {
                javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
                chooser.setTitle("Seleccionar imagen del correo");
                chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"));
                java.io.File file = chooser.showOpenDialog(dialog.getOwner());
                if (file != null) {
                    txtImgCorreo.setText(file.getAbsolutePath());
                }
            });
            
            HBox hboxCorreo = new HBox(10);
            hboxCorreo.getChildren().addAll(txtImgCorreo, btnExaminarCorreo);
            HBox.setHgrow(txtImgCorreo, Priority.ALWAYS);
            
            // Drag & Drop para Correo
            configurarDragDropTextField(txtImgCorreo);
            
            vboxImg.getChildren().add(hboxCorreo);
            
            contenido.getChildren().add(vboxImg);
            
            javafx.scene.control.ScrollPane scrollContenido = new javafx.scene.control.ScrollPane(contenido);
            scrollContenido.setFitToWidth(true);
            scrollContenido.setPrefHeight(600);
            
            dialog.getDialogPane().setContent(scrollContenido);
            
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == btnGuardar) {
                    com.orquestador.modelo.Credenciales credActualizada = new com.orquestador.modelo.Credenciales();
                    
                    // Recopilar valores según el tipo de proyecto
                    if (nombre.contains("zenit")) {
                        if (campos.containsKey("user")) credActualizada.setUser(((TextField)campos.get("user")).getText());
                        if (campos.containsKey("password")) credActualizada.setPasword(((PasswordField)campos.get("password")).getText());
                        if (campos.containsKey("natencion")) credActualizada.setNAtencionZenit(((TextField)campos.get("natencion")).getText());
                    } else if (nombre.contains("vida")) {
                        if (campos.containsKey("numeroticket")) credActualizada.setNumeroTicket(((TextField)campos.get("numeroticket")).getText());
                    } else if (nombre.contains("corredores") && !nombre.contains("vida")) {
                        if (campos.containsKey("user2")) credActualizada.setUser2(((TextField)campos.get("user2")).getText());
                        if (campos.containsKey("password2")) credActualizada.setPasword2(((PasswordField)campos.get("password2")).getText());
                    } else {
                        // BCI por defecto
                        if (campos.containsKey("user")) credActualizada.setUser(((TextField)campos.get("user")).getText());
                        if (campos.containsKey("password")) credActualizada.setPasword(((PasswordField)campos.get("password")).getText());
                        if (campos.containsKey("natencion")) credActualizada.setNAtencionBci(((TextField)campos.get("natencion")).getText());
                    }
                    
                    // Imágenes (comunes)
                    if (campos.containsKey("imgSolicitud")) {
                        credActualizada.setRutaImagenSolicitud(((TextField)campos.get("imgSolicitud")).getText());
                    }
                    if (campos.containsKey("imgCorreo")) {
                        credActualizada.setRutaImagenCorreo(((TextField)campos.get("imgCorreo")).getText());
                    }
                    
                    // Mostrar alerta de campos no completados (pero permitir guardar)
                    java.util.List<String> camposVacios = new java.util.ArrayList<>();
                    if (credActualizada.getUser().isEmpty() && !nombre.contains("vida") && !nombre.contains("corredores")) {
                        camposVacios.add("Usuario");
                    }
                    if (credActualizada.getPasword().isEmpty() && !nombre.contains("vida")) {
                        camposVacios.add("Contraseña");
                    }
                    if (credActualizada.getNumeroTicket().isEmpty() && nombre.contains("vida")) {
                        camposVacios.add("Número Ticket");
                    }
                    if (credActualizada.getRutaImagenSolicitud().isEmpty() && (!nombre.contains("corredores") || nombre.contains("vida"))) {
                        camposVacios.add("Imagen Solicitud");
                    }
                    if (credActualizada.getRutaImagenCorreo().isEmpty()) {
                        camposVacios.add("Imagen Correo");
                    }
                    
                    if (!camposVacios.isEmpty()) {
                        String msg = "⚠️ Campos no completados:\n" + String.join(", ", camposVacios) + "\n\nPuedes continuar igual.";
                        mostrarAlerta("Campos Incompletos", msg, Alert.AlertType.INFORMATION);
                    }
                    
                    return credActualizada;
                }
                return null;
            });
            
            Optional<com.orquestador.modelo.Credenciales> resultado = dialog.showAndWait();
            resultado.ifPresent(credGuardar -> {
                try {
                    com.orquestador.util.GestorCredenciales.guardarCredenciales(proyecto, credGuardar);
                    agregarLog("✅ Credenciales actualizadas para: " + proyecto.getNombre());
                    mostrarAlerta("Éxito", "Credenciales guardadas correctamente", Alert.AlertType.INFORMATION);
                } catch (Exception e) {
                    agregarLog("❌ Error al guardar credenciales: " + e.getMessage());
                    mostrarAlerta("Error", "No se pudieron guardar las credenciales: " + e.getMessage(), Alert.AlertType.ERROR);
                }
            });
            
        } catch (Exception e) {
            agregarLog("❌ Error abriendo diálogo de credenciales: " + e.getMessage());
            mostrarAlerta("Error", "Error abriendo el diálogo: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }
    
    // Configurar drag & drop para un TextField de imagen
    private void configurarDragDropTextField(TextField textField) {
        textField.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            event.consume();
        });
        
        textField.setOnDragDropped(event -> {
            javafx.scene.input.Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                java.io.File file = db.getFiles().get(0);
                String path = file.getAbsolutePath();
                // Validar que sea una imagen
                if (path.matches("(?i).*\\.(png|jpg|jpeg|bmp|gif)$")) {
                    textField.setText(path);
                    success = true;
                } else {
                    mostrarAlerta("Archivo inválido", "Por favor, selecciona una imagen válida (.png, .jpg, .jpeg, .bmp, .gif)", Alert.AlertType.WARNING);
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void ejecutarSeleccionados() {
        List<ProyectoAutomatizacion> seleccionados = proyectos.stream()
            .filter(ProyectoAutomatizacion::isSeleccionado)
            .collect(Collectors.toList());
        
        if (seleccionados.isEmpty()) {
            mostrarAlerta("Advertencia", "No hay proyectos seleccionados", Alert.AlertType.WARNING);
            return;
        }
        
        ejecutarProyectos(seleccionados);
    }
    
    private void ejecutarPorArea() {
        String areaSeleccionada = cboFiltroArea.getValue();
        if (areaSeleccionada == null || areaSeleccionada.equals("Todas")) {
            mostrarAlerta("Advertencia", "Selecciona un Area especifica", Alert.AlertType.WARNING);
            return;
        }
        
        List<ProyectoAutomatizacion> porArea = proyectos.stream()
            .filter(p -> p.getArea().equals(areaSeleccionada))
            .collect(Collectors.toList());
        
        if (porArea.isEmpty()) {
            mostrarAlerta("Advertencia", "No hay proyectos en el Area: " + areaSeleccionada, Alert.AlertType.WARNING);
            return;
        }
        
        ejecutarProyectos(porArea);
    }
    
    private void ejecutarProyectos(List<ProyectoAutomatizacion> listaProyectos) {
        if (ejecutando) {
            mostrarAlerta("Advertencia", "Ya hay una ejecucin en curso", Alert.AlertType.WARNING);
            return;
        }
        
        ejecutando = true;
        btnEjecutarSeleccionados.setDisable(true);
        btnEjecutarPorArea.setDisable(true);
        btnDetener.setDisable(false);
        btnAgregar.setDisable(true);
        btnEliminar.setDisable(true);
        
        agregarLog("\n========================================");
        agregarLog(" INICIANDO EJECUCIN");
        agregarLog("Total de proyectos: " + listaProyectos.size());
        agregarLog("========================================\n");
        
        // Agrupar por VPN
        Map<TipoVPN, List<ProyectoAutomatizacion>> grupos = agruparPorVPN(listaProyectos);
        
        // Determinar orden de ejecucin
        List<TipoVPN> ordenEjecucion = determinarOrdenVPN(grupos);
        
        agregarLog(" Distribucin por VPN:");
        for (TipoVPN tipo : ordenEjecucion) {
            agregarLog("   " + tipo.getDescripcion() + ": " + grupos.get(tipo).size() + " proyecto(s)");
        }
        agregarLog("");
        
        // Ejecutar en hilo separado
        new Thread(() -> {
            try {
                for (TipoVPN tipoVPN : ordenEjecucion) {
                    List<ProyectoAutomatizacion> grupoVPN = grupos.get(tipoVPN);
                    
                    // Mostrar popup de VPN si es necesario
                    if (tipoVPN != TipoVPN.SIN_VPN) {
                        mostrarPopupVPN(tipoVPN, true);
                    }
                    
                    // Ejecutar proyectos del grupo
                    for (ProyectoAutomatizacion proyecto : grupoVPN) {
                        if (!ejecutando) break;
                        
                        ejecutarProyectoSync(proyecto);
                    }
                    
                    // Mostrar popup de desconexin si es necesario
                    if (tipoVPN != TipoVPN.SIN_VPN) {
                        mostrarPopupVPN(tipoVPN, false);
                    }
                }
                
                Platform.runLater(() -> {
                    agregarLog("\n========================================");
                    agregarLog(" Ejecucion COMPLETADA");
                    agregarLog("========================================\n");
                    finalizarEjecucion();
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    agregarLog(" Error en la Ejecucion: " + e.getMessage());
                    finalizarEjecucion();
                });
            }
        }).start();
    }
    
    private Map<TipoVPN, List<ProyectoAutomatizacion>> agruparPorVPN(List<ProyectoAutomatizacion> proyectos) {
        Map<TipoVPN, List<ProyectoAutomatizacion>> grupos = new HashMap<>();
        for (TipoVPN tipo : TipoVPN.values()) {
            grupos.put(tipo, new ArrayList<>());
        }
        
        for (ProyectoAutomatizacion proyecto : proyectos) {
            grupos.get(proyecto.getTipoVPN()).add(proyecto);
        }
        
        return grupos;
    }
    
    private List<TipoVPN> determinarOrdenVPN(Map<TipoVPN, List<ProyectoAutomatizacion>> grupos) {
        List<TipoVPN> orden = new ArrayList<>();
        
        // Si VPN BCI tiene ms proyectos que Sin VPN, ejecutar primero
        int sinVPN = grupos.get(TipoVPN.SIN_VPN).size();
        int vpnBCI = grupos.get(TipoVPN.VPN_BCI).size();
        
        if (vpnBCI > sinVPN && vpnBCI > 0) {
            if (vpnBCI > 0) orden.add(TipoVPN.VPN_BCI);
            if (sinVPN > 0) orden.add(TipoVPN.SIN_VPN);
        } else {
            if (sinVPN > 0) orden.add(TipoVPN.SIN_VPN);
            if (vpnBCI > 0) orden.add(TipoVPN.VPN_BCI);
        }
        
        // VPN Clip siempre al final
        if (grupos.get(TipoVPN.VPN_CLIP).size() > 0) {
            orden.add(TipoVPN.VPN_CLIP);
        }
        
        return orden;
    }
    
    private void mostrarPopupVPN(TipoVPN tipo, boolean conectar) {
        final Object lock = new Object();
        final boolean[] dialogoCerrado = {false};

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Accion Requerida - " + tipo.getDescripcion());

            if (conectar) {
                alert.setHeaderText(" CONCTATE A " + tipo.getDescripcion());
                alert.setContentText("Antes de continuar, asegrate de estar conectado a " +
                        tipo.getDescripcion() + ".\n\nPresiona OK cuando ests listo.");
            } else {
                alert.setHeaderText(" DESCONCTATE DE " + tipo.getDescripcion());
                alert.setContentText("Desconctate de " + tipo.getDescripcion() + " antes de continuar.\n\nPresiona OK cuando hayas terminado.");
            }

            agregarLog((conectar ? " " : " ") + (conectar ? "Esperando conexin a " : "Esperando desconexin de ") + tipo.getDescripcion());
            
            alert.showAndWait();
            
            agregarLog(" Accin confirmada: " + (conectar ? "Conectado a " : "Desconectado de ") + tipo.getDescripcion());
            
            synchronized (lock) {
                dialogoCerrado[0] = true;
                lock.notify();
            }
        });

        // BLOQUEAR hasta que el usuario presione OK
        synchronized (lock) {
            while (!dialogoCerrado[0]) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
    
    private void ejecutarProyectoSync(ProyectoAutomatizacion proyecto) {
        final Object lock = new Object();
        final boolean[] terminado = {false};
        
        Platform.runLater(() -> {
            proyecto.setEstado(EstadoEjecucion.EJECUTANDO);
            tablaProyectos.refresh();
        });
        
        ejecutor.ejecutarProyecto(proyecto, 
            mensaje -> Platform.runLater(() -> agregarLog(mensaje)),
            () -> {
                Platform.runLater(() -> {
                    tablaProyectos.refresh();
                    actualizarEstadisticas();
                    guardarProyectos();
                });
                synchronized (lock) {
                    terminado[0] = true;
                    lock.notify();
                }
            }
        );
        
        // Esperar a que termine
        synchronized (lock) {
            while (!terminado[0]) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
    
    private void detenerEjecucion() {
        ejecutando = false;
        ejecutor.detener();
        agregarLog(" Ejecucin detenida por el usuario");
        finalizarEjecucion();
    }
    
    private void mostrarCapturas() {
        // Obtener el proyecto seleccionado en la tabla
        ProyectoAutomatizacion seleccionado = tablaProyectos.getSelectionModel().getSelectedItem();
        
        if (seleccionado == null) {
            mostrarAlerta("Sin seleccion", "Selecciona un proyecto de la tabla primero", Alert.AlertType.WARNING);
            return;
        }
        
        if (seleccionado.getUltimaEjecucion() == null) {
            mostrarAlerta("Sin capturas", "El proyecto seleccionado no ha sido ejecutado", Alert.AlertType.INFORMATION);
            return;
        }
        
        // Crear ventana de capturas
        Stage stage = new Stage();
        stage.setTitle("Capturas de Pantalla - " + seleccionado.getNombre());
        
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane();
        javafx.scene.layout.FlowPane flow = new javafx.scene.layout.FlowPane();
        flow.setHgap(10);
        flow.setVgap(10);
        flow.setPadding(new javafx.geometry.Insets(10));
        
        // Procesar solo el proyecto seleccionado
        java.io.File carpetaCapturas = new java.io.File(seleccionado.getRuta(), "test-output/capturaPantalla");
        
        System.out.println("[DEBUG] Ruta del proyecto: " + seleccionado.getRuta());
        System.out.println("[DEBUG] Buscando capturas en: " + carpetaCapturas.getAbsolutePath());
        System.out.println("[DEBUG] Carpeta existe: " + carpetaCapturas.exists());
        
        if (!carpetaCapturas.exists()) {
            mostrarAlerta("Sin capturas", "No se encontro la carpeta de capturas en:\n" + carpetaCapturas.getAbsolutePath(), Alert.AlertType.INFORMATION);
            return;
        }
        
        java.io.File[] imagenes = carpetaCapturas.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".jpg"));
        
        System.out.println("[DEBUG] Imágenes encontradas: " + (imagenes != null ? imagenes.length : 0));
        if (imagenes != null && imagenes.length > 0) {
            System.out.println("[DEBUG] Primera imagen: " + imagenes[0].getName());
        }
            
        if (imagenes == null || imagenes.length == 0) {
            mostrarAlerta("Sin capturas", "No se encontraron imagenes en la carpeta de capturas", Alert.AlertType.INFORMATION);
            return;
        }
        
        // Mostrar todas las imágenes disponibles sin filtro temporal
        for (java.io.File img : imagenes) {
            try {
                javafx.scene.image.Image image = new javafx.scene.image.Image(img.toURI().toString());
                javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(image);
                iv.setFitWidth(300);
                iv.setPreserveRatio(true);
                
                VBox box = new VBox(5);
                Label lbl = new Label(seleccionado.getNombre() + " - " + img.getName());
                lbl.setStyle("-fx-font-size: 10px;");
                box.getChildren().addAll(iv, lbl);
                box.setStyle("-fx-border-color: #ccc; -fx-padding: 5;");
                    
                // Click para ver en tamano completo
                iv.setOnMouseClicked(e -> {
                    Stage fullStage = new Stage();
                    fullStage.setTitle(img.getName());
                    javafx.scene.image.ImageView fullIv = new javafx.scene.image.ImageView(image);
                    javafx.scene.control.ScrollPane fullScroll = new javafx.scene.control.ScrollPane(fullIv);
                    Scene fullScene = new Scene(fullScroll, 1000, 700);
                    fullStage.setScene(fullScene);
                    fullStage.show();
                });
                
                flow.getChildren().add(box);
            } catch (Exception e) {
                // Ignorar imagenes que no se pueden cargar
            }
        }
        
        if (flow.getChildren().isEmpty()) {
            mostrarAlerta("Sin capturas", "No se encontraron capturas en la carpeta", Alert.AlertType.INFORMATION);
            return;
        }
        
        scroll.setContent(flow);
        scroll.setFitToWidth(true);
        
        Scene scene = new Scene(scroll, 1200, 700);
        stage.setScene(scene);
        stage.show();
    }

    private void finalizarEjecucion() {
        ejecutando = false;
        btnEjecutarSeleccionados.setDisable(false);
        btnEjecutarPorArea.setDisable(false);
        btnDetener.setDisable(true);
        btnAgregar.setDisable(false);
        btnEliminar.setDisable(false);
        actualizarEstadisticas();
    }
    
    private void aplicarFiltro() {
        String filtro = cboFiltroArea.getValue();
        if (filtro.equals("Todas")) {
            tablaProyectos.setItems(proyectos);
        } else {
            ObservableList<ProyectoAutomatizacion> filtrados = proyectos.stream()
                .filter(p -> p.getArea().equals(filtro))
                .collect(Collectors.toCollection(FXCollections::observableArrayList));
            tablaProyectos.setItems(filtrados);
        }
        actualizarEstadisticas();
    }
    
    private void refrescarTabla() {
        // Limpiar todos los datos de ejecución para comenzar desde cero
        for (ProyectoAutomatizacion proyecto : proyectos) {
            proyecto.setEstado(EstadoEjecucion.PENDIENTE);
            proyecto.setUltimaEjecucion(null);
            proyecto.setDuracionSegundos(0);
            proyecto.setReporteGenerado(false);
            proyecto.setMensajeError(null);
        }
        
        // Guardar el estado limpio
        guardarProyectos();
        
        // Forzar actualización visual de la tabla
        tablaProyectos.refresh();
        
        // Aplicar filtro y actualizar estadísticas
        aplicarFiltro();
        actualizarEstadisticas();
        
        agregarLog("✅ Tabla limpiada y lista para nueva ejecución - " + proyectos.size() + " proyecto(s)");
    }
    
    private void actualizarEstadisticas() {
        long total = proyectos.size();
        long seleccionados = proyectos.stream().filter(ProyectoAutomatizacion::isSeleccionado).count();
        long exitosos = proyectos.stream().filter(p -> p.getEstado() == EstadoEjecucion.EXITOSO).count();
        long fallidos = proyectos.stream().filter(p -> p.getEstado() == EstadoEjecucion.FALLIDO).count();
        
        lblEstadisticas.setText(String.format(
            " Total: %d |  Seleccionados: %d |  Exitosos: %d |  Fallidos: %d",
            total, seleccionados, exitosos, fallidos
        ));
    }
    
    private void agregarLog(String mensaje) {
        Platform.runLater(() -> {
            String timestamp = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            logArea.appendText("[" + timestamp + "] " + mensaje + "\n");
        });
    }
    
    private void guardarProyectos() {
        try {
            GestorConfiguracion.guardarProyectos(new ArrayList<>(proyectos));
        } catch (IOException e) {
            mostrarAlerta("Error", "No se pudo guardar: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }
    
    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Platform.runLater(() -> {
            Alert alert = new Alert(tipo);
            alert.setTitle(titulo);
            alert.setContentText(mensaje);
            alert.showAndWait();
        });
    }
    
    private void generarInformes() {
        List<ProyectoAutomatizacion> seleccionados = proyectos.stream()
            .filter(ProyectoAutomatizacion::isSeleccionado)
            .collect(Collectors.toList());
        
        if (seleccionados.isEmpty()) {
            mostrarAlerta("Sin seleccion", "Selecciona al menos un proyecto para generar informes", Alert.AlertType.WARNING);
            return;
        }
        
        agregarLog("=== GENERACION DE INFORMES INICIADA ===");
        agregarLog("Proyectos seleccionados: " + seleccionados.size());
        
        new Thread(() -> {
            int exitosos = 0;
            int fallidos = 0;
            StringBuilder errores = new StringBuilder();
            
            for (ProyectoAutomatizacion proyAuto : seleccionados) {
                try {
                    Platform.runLater(() -> agregarLog("Procesando: " + proyAuto.getNombre()));
                    
                    // Validar que tenga configuracion minima
                    if (proyAuto.getRutaTemplateWord() == null || proyAuto.getRutaTemplateWord().isEmpty()) {
                        Platform.runLater(() -> agregarLog("  ERROR: Sin template Word configurado"));
                        fallidos++;
                        errores.append("- ").append(proyAuto.getNombre()).append(": Sin template Word\n");
                        continue;
                    }
                    
                    // Crear proyecto del generador con TODAS las configuraciones
                    Proyecto proyecto = new Proyecto();
                    proyecto.setNombre(proyAuto.getNombre());
                    
                    // USAR EL FLAG DE PROYECTO MANUAL (o detectar si no está configurado)
                    boolean esManual = proyAuto.isEsProyectoManual() || 
                                      (proyAuto.getRuta() == null || proyAuto.getRuta().trim().isEmpty());
                    proyecto.setEsProyectoManual(esManual);
                    
                    // Ruta de imagenes: usar configurada o carpeta de capturas por defecto
                    String rutaImgs = proyAuto.getRutaImagenes();
                    if (!esManual && (rutaImgs == null || rutaImgs.isEmpty())) {
                        rutaImgs = proyAuto.getRuta() + "\\test-output\\capturaPantalla";
                    }
                    proyecto.setRutaImagenes(rutaImgs);
                    
                    proyecto.setRutaTemplateWord(proyAuto.getRutaTemplateWord());
                    proyecto.setRutaSalidaWord(proyAuto.getRutaSalidaWord() != null ? proyAuto.getRutaSalidaWord() : proyAuto.getRuta());
                    proyecto.setRutaSalidaPdf(proyAuto.getRutaSalidaPdf() != null ? proyAuto.getRutaSalidaPdf() : proyAuto.getRuta());
                    
                    // Patrones de imagenes
                    List<String> patrones = proyAuto.getImagenesSeleccionadas();
                    if (patrones == null || patrones.isEmpty()) {
                        // Si no hay selección manual, obtener TODAS las imágenes del último set
                        List<String> patronesDetectados = new ArrayList<>();
                        java.io.File dirImagenes = new java.io.File(rutaImgs);
                        if (dirImagenes.exists()) {
                            java.io.File[] archivos = dirImagenes.listFiles((dir, name) -> 
                                name.toLowerCase().endsWith(".png") || 
                                name.toLowerCase().endsWith(".jpg") || 
                                name.toLowerCase().endsWith(".jpeg"));
                            
                            if (archivos != null && archivos.length > 0) {
                                // Ordenar por fecha (más recientes primero)
                                java.util.Arrays.sort(archivos, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                                
                                // Obtener el último set (imágenes con timestamp similar - 2 minutos)
                                long timestampBase = archivos[0].lastModified();
                                for (java.io.File img : archivos) {
                                    if (Math.abs(img.lastModified() - timestampBase) <= 120000) {
                                        patronesDetectados.add(img.getName());
                                    }
                                }
                                
                                int totalDetectadas = patronesDetectados.size();
                                Platform.runLater(() -> agregarLog("  📸 Detectadas " + totalDetectadas + " imágenes del último set"));
                            }
                        }
                        
                        if (patronesDetectados.isEmpty()) {
                            patronesDetectados.add("*.png");
                            patronesDetectados.add("*.jpg");
                        }
                        patrones = patronesDetectados;
                    }
                    proyecto.setImagenesSeleccionadas(patrones);
                    
                    // Generar usando el GeneradorDocumentos original (mantiene TODAS las funcionalidades)
                    GeneradorDocumentos generador = new GeneradorDocumentos(proyecto);
                    boolean exito = generador.generar();
                    
                    if (exito) {
                        exitosos++;
                        proyAuto.setReporteGenerado(true); // Marcar como generado
                        final String docWord = proyecto.getDocumentoWordGenerado();
                        final String docPdf = proyecto.getDocumentoPdfGenerado();
                        Platform.runLater(() -> {
                            agregarLog("  Exitoso!");
                            agregarLog("    Word: " + docWord);
                            agregarLog("    PDF: " + docPdf);
                            tablaProyectos.refresh(); // Actualizar tabla para mostrar ✅
                        });
                    } else {
                        fallidos++;
                        proyAuto.setReporteGenerado(false); // Marcar como fallido
                        final String error = proyecto.getMensajeError();
                        errores.append("- ").append(proyAuto.getNombre()).append(": ").append(error).append("\n");
                        Platform.runLater(() -> {
                            agregarLog("  Error: " + error);
                            tablaProyectos.refresh();
                        });
                    }
                    
                } catch (Exception e) {
                    fallidos++;
                    String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    errores.append("- ").append(proyAuto.getNombre()).append(": ").append(errorMsg).append("\n");
                    Platform.runLater(() -> agregarLog("  Error inesperado: " + errorMsg));
                }
            }
            
            final int totalExitosos = exitosos;
            final int totalFallidos = fallidos;
            final String mensajeErrores = errores.toString();
            
            Platform.runLater(() -> {
                agregarLog("=== GENERACION COMPLETADA ===");
                agregarLog("Exitosos: " + totalExitosos);
                agregarLog("Fallidos: " + totalFallidos);
                
                // Guardar estado actualizado de los proyectos
                guardarProyectos();
                
                String mensaje = String.format("Generacion de informes completada:\n\nExitosos: %d\nFallidos: %d",
                    totalExitosos, totalFallidos);
                if (totalFallidos > 0) {
                    mensaje += "\n\nErrores:\n" + mensajeErrores;
                }
                
                mostrarAlerta("Informes Generados", mensaje, 
                    totalFallidos == 0 ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
            });
        }).start();
    }
    
    /**
     * Abre diálogo para cargar imágenes manualmente en proyectos sin automatización
     * Formato de imágenes esperado: YYYY-MM-DD_HH-MM.png
     */
    private void abrirDialogoCargaImagenesManual(ProyectoAutomatizacion proyecto) {
        Dialog<List<String>> dialog = new Dialog<>();
        dialog.setTitle("Cargar Imágenes Manual - " + proyecto.getNombre());
        dialog.setHeaderText("📁 Proyecto sin automatización - Selección manual de imágenes");
        
        ButtonType btnGuardar = new ButtonType("Guardar y Usar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, ButtonType.CANCEL);
        
        VBox contenido = new VBox(15);
        contenido.setPadding(new Insets(20));
        contenido.setMinWidth(700);
        
        Label lblInfo = new Label("Este proyecto no tiene ruta de automatización configurada.\nPuedes cargar imágenes manualmente para generar el informe.\n\n✨ Funcionalidades:\n  • Arrastra archivos desde Windows Explorer\n  • Reordena imágenes arrastrándolas dentro de la lista");
        lblInfo.setStyle("-fx-font-size: 12px; -fx-text-fill: #555;");
        lblInfo.setWrapText(true);
        
        Label lblFormato = new Label("📋 Formatos aceptados: 2025-11-20_22-29.png o 2025-11-20_22-29-45.png");
        lblFormato.setStyle("-fx-font-weight: bold; -fx-text-fill: #2196F3;");
        
        // Ruta de carpeta de imágenes
        Label lblCarpeta = new Label("Carpeta de imágenes:");
        TextField txtCarpeta = new TextField();
        txtCarpeta.setPromptText("Selecciona la carpeta con las imágenes...");
        
        Button btnExaminar = new Button("📂 Examinar Carpeta");
        btnExaminar.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        
        HBox hboxCarpeta = new HBox(10, txtCarpeta, btnExaminar);
        HBox.setHgrow(txtCarpeta, Priority.ALWAYS);
        
        // Lista de imágenes seleccionadas (ordenadas cronológicamente)
        Label lblSeleccionadas = new Label("✅ Imágenes seleccionadas (orden: más antigua → más nueva):");
        lblSeleccionadas.setStyle("-fx-font-weight: bold;");
        
        javafx.scene.control.ListView<String> listViewImagenes = new javafx.scene.control.ListView<>();
        listViewImagenes.setPrefHeight(300);
        
        Label lblCount = new Label("Total: 0 imágenes");
        lblCount.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        
        // Lista observable para mantener las imágenes ordenadas
        javafx.collections.ObservableList<String> imagenesOrdenadas = javafx.collections.FXCollections.observableArrayList();
        listViewImagenes.setItems(imagenesOrdenadas);
        
        // ===== DRAG & DROP INTERNO: Reordenar elementos dentro de la lista =====
        listViewImagenes.setCellFactory(lv -> {
            javafx.scene.control.ListCell<String> cell = new javafx.scene.control.ListCell<String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(new java.io.File(item).getName());
                    }
                }
            };
            
            // Detectar inicio de arrastre
            cell.setOnDragDetected(event -> {
                if (!cell.isEmpty()) {
                    javafx.scene.input.Dragboard db = cell.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
                    javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                    content.putString(cell.getItem());
                    db.setContent(content);
                    event.consume();
                }
            });
            
            // Permitir soltar sobre esta celda
            cell.setOnDragOver(event -> {
                if (event.getGestureSource() != cell && event.getDragboard().hasString()) {
                    event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
                }
                event.consume();
            });
            
            // Ejecutar reordenamiento al soltar
            cell.setOnDragDropped(event -> {
                javafx.scene.input.Dragboard db = event.getDragboard();
                boolean success = false;
                if (db.hasString() && !cell.isEmpty()) {
                    String draggedItem = db.getString();
                    String targetItem = cell.getItem();
                    
                    int draggedIdx = imagenesOrdenadas.indexOf(draggedItem);
                    int targetIdx = imagenesOrdenadas.indexOf(targetItem);
                    
                    if (draggedIdx >= 0 && targetIdx >= 0) {
                        imagenesOrdenadas.remove(draggedIdx);
                        if (draggedIdx < targetIdx) {
                            imagenesOrdenadas.add(targetIdx, draggedItem);
                        } else {
                            imagenesOrdenadas.add(targetIdx, draggedItem);
                        }
                        success = true;
                    }
                }
                event.setDropCompleted(success);
                event.consume();
            });
            
            return cell;
        });
        
        // ===== DRAG & DROP EXTERNO: Arrastrar archivos desde Windows Explorer =====
        listViewImagenes.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            event.consume();
        });
        
        listViewImagenes.setOnDragDropped(event -> {
            javafx.scene.input.Dragboard db = event.getDragboard();
            boolean success = false;
            
            if (db.hasFiles()) {
                for (java.io.File file : db.getFiles()) {
                    String name = file.getName().toLowerCase();
                    // Validar extensión
                    if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                        String rutaAbsoluta = file.getAbsolutePath();
                        if (!imagenesOrdenadas.contains(rutaAbsoluta)) {
                            imagenesOrdenadas.add(rutaAbsoluta);
                            success = true;
                        }
                    }
                }
                
                if (success) {
                    lblCount.setText("Total: " + imagenesOrdenadas.size() + " imágenes");
                    mostrarAlerta("Imágenes agregadas", 
                        "Se agregaron las imágenes arrastradas.\nPuedes reordenarlas arrastrándolas dentro de la lista.", 
                        Alert.AlertType.INFORMATION);
                }
            }
            
            event.setDropCompleted(success);
            event.consume();
        });
        
        btnExaminar.setOnAction(e -> {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle("Seleccionar carpeta de imágenes");
            java.io.File carpeta = chooser.showDialog(dialog.getOwner());
            
            if (carpeta != null && carpeta.exists()) {
                txtCarpeta.setText(carpeta.getAbsolutePath());
                
                // Buscar todas las imágenes con formato YYYY-MM-DD_HH-MM.png o YYYY-MM-DD_HH-MM-SS.png
                java.io.File[] archivos = carpeta.listFiles((dir, name) -> {
                    String lower = name.toLowerCase();
                    return (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) 
                        && name.matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}(-\\d{2})?\\.(png|jpg|jpeg)");
                });
                
                if (archivos != null && archivos.length > 0) {
                    // Ordenar por nombre (que incluye timestamp) de más antigua a más nueva
                    java.util.Arrays.sort(archivos, (a, b) -> a.getName().compareTo(b.getName()));
                    
                    imagenesOrdenadas.clear();
                    for (java.io.File img : archivos) {
                        imagenesOrdenadas.add(img.getAbsolutePath());
                    }
                    
                    lblCount.setText("Total: " + archivos.length + " imágenes");
                    mostrarAlerta("Imágenes cargadas", 
                        "Se encontraron " + archivos.length + " imágenes válidas.\nOrden: más antigua → más nueva", 
                        Alert.AlertType.INFORMATION);
                } else {
                    imagenesOrdenadas.clear();
                    lblCount.setText("Total: 0 imágenes");
                    mostrarAlerta("Sin imágenes", 
                        "No se encontraron imágenes con formato válido (YYYY-MM-DD_HH-MM.png o YYYY-MM-DD_HH-MM-SS.png)", 
                        Alert.AlertType.WARNING);
                }
            }
        });
        
        contenido.getChildren().addAll(
            lblInfo, 
            new javafx.scene.control.Separator(),
            lblFormato,
            lblCarpeta,
            hboxCarpeta,
            new javafx.scene.control.Separator(),
            lblSeleccionadas,
            listViewImagenes,
            lblCount
        );
        
        dialog.getDialogPane().setContent(contenido);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnGuardar) {
                if (imagenesOrdenadas.isEmpty()) {
                    mostrarAlerta("Error", "Debes seleccionar al menos una imagen", Alert.AlertType.ERROR);
                    return null;
                }
                return new ArrayList<>(imagenesOrdenadas);
            }
            return null;
        });
        
        Optional<List<String>> resultado = dialog.showAndWait();
        resultado.ifPresent(imagenes -> {
            // Guardar las imágenes en el proyecto (rutas absolutas)
            proyecto.setImagenesSeleccionadas(imagenes);
            // Marcar como proyecto manual (sin automatización)
            proyecto.setEsProyectoManual(true);
            // Guardar la carpeta como "ruta de imágenes"
            if (!txtCarpeta.getText().isEmpty()) {
                proyecto.setRutaImagenes(txtCarpeta.getText());
            }
            guardarProyectos();
            agregarLog("✓ " + proyecto.getNombre() + ": " + imagenes.size() + " imágenes cargadas manualmente");
            mostrarAlerta("Imágenes guardadas", 
                "Se guardaron " + imagenes.size() + " imágenes.\nYa puedes generar el informe.", 
                Alert.AlertType.INFORMATION);
        });
    }
    
    /**
     * Muestra un selector visual de imágenes para ordenamiento manual
     * @param rutaImagenes Carpeta donde están las imágenes
     * @param imagenesPreseleccionadas Lista de imágenes ya seleccionadas (puede ser null)
     * @return Lista ordenada de nombres de archivos seleccionados
     */
    private List<String> mostrarSelectorImagenesVisual(String rutaImagenes, List<String> imagenesPreseleccionadas) {
        if (rutaImagenes == null || rutaImagenes.isEmpty()) {
            mostrarAlerta("Sin ruta", "Primero selecciona la carpeta de imágenes", Alert.AlertType.WARNING);
            return new ArrayList<>();
        }
        
        java.io.File dirImagenes = new java.io.File(rutaImagenes);
        if (!dirImagenes.exists() || !dirImagenes.isDirectory()) {
            mostrarAlerta("Ruta inválida", "La carpeta de imágenes no existe", Alert.AlertType.ERROR);
            return new ArrayList<>();
        }
        
        // Obtener el último set completo de imágenes
        java.io.File[] archivos = dirImagenes.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".jpeg"));
        
        if (archivos == null || archivos.length == 0) {
            mostrarAlerta("Sin imágenes", "No se encontraron imágenes en la carpeta", Alert.AlertType.WARNING);
            return new ArrayList<>();
        }
        
        // Ordenar por fecha de modificación (las más recientes primero)
        java.util.Arrays.sort(archivos, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        
        // Obtener solo las imágenes de la última ejecución del test
        // Se agrupan todas las imágenes que están dentro de 15 minutos desde la más reciente
        // Esto permite tests largos pero evita mezclar con ejecuciones anteriores
        List<java.io.File> setReciente = new ArrayList<>();
        if (archivos.length > 0) {
            long timestampBase = archivos[0].lastModified();
            for (java.io.File img : archivos) {
                // 15 minutos = 900000 ms (permite tests largos sin perder evidencias)
                if (Math.abs(img.lastModified() - timestampBase) <= 900000) {
                    setReciente.add(img);
                }
            }
        }
        
        if (setReciente.isEmpty()) {
            setReciente = java.util.Arrays.asList(archivos);
        }
        
        // Crear ventana modal
        Stage stage = new Stage();
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.setTitle("Seleccionar Imágenes para el Informe");
        
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        
        Label lblInstrucciones = new Label("📸 Selecciona las imágenes en el orden que aparecerán en el informe");
        lblInstrucciones.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        
        // Lista de imágenes seleccionadas (en orden)
        List<String> imagenesOrdenadas = new ArrayList<>();
        if (imagenesPreseleccionadas != null) {
            imagenesOrdenadas.addAll(imagenesPreseleccionadas);
        }
        
        // Panel de imágenes seleccionadas
        VBox panelSeleccionadas = new VBox(5);
        panelSeleccionadas.setStyle("-fx-border-color: #4CAF50; -fx-border-width: 2; -fx-padding: 10; -fx-background-color: #f0f8f0;");
        Label lblSeleccionadas = new Label("✅ Imágenes seleccionadas (en orden):");
        lblSeleccionadas.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        panelSeleccionadas.getChildren().add(lblSeleccionadas);
        
        javafx.scene.control.ListView<String> listViewSeleccionadas = new javafx.scene.control.ListView<>();
        listViewSeleccionadas.setPrefHeight(150);
        listViewSeleccionadas.getItems().addAll(imagenesOrdenadas);
        panelSeleccionadas.getChildren().add(listViewSeleccionadas);
        
        // Panel de imágenes disponibles (declarar antes para usarlo en el botón quitar)
        Label lblDisponibles = new Label("🖼️ Imágenes disponibles (haz clic en ➕ para agregar):");
        lblDisponibles.setStyle("-fx-font-weight: bold; -fx-padding: 10 0 5 0; -fx-font-size: 13px;");
        
        javafx.scene.layout.FlowPane panelImagenesFlow = new javafx.scene.layout.FlowPane();
        panelImagenesFlow.setHgap(10);
        panelImagenesFlow.setVgap(10);
        panelImagenesFlow.setPadding(new Insets(10));
        panelImagenesFlow.setStyle("-fx-background-color: white;");
        
        // Botón para eliminar imagen seleccionada de la lista
        Button btnQuitar = new Button("➖ Quitar de la Lista");
        btnQuitar.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-weight: bold;");
        btnQuitar.setOnAction(e -> {
            String seleccionada = listViewSeleccionadas.getSelectionModel().getSelectedItem();
            if (seleccionada != null) {
                imagenesOrdenadas.remove(seleccionada);
                listViewSeleccionadas.getItems().remove(seleccionada);
                
                // Reactivar el botón de esa imagen en el flow
                actualizarBotonesFlow(panelImagenesFlow, imagenesOrdenadas);
            }
        });
        
        // Botón para limpiar toda la configuración de imágenes guardadas
        Button btnLimpiarConfig = new Button("🗑️ Limpiar Configuración");
        btnLimpiarConfig.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold;");
        btnLimpiarConfig.setTooltip(new javafx.scene.control.Tooltip("Limpia todas las imágenes guardadas (no elimina archivos físicos)"));
        btnLimpiarConfig.setOnAction(e -> {
            if (imagenesOrdenadas.isEmpty()) {
                mostrarAlerta("Sin configuración", "No hay imágenes seleccionadas para limpiar", Alert.AlertType.INFORMATION);
                return;
            }
            
            Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
            confirmacion.setTitle("Confirmar limpieza");
            confirmacion.setHeaderText("¿Limpiar configuración de imágenes?");
            confirmacion.setContentText("Se eliminarán " + imagenesOrdenadas.size() + " imágenes de la lista.\nLos archivos físicos NO serán eliminados.");
            
            if (confirmacion.showAndWait().get() == ButtonType.OK) {
                imagenesOrdenadas.clear();
                listViewSeleccionadas.getItems().clear();
                
                // Reactivar todos los botones
                actualizarBotonesFlow(panelImagenesFlow, imagenesOrdenadas);
                
                mostrarAlerta("Configuración limpiada", "Se limpiaron todas las imágenes de la lista", Alert.AlertType.INFORMATION);
            }
        });
        
        HBox botonesListaSeleccionadas = new HBox(10);
        botonesListaSeleccionadas.getChildren().addAll(btnQuitar, btnLimpiarConfig);
        panelSeleccionadas.getChildren().add(botonesListaSeleccionadas);
        
        // Agregar cada imagen del set reciente
        for (java.io.File imgFile : setReciente) {
            try {
                javafx.scene.image.Image image = new javafx.scene.image.Image(imgFile.toURI().toString());
                javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(image);
                iv.setFitWidth(200);
                iv.setPreserveRatio(true);
                
                VBox box = new VBox(5);
                box.setAlignment(Pos.CENTER);
                box.setUserData(imgFile.getName()); // Guardar nombre para identificación
                
                Label lblNombre = new Label(imgFile.getName());
                lblNombre.setStyle("-fx-font-size: 10px;");
                lblNombre.setMaxWidth(200);
                lblNombre.setWrapText(true);
                
                Button btnAgregar = new Button("➕ Agregar como #" + (imagenesOrdenadas.size() + 1));
                btnAgregar.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
                
                // Deshabilitar si ya está seleccionada
                if (imagenesOrdenadas.contains(imgFile.getName())) {
                    btnAgregar.setDisable(true);
                    btnAgregar.setText("✓ Seleccionada");
                    btnAgregar.setStyle("-fx-background-color: #999; -fx-text-fill: white;");
                }
                
                btnAgregar.setOnAction(e -> {
                    imagenesOrdenadas.add(imgFile.getName());
                    listViewSeleccionadas.getItems().add(imgFile.getName());
                    btnAgregar.setDisable(true);
                    btnAgregar.setText("✓ Seleccionada");
                    btnAgregar.setStyle("-fx-background-color: #999; -fx-text-fill: white;");
                    
                    // Actualizar números en todos los botones activos
                    actualizarBotonesFlow(panelImagenesFlow, imagenesOrdenadas);
                });
                
                // Click en imagen para ver tamaño completo
                iv.setOnMouseClicked(e -> {
                    Stage fullStage = new Stage();
                    fullStage.setTitle(imgFile.getName());
                    javafx.scene.image.ImageView fullIv = new javafx.scene.image.ImageView(image);
                    javafx.scene.control.ScrollPane fullScroll = new javafx.scene.control.ScrollPane(fullIv);
                    Scene fullScene = new Scene(fullScroll, 1000, 700);
                    fullStage.setScene(fullScene);
                    fullStage.show();
                });
                
                box.getChildren().addAll(iv, lblNombre, btnAgregar);
                box.setStyle("-fx-border-color: #ccc; -fx-padding: 5; -fx-background-color: white;");
                
                panelImagenesFlow.getChildren().add(box);
            } catch (Exception e) {
                // Ignorar imágenes que no se pueden cargar
            }
        }
        
        javafx.scene.control.ScrollPane scrollDisponibles = new javafx.scene.control.ScrollPane(panelImagenesFlow);
        scrollDisponibles.setFitToWidth(true);
        scrollDisponibles.setPrefHeight(400);
        
        // Botones de acción
        HBox botonesAccion = new HBox(10);
        botonesAccion.setAlignment(Pos.CENTER_RIGHT);
        botonesAccion.setPadding(new Insets(10, 0, 0, 0));
        
        Label lblInfo = new Label("Total seleccionadas: 0");
        lblInfo.setStyle("-fx-font-weight: bold;");
        
        // Actualizar contador cuando cambie la lista
        listViewSeleccionadas.getItems().addListener((javafx.collections.ListChangeListener.Change<? extends String> c) -> {
            lblInfo.setText("Total seleccionadas: " + listViewSeleccionadas.getItems().size());
        });
        lblInfo.setText("Total seleccionadas: " + imagenesOrdenadas.size());
        
        Button btnAceptar = new Button("✓ Aceptar");
        btnAceptar.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        btnAceptar.setOnAction(e -> stage.close());
        
        Button btnCancelar = new Button("✗ Cancelar");
        btnCancelar.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        btnCancelar.setOnAction(e -> {
            imagenesOrdenadas.clear();
            stage.close();
        });
        
        HBox.setHgrow(lblInfo, Priority.ALWAYS);
        botonesAccion.getChildren().addAll(lblInfo, btnAceptar, btnCancelar);
        
        root.getChildren().addAll(lblInstrucciones, panelSeleccionadas, lblDisponibles, scrollDisponibles, botonesAccion);
        
        Scene scene = new Scene(root, 1200, 800);
        stage.setScene(scene);
        stage.showAndWait();
        
        return imagenesOrdenadas;
    }
    
    /**
     * Actualiza los botones del FlowPane después de quitar una imagen
     */
    private void actualizarBotonesFlow(javafx.scene.layout.FlowPane flowPane, List<String> imagenesSeleccionadas) {
        for (javafx.scene.Node node : flowPane.getChildren()) {
            if (node instanceof VBox) {
                VBox vb = (VBox) node;
                String nombreImagen = (String) vb.getUserData();
                
                if (vb.getChildren().size() > 2 && vb.getChildren().get(2) instanceof Button) {
                    Button btn = (Button) vb.getChildren().get(2);
                    
                    if (imagenesSeleccionadas.contains(nombreImagen)) {
                        // Ya está seleccionada
                        btn.setDisable(true);
                        btn.setText("✓ Seleccionada");
                        btn.setStyle("-fx-background-color: #999; -fx-text-fill: white;");
                    } else {
                        // Disponible para seleccionar
                        btn.setDisable(false);
                        btn.setText("➕ Agregar como #" + (imagenesSeleccionadas.size() + 1));
                        btn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
                    }
                }
            }
        }
    }
    
    public Parent getRoot() {
        return root;
    }
}

