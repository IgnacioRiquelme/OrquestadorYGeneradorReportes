package com.orquestador.ui;

import com.orquestador.modelo.ProyectoAutomatizacion;
import com.orquestador.modelo.ProyectoAutomatizacion.*;
import com.orquestador.modelo.Proyecto;
import com.orquestador.modelo.ConfiguracionInforme;
import com.orquestador.servicio.EjecutorAutomatizaciones;
import com.orquestador.servicio.GeneradorDocumentos;
import com.orquestador.util.GestorConfiguracion;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
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
import java.text.Normalizer;
import java.util.stream.Collectors;

/**
 * Controlador principal de la interfaz
 */
public class ControladorPrincipal {
    
    private BorderPane root;
    private TableView<ProyectoAutomatizacion> tablaProyectos;
    private ObservableList<ProyectoAutomatizacion> proyectos;
    private FilteredList<ProyectoAutomatizacion> proyectosFiltrados;
    private SortedList<ProyectoAutomatizacion> proyectosOrdenados;
    private TextArea logArea;
    private Label lblEstadisticas;
    private Button btnEjecutarSeleccionados, btnCancelarEjecucion, btnVerCapturas, btnGenerarInformes, btnAgregar, btnEliminar, btnAutomatizar;
    private ComboBox<String> cboFiltroArea;
    private ComboBox<String> cboFiltroVPN;
    private EjecutorAutomatizaciones ejecutor;
    private boolean ejecutando = false;
    private boolean automatizacionProgramada = false;
    private java.util.Timer timerAutomatizacion;
    private List<ProyectoAutomatizacion> proyectosAutomatizados;
    
    public ControladorPrincipal() {
        ejecutor = new EjecutorAutomatizaciones();
        proyectos = FXCollections.observableArrayList(GestorConfiguracion.cargarProyectos());

        // Crear lista filtrada
        proyectosFiltrados = new FilteredList<>(proyectos, p -> true);

        // Crear lista ordenada con comparador personalizado para ordenar por nombre con números
        proyectosOrdenados = new SortedList<>(proyectosFiltrados, (p1, p2) -> {
            String nombre1 = p1.getNombre();
            String nombre2 = p2.getNombre();

            // Extraer números al inicio de los nombres para ordenamiento natural
            String num1 = extraerNumeroInicio(nombre1);
            String num2 = extraerNumeroInicio(nombre2);

            if (!num1.isEmpty() && !num2.isEmpty()) {
                try {
                    int n1 = Integer.parseInt(num1);
                    int n2 = Integer.parseInt(num2);
                    int cmp = Integer.compare(n1, n2);
                    if (cmp != 0) return cmp;
                } catch (NumberFormatException e) {
                    // Si no son números válidos, comparar como texto
                }
            }

            // Comparación alfabética si no hay números o son iguales
            return nombre1.compareToIgnoreCase(nombre2);
        });

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

        btnEliminar = new Button(" Eliminar Seleccionados");
        btnEliminar.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold;");
        btnEliminar.setOnAction(e -> eliminarSeleccionados());
        
        cboFiltroArea = new ComboBox<>();
        cboFiltroArea.setPromptText("Filtrar por Area");
        cboFiltroArea.setEditable(false);
        cboFiltroArea.getItems().addAll("Todas", "Clientes", "Comercial", "Integraciones", "Siniestros");
        cboFiltroArea.setValue("Todas");
        cboFiltroArea.setOnAction(e -> aplicarFiltro());
        
        cboFiltroVPN = new ComboBox<>();
        cboFiltroVPN.setPromptText("Filtrar por VPN");
        cboFiltroVPN.setEditable(false);
        cboFiltroVPN.getItems().addAll("Todas", "Sin VPN", "Hibrido", "Con VPN BCI", "Con VPN CLIP");
        cboFiltroVPN.setValue("Todas");
        cboFiltroVPN.setOnAction(e -> aplicarFiltro());
        
        Button btnRefrescar = new Button(" Refrescar");
        btnRefrescar.setOnAction(e -> refrescarTabla());
        
        Button btnActualizarChromeDriver = new Button("🔄 Actualizar ChromeDriver");
        btnActualizarChromeDriver.setStyle("-fx-background-color: #00BCD4; -fx-text-fill: white; -fx-font-weight: bold;");
        btnActualizarChromeDriver.setOnAction(e -> actualizarChromeDriver());
        
        botonesAccion.getChildren().addAll(btnAgregar, btnEliminar, new Separator(javafx.geometry.Orientation.VERTICAL),
                           new Label("Area:"), cboFiltroArea, new Label("VPN:"), cboFiltroVPN, btnRefrescar, btnActualizarChromeDriver);
        
        // Botones de ejecucin
        HBox botonesEjecucion = new HBox(10);
        botonesEjecucion.setAlignment(Pos.CENTER_LEFT);
        
        btnEjecutarSeleccionados = new Button(" Ejecutar Seleccionados");
        btnEjecutarSeleccionados.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        btnEjecutarSeleccionados.setOnAction(e -> ejecutarSeleccionados());

        btnCancelarEjecucion = new Button(" Cancelar Ejecución");
        btnCancelarEjecucion.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        btnCancelarEjecucion.setDisable(true);
        btnCancelarEjecucion.setOnAction(e -> cancelarEjecucion());
        
        btnGenerarInformes = new Button(" Generar Informes");
        btnGenerarInformes.setStyle("-fx-background-color: #FF6F00; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        btnGenerarInformes.setOnAction(e -> generarInformes());

        btnAutomatizar = new Button(" Automatizar");
        btnAutomatizar.setStyle("-fx-background-color: #9C27B0; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        btnAutomatizar.setOnAction(e -> automatizarEjecucion());

        botonesEjecucion.getChildren().addAll(btnEjecutarSeleccionados, btnCancelarEjecucion, btnGenerarInformes, btnAutomatizar);
        
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
        tablaProyectos.setItems(proyectosOrdenados);
        
        // Columna Seleccionar
        TableColumn<ProyectoAutomatizacion, Boolean> colSeleccionar = new TableColumn<>("");
        
        // Checkbox en header para seleccionar/deseleccionar todos los proyectos
        CheckBox headerCheckBox = new CheckBox();
        headerCheckBox.setOnAction(e -> {
            boolean selected = headerCheckBox.isSelected();
            // Aplicar a todos los proyectos (deseleccionar/seleccionar global)
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
        colEstado.setCellValueFactory(cellData -> {
            ProyectoAutomatizacion proyecto = cellData.getValue();
            // Mostrar estado solo si está seleccionado o en ejecución
            if (proyecto.isSeleccionado() || proyecto.getEstado() != EstadoEjecucion.PENDIENTE) {
                return new javafx.beans.property.SimpleStringProperty(proyecto.getEstado().getDescripcion());
            } else {
                return new javafx.beans.property.SimpleStringProperty("");
            }
        });
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
        
        // Columna Ver Log
        TableColumn<ProyectoAutomatizacion, Void> colVerLog = new TableColumn<>("Log");
        colVerLog.setCellFactory(param -> new javafx.scene.control.TableCell<ProyectoAutomatizacion, Void>() {
            private final Button btnVerLog = new Button("📄 Ver Log");
            {
                btnVerLog.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
                btnVerLog.setOnAction(event -> {
                    ProyectoAutomatizacion proyecto = getTableView().getItems().get(getIndex());
                    abrirLogEjecucion(proyecto);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableView().getItems().get(getIndex()) == null) {
                    setGraphic(null);
                } else {
                    ProyectoAutomatizacion proyecto = getTableView().getItems().get(getIndex());
                    // Mostrar botón solo si hay log de ejecución
                    if (proyecto.getRutaLogEjecucion() != null && !proyecto.getRutaLogEjecucion().trim().isEmpty()) {
                        setGraphic(btnVerLog);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });
        colVerLog.setMinWidth(100);
        
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
                        // Ajustar texto/icono del botón Config según el nombre del proyecto
                        String nombreProyecto = proyecto.getNombre() != null ? proyecto.getNombre().toLowerCase() : "";
                        // Para los proyectos de 'Contactenos' (BCI / Zenit / Corredores) mostramos un ícono más descriptivo
                        if (esProyectoContactenos(proyecto)) {
                            btnConfigurar.setText("🔐 Credenciales");
                            btnConfigurar.setTooltip(new Tooltip("Editar credenciales del proyecto"));
                        } else {
                            btnConfigurar.setText("⚙️ Config");
                            btnConfigurar.setTooltip(new Tooltip("Configurar proyecto"));
                        }
                        setGraphic(btnConfigurar);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });
        colConfigurar.setMinWidth(150);
        
        tablaProyectos.getColumns().addAll(colSeleccionar, colNombre, colRuta, colArea, colVPN, colTipo, colEstado, colUltima, colDuracion, colReporte, colVerLog, colConfigurar);

        // Agregar menú contextual (click derecho) para editar, ver capturas y explorar directorio
        ContextMenu contextMenu = new ContextMenu();
        MenuItem menuEditar = new MenuItem("Editar Proyecto");
        menuEditar.setOnAction(e -> editarProyecto());

        MenuItem menuVerCapturas = new MenuItem("Ver Capturas");
        menuVerCapturas.setOnAction(e -> mostrarCapturas());

        MenuItem menuExplorar = new MenuItem("Explorar directorio");
        menuExplorar.setOnAction(e -> {
            ProyectoAutomatizacion seleccionado = tablaProyectos.getSelectionModel().getSelectedItem();
            if (seleccionado != null && seleccionado.getRuta() != null && !seleccionado.getRuta().trim().isEmpty()) {
                try {
                    new ProcessBuilder("explorer.exe", seleccionado.getRuta()).start();
                } catch (Exception ex) {
                    logArea.appendText("Error abriendo el Explorador: " + ex.getMessage() + "\n");
                }
            } else {
                logArea.appendText("No hay ruta disponible para el proyecto seleccionado.\n");
            }
        });

        contextMenu.getItems().addAll(menuEditar, menuVerCapturas, menuExplorar);
        tablaProyectos.setContextMenu(contextMenu);

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
            // Si el campo ya contiene una ruta válida, abrir ahí
            try {
                if (txtRuta.getText() != null && !txtRuta.getText().trim().isEmpty()) {
                    java.io.File init = new java.io.File(txtRuta.getText());
                    if (init.exists() && init.isDirectory()) {
                        chooser.setInitialDirectory(init);
                    }
                }
            } catch (Exception ignored) {}
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
            try {
                if (txtRutaImagenes.getText() != null && !txtRutaImagenes.getText().trim().isEmpty()) {
                    java.io.File init = new java.io.File(txtRutaImagenes.getText());
                    if (init.exists() && init.isDirectory()) {
                        chooser.setInitialDirectory(init);
                    }
                }
            } catch (Exception ignored) {}
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
            // Abrir inicialmente en la carpeta central de templates si existe
            try {
                java.io.File defaultTemplates = new java.io.File("C:\\Users\\IARC\\Desktop\\Nuevos esqueletos");
                if (defaultTemplates.exists() && defaultTemplates.isDirectory()) {
                    chooser.setInitialDirectory(defaultTemplates);
                } else if (txtTemplate.getText() != null && !txtTemplate.getText().trim().isEmpty()) {
                    java.io.File init = new java.io.File(txtTemplate.getText()).getParentFile();
                    if (init != null && init.exists()) chooser.setInitialDirectory(init);
                }
            } catch (Exception ignored) {}
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
        
        // ===== SECCIÓN DE MÚLTIPLES INFORMES =====
        javafx.scene.control.Separator sep2 = new javafx.scene.control.Separator();
        Label lblInformes = new Label("📑 Configuración de Informes Múltiples (Opcional)");
        lblInformes.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #FF6B6B;");
        
        Label lblInfoInformes = new Label("Si el proyecto genera imágenes para múltiples informes, configúralos aquí.\nCada informe filtrará las imágenes según el patrón definido.");
        lblInfoInformes.setStyle("-fx-font-size: 11px; -fx-text-fill: #666; -fx-font-style: italic;");
        lblInfoInformes.setWrapText(true);
        
        VBox contenedorInformes = new VBox(10);
        contenedorInformes.setPadding(new Insets(10));
        contenedorInformes.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #ddd; -fx-border-radius: 5;");
        
        javafx.collections.ObservableList<ConfiguracionInforme> listaInformes = javafx.collections.FXCollections.observableArrayList();
        
        Button btnAgregarInforme = new Button("➕ Agregar Informe");
        btnAgregarInforme.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        btnAgregarInforme.setOnAction(e -> {
            VBox filaInforme = new VBox(5);
            filaInforme.setPadding(new Insets(10));
            filaInforme.setStyle("-fx-background-color: white; -fx-border-color: #ccc; -fx-border-radius: 3; -fx-padding: 10;");
            
            ConfiguracionInforme nuevoInforme = new ConfiguracionInforme();
            listaInformes.add(nuevoInforme);
            
            Label lblNumInforme = new Label("Informe #" + (listaInformes.size() + 1));
            lblNumInforme.setStyle("-fx-font-weight: bold; -fx-text-fill: #2196F3;");
            
            TextField txtNombreArchivo = new TextField();
            txtNombreArchivo.setPromptText("Nombre del archivo de salida (sin extensión)");
            txtNombreArchivo.textProperty().addListener((obs, old, val) -> nuevoInforme.setNombreArchivo(val));
            
            TextField txtTemplateInforme = new TextField();
            txtTemplateInforme.setPromptText("Ruta del template Word para este informe");
            txtTemplateInforme.textProperty().addListener((obs, old, val) -> nuevoInforme.setTemplateWord(val));
            
            Button btnExaminarTemplateInforme = new Button("📁");
            btnExaminarTemplateInforme.setOnAction(ev -> {
                javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
                chooser.setTitle("Seleccionar template Word");
                chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Word", "*.docx"));
                java.io.File file = chooser.showOpenDialog(dialog.getOwner());
                if (file != null) {
                    txtTemplateInforme.setText(file.getAbsolutePath());
                }
            });
            
            Label lblResumenImagenes = new Label("(Sin imágenes seleccionadas)");
            lblResumenImagenes.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
            
            Button btnSeleccionarImagenes = new Button("🖼️ Seleccionar Imágenes");
            btnSeleccionarImagenes.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
            btnSeleccionarImagenes.setOnAction(ev -> {
                String rutaImagenes = txtRutaImagenes.getText();
                if (rutaImagenes == null || rutaImagenes.trim().isEmpty()) {
                    mostrarAlerta("Error", "Primero debes configurar la 'Ruta de imágenes' en la sección superior", Alert.AlertType.ERROR);
                    return;
                }
                
                abrirSelectorImagenesParaInforme(rutaImagenes, nuevoInforme, lblResumenImagenes);
            });
            
            Button btnEliminarInforme = new Button("🗑️ Eliminar");
            btnEliminarInforme.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
            btnEliminarInforme.setOnAction(ev -> {
                contenedorInformes.getChildren().remove(filaInforme);
                listaInformes.remove(nuevoInforme);
            });
            
            HBox hboxTemplateInforme = new HBox(10);
            hboxTemplateInforme.getChildren().addAll(txtTemplateInforme, btnExaminarTemplateInforme);
            HBox.setHgrow(txtTemplateInforme, Priority.ALWAYS);
            
            filaInforme.getChildren().addAll(
                lblNumInforme,
                new Label("Nombre del Archivo:"),
                txtNombreArchivo,
                new Label("Template Word:"),
                hboxTemplateInforme,
                new Label("Imágenes:"),
                lblResumenImagenes,
                btnSeleccionarImagenes,
                btnEliminarInforme
            );
            
            contenedorInformes.getChildren().add(filaInforme);
        });
        
        contenido.getChildren().addAll(sep2, lblInformes, lblInfoInformes, btnAgregarInforme, contenedorInformes);
        
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
                    // Para proyectos con automatización, convertir rutas absolutas a patrones
                    // Para proyectos manuales, mantener rutas absolutas
                    if (!rutaVacia) {
                        List<String> patrones = new ArrayList<>();
                        for (String rutaAbsoluta : imagenesSeleccionadasManualmente) {
                            String nombreArchivo = new java.io.File(rutaAbsoluta).getName();
                            String patron = extraerPatronDeImagen(nombreArchivo);
                            if (patron != null && !patron.isEmpty()) {
                                patrones.add(patron);
                            } else {
                                patrones.add(nombreArchivo); // fallback
                            }
                        }
                        proyecto.setImagenesSeleccionadas(patrones);
                    } else {
                        // Proyecto manual: mantener rutas absolutas
                        proyecto.setImagenesSeleccionadas(new ArrayList<>(imagenesSeleccionadasManualmente));
                    }
                }

                // Guardar lista de informes configurados
                if (!listaInformes.isEmpty()) {
                    proyecto.setInformes(new ArrayList<>(listaInformes));
                }

                return proyecto;
            }
            return null;
        });
        
        Optional<ProyectoAutomatizacion> resultado = dialog.showAndWait();
        resultado.ifPresent(proyecto -> {
            proyectos.add(proyecto);
            guardarProyectos();

            // Resetear filtros para que el proyecto nuevo aparezca inmediatamente
            cboFiltroArea.setValue("Todas");
            cboFiltroVPN.setValue("Todas");
            aplicarFiltro();

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
            try {
                if (txtRuta.getText() != null && !txtRuta.getText().trim().isEmpty()) {
                    java.io.File init = new java.io.File(txtRuta.getText());
                    if (init.exists() && init.isDirectory()) chooser.setInitialDirectory(init);
                }
            } catch (Exception ignored) {}
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
            // Abrir inicialmente en la carpeta central de templates si existe
            try {
                java.io.File defaultTemplates = new java.io.File("C:\\Users\\IARC\\Desktop\\Nuevos esqueletos");
                if (defaultTemplates.exists() && defaultTemplates.isDirectory()) {
                    chooser.setInitialDirectory(defaultTemplates);
                } else if (txtTemplate.getText() != null && !txtTemplate.getText().trim().isEmpty()) {
                    java.io.File init = new java.io.File(txtTemplate.getText()).getParentFile();
                    if (init != null && init.exists()) chooser.setInitialDirectory(init);
                }
            } catch (Exception ignored) {}
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
        
        // ===== SECCIÓN DE MÚLTIPLES INFORMES =====
        javafx.scene.control.Separator sep2 = new javafx.scene.control.Separator();
        Label lblInformes = new Label("📑 Configuración de Informes Múltiples (Opcional)");
        lblInformes.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #FF6B6B;");
        
        Label lblInfoInformes = new Label("Si el proyecto genera imágenes para múltiples informes, configúralos aquí.\nCada informe filtrará las imágenes según el patrón definido.");
        lblInfoInformes.setStyle("-fx-font-size: 11px; -fx-text-fill: #666; -fx-font-style: italic;");
        lblInfoInformes.setWrapText(true);
        
        VBox contenedorInformes = new VBox(10);
        contenedorInformes.setPadding(new Insets(10));
        contenedorInformes.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #ddd; -fx-border-radius: 5;");
        
        javafx.collections.ObservableList<ConfiguracionInforme> listaInformes = javafx.collections.FXCollections.observableArrayList();
        
        // Cargar informes existentes
        if (seleccionado.getInformes() != null && !seleccionado.getInformes().isEmpty()) {
            listaInformes.addAll(seleccionado.getInformes());
        }
        
        Button btnAgregarInforme = new Button("➕ Agregar Informe");
        btnAgregarInforme.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        btnAgregarInforme.setOnAction(e -> {
            VBox filaInforme = new VBox(5);
            filaInforme.setPadding(new Insets(10));
            filaInforme.setStyle("-fx-background-color: white; -fx-border-color: #ccc; -fx-border-radius: 3; -fx-padding: 10;");
            
            ConfiguracionInforme nuevoInforme = new ConfiguracionInforme();
            listaInformes.add(nuevoInforme);
            
            Label lblNumInforme = new Label("Informe #" + (listaInformes.size() + 1));
            lblNumInforme.setStyle("-fx-font-weight: bold; -fx-text-fill: #2196F3;");
            
            TextField txtNombreArchivoEdit = new TextField();
            txtNombreArchivoEdit.setPromptText("Nombre del archivo de salida (sin extensión)");
            txtNombreArchivoEdit.textProperty().addListener((obs, old, val) -> nuevoInforme.setNombreArchivo(val));
            
            TextField txtTemplateInforme = new TextField();
            txtTemplateInforme.setPromptText("Ruta del template Word para este informe");
            txtTemplateInforme.textProperty().addListener((obs, old, val) -> nuevoInforme.setTemplateWord(val));
            
            Button btnExaminarTemplateInforme = new Button("📁");
            btnExaminarTemplateInforme.setOnAction(ev -> {
                javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
                chooser.setTitle("Seleccionar template Word");
                chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Word", "*.docx"));
                java.io.File file = chooser.showOpenDialog(dialog.getOwner());
                if (file != null) {
                    txtTemplateInforme.setText(file.getAbsolutePath());
                }
            });
            
            Label lblResumenImagenes = new Label("(Sin imágenes seleccionadas)");
            lblResumenImagenes.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
            
            Button btnSeleccionarImagenes = new Button("🖼️ Seleccionar Imágenes");
            btnSeleccionarImagenes.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
            btnSeleccionarImagenes.setOnAction(ev -> {
                String rutaImagenes = txtRutaImagenes.getText();
                if (rutaImagenes == null || rutaImagenes.trim().isEmpty()) {
                    mostrarAlerta("Error", "Primero debes configurar la 'Ruta de imágenes' en la sección superior", Alert.AlertType.ERROR);
                    return;
                }
                
                abrirSelectorImagenesParaInforme(rutaImagenes, nuevoInforme, lblResumenImagenes);
            });
            
            Button btnEliminarInforme = new Button("🗑️ Eliminar");
            btnEliminarInforme.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
            btnEliminarInforme.setOnAction(ev -> {
                contenedorInformes.getChildren().remove(filaInforme);
                listaInformes.remove(nuevoInforme);
            });
            
            HBox hboxTemplateInforme = new HBox(10);
            hboxTemplateInforme.getChildren().addAll(txtTemplateInforme, btnExaminarTemplateInforme);
            HBox.setHgrow(txtTemplateInforme, Priority.ALWAYS);
            
            filaInforme.getChildren().addAll(
                lblNumInforme,
                new Label("Nombre del archivo:"),
                txtNombreArchivoEdit,
                new Label("Template Word:"),
                hboxTemplateInforme,
                new Label("Imágenes:"),
                lblResumenImagenes,
                btnSeleccionarImagenes,
                btnEliminarInforme
            );
            
            contenedorInformes.getChildren().add(filaInforme);
        });
        
        // Cargar informes existentes en la UI
        for (int i = 0; i < listaInformes.size(); i++) {
            ConfiguracionInforme informeExistente = listaInformes.get(i);
            
            VBox filaInforme = new VBox(5);
            filaInforme.setPadding(new Insets(10));
            filaInforme.setStyle("-fx-background-color: white; -fx-border-color: #ccc; -fx-border-radius: 3; -fx-padding: 10;");
            
            Label lblNumInforme = new Label("Informe #" + (i + 2));
            lblNumInforme.setStyle("-fx-font-weight: bold; -fx-text-fill: #2196F3;");
            
            TextField txtNombreArchivo = new TextField(informeExistente.getNombreArchivo());
            txtNombreArchivo.setPromptText("Nombre del archivo de salida (sin extensión)");
            txtNombreArchivo.textProperty().addListener((obs, old, val) -> informeExistente.setNombreArchivo(val));
            
            TextField txtTemplateInforme = new TextField(informeExistente.getTemplateWord());
            txtTemplateInforme.setPromptText("Ruta del template Word para este informe");
            txtTemplateInforme.textProperty().addListener((obs, old, val) -> informeExistente.setTemplateWord(val));
            
            Button btnExaminarTemplateInforme = new Button("📁");
            btnExaminarTemplateInforme.setOnAction(ev -> {
                javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
                chooser.setTitle("Seleccionar template Word");
                chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Word", "*.docx"));
                java.io.File file = chooser.showOpenDialog(dialog.getOwner());
                if (file != null) {
                    txtTemplateInforme.setText(file.getAbsolutePath());
                }
            });
            
            Label lblResumenImagenes = new Label(
                informeExistente.getImagenesSeleccionadas() != null && !informeExistente.getImagenesSeleccionadas().isEmpty()
                    ? informeExistente.getImagenesSeleccionadas().size() + " imagen(es) | Patrón: " + informeExistente.getPatronImagenes()
                    : "(Sin imágenes seleccionadas)"
            );
            lblResumenImagenes.setStyle(
                informeExistente.getImagenesSeleccionadas() != null && !informeExistente.getImagenesSeleccionadas().isEmpty()
                    ? "-fx-font-size: 11px; -fx-text-fill: #4CAF50; -fx-font-weight: bold;"
                    : "-fx-font-size: 11px; -fx-text-fill: #666;"
            );
            
            Button btnSeleccionarImagenes = new Button("🖼️ Seleccionar Imágenes");
            btnSeleccionarImagenes.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
            btnSeleccionarImagenes.setOnAction(ev -> {
                String rutaImagenes = txtRutaImagenes.getText();
                if (rutaImagenes == null || rutaImagenes.trim().isEmpty()) {
                    mostrarAlerta("Error", "Primero debes configurar la 'Ruta de imágenes' en la sección superior", Alert.AlertType.ERROR);
                    return;
                }
                
                abrirSelectorImagenesParaInforme(rutaImagenes, informeExistente, lblResumenImagenes);
            });
            
            Button btnEliminarInforme = new Button("🗑️ Eliminar");
            btnEliminarInforme.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
            btnEliminarInforme.setOnAction(ev -> {
                contenedorInformes.getChildren().remove(filaInforme);
                listaInformes.remove(informeExistente);
            });
            
            HBox hboxTemplateInforme = new HBox(10);
            hboxTemplateInforme.getChildren().addAll(txtTemplateInforme, btnExaminarTemplateInforme);
            HBox.setHgrow(txtTemplateInforme, Priority.ALWAYS);
            
            filaInforme.getChildren().addAll(
                lblNumInforme,
                new Label("Nombre del archivo:"),
                txtNombreArchivo,
                new Label("Template Word:"),
                hboxTemplateInforme,
                new Label("Imágenes:"),
                lblResumenImagenes,
                btnSeleccionarImagenes,
                btnEliminarInforme
            );
            
            contenedorInformes.getChildren().add(filaInforme);
        }
        
        contenido.getChildren().addAll(sep2, lblInformes, lblInfoInformes, btnAgregarInforme, contenedorInformes);
        
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
                
                // Guardar lista de informes configurados
                if (!listaInformes.isEmpty()) {
                    seleccionado.setInformes(new ArrayList<>(listaInformes));
                } else {
                    seleccionado.setInformes(new ArrayList<>());
                }
                
                return seleccionado;
            }
            return null;
        });
        
        Optional<ProyectoAutomatizacion> resultado = dialog.showAndWait();
        resultado.ifPresent(proyecto -> {
            guardarProyectos();
            tablaProyectos.refresh();
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

    // Determina si el proyecto es uno de los 'Contactenos' concretos (ignorando mayúsculas y tildes)
    private boolean esProyectoContactenos(ProyectoAutomatizacion proyecto) {
        if (proyecto == null || proyecto.getNombre() == null) return false;
        String nombre = proyecto.getNombre().trim();
        if (nombre.isEmpty()) return false;

        // Normalizar (quitar tildes) y comparar en minúsculas
        String normalized = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        // Quitar caracteres especiales y compactar espacios
        String clave = normalized.toLowerCase().replaceAll("[^\\p{Alnum}\\s]", " ").replaceAll("\\s+", " ").trim();

        // Eliminar prefijo numérico tipo "15 - " si existe
        clave = clave.replaceFirst("^\\d+\\s*[-:]?\\s*", "");

        String[] targets = new String[] {
            "contactenos bci seguros",
            "contactenos zenit seguros",
            "contactenos corredores generales bci"
        };

        for (String t : targets) {
            if (clave.contains(t)) return true;
        }

        return false;
    }

    // Detectar la ruta de imágenes probando rutas candidatas dentro del proyecto
    private String detectarRutaImagenesDesdeRuta(String ruta) {
        if (ruta == null || ruta.isEmpty()) return null;
        
        // Normalizar separadores
        String normalized = ruta.replace('/', '\\').trim();
        java.io.File base = new java.io.File(normalized);
        
        // Candidatos de rutas relativas (intentar en orden)
        String[] candidatos = new String[] {
            "test-output\\capturaPantalla",
            "Archivos\\screenshots\\evidencia",
            "Archivos\\screenshots",
            "screenshots\\evidencia",
            "screenshots",
            "capturaPantalla",
            "test-output"
        };
        
        // Buscar en la ruta base actual
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
        
        // Si no encontró en rutas relativas, buscar subiendo niveles (en caso de rutas anidadas)
        java.io.File current = base;
        int maxLevels = 5; // máximo 5 niveles hacia arriba
        while (current != null && maxLevels > 0) {
            for (String rel : candidatos) {
                java.io.File cand = new java.io.File(current, rel);
                if (cand.exists() && cand.isDirectory()) {
                    try {
                        return cand.getCanonicalPath();
                    } catch (java.io.IOException e) {
                        return cand.getAbsolutePath();
                    }
                }
            }
            current = current.getParentFile();
            maxLevels--;
        }
        
        return null;
    }

    // Diálogo modal para configurar credenciales de proyectos especiales
    private void abrirDialogoCredenciales(ProyectoAutomatizacion proyecto) {
        try {
            // Cargar credenciales actuales
            com.orquestador.modelo.Credenciales cred = com.orquestador.util.GestorCredenciales.cargarCredenciales(proyecto);

            String nombre = proyecto.getNombre() != null ? proyecto.getNombre().toLowerCase() : "";

            // Nota: proyectos que ya no son especiales (p.ej. 'vida') serán filtrados
            // por GestorCredenciales.esProyectoEspecial(...) y no mostrarán el botón de configuración.

            Dialog<com.orquestador.modelo.Credenciales> dialog = new Dialog<>();
            dialog.setTitle("Configurar Credenciales - " + proyecto.getNombre());
            dialog.setHeaderText("Actualizar credenciales para: " + proyecto.getNombre());

            ButtonType btnGuardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, ButtonType.CANCEL);

            VBox contenido = new VBox(12);
            contenido.setPadding(new Insets(18));
            contenido.setMinWidth(480);

            Label lblCredenciales = new Label("📝 Credenciales");
            lblCredenciales.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

            // Botón para ver contraseña actual
            Button btnVerContrasenaActual = new Button("👁️ Ver Contraseña Actual");
            btnVerContrasenaActual.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
            btnVerContrasenaActual.setOnAction(e -> {
                String contrasenaActual = "";
                if (nombre.contains("zenit")) {
                    contrasenaActual = cred.getPasword();
                } else if (nombre.contains("corredores")) {
                    contrasenaActual = cred.getPasword2();
                } else {
                    contrasenaActual = cred.getPasword();
                }

                if (contrasenaActual == null || contrasenaActual.isEmpty()) {
                    mostrarAlerta("Contraseña Actual", "No hay contraseña configurada actualmente.", Alert.AlertType.INFORMATION);
                } else {
                    mostrarAlerta("Contraseña Actual", "Contraseña actual: " + contrasenaActual, Alert.AlertType.INFORMATION);
                }
            });

            HBox header = new HBox(10);
            header.setAlignment(Pos.CENTER_LEFT);
            header.getChildren().addAll(lblCredenciales, btnVerContrasenaActual);

            contenido.getChildren().add(header);

            // Campos mínimos: usuario y contraseña (según tipo)
            java.util.Map<String, javafx.scene.control.Control> campos = new java.util.HashMap<>();

            VBox vboxCred = new VBox(8);
            vboxCred.setPadding(new Insets(8));
            vboxCred.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 5;");

            if (nombre.contains("zenit")) {
                vboxCred.getChildren().add(new Label("Usuario:"));
                TextField txtUser = new TextField(cred.getUser());
                campos.put("user", txtUser);
                vboxCred.getChildren().add(txtUser);

                vboxCred.getChildren().add(new Label("Contraseña:"));
                PasswordField txtPassword = new PasswordField();
                txtPassword.setText(cred.getPasword());
                campos.put("password", txtPassword);
                vboxCred.getChildren().add(txtPassword);

            } else if (nombre.contains("corredores")) {
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
                // Por defecto (BCI u otros): user + password
                vboxCred.getChildren().add(new Label("Usuario:"));
                TextField txtUser = new TextField(cred.getUser());
                campos.put("user", txtUser);
                vboxCred.getChildren().add(txtUser);

                vboxCred.getChildren().add(new Label("Contraseña:"));
                PasswordField txtPassword = new PasswordField();
                txtPassword.setText(cred.getPasword());
                campos.put("password", txtPassword);
                vboxCred.getChildren().add(txtPassword);
            }

            contenido.getChildren().add(vboxCred);

            dialog.getDialogPane().setContent(contenido);

            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == btnGuardar) {
                    com.orquestador.modelo.Credenciales credActualizada = new com.orquestador.modelo.Credenciales();

                    if (nombre.contains("zenit")) {
                        if (campos.containsKey("user")) credActualizada.setUser(((TextField) campos.get("user")).getText());
                        if (campos.containsKey("password")) credActualizada.setPasword(((PasswordField) campos.get("password")).getText());
                    } else if (nombre.contains("corredores")) {
                        if (campos.containsKey("user2")) credActualizada.setUser2(((TextField) campos.get("user2")).getText());
                        if (campos.containsKey("password2")) credActualizada.setPasword2(((PasswordField) campos.get("password2")).getText());
                    } else {
                        if (campos.containsKey("user")) credActualizada.setUser(((TextField) campos.get("user")).getText());
                        if (campos.containsKey("password")) credActualizada.setPasword(((PasswordField) campos.get("password")).getText());
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
        // Usar solo los proyectos visibles en la tabla (filtrados) para evitar ejecutar proyectos ocultos por filtros
        List<ProyectoAutomatizacion> seleccionados = tablaProyectos.getItems().stream()
            .filter(ProyectoAutomatizacion::isSeleccionado)
            .collect(Collectors.toList());

        if (seleccionados.isEmpty()) {
            mostrarAlerta("Advertencia", "No hay proyectos seleccionados en la vista actual", Alert.AlertType.WARNING);
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
        btnCancelarEjecucion.setDisable(false);
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
    
    private void cancelarEjecucion() {
        ejecutando = false;
        ejecutor.detener();
        agregarLog("🚫 EJECUCIÓN CANCELADA - Deteniendo proceso actual y cancelando ejecuciones siguientes");
        finalizarEjecucion();
    }
    
    private void abrirLogEjecucion(ProyectoAutomatizacion proyecto) {
        if (proyecto.getRutaLogEjecucion() == null || proyecto.getRutaLogEjecucion().trim().isEmpty()) {
            mostrarAlerta("Sin log", "No hay log de ejecución disponible para este proyecto", Alert.AlertType.INFORMATION);
            return;
        }
        
        java.io.File logFile = new java.io.File(proyecto.getRutaLogEjecucion());
        
        if (!logFile.exists()) {
            mostrarAlerta("Log no encontrado", "El archivo de log no existe:\n" + proyecto.getRutaLogEjecucion(), Alert.AlertType.WARNING);
            return;
        }
        
        try {
            // Abrir el archivo con el editor predeterminado del sistema
            java.awt.Desktop.getDesktop().open(logFile);
            agregarLog("📄 Abriendo log de: " + proyecto.getNombre());
        } catch (Exception e) {
            mostrarAlerta("Error", "No se pudo abrir el archivo de log:\n" + e.getMessage(), Alert.AlertType.ERROR);
            agregarLog("❌ Error abriendo log: " + e.getMessage());
        }
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
        
        // Determinar la ruta de las imágenes: usar ruta configurada o ruta por defecto
        String rutaImagenes = seleccionado.getRutaImagenes();
        java.io.File carpetaCapturas;

        if (rutaImagenes != null && !rutaImagenes.trim().isEmpty()) {
            // Usar ruta de imágenes configurada (para proyectos con selección manual)
            carpetaCapturas = new java.io.File(rutaImagenes);
            System.out.println("[DEBUG] Usando ruta de imágenes configurada: " + carpetaCapturas.getAbsolutePath());
        } else {
            // Usar ruta por defecto del proyecto
            carpetaCapturas = new java.io.File(seleccionado.getRuta(), "test-output/capturaPantalla");
            System.out.println("[DEBUG] Usando ruta por defecto: " + carpetaCapturas.getAbsolutePath());
        }

        System.out.println("[DEBUG] Ruta del proyecto: " + seleccionado.getRuta());
        System.out.println("[DEBUG] Ruta de imágenes configurada: " + rutaImagenes);
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
        btnCancelarEjecucion.setDisable(true);
        btnAgregar.setDisable(false);
        btnEliminar.setDisable(false);
        actualizarEstadisticas();
    }
    
    private void aplicarFiltro() {
        String filtroArea = cboFiltroArea.getValue();
        String filtroVpn = cboFiltroVPN != null ? cboFiltroVPN.getValue() : null;

        proyectosFiltrados.setPredicate(p -> {
            boolean areaOk = true;
            boolean vpnOk = true;
            if (filtroArea != null && !filtroArea.equals("Todas")) {
                areaOk = p.getArea() != null && p.getArea().equals(filtroArea);
            }
            if (filtroVpn != null) {
                switch (filtroVpn) {
                    case "Sin VPN":
                        vpnOk = p.getTipoVPN() == TipoVPN.SIN_VPN;
                        break;
                    case "Hibrido":
                        vpnOk = p.getTipoVPN() == TipoVPN.HIBRIDO;
                        break;
                    case "Con VPN BCI":
                        vpnOk = p.getTipoVPN() == TipoVPN.VPN_BCI;
                        break;
                    case "Con VPN CLIP":
                        vpnOk = p.getTipoVPN() == TipoVPN.VPN_CLIP;
                        break;
                    default:
                        vpnOk = true;
                }
            }
            return areaOk && vpnOk;
        });

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

        // Resetear filtros para mostrar todos los proyectos
        proyectosFiltrados.setPredicate(p -> true);

        // Forzar actualización visual de la tabla
        tablaProyectos.refresh();

        // Aplicar filtro actual y actualizar estadísticas
        aplicarFiltro();
        actualizarEstadisticas();

        agregarLog("✅ Tabla limpiada y lista para nueva ejecución - " + proyectos.size() + " proyecto(s)");
    }
    
    private void actualizarEstadisticas() {
        long total = proyectos.size();
        long visibles = tablaProyectos.getItems().size();
        long seleccionados = proyectos.stream().filter(ProyectoAutomatizacion::isSeleccionado).count();
        long exitosos = proyectos.stream().filter(p -> p.getEstado() == EstadoEjecucion.EXITOSO).count();
        long fallidos = proyectos.stream().filter(p -> p.getEstado() == EstadoEjecucion.FALLIDO).count();

        lblEstadisticas.setText(String.format(
            " Total: %d |  Visibles: %d |  Seleccionados: %d |  Exitosos: %d |  Fallidos: %d",
            total, visibles, seleccionados, exitosos, fallidos
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
            int informesExitosos = 0;
            int informesFallidos = 0;
            int proyectosExitosos = 0;
            int proyectosFallidos = 0;
            StringBuilder errores = new StringBuilder();
            
            for (ProyectoAutomatizacion proyAuto : seleccionados) {
                // Calcular cuántos informes tiene este proyecto (principal + adicionales)
                final int totalInformesProyecto = 1 + (proyAuto.getInformes() != null ? proyAuto.getInformes().size() : 0);
                
                try {
                    Platform.runLater(() -> agregarLog("Procesando: " + proyAuto.getNombre() + " (" + totalInformesProyecto + " informe" + (totalInformesProyecto > 1 ? "s" : "") + ")"));
                    
                    // Validar que tenga configuracion minima
                    if (proyAuto.getRutaTemplateWord() == null || proyAuto.getRutaTemplateWord().isEmpty()) {
                        Platform.runLater(() -> agregarLog("  ERROR: Sin template Word configurado"));
                        proyectosFallidos++;
                        informesFallidos += totalInformesProyecto;
                        errores.append("- ").append(proyAuto.getNombre()).append(": Sin template Word\n");
                        continue;
                    }
                    
                    // Crear proyecto del generador con TODAS las configuraciones
                    Proyecto proyecto = new Proyecto();
                    proyecto.setNombre(proyAuto.getNombre());
                    
                    // COPIAR LOS INFORMES DEL ProyectoAutomatizacion al Proyecto
                    if (proyAuto.getInformes() != null && !proyAuto.getInformes().isEmpty()) {
                        List<com.orquestador.modelo.ConfiguracionInforme> copiaInformes = new ArrayList<>(proyAuto.getInformes());
                        proyecto.setInformes(copiaInformes);
                        Platform.runLater(() -> agregarLog("  Informes configurados: " + copiaInformes.size()));
                    }
                    
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
                    
                    // Generar usando el GeneradorDocumentos
                    GeneradorDocumentos generador = new GeneradorDocumentos(proyecto);
                    
                    // Forzar generación múltiple si proyAuto tiene informes adicionales
                    boolean tieneInformesAdicionales = (proyAuto.getInformes() != null && !proyAuto.getInformes().isEmpty());
                    boolean exito;
                    
                    if (tieneInformesAdicionales) {
                        // Asegurar que proyecto tenga los informes
                        if (proyecto.getInformes() == null || proyecto.getInformes().isEmpty()) {
                            proyecto.setInformes(new ArrayList<>(proyAuto.getInformes()));
                        }
                        Platform.runLater(() -> agregarLog("  Usando generador MÚLTIPLE (" + proyecto.getInformes().size() + " adicionales)"));
                        exito = generador.generar(); // Esto debería llamar a generarMultiplesInformes()
                    } else {
                        Platform.runLater(() -> agregarLog("  Usando generador SIMPLE (1 informe)"));
                        exito = generador.generar();
                    }
                    
                    if (exito) {
                        proyectosExitosos++;
                        proyAuto.setReporteGenerado(true); // Marcar como generado
                        final String docWord = proyecto.getDocumentoWordGenerado();
                        final String docPdf = proyecto.getDocumentoPdfGenerado();
                        
                        // Contar cuántos informes se generaron (por los PDFs separados por ;)
                        final int cantidadInformes = docPdf != null ? docPdf.split(";").length : 1;
                        informesExitosos += cantidadInformes;
                        
                        Platform.runLater(() -> {
                            agregarLog("  ✅ " + cantidadInformes + " informe" + (cantidadInformes > 1 ? "s generados" : " generado") + " exitosamente");
                            // Mostrar cada PDF generado
                            if (docPdf != null && docPdf.contains(";")) {
                                String[] pdfs = docPdf.split(";");
                                for (int i = 0; i < pdfs.length; i++) {
                                    String pdfPath = pdfs[i].trim();
                                    String nombrePdf = new java.io.File(pdfPath).getName();
                                    agregarLog("    [" + (i + 1) + "] " + nombrePdf);
                                }
                            } else if (docPdf != null) {
                                String nombrePdf = new java.io.File(docPdf).getName();
                                agregarLog("    PDF: " + nombrePdf);
                            }
                            tablaProyectos.refresh(); // Actualizar tabla para mostrar ✅
                        });
                    } else {
                        proyectosFallidos++;
                        informesFallidos += totalInformesProyecto;
                        proyAuto.setReporteGenerado(false); // Marcar como fallido
                        final String error = proyecto.getMensajeError();
                        errores.append("- ").append(proyAuto.getNombre()).append(": ").append(error).append("\n");
                        Platform.runLater(() -> {
                            agregarLog("  ❌ Error: " + error);
                            tablaProyectos.refresh();
                        });
                    }
                    
                } catch (Exception e) {
                    proyectosFallidos++;
                    informesFallidos += totalInformesProyecto;
                    String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    errores.append("- ").append(proyAuto.getNombre()).append(": ").append(errorMsg).append("\n");
                    Platform.runLater(() -> agregarLog("  ❌ Error inesperado: " + errorMsg));
                }
            }
            
            final int totalInformesExitosos = informesExitosos;
            final int totalInformesFallidos = informesFallidos;
            final int totalProyectosExitosos = proyectosExitosos;
            final int totalProyectosFallidos = proyectosFallidos;
            final String mensajeErrores = errores.toString();
            
            Platform.runLater(() -> {
                agregarLog("=== GENERACION COMPLETADA ===");
                agregarLog("Informes generados: " + totalInformesExitosos);
                agregarLog("Informes fallidos: " + totalInformesFallidos);
                agregarLog("Proyectos procesados: " + totalProyectosExitosos + " exitosos, " + totalProyectosFallidos + " fallidos");
                
                // Guardar estado actualizado de los proyectos
                guardarProyectos();
                
                String mensaje = String.format("Generacion de informes completada:\n\nInformes generados: %d\nInformes fallidos: %d\n\nProyectos procesados: %d exitosos, %d fallidos",
                    totalInformesExitosos, totalInformesFallidos, totalProyectosExitosos, totalProyectosFallidos);
                if (totalInformesFallidos > 0 || totalProyectosFallidos > 0) {
                    mensaje += "\n\nErrores:\n" + mensajeErrores;
                }
                
                mostrarAlerta("Informes Generados", mensaje, 
                    (totalInformesFallidos == 0 && totalProyectosFallidos == 0) ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
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
    
    /**
     * Abre selector de imágenes para un informe específico
     * Muestra diálogo idéntico al del informe principal, con selección múltiple
     */
    private void abrirSelectorImagenesParaInforme(String rutaCarpeta, ConfiguracionInforme informe, Label lblResumen) {
        List<String> imagenesSeleccionadasInforme = abrirDialogoSeleccionImagenesMultiples(rutaCarpeta, informe.getPatronImagenes());
        
        if (imagenesSeleccionadasInforme != null && !imagenesSeleccionadasInforme.isEmpty()) {
            // Extraer patrón de la primera imagen si no existe
            if (informe.getPatronImagenes() == null || informe.getPatronImagenes().isEmpty()) {
                String primeraImagen = new java.io.File(imagenesSeleccionadasInforme.get(0)).getName();
                String patron = extraerPatronDeImagen(primeraImagen);
                informe.setPatronImagenes(patron);
            }
            
            // Guardar las imágenes seleccionadas
            informe.setImagenesSeleccionadas(imagenesSeleccionadasInforme);
            
            // Actualizar label de resumen
            lblResumen.setText(imagenesSeleccionadasInforme.size() + " imagen(es) | Patrón: " + informe.getPatronImagenes());
            lblResumen.setStyle("-fx-font-size: 11px; -fx-text-fill: #4CAF50; -fx-font-weight: bold;");
        }
    }
    
    /**
     * Abre diálogo de selección múltiple de imágenes con filtro por patrón opcional
     */
    private List<String> abrirDialogoSeleccionImagenesMultiples(String rutaCarpeta, String patronFiltro) {
        Dialog<List<String>> dialog = new Dialog<>();
        dialog.setTitle("Seleccionar Imágenes para el Informe");
        dialog.setHeaderText("📁 Selecciona las imágenes en el orden que aparecerán en el informe");
        
        ButtonType btnGuardar = new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, ButtonType.CANCEL);
        
        VBox contenido = new VBox(15);
        contenido.setPadding(new Insets(20));
        contenido.setMinWidth(900);
        contenido.setMaxWidth(1200);
        
        Label lblInfo = new Label("📋 Selecciona las imágenes en el orden que desees");
        lblInfo.setStyle("-fx-font-size: 12px; -fx-text-fill: #555; -fx-font-weight: bold;");
        
        // Lista de imágenes seleccionadas
        Label lblSeleccionadas = new Label("✅ Imágenes seleccionadas (en orden):");
        lblSeleccionadas.setStyle("-fx-font-weight: bold;");
        
        javafx.scene.control.ListView<String> listViewSeleccionadas = new javafx.scene.control.ListView<>();
        listViewSeleccionadas.setPrefHeight(150);
        
        javafx.collections.ObservableList<String> imagenesOrdenadas = javafx.collections.FXCollections.observableArrayList();
        listViewSeleccionadas.setItems(imagenesOrdenadas);
        
        listViewSeleccionadas.setCellFactory(lv -> new javafx.scene.control.ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(new java.io.File(item).getName());
                }
            }
        });
        
        Button btnQuitar = new Button("➖ Quitar de la Lista");
        btnQuitar.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white;");
        btnQuitar.setOnAction(e -> {
            String seleccionada = listViewSeleccionadas.getSelectionModel().getSelectedItem();
            if (seleccionada != null) {
                imagenesOrdenadas.remove(seleccionada);
            }
        });
        
        Button btnLimpiar = new Button("🗑️ Limpiar Configuración");
        btnLimpiar.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        btnLimpiar.setOnAction(e -> imagenesOrdenadas.clear());
        
        HBox hboxBotones = new HBox(10, btnQuitar, btnLimpiar);
        
        Label lblCount = new Label("Total: 0 imágenes");
        lblCount.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        imagenesOrdenadas.addListener((javafx.collections.ListChangeListener<String>) c -> {
            lblCount.setText("Total: " + imagenesOrdenadas.size() + " imágenes");
        });
        
        // Imágenes disponibles
        Label lblDisponibles = new Label("🖼️ Imágenes disponibles" + (patronFiltro != null && !patronFiltro.isEmpty() ? " (filtro: " + patronFiltro + ")" : "") + ":");
        lblDisponibles.setStyle("-fx-font-weight: bold;");
        
        javafx.scene.layout.FlowPane flowPane = new javafx.scene.layout.FlowPane();
        flowPane.setHgap(10);
        flowPane.setVgap(10);
        flowPane.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 10;");
        flowPane.setPrefWrapLength(850);
        
        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(flowPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(400);
        
        // Cargar imágenes de la carpeta
        java.io.File carpeta = new java.io.File(rutaCarpeta);
        if (carpeta.exists() && carpeta.isDirectory()) {
            java.io.File[] archivos = carpeta.listFiles();
            if (archivos != null) {
                java.util.Arrays.sort(archivos, (a, b) -> a.getName().compareTo(b.getName()));
                
                for (java.io.File archivo : archivos) {
                    String nombre = archivo.getName();
                    String nombreLower = nombre.toLowerCase();
                    
                    // Filtrar por patrón si existe
                    if (patronFiltro != null && !patronFiltro.isEmpty() && !nombre.startsWith(patronFiltro)) {
                        continue;
                    }
                    
                    if (nombreLower.endsWith(".png") || nombreLower.endsWith(".jpg") || nombreLower.endsWith(".jpeg")) {
                        VBox vbox = crearTarjetaImagen(archivo, imagenesOrdenadas);
                        flowPane.getChildren().add(vbox);
                    }
                }
            }
        }
        
        contenido.getChildren().addAll(
            lblInfo,
            new Separator(),
            lblSeleccionadas,
            listViewSeleccionadas,
            lblCount,
            hboxBotones,
            new Separator(),
            lblDisponibles,
            scrollPane
        );
        
        dialog.getDialogPane().setContent(contenido);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnGuardar) {
                return new java.util.ArrayList<>(imagenesOrdenadas);
            }
            return null;
        });
        
        return dialog.showAndWait().orElse(null);
    }
    
    /**
     * Crea tarjeta visual para una imagen con miniatura y botón de agregar
     */
    private VBox crearTarjetaImagen(java.io.File archivo, javafx.collections.ObservableList<String> imagenesOrdenadas) {
        VBox vbox = new VBox(5);
        vbox.setAlignment(javafx.geometry.Pos.CENTER);
        vbox.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-border-radius: 5; -fx-padding: 10;");
        vbox.setPrefWidth(180);
        vbox.setUserData(archivo.getAbsolutePath());
        
        // Miniatura
        javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView();
        imageView.setFitWidth(160);
        imageView.setFitHeight(120);
        imageView.setPreserveRatio(true);
        
        try {
            javafx.scene.image.Image img = new javafx.scene.image.Image(archivo.toURI().toString(), 160, 120, true, true);
            imageView.setImage(img);
        } catch (Exception e) {
            imageView.setImage(null);
        }
        
        // Nombre del archivo
        Label lblNombre = new Label(archivo.getName());
        lblNombre.setStyle("-fx-font-size: 10px; -fx-text-fill: #333;");
        lblNombre.setWrapText(true);
        lblNombre.setMaxWidth(160);
        lblNombre.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        
        // Botón agregar
        Button btnAgregar = new Button("➕ Agregar como #1");
        btnAgregar.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        btnAgregar.setMaxWidth(Double.MAX_VALUE);
        
        btnAgregar.setOnAction(e -> {
            String rutaCompleta = archivo.getAbsolutePath();
            if (!imagenesOrdenadas.contains(rutaCompleta)) {
                imagenesOrdenadas.add(rutaCompleta);
                btnAgregar.setDisable(true);
                btnAgregar.setText("✓ Seleccionada");
                btnAgregar.setStyle("-fx-background-color: #999; -fx-text-fill: white;");
            }
        });
        
        // Actualizar estado inicial
        if (imagenesOrdenadas.contains(archivo.getAbsolutePath())) {
            btnAgregar.setDisable(true);
            btnAgregar.setText("✓ Seleccionada");
            btnAgregar.setStyle("-fx-background-color: #999; -fx-text-fill: white;");
        } else {
            btnAgregar.setText("➕ Agregar como #" + (imagenesOrdenadas.size() + 1));
        }
        
        // Listener para actualizar botones cuando cambia la lista
        imagenesOrdenadas.addListener((javafx.collections.ListChangeListener<String>) c -> {
            if (imagenesOrdenadas.contains(archivo.getAbsolutePath())) {
                btnAgregar.setDisable(true);
                btnAgregar.setText("✓ Seleccionada");
                btnAgregar.setStyle("-fx-background-color: #999; -fx-text-fill: white;");
            } else {
                btnAgregar.setDisable(false);
                btnAgregar.setText("➕ Agregar como #" + (imagenesOrdenadas.size() + 1));
                btnAgregar.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
            }
        });
        
        vbox.getChildren().addAll(imageView, lblNombre, btnAgregar);
        return vbox;
    }
    
    /**
     * Inicia o detiene la automatización programada de ejecuciones
     */
    private void automatizarEjecucion() {
        if (automatizacionProgramada) {
            // Detener automatización
            detenerAutomatizacion();
            return;
        }

        // Mostrar diálogo para configurar automatización
        Dialog<javafx.util.Pair<List<ProyectoAutomatizacion>, Integer>> dialog = new Dialog<>();
        dialog.setTitle("Configurar Automatización");
        dialog.setHeaderText("Selecciona proyectos y intervalo para ejecución automática");

        ButtonType btnIniciar = new ButtonType("Iniciar Automatización", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnIniciar, ButtonType.CANCEL);

        VBox contenido = new VBox(15);
        contenido.setPadding(new Insets(20));
        contenido.setMinWidth(600);

        // Lista de proyectos disponibles
        Label lblProyectos = new Label("Proyectos disponibles:");
        javafx.scene.control.ListView<ProyectoAutomatizacion> listProyectos = new javafx.scene.control.ListView<>();
        listProyectos.setItems(proyectosOrdenados);
        listProyectos.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        listProyectos.setPrefHeight(200);

        // Configurar cómo mostrar los proyectos en la lista
        listProyectos.setCellFactory(lv -> new javafx.scene.control.ListCell<ProyectoAutomatizacion>() {
            @Override
            protected void updateItem(ProyectoAutomatizacion item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getNombre() + " (" + item.getArea() + ")");
                }
            }
        });

        // Intervalo de tiempo
        Label lblIntervalo = new Label("Intervalo entre ejecuciones (minutos):");
        Spinner<Integer> spinnerIntervalo = new Spinner<>(5, 480, 60); // 5 min a 8 horas, default 1 hora
        spinnerIntervalo.setEditable(true);

        // Información
        Label lblInfo = new Label("⚠️ La automatización ejecutará los proyectos seleccionados cada X minutos.\n" +
                                 "Los proyectos se ejecutarán en orden secuencial.\n" +
                                 "Puedes detener la automatización en cualquier momento.");
        lblInfo.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        lblInfo.setWrapText(true);

        contenido.getChildren().addAll(lblProyectos, listProyectos, lblIntervalo, spinnerIntervalo, lblInfo);

        dialog.getDialogPane().setContent(contenido);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnIniciar) {
                List<ProyectoAutomatizacion> seleccionados = listProyectos.getSelectionModel().getSelectedItems();
                if (seleccionados.isEmpty()) {
                    mostrarAlerta("Sin selección", "Debes seleccionar al menos un proyecto", Alert.AlertType.WARNING);
                    return null;
                }
                return new javafx.util.Pair<>(seleccionados, spinnerIntervalo.getValue());
            }
            return null;
        });

        Optional<javafx.util.Pair<List<ProyectoAutomatizacion>, Integer>> resultado = dialog.showAndWait();
        resultado.ifPresent(pair -> {
            proyectosAutomatizados = new ArrayList<>(pair.getKey());
            int intervaloMinutos = pair.getValue();

            iniciarAutomatizacion(intervaloMinutos);
        });
    }

    /**
     * Inicia la automatización programada
     */
    private void iniciarAutomatizacion(int intervaloMinutos) {
        automatizacionProgramada = true;
        btnAutomatizar.setText(" Detener Auto");
        btnAutomatizar.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");

        agregarLog("🔄 INICIANDO AUTOMATIZACIÓN - " + proyectosAutomatizados.size() + " proyecto(s) cada " + intervaloMinutos + " minutos");

        timerAutomatizacion = new java.util.Timer();
        timerAutomatizacion.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                if (!automatizacionProgramada) return;

                Platform.runLater(() -> {
                    if (!ejecutando) {
                        agregarLog("\n⏰ EJECUCIÓN AUTOMÁTICA PROGRAMADA");
                        ejecutarProyectos(new ArrayList<>(proyectosAutomatizados));
                    } else {
                        agregarLog("⏰ Automatización: esperando ejecución actual...");
                    }
                });
            }
        }, 0, intervaloMinutos * 60 * 1000); // Convertir minutos a milisegundos
    }

    /**
     * Detiene la automatización programada
     */
    private void detenerAutomatizacion() {
        automatizacionProgramada = false;
        if (timerAutomatizacion != null) {
            timerAutomatizacion.cancel();
            timerAutomatizacion = null;
        }

        btnAutomatizar.setText(" Automatizar");
        btnAutomatizar.setStyle("-fx-background-color: #9C27B0; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");

        agregarLog("🛑 AUTOMATIZACIÓN DETENIDA");
    }

    /**
     * Extrae el número al inicio del nombre del proyecto para ordenamiento
     * Ejemplo: "01-Proyecto A" → "01", "Proyecto B" → ""
     */
    private String extraerNumeroInicio(String nombre) {
        if (nombre == null || nombre.isEmpty()) return "";

        // Buscar patrón de números al inicio seguido de guion o espacio
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(\\d+)[\\s-]");
        java.util.regex.Matcher matcher = pattern.matcher(nombre);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return "";
    }

    /**
     * Extrae el patrón de una imagen hasta el último guión bajo antes del timestamp
     * Ejemplo: "t0001_auxilia_bci_20251024_082931.png" → "t0001_auxilia_bci_"
     */
    private String extraerPatronDeImagen(String nombreArchivo) {
        // Eliminar extensión
        int ultimoPunto = nombreArchivo.lastIndexOf('.');
        String nombreSinExtension = ultimoPunto > 0 ? nombreArchivo.substring(0, ultimoPunto) : nombreArchivo;
        
        // Buscar el último guión bajo (antes del timestamp)
        int ultimoGuion = nombreSinExtension.lastIndexOf('_');
        if (ultimoGuion > 0) {
            // Verificar si después del guión bajo hay números (timestamp)
            String despuesGuion = nombreSinExtension.substring(ultimoGuion + 1);
            if (despuesGuion.matches("\\d+")) {
                // Es un timestamp numérico, buscar el guión bajo anterior
                String antesTimestamp = nombreSinExtension.substring(0, ultimoGuion);
                int penultimoGuion = antesTimestamp.lastIndexOf('_');
                if (penultimoGuion > 0) {
                    return nombreSinExtension.substring(0, penultimoGuion + 1);
                }
            }
            // Si no es timestamp, incluir hasta este guión bajo
            return nombreSinExtension.substring(0, ultimoGuion + 1);
        }
        
        // Si no hay guión bajo, devolver nombre completo con guión bajo al final
        return nombreSinExtension + "_";
    }
    
    /**
     * Detiene la automatización al cerrar la aplicación
     */
    public void detenerAutomatizacionAlCerrar() {
        if (automatizacionProgramada) {
            detenerAutomatizacion();
        }
    }
    
    /**
     * Actualiza el chromedriver.exe en todos los proyectos que lo tengan
     */
    private void actualizarChromeDriver() {
        // Abrir diálogo para seleccionar el nuevo chromedriver.exe
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Seleccionar ChromeDriver.exe actualizado");
        fileChooser.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("ChromeDriver", "chromedriver.exe")
        );
        
        java.io.File nuevoDriver = fileChooser.showOpenDialog(root.getScene().getWindow());
        
        if (nuevoDriver == null || !nuevoDriver.exists()) {
            return; // Usuario canceló
        }
        
        // Confirmar acción
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Confirmar actualización");
        confirmacion.setHeaderText("¿Actualizar ChromeDriver en todos los proyectos?");
        confirmacion.setContentText("Se buscará y reemplazará chromedriver.exe en todos los proyectos registrados.");
        
        if (confirmacion.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        
        // Realizar actualización en background
        new Thread(() -> {
            int actualizados = 0;
            int errores = 0;
            StringBuilder detalles = new StringBuilder();
            
            for (ProyectoAutomatizacion proyecto : proyectos) {
                if (proyecto.getRuta() == null || proyecto.getRuta().trim().isEmpty()) {
                    continue; // Proyecto manual sin ruta
                }
                
                try {
                    // Buscar chromedriver.exe recursivamente en la carpeta del proyecto
                    java.io.File carpetaProyecto = new java.io.File(proyecto.getRuta());
                    java.util.List<java.io.File> driverEncontrados = buscarChromeDriver(carpetaProyecto);
                    
                    if (driverEncontrados.isEmpty()) {
                        detalles.append("⚠️ ").append(proyecto.getNombre()).append(": No se encontró chromedriver.exe\n");
                        continue;
                    }
                    
                    // Reemplazar cada chromedriver.exe encontrado
                    for (java.io.File driverAntiguo : driverEncontrados) {
                        try {
                            java.nio.file.Files.copy(
                                nuevoDriver.toPath(),
                                driverAntiguo.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING
                            );
                            detalles.append("✅ ").append(proyecto.getNombre())
                                   .append(": ").append(driverAntiguo.getAbsolutePath().replace(proyecto.getRuta(), "..."))
                                   .append("\n");
                            actualizados++;
                        } catch (Exception e) {
                            detalles.append("❌ ").append(proyecto.getNombre())
                                   .append(": Error - ").append(e.getMessage()).append("\n");
                            errores++;
                        }
                    }
                    
                } catch (Exception e) {
                    detalles.append("❌ ").append(proyecto.getNombre())
                           .append(": Error - ").append(e.getMessage()).append("\n");
                    errores++;
                }
            }
            
            // Mostrar resultado en el hilo de JavaFX
            final int totalActualizados = actualizados;
            final int totalErrores = errores;
            final String mensajeDetalles = detalles.toString();
            
            Platform.runLater(() -> {
                Alert resultado = new Alert(Alert.AlertType.INFORMATION);
                resultado.setTitle("Actualización completada");
                resultado.setHeaderText("ChromeDriver actualizado");
                
                String resumen = "✅ Actualizados: " + totalActualizados + "\n";
                if (totalErrores > 0) {
                    resumen += "❌ Errores: " + totalErrores + "\n";
                }
                
                resultado.setContentText(resumen + "\nDetalles:");
                
                // Agregar detalles en TextArea expandible
                TextArea textArea = new TextArea(mensajeDetalles);
                textArea.setEditable(false);
                textArea.setWrapText(true);
                textArea.setMaxWidth(Double.MAX_VALUE);
                textArea.setMaxHeight(Double.MAX_VALUE);
                
                resultado.getDialogPane().setExpandableContent(textArea);
                resultado.getDialogPane().setExpanded(true);
                resultado.showAndWait();
            });
            
        }).start();
    }
    
    /**
     * Busca recursivamente chromedriver.exe en una carpeta
     */
    private java.util.List<java.io.File> buscarChromeDriver(java.io.File carpeta) {
        java.util.List<java.io.File> resultados = new java.util.ArrayList<>();
        
        if (!carpeta.exists() || !carpeta.isDirectory()) {
            return resultados;
        }
        
        java.io.File[] archivos = carpeta.listFiles();
        if (archivos == null) {
            return resultados;
        }
        
        for (java.io.File archivo : archivos) {
            if (archivo.isFile() && archivo.getName().equalsIgnoreCase("chromedriver.exe")) {
                resultados.add(archivo);
            } else if (archivo.isDirectory()) {
                // Recursión en subcarpetas
                resultados.addAll(buscarChromeDriver(archivo));
            }
        }
        
        return resultados;
    }

    public Parent getRoot() {
        return root;
    }
}

