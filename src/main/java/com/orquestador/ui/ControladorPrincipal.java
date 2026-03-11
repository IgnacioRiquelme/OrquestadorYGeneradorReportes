package com.orquestador.ui;

import com.orquestador.modelo.ProyectoAutomatizacion;
import com.orquestador.modelo.ProyectoAutomatizacion.*;
import com.orquestador.modelo.Proyecto;
import com.orquestador.modelo.ConfiguracionInforme;
import com.orquestador.servicio.EjecutorAutomatizaciones;
import com.orquestador.servicio.GeneradorDocumentos;
import com.orquestador.servicio.ProgramadorTareas;
import com.orquestador.modelo.TareaProgramada;
import com.orquestador.util.GestorConfiguracion;
import com.orquestador.util.GestorConfiguracionCorreo;
import com.orquestador.util.GestorExportImport;
import com.orquestador.modelo.ConfiguracionCorreo;
import com.orquestador.servicio.EnviadorCorreoOutlook;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Point2D;
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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseEvent;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.stage.Popup;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
    private Button btnLicencia;
    private Button btnInformeExcel;
    private Button btnConfigCorreo;
    /** Configuraciones de correo por área, cargadas al inicio y persistidas en AppData */
    private List<ConfiguracionCorreo> configsCorreo = GestorConfiguracionCorreo.cargar();
    private static final String EMPRESA_DEFAULT = "BCI Seguros";
    private ComboBox<String> cboFiltroEmpresa;
    private final java.util.Set<String> empresasRegistradas = new java.util.LinkedHashSet<>();
    private ComboBox<String> cboFiltroArea;
    private ComboBox<String> cboFiltroVPN;
    private EjecutorAutomatizaciones ejecutor;
    private ProgramadorTareas programadorTareas;
    private boolean ejecutando = false;
    private boolean automatizacionProgramada = false;
    private java.util.Timer timerAutomatizacion;
    private List<ProyectoAutomatizacion> proyectosAutomatizados;
    private List<ProyectoAutomatizacion> proyectosEnEjecucion = new ArrayList<>(); // Lista de proyectos en ejecución actual
    private Thread threadEjecucion = null; // Thread actual de ejecución para poder cancelarlo
    private boolean retryGlobalHabilitado = false;
    private volatile boolean cancelRequested = false;
    
    // Variables para vista compacta y tiempo total de ejecución
    private boolean vistaCompacta = false;
    private Button btnVistaCompacta;
    private long tiempoInicioEjecucion = 0;
    private long tiempoTotalAcumulado = 0;

    // Descripciones para ayuda rápida (hover prolongado)
    private java.util.Map<Button, String> descripcionBotones = new java.util.HashMap<>();
    // Proyectos deshabilitados (persistente)
    private java.util.Set<String> proyectosDeshabilitados = new java.util.HashSet<>();
    
    public ControladorPrincipal() {
        ejecutor = new EjecutorAutomatizaciones();
        proyectos = FXCollections.observableArrayList(GestorConfiguracion.cargarProyectos());
        normalizarEmpresasEnProyectos();
        empresasRegistradas.add(EMPRESA_DEFAULT);
        empresasRegistradas.addAll(obtenerEmpresasDesdeProyectos());

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
        // Cargar preferencias (estado de vista compacta) después de inicializar la UI
        cargarPreferencias();
        // Verificar activación/licencia en primer arranque
        // Solo requerir licencia si existe el archivo .orquestador_license_required en el home del usuario
        java.io.File licFlag = new java.io.File(System.getProperty("user.home"), ".orquestador_license_required");
        if (licFlag.exists()) {
            try {
                com.orquestador.servicio.LicenciaService licencia = new com.orquestador.servicio.LicenciaService();
                if (!licencia.isActivated()) {
                    boolean ok = licencia.activateInteractive();
                    if (!ok) {
                        // Usuario no activó o error: salir
                        System.err.println("Aplicación no activada. Saliendo.");
                        throw new IllegalStateException("Aplicación no activada por el usuario.");
                    }
                } else {
                    // Chequear estado remoto (no bloqueante)
                    new Thread(() -> { licencia.checkStatus(); }).start();
                }
            } catch (Exception e) {
                // Si falla la verificación, permitir ejecución local (modo offline)
                System.err.println("Advertencia: no se pudo verificar licencia: " + e.getMessage());
            }
        }
        // Iniciar gestor de tareas programadas
        programadorTareas = new ProgramadorTareas();
        programadorTareas.setEjecucionHandler(tarea -> {
            // Resolver nombres a objetos ProyectoAutomatizacion
            List<ProyectoAutomatizacion> porEjecutar = proyectos.stream()
                    .filter(p -> tarea.getProyectos().contains(p.getNombre()))
                    .collect(Collectors.toList());

            if (tarea.getModo() == TareaProgramada.Modo.CSV_ONLY) {
                try {
                    File destino = new File(System.getProperty("user.home"), "orquestador_results_" + tarea.getId() + ".csv");
                    com.orquestador.servicio.CSVGeneradorResultados.generarCSV(porEjecutar, destino);
                    agregarLog("📝 CSV generado: " + destino.getAbsolutePath());
                    mostrarAlerta("CSV generado", "CSV creado: " + destino.getAbsolutePath(), Alert.AlertType.INFORMATION);
                } catch (Exception e) {
                    agregarLog("❌ Error generando CSV: " + e.getMessage());
                }
                return;
            }

            // Modo EXEC_AND_REPORT y EXEC_WITH_EMAIL: ejecutar y luego generar informes
            if (!porEjecutar.isEmpty()) {
                final boolean conCorreo = tarea.getModo() == TareaProgramada.Modo.EXEC_WITH_EMAIL;
                final java.time.LocalDateTime fechaDesde = tarea.getFechaHora();

                agregarLog("\n⏰ EJECUCIÓN PROGRAMADA" + (conCorreo ? " + CORREO" : "") + ": " + porEjecutar.size() + " proyecto(s)");
                ejecutarProyectos(new ArrayList<>(porEjecutar));

                // Hilo que espera fin de ejecución, genera informes y (si aplica) envía correo
                new Thread(() -> {
                    while (ejecutando) {
                        try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
                    }

                    // NO modificamos el estado global de selección para no contaminar otras empresas.
                    // generarInformes() leerá los proyectos de porEjecutar directamente.
                    tablaProyectos.refresh();
                    Platform.runLater(() -> generarInformes());

                    if (conCorreo) {
                        // Esperar un poco para que los PDFs terminen de escribirse
                        try { Thread.sleep(3000); } catch (InterruptedException e) { /* ignore */ }

                        // Agrupar proyectos por área
                        Map<String, List<ProyectoAutomatizacion>> porArea = new java.util.LinkedHashMap<>();
                        for (ProyectoAutomatizacion p : porEjecutar) {
                            String area = p.getArea() != null ? p.getArea() : "Sin Área";
                            porArea.computeIfAbsent(area, k -> new ArrayList<>()).add(p);
                        }

                        for (Map.Entry<String, List<ProyectoAutomatizacion>> entry : porArea.entrySet()) {
                            String area = entry.getKey();
                            List<ProyectoAutomatizacion> proyArea = entry.getValue();

                            java.util.Optional<ConfiguracionCorreo> cfgOpt =
                                    GestorConfiguracionCorreo.buscarPorArea(configsCorreo, area);

                            if (cfgOpt.isEmpty()) {
                                Platform.runLater(() -> agregarLog(
                                    "⚠ Sin configuración de correo para área \"" + area + "\" - omitido"));
                                continue;
                            }

                            ConfiguracionCorreo cfg = cfgOpt.get();

                            List<ProyectoAutomatizacion> exitosos = proyArea.stream()
                                .filter(p -> p.getEstado() == ProyectoAutomatizacion.EstadoEjecucion.EXITOSO)
                                .collect(Collectors.toList());
                            List<ProyectoAutomatizacion> fallidos = proyArea.stream()
                                .filter(p -> p.getEstado() != ProyectoAutomatizacion.EstadoEjecucion.EXITOSO)
                                .collect(Collectors.toList());

                            Platform.runLater(() -> agregarLog(
                                "📧 Enviando correo área \"" + area + "\" | Exitosos: " + exitosos.size()
                                + " | Con error: " + fallidos.size()));

                            EnviadorCorreoOutlook.ResultadoEnvio resultado =
                                EnviadorCorreoOutlook.enviar(cfg, exitosos, fallidos, fechaDesde);

                            Platform.runLater(() -> {
                                agregarLog(resultado.getMensaje());
                                if (!resultado.isExito() && !resultado.getDetalle().isBlank()) {
                                    agregarLog("   Detalle: "
                                        + resultado.getDetalle().lines().findFirst().orElse(""));
                                }
                            });
                        }
                    }
                }, "ProgramadorTarea-PostExec-").start();
            }
        });
    }
    
    private void inicializarUI() {
        root = new BorderPane();
        root.setPadding(new Insets(15));
        
        // Barra de menú estilo Windows + Header
        MenuBar menuBar = crearMenuBar();
        VBox header = crearHeader();
        VBox topContainer = new VBox(menuBar, header);
        root.setTop(topContainer);
        
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

        cboFiltroEmpresa = new ComboBox<>();
        cboFiltroEmpresa.setPromptText("Filtrar por Empresa");
        cboFiltroEmpresa.setEditable(false);
        cboFiltroEmpresa.setOnAction(e -> aplicarFiltro());

        Button btnAgregarEmpresa = new Button("+ Empresa");
        btnAgregarEmpresa.setOnAction(e -> agregarEmpresa());

        Button btnQuitarEmpresa = new Button("- Empresa");
        btnQuitarEmpresa.setOnAction(e -> quitarEmpresaSeleccionada());

        refrescarEmpresasDisponibles(null);
        
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
        
        Button btnRefrescar = new Button("↻ Refrescar");
        btnRefrescar.setOnAction(e -> refrescarTabla());
        
        Button btnCargarChromeDriver = new Button("📁 Actualizar ChromeDriver");
        btnCargarChromeDriver.setStyle("-fx-background-color: #009688; -fx-text-fill: white; -fx-font-weight: bold;");
        btnCargarChromeDriver.setOnAction(e -> cargarChromeDriverManual());
        
        // Configurar drag and drop para ChromeDriver
        btnCargarChromeDriver.setOnDragOver(event -> {
            if (event.getGestureSource() != btnCargarChromeDriver && event.getDragboard().hasFiles()) {
                java.util.List<File> files = event.getDragboard().getFiles();
                if (files.size() == 1 && files.get(0).getName().equalsIgnoreCase("chromedriver.exe")) {
                    event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
                    btnCargarChromeDriver.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
                }
            }
            event.consume();
        });
        
        btnCargarChromeDriver.setOnDragExited(event -> {
            btnCargarChromeDriver.setStyle("-fx-background-color: #009688; -fx-text-fill: white; -fx-font-weight: bold;");
            event.consume();
        });
        
        btnCargarChromeDriver.setOnDragDropped(event -> {
            javafx.scene.input.Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles() && db.getFiles().size() == 1) {
                File archivoArrastrado = db.getFiles().get(0);
                if (archivoArrastrado.getName().equalsIgnoreCase("chromedriver.exe")) {
                    procesarActualizacionChromeDriver(archivoArrastrado);
                    success = true;
                }
            }
            event.setDropCompleted(success);
            event.consume();
            btnCargarChromeDriver.setStyle("-fx-background-color: #009688; -fx-text-fill: white; -fx-font-weight: bold;");
        });
        
        btnVistaCompacta = new Button("📦 Vista Compacta");
        btnVistaCompacta.setStyle("-fx-background-color: #673AB7; -fx-text-fill: white; -fx-font-weight: bold;");
        btnVistaCompacta.setOnAction(e -> alternarVistaCompacta());

        btnLicencia = new Button("🔐 Licencia");
        // Si es equipo maestro, mostrar panel de gestión completo; si no, diálogo de cliente
        if (LicenciaDialog.esMaestro()) {
            btnLicencia.setText("🛡 Gestión de Licencias");
            btnLicencia.setStyle("-fx-background-color: #1565C0; -fx-text-fill: white; -fx-font-weight: bold;");
        } else {
            btnLicencia.setStyle("-fx-background-color: #607D8B; -fx-text-fill: white;");
        }
        btnLicencia.setOnAction(e -> {
            try {
                if (LicenciaDialog.esMaestro()) {
                    // Abrir panel completo de gestión de licencias (App Maestra embebida)
                    javafx.stage.Stage stage = new javafx.stage.Stage();
                    stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
                    stage.setTitle("Gestión de Licencias — App Maestra");
                    stage.setWidth(1000);
                    stage.setHeight(660);
                    stage.setMinWidth(800);
                    stage.setMinHeight(520);
                    com.orquestador.maestro.LicenciaManagerController mgr =
                        new com.orquestador.maestro.LicenciaManagerController();
                    stage.setScene(new javafx.scene.Scene(
                        (javafx.scene.Parent) mgr.buildRoot()));
                    stage.showAndWait();
                } else {
                    new LicenciaDialog().showAndWait();
                }
            } catch (Exception ex) {
                System.err.println("Error abriendo gestión de licencia: " + ex.getMessage());
            }
        });
        
        // Checkbox para habilitar/deshabilitar RETRY
        CheckBox chkRetry = new CheckBox("Retry (3 intentos)");
        chkRetry.setStyle("-fx-font-size: 12px; -fx-padding: 5px;");
        // Inicializar siempre a FALSE - el usuario decide si habilitar
        chkRetry.setSelected(false);
        retryGlobalHabilitado = false;
        chkRetry.setTooltip(new Tooltip("Habilitar reintentos: si un proyecto falla, se reintentará hasta 3 veces. Por defecto deshabilitado."));
        chkRetry.selectedProperty().addListener((obs, oldVal, newVal) -> {
            // Aplicar el estado de retry a todos los proyectos seleccionados
            retryGlobalHabilitado = newVal;
            for (ProyectoAutomatizacion proyecto : proyectos) {
                proyecto.setRetryHabilitado(newVal);
                proyecto.setIntentoActual(0); // Resetear intento
            }
            guardarProyectos();
        });
        
        // Registrar descripciones y comportamiento de hover prolongado (3s)
        descripcionBotones.put(btnAgregar, "Agregar un nuevo proyecto al listado. Abre un diálogo para ingresar nombre, ruta y configuración de generación de informes.");
        descripcionBotones.put(btnEliminar, "Eliminar los proyectos actualmente seleccionados (checkbox marcados). Esta acción se puede deshacer en la configuración solo manualmente.");
        descripcionBotones.put(btnVistaCompacta, "Alterna la vista compacta: muestra solo los proyectos seleccionados. Útil para concentrarse en un subconjunto de proyectos.");
        descripcionBotones.put(btnLicencia, "Administrar licencia: activar, comprobar estado o revocar la instalación.");
        descripcionBotones.put(btnEjecutarSeleccionados, "Inicia la ejecución secuencial de los proyectos seleccionados en la vista actual. Agrupa por tipo de VPN y muestra popups de conexión cuando corresponde.");
        descripcionBotones.put(btnCancelarEjecucion, "Cancela la ejecución en curso (intenta detener el proceso actual). No deshace las marcas de ejecución previas.");
        descripcionBotones.put(btnGenerarInformes, "Genera informes (Word/PDF) a partir de las capturas y resultados de los proyectos seleccionados.");
        descripcionBotones.put(btnInformeExcel, "Genera un informe de ejecución en Excel con todos los proyectos, incluyendo: Nombre, Área, Retry, Estado, Última Ejecución y Duración.");
        descripcionBotones.put(btnAutomatizar, "Configura una automatización programada: selecciona proyectos y un intervalo en minutos para ejecutar automáticamente.");
        descripcionBotones.put(btnCargarChromeDriver, "Actualizar ChromeDriver: Selecciona un chromedriver.exe y se copiará PERMANENTEMENTE a todos los proyectos que tengan este archivo. También puedes arrastrar y soltar el archivo sobre este botón.");

        // Adjuntar comportamiento hover a cada botón con descripción
        attachHoverInfo(btnAgregar, descripcionBotones.get(btnAgregar));
        attachHoverInfo(btnEliminar, descripcionBotones.get(btnEliminar));
        attachHoverInfo(btnVistaCompacta, descripcionBotones.get(btnVistaCompacta));
        attachHoverInfo(btnEjecutarSeleccionados, descripcionBotones.get(btnEjecutarSeleccionados));
        attachHoverInfo(btnCancelarEjecucion, descripcionBotones.get(btnCancelarEjecucion));
        attachHoverInfo(btnGenerarInformes, descripcionBotones.get(btnGenerarInformes));
        attachHoverInfo(btnInformeExcel, descripcionBotones.get(btnInformeExcel));
        attachHoverInfo(btnAutomatizar, descripcionBotones.get(btnAutomatizar));
        attachHoverInfo(btnCargarChromeDriver, descripcionBotones.get(btnCargarChromeDriver));
        
        // Botón de configuración de correo (email por área)
        btnConfigCorreo = new Button("📧 Config. Correo");
        btnConfigCorreo.setStyle("-fx-background-color: #0D47A1; -fx-text-fill: white; -fx-font-weight: bold;");
        btnConfigCorreo.setTooltip(new Tooltip("Configura los destinatarios, plantilla y ruta de PDFs para envío de correo por área"));
        btnConfigCorreo.setOnAction(e -> mostrarDialogoConfiguracionCorreo());

        botonesAccion.getChildren().addAll(btnAgregar, btnEliminar, new Separator(javafx.geometry.Orientation.VERTICAL),
               new Label("Empresa:"), cboFiltroEmpresa, btnAgregarEmpresa, btnQuitarEmpresa,
               new Label("Area:"), cboFiltroArea, new Label("VPN:"), cboFiltroVPN, btnRefrescar, btnVistaCompacta, new Separator(javafx.geometry.Orientation.VERTICAL), chkRetry);
        
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
        
        btnInformeExcel = new Button("📊 Informe Excel");
        btnInformeExcel.setStyle("-fx-background-color: #1B5E20; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        btnInformeExcel.setOnAction(e -> generarInformeExcel());

        botonesEjecucion.getChildren().addAll(btnEjecutarSeleccionados, btnCancelarEjecucion, btnGenerarInformes);
        
        header.getChildren().addAll(titulo, botonesAccion, botonesEjecucion);
        return header;
    }

    // -----------------------------------------------------------------------
    // BARRA DE MENÚ ESTILO WINDOWS
    // -----------------------------------------------------------------------

    private MenuBar crearMenuBar() {
        MenuBar menuBar = new MenuBar();
        // Estilo discreto igual a la barra de título de Windows Explorer
        menuBar.setStyle("-fx-font-size: 12px; -fx-padding: 2 0 2 0; -fx-background-color: #F0F0F0; -fx-border-color: #CCCCCC; -fx-border-width: 0 0 1 0;");

        // ── Menú Archivo ─────────────────────────────────────────────────
        Menu menuArchivo = new Menu("Archivo");

        MenuItem itemExportar = new MenuItem("⬆  Exportar configuración...");
        itemExportar.setOnAction(e -> exportarConfiguracion());

        MenuItem itemImportar = new MenuItem("⬇  Importar configuración...");
        itemImportar.setOnAction(e -> importarConfiguracion());

        SeparatorMenuItem sep1 = new SeparatorMenuItem();

        MenuItem itemSalir = new MenuItem("Salir");
        itemSalir.setOnAction(e -> Platform.exit());

        menuArchivo.getItems().addAll(itemExportar, itemImportar, sep1, itemSalir);

        // ── Menú Herramientas ─────────────────────────────────────────────
        Menu menuHerramientas = new Menu("Herramientas");

        MenuItem itemAutomatizar = new MenuItem("⏰  Automatizar...");
        itemAutomatizar.setOnAction(e -> automatizarEjecucion());

        MenuItem itemInformeExcel = new MenuItem("📊  Informe Excel");
        itemInformeExcel.setOnAction(e -> generarInformeExcel());

        SeparatorMenuItem sep2 = new SeparatorMenuItem();

        MenuItem itemConfigCorreo = new MenuItem("📧  Configuración de Correo...");
        itemConfigCorreo.setOnAction(e -> mostrarDialogoConfiguracionCorreo());

        MenuItem itemLicencia = new MenuItem(
            LicenciaDialog.esMaestro() ? "🛡  Gestión de Licencias..." : "🔐  Licencia...");
        itemLicencia.setOnAction(e -> {
            try {
                if (LicenciaDialog.esMaestro()) {
                    javafx.stage.Stage stage = new javafx.stage.Stage();
                    stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
                    stage.setTitle("Gestión de Licencias \u2014 App Maestra");
                    stage.setWidth(1000);
                    stage.setHeight(660);
                    stage.setMinWidth(800);
                    stage.setMinHeight(520);
                    com.orquestador.maestro.LicenciaManagerController mgr =
                        new com.orquestador.maestro.LicenciaManagerController();
                    stage.setScene(new javafx.scene.Scene(
                        (javafx.scene.Parent) mgr.buildRoot()));
                    stage.showAndWait();
                } else {
                    new LicenciaDialog().showAndWait();
                }
            } catch (Exception ex) {
                System.err.println("Error abriendo licencia: " + ex.getMessage());
            }
        });

        SeparatorMenuItem sep3 = new SeparatorMenuItem();

        MenuItem itemChromeDriver = new MenuItem("📁  Actualizar ChromeDriver...");
        itemChromeDriver.setOnAction(e -> cargarChromeDriverManual());

        menuHerramientas.getItems().addAll(
            itemAutomatizar, itemInformeExcel, sep2,
            itemConfigCorreo, itemLicencia, sep3,
            itemChromeDriver);

        menuBar.getMenus().addAll(menuArchivo, menuHerramientas);
        return menuBar;
    }

    // -----------------------------------------------------------------------
    // EXPORTAR / IMPORTAR  CONFIGURACIÓN
    // -----------------------------------------------------------------------

    /**
     * Guía al usuario paso a paso para exportar toda la configuración de proyectos
     * a un archivo JSON con rutas relativizadas.
     */
    private void exportarConfiguracion() {
        javafx.stage.Stage ventana = (javafx.stage.Stage) root.getScene().getWindow();

        if (proyectos.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "No hay proyectos cargados para exportar.",
                ButtonType.OK).showAndWait();
            return;
        }

        // ── Detectar raíces automáticamente desde los datos en memoria ──
        java.util.List<String> rutasProyecto = new java.util.ArrayList<>();
        java.util.List<String> rutasInformes = new java.util.ArrayList<>();
        java.util.List<String> rutasTemplates = new java.util.ArrayList<>();

        for (ProyectoAutomatizacion p : proyectos) {
            if (p.getRuta() != null && !p.getRuta().isBlank())
                rutasProyecto.add(p.getRuta());
            if (p.getRutaImagenes() != null && !p.getRutaImagenes().isBlank())
                rutasProyecto.add(p.getRutaImagenes());
            if (p.getImagenesSeleccionadas() != null)
                rutasProyecto.addAll(p.getImagenesSeleccionadas().stream()
                    .filter(s -> s != null && !s.isBlank()).collect(Collectors.toList()));
            if (p.getRutaSalidaWord() != null && !p.getRutaSalidaWord().isBlank())
                rutasInformes.add(p.getRutaSalidaWord());
            if (p.getRutaSalidaPdf() != null && !p.getRutaSalidaPdf().isBlank())
                rutasInformes.add(p.getRutaSalidaPdf());
            if (p.getRutaTemplateWord() != null && !p.getRutaTemplateWord().isBlank())
                rutasTemplates.add(p.getRutaTemplateWord());
            if (p.getInformes() != null) {
                for (com.orquestador.modelo.ConfiguracionInforme ci : p.getInformes()) {
                    if (ci.getTemplateWord() != null && !ci.getTemplateWord().isBlank())
                        rutasTemplates.add(ci.getTemplateWord());
                    if (ci.getPatronImagenes() != null && !ci.getPatronImagenes().isBlank())
                        rutasProyecto.add(ci.getPatronImagenes());
                    if (ci.getImagenesSeleccionadas() != null)
                        rutasProyecto.addAll(ci.getImagenesSeleccionadas().stream()
                            .filter(s -> s != null && !s.isBlank()).collect(Collectors.toList()));
                }
            }
        }

        String raizProyectos = GestorExportImport.detectarRaizComun(rutasProyecto);
        String raizInformes  = GestorExportImport.detectarRaizComun(rutasInformes);
        String raizTemplates = GestorExportImport.detectarRaizComun(rutasTemplates);

        // ── Mostrar resumen de lo que se detectó y pedir confirmación ────────────
        Alert resumen = new Alert(Alert.AlertType.CONFIRMATION);
        resumen.initOwner(ventana);
        resumen.setTitle("Exportar configuración");
        resumen.setHeaderText("Se exportarán " + proyectos.size() + " proyecto(s)");
        resumen.setContentText(
            "Raíz proyectos detectada:\n  " + (raizProyectos.isBlank() ? "(no detectada)" : raizProyectos) +
            "\n\nRaíz informes detectada:\n  " + (raizInformes.isBlank() ? "(no detectada)" : raizInformes) +
            "\n\nRaíz templates detectada:\n  " + (raizTemplates.isBlank() ? "(no detectada)" : raizTemplates) +
            "\n\n¿Deseas continuar con la exportación?");
        java.util.Optional<ButtonType> conf = resumen.showAndWait();
        if (conf.isEmpty() || conf.get() != ButtonType.OK) return;

        // ── Solo pedir dónde guardar el archivo ───────────────────────────────
        javafx.stage.FileChooser fcDestino = new javafx.stage.FileChooser();
        fcDestino.setTitle("Guardar archivo de configuración");
        fcDestino.setInitialFileName("configuracion_proyectos.json");
        fcDestino.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("Configuración JSON", "*.json"));
        File destino = fcDestino.showSaveDialog(ventana);
        if (destino == null) return;

        // ── Ejecutar exportación ─────────────────────────────────────────────
        try {
            GestorExportImport.exportar(
                new java.util.ArrayList<>(proyectos),
                destino,
                raizProyectos,
                raizInformes,
                raizTemplates
            );
            agregarLog("✅ Configuración exportada: " + destino.getAbsolutePath());
            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ok.initOwner(ventana);
            ok.setTitle("Exportación exitosa");
            ok.setHeaderText("Configuración exportada correctamente");
            ok.setContentText(
                "Archivo generado:\n" + destino.getAbsolutePath() +
                "\nProyectos exportados: " + proyectos.size());
            ok.showAndWait();
        } catch (Exception ex) {
            agregarLog("❌ Error al exportar: " + ex.getMessage());
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.initOwner(ventana);
            err.setTitle("Error de exportación");
            err.setHeaderText("No se pudo exportar la configuración");
            err.setContentText(ex.getMessage());
            err.showAndWait();
        }
    }

    /**
     * Guía al usuario paso a paso para importar una configuración desde un archivo JSON,
     * reconstruyendo todas las rutas absolutas con las nuevas raíces del equipo destino.
     */
    private void importarConfiguracion() {
        javafx.stage.Stage ventana = (javafx.stage.Stage) root.getScene().getWindow();

        // ── Paso 1: Seleccionar el archivo .json a importar ──────────────────
        javafx.stage.FileChooser fcImport = new javafx.stage.FileChooser();
        fcImport.setTitle("Selecciona el archivo de configuración — Paso 1/4");
        fcImport.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("Configuración JSON", "*.json"));
        File archivoImport = fcImport.showOpenDialog(ventana);
        if (archivoImport == null) return;

        // Mostrar metadatos del archivo antes de continuar
        try {
            GestorExportImport.PaqueteExportacion meta =
                GestorExportImport.leerMetadatos(archivoImport);
            int totalProy = meta.proyectos != null ? meta.proyectos.size() : 0;
            Alert infoMeta = new Alert(Alert.AlertType.CONFIRMATION);
            infoMeta.initOwner(ventana);
            infoMeta.setTitle("Información del archivo");
            infoMeta.setHeaderText("Archivo de configuración válido. ¿Deseas continuar?");
            infoMeta.setContentText(
                "Fecha de exportación: " + (meta.fecha != null ? meta.fecha : "desconocida") +
                "\nProyectos incluidos: " + totalProy +
                "\nRaíz proyectos original: " + (meta.infoRaizProyectos != null ? meta.infoRaizProyectos : "—") +
                "\nRaíz informes original: " + (meta.infoRaizInformes != null ? meta.infoRaizInformes : "—") +
                "\nRaíz templates original: " + (meta.infoRaizTemplates != null ? meta.infoRaizTemplates : "—") +
                "\n\nA continuación se te pedirán las rutas en ESTE equipo.");
            java.util.Optional<ButtonType> resp = infoMeta.showAndWait();
            if (resp.isEmpty() || resp.get() != ButtonType.OK) return;
        } catch (Exception ex) {
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.initOwner(ventana);
            err.setTitle("Archivo inválido");
            err.setContentText("No se pudo leer el archivo: " + ex.getMessage());
            err.showAndWait();
            return;
        }

        // ── Paso 2: Raíz de proyectos en ESTE equipo ─────────────────────────
        Alert info2 = new Alert(Alert.AlertType.INFORMATION);
        info2.initOwner(ventana);
        info2.setTitle("Importar configuración — Paso 2/4");
        info2.setHeaderText("Selecciona la carpeta RAÍZ de proyectos en este equipo");
        info2.setContentText(
            "Selecciona la carpeta que contiene las 4 áreas (Clientes, Comercial, Integraciones, Siniestros).\n\n" +
            "Ejemplo: C:\\Proyectos Respaldo");
        info2.showAndWait();

        javafx.stage.DirectoryChooser dcProyectos = new javafx.stage.DirectoryChooser();
        dcProyectos.setTitle("Raíz de proyectos — Paso 2/4");
        File raizProyectos = dcProyectos.showDialog(ventana);
        if (raizProyectos == null) return;

        // ── Paso 3: Raíz de informes en ESTE equipo ──────────────────────────
        Alert info3 = new Alert(Alert.AlertType.INFORMATION);
        info3.initOwner(ventana);
        info3.setTitle("Importar configuración — Paso 3/4");
        info3.setHeaderText("Selecciona la carpeta RAÍZ de informes en este equipo");
        info3.setContentText(
            "Selecciona la carpeta donde se almacenarán los informes PDF y WORD.\n" +
            "Se crearán automáticamente las subcarpetas necesarias.");
        info3.showAndWait();

        javafx.stage.DirectoryChooser dcInformes = new javafx.stage.DirectoryChooser();
        dcInformes.setTitle("Raíz de informes — Paso 3/4");
        File raizInformes = dcInformes.showDialog(ventana);
        if (raizInformes == null) return;

        // ── Paso 4: Raíz de templates Word en ESTE equipo ────────────────────
        Alert info4 = new Alert(Alert.AlertType.INFORMATION);
        info4.initOwner(ventana);
        info4.setTitle("Importar configuración — Paso 4/4");
        info4.setHeaderText("Selecciona la carpeta RAÍZ de templates Word en este equipo");
        info4.setContentText("Selecciona la carpeta donde están los templates (.docx) base para los informes.");
        info4.showAndWait();

        javafx.stage.DirectoryChooser dcTemplates = new javafx.stage.DirectoryChooser();
        dcTemplates.setTitle("Raíz de templates — Paso 4/4");
        File raizTemplates = dcTemplates.showDialog(ventana);
        if (raizTemplates == null) return;

        // ── Preguntar si reemplazar o fusionar ───────────────────────────────
        Alert pregunta = new Alert(Alert.AlertType.CONFIRMATION);
        pregunta.initOwner(ventana);
        pregunta.setTitle("Modo de importación");
        pregunta.setHeaderText("¿Cómo deseas importar los proyectos?");
        pregunta.setContentText(
            "REEMPLAZAR: elimina la configuración actual e importa solo los proyectos del archivo.\n\n" +
            "AGREGAR: mantiene los proyectos actuales y agrega los nuevos del archivo (evita duplicados por nombre).");
        ButtonType btnReemplazar = new ButtonType("Reemplazar todo");
        ButtonType btnAgregar2   = new ButtonType("Agregar");
        ButtonType btnCancelar2  = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        pregunta.getButtonTypes().setAll(btnReemplazar, btnAgregar2, btnCancelar2);
        java.util.Optional<ButtonType> modoResp = pregunta.showAndWait();
        if (modoResp.isEmpty() || modoResp.get() == btnCancelar2) return;
        boolean reemplazar = modoResp.get() == btnReemplazar;

        // ── Ejecutar importación ─────────────────────────────────────────────
        try {
            java.util.List<ProyectoAutomatizacion> importados = GestorExportImport.importar(
                archivoImport,
                raizProyectos.getAbsolutePath(),
                raizInformes.getAbsolutePath(),
                raizTemplates.getAbsolutePath()
            );

            if (reemplazar) {
                proyectos.setAll(importados);
            } else {
                // Agregar solo los que no existan por nombre
                java.util.Set<String> nombresActuales = new java.util.HashSet<>();
                for (ProyectoAutomatizacion p : proyectos) nombresActuales.add(p.getNombre());
                for (ProyectoAutomatizacion p : importados) {
                    if (!nombresActuales.contains(p.getNombre())) proyectos.add(p);
                }
            }

            normalizarEmpresasEnProyectos();
            empresasRegistradas.addAll(obtenerEmpresasDesdeProyectos());
            refrescarEmpresasDisponibles(cboFiltroEmpresa != null ? cboFiltroEmpresa.getValue() : null);

            guardarProyectos();
            guardarPreferencias();
            tablaProyectos.refresh();
            actualizarEstadisticas();

            agregarLog("✅ Configuración importada: " + importados.size() + " proyecto(s) desde " + archivoImport.getName());
            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ok.initOwner(ventana);
            ok.setTitle("Importación exitosa");
            ok.setHeaderText("Configuración importada correctamente");
            ok.setContentText(
                "Proyectos importados: " + importados.size() +
                "\nEstructura de carpetas de informes creada en:\n" + raizInformes.getAbsolutePath());
            ok.showAndWait();
        } catch (Exception ex) {
            agregarLog("❌ Error al importar: " + ex.getMessage());
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.initOwner(ventana);
            err.setTitle("Error de importación");
            err.setHeaderText("No se pudo importar la configuración");
            err.setContentText(ex.getMessage());
            err.showAndWait();
        }
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
        
        // Checkbox en header para seleccionar/deseleccionar todos los proyectos visibles (filtrados)
        CheckBox headerCheckBox = new CheckBox();
        headerCheckBox.setOnAction(e -> {
            boolean selected = headerCheckBox.isSelected();
            // Aplicar SOLO a los proyectos filtrados/visibles en la tabla
            for (ProyectoAutomatizacion p : proyectosOrdenados) {
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
                guardarPreferencias(); // Guardar también el estado de selección en preferencias
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
        colNombre.setCellFactory(column -> new javafx.scene.control.cell.TextFieldTableCell<ProyectoAutomatizacion, String>(new javafx.util.converter.DefaultStringConverter()) {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) return;
                ProyectoAutomatizacion p = (ProyectoAutomatizacion) getTableRow().getItem();
                if (p != null && isProyectoDeshabilitado(p.getNombre())) {
                    setText(item + "  (DESHABILITADO)");
                    setStyle("-fx-text-fill: #888; -fx-font-style: italic;");
                } else {
                    setText(item);
                    setStyle("");
                }
            }
        });
        colNombre.setOnEditCommit(e -> {
            String oldName = e.getRowValue().getNombre();
            e.getRowValue().setNombre(e.getNewValue());
            // Si el proyecto estaba deshabilitado bajo el nombre antiguo, transferir el estado
            if (isProyectoDeshabilitado(oldName)) {
                setProyectoDeshabilitado(oldName, false);
                setProyectoDeshabilitado(e.getNewValue(), true);
            }
            guardarProyectos();
        });
        colNombre.setMinWidth(200);

        // Columna Empresa
        TableColumn<ProyectoAutomatizacion, String> colEmpresa = new TableColumn<>("Empresa");
        colEmpresa.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getEmpresa()));
        colEmpresa.setCellFactory(TextFieldTableCell.forTableColumn());
        colEmpresa.setOnEditCommit(e -> {
            e.getRowValue().setEmpresa(e.getNewValue());
            empresasRegistradas.add(e.getRowValue().getEmpresa());
            refrescarEmpresasDisponibles(cboFiltroEmpresa != null ? cboFiltroEmpresa.getValue() : null);
            guardarProyectos();
            guardarPreferencias();
            aplicarFiltro();
        });
        colEmpresa.setMinWidth(150);
        
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
        
        // Columna Retry (antes era Tipo Ejecución que ahora está oculto/no visible)
        TableColumn<ProyectoAutomatizacion, String> colRetry = new TableColumn<>("Retry");
        colRetry.setCellValueFactory(cellData -> {
            ProyectoAutomatizacion proyecto = cellData.getValue();
            // Crear una propiedad observable que se actualiza con los cambios de intento
            javafx.beans.property.SimpleStringProperty prop = new javafx.beans.property.SimpleStringProperty() {
                @Override
                public String get() {
                    return proyecto.getFormatoRetry();
                }
            };
            return prop;
        });
        colRetry.setCellFactory(column -> new javafx.scene.control.TableCell<ProyectoAutomatizacion, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setText("-");
                    setStyle("");
                } else {
                    ProyectoAutomatizacion proyecto = getTableView().getItems().get(getIndex());
                    String texto = proyecto.getFormatoRetry();
                    
                    if (texto == null || texto.isEmpty()) {
                        setText("-");
                        setStyle("");
                    } else {
                        setText(texto);
                        // Colorear según el estado del reintento
                        if (proyecto.getEstado() == EstadoEjecucion.FALLIDO) {
                            setStyle("-fx-text-fill: #FF6B6B; -fx-font-weight: bold;");
                        } else if (proyecto.getEstado() == EstadoEjecucion.EXITOSO) {
                            setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
                        } else if (proyecto.getEstado() == EstadoEjecucion.EJECUTANDO) {
                            setStyle("-fx-text-fill: #FF9800; -fx-font-weight: bold;");
                        } else {
                            setStyle("");
                        }
                    }
                }
            }
        });
        colRetry.setMinWidth(80);
        
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
        colEstado.setCellFactory(column -> new javafx.scene.control.TableCell<ProyectoAutomatizacion, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setText("");
                    setStyle("");
                } else {
                    setText(item);
                    ProyectoAutomatizacion proyecto = getTableView().getItems().get(getIndex());
                    // Aplicar color rojo si el estado es FALLIDO
                    if (proyecto != null && proyecto.getEstado() == EstadoEjecucion.FALLIDO) {
                        setStyle("-fx-text-fill: #FF0000; -fx-font-weight: bold;");
                    } else if (proyecto != null && proyecto.getEstado() == EstadoEjecucion.EXITOSO) {
                        setStyle("-fx-text-fill: #00AA00; -fx-font-weight: bold;");
                    } else if (proyecto != null && proyecto.getEstado() == EstadoEjecucion.EJECUTANDO) {
                        setStyle("-fx-text-fill: #FF9800; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
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
                    } else if (esProyectoWebLiquidacion(proyecto)) {
                        // Proyecto 20: Web de Liquidación con CSV especial
                        btnConfigurar.setText("📋 Configurar CSV");
                        btnConfigurar.setTooltip(new Tooltip("Editar datos del CSV (RUT, Contraseña, Siniestros)"));
                        btnConfigurar.setStyle("-fx-background-color: #FF6B6B; -fx-text-fill: white; -fx-font-weight: bold;");
                        setGraphic(btnConfigurar);
                    } else if (esProyectoMesaRepuestos(proyecto)) {
                        // Proyecto 21: Mesa de Repuestos con búsqueda flexible de CSV
                        btnConfigurar.setText("📋 Configurar CSV");
                        btnConfigurar.setTooltip(new Tooltip("Editar datos del CSV (RUT, Contraseña)"));
                        btnConfigurar.setStyle("-fx-background-color: #9C27B0; -fx-text-fill: white; -fx-font-weight: bold;");
                        setGraphic(btnConfigurar);
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
        
        tablaProyectos.getColumns().addAll(colSeleccionar, colNombre, colEmpresa, colRuta, colArea, colVPN, colRetry, colEstado, colUltima, colDuracion, colReporte, colVerLog, colConfigurar);

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
                    String ruta = seleccionado.getRuta();
                    new ProcessBuilder("explorer.exe", ruta).start();

                    // Copiar ruta al portapapeles
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent content = new ClipboardContent();
                    content.putString(ruta);
                    clipboard.setContent(content);

                    logArea.appendText("Abriendo Explorador en: " + ruta + " (ruta copiada al portapapeles)\n");
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

        ComboBox<String> cboEmpresa = new ComboBox<>();
        cboEmpresa.setEditable(true);
        cboEmpresa.getItems().addAll(empresasRegistradas);
        String empresaActual = (cboFiltroEmpresa != null && cboFiltroEmpresa.getValue() != null && !"Todas".equals(cboFiltroEmpresa.getValue()))
            ? cboFiltroEmpresa.getValue()
            : EMPRESA_DEFAULT;
        cboEmpresa.setValue(empresaActual);
        
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

        contenido.getChildren().add(new Label("Empresa:"));
        contenido.getChildren().add(cboEmpresa);
        
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
                    cboEmpresa.getValue(),
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
            empresasRegistradas.add(proyecto.getEmpresa());
            guardarProyectos();
            refrescarEmpresasDisponibles(proyecto.getEmpresa());
            guardarPreferencias();

            // Resetear filtros para que el proyecto nuevo aparezca inmediatamente
            if (cboFiltroArea != null) cboFiltroArea.setValue("Todas");
            if (cboFiltroVPN != null) cboFiltroVPN.setValue("Todas");
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
            refrescarEmpresasDisponibles(cboFiltroEmpresa != null ? cboFiltroEmpresa.getValue() : null);
            guardarProyectos();
            guardarPreferencias();
            actualizarEstadisticas();
            agregarLog(" Eliminados " + seleccionados.size() + " proyecto(s)");
        }
    }
    
    private void editarProyecto() {
        ProyectoAutomatizacion seleccionado = tablaProyectos.getSelectionModel().getSelectedItem();
        final String nombreOriginal = seleccionado != null ? seleccionado.getNombre() : null;
        
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

        ComboBox<String> cboEmpresa = new ComboBox<>();
        cboEmpresa.setEditable(true);
        cboEmpresa.getItems().addAll(empresasRegistradas);
        cboEmpresa.setValue((seleccionado.getEmpresa() == null || seleccionado.getEmpresa().trim().isEmpty())
            ? EMPRESA_DEFAULT : seleccionado.getEmpresa());
        
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

        contenido.getChildren().add(new Label("Empresa:"));
        contenido.getChildren().add(cboEmpresa);
        
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

        // Checkbox para deshabilitar el proyecto (persistente)
        CheckBox chkDeshabilitado = new CheckBox("Deshabilitar proyecto (no participar en ejecuciones)");
        chkDeshabilitado.setSelected(isProyectoDeshabilitado(nombreOriginal));
        contenido.getChildren().add(0, chkDeshabilitado);
        
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
                seleccionado.setEmpresa(cboEmpresa.getValue());
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

                // Gestionar estado de deshabilitado: si cambió el nombre, transferir estado
                String nuevoNombre = seleccionado.getNombre();
                if (nombreOriginal != null && !nombreOriginal.equals(nuevoNombre)) {
                    // Si el original estaba deshabilitado pero ahora no está marcado, quitarlo
                    if (isProyectoDeshabilitado(nombreOriginal) && !chkDeshabilitado.isSelected()) {
                        setProyectoDeshabilitado(nombreOriginal, false);
                    }
                    // Si marcado como deshabilitado, asegurar que el nuevo nombre quede deshabilitado
                    if (chkDeshabilitado.isSelected()) {
                        setProyectoDeshabilitado(nuevoNombre, true);
                    }
                } else {
                    // Mismo nombre: solo establecer según checkbox
                    setProyectoDeshabilitado(nuevoNombre, chkDeshabilitado.isSelected());
                }

                return seleccionado;
            }
            return null;
        });
        
        Optional<ProyectoAutomatizacion> resultado = dialog.showAndWait();
        resultado.ifPresent(proyecto -> {
            empresasRegistradas.add(proyecto.getEmpresa());
            guardarProyectos();
            refrescarEmpresasDisponibles(proyecto.getEmpresa());
            guardarPreferencias();
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

    // Determina si el proyecto es "20- Web de Liquidación" (con archivo CSV especial)
    private boolean esProyectoWebLiquidacion(ProyectoAutomatizacion proyecto) {
        if (proyecto == null || proyecto.getNombre() == null) return false;
        String nombre = proyecto.getNombre().trim();
        if (nombre.isEmpty()) return false;

        // Normalizar (quitar tildes) y comparar en minúsculas
        String normalized = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        // Quitar caracteres especiales y compactar espacios
        String clave = normalized.toLowerCase().replaceAll("[^\\p{Alnum}\\s]", " ").replaceAll("\\s+", " ").trim();

        // Eliminar prefijo numérico tipo "20 - " si existe
        clave = clave.replaceFirst("^\\d+\\s*[-:]?\\s*", "");

        String target = "web de liquidacion";
        return clave.contains(target);
    }

    // Determina si el proyecto es "21- Mesa de Repuestos" (con búsqueda flexible de CSV)
    private boolean esProyectoMesaRepuestos(ProyectoAutomatizacion proyecto) {
        if (proyecto == null || proyecto.getNombre() == null) return false;
        String nombre = proyecto.getNombre().trim();
        if (nombre.isEmpty()) return false;

        // Normalizar (quitar tildes) y comparar en minúsculas
        String normalized = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        // Quitar caracteres especiales y compactar espacios
        String clave = normalized.toLowerCase().replaceAll("[^\\p{Alnum}\\s]", " ").replaceAll("\\s+", " ").trim();

        // Eliminar prefijo numérico tipo "21 - " si existe
        clave = clave.replaceFirst("^\\d+\\s*[-:]?\\s*", "");

        String target = "mesa de repuestos";
        return clave.contains(target);
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

    // Detecta y abre el diálogo apropiado según el tipo de proyecto
    private void abrirDialogoCredenciales(ProyectoAutomatizacion proyecto) {
        if (esProyectoWebLiquidacion(proyecto)) {
            abrirDialogoCSVWebLiquidacion(proyecto);
        } else if (esProyectoMesaRepuestos(proyecto)) {
            abrirDialogoCSVMesaRepuestos(proyecto);
        } else {
            abrirDialogoCredencialesEspeciales(proyecto);
        }
    }

    // Diálogo para editar el CSV del proyecto 20 (Web de Liquidación)
    private void abrirDialogoCSVWebLiquidacion(ProyectoAutomatizacion proyecto) {
        try {
            // Ruta base para buscar el archivo CSV - Búsqueda recursiva
            String rutaBaseSearch = "C:\\Automatizaciones_V2\\Siniestros\\20- Web de Liquidación\\Archivos\\data";
            
            // Buscar el archivo CSVBCISeguros.csv de forma flexible
            java.io.File archivoCSV = buscarArchivoCSVFlexible(new java.io.File(rutaBaseSearch), "CSVBCISeguros.csv");

            if (archivoCSV == null || !archivoCSV.exists()) {
                mostrarAlerta("Error", "No se encontró el archivo CSVBCISeguros.csv en:\n" + rutaBaseSearch + 
                             "\n\nVerifica que el archivo exista en las subcarpetas.", Alert.AlertType.ERROR);
                return;
            }

            // Leer datos actuales del CSV
            java.util.Map<String, String> datosCSV = leerCSVWebLiquidacion(archivoCSV);

            Dialog<java.util.Map<String, String>> dialog = new Dialog<>();
            dialog.setTitle("Configurar Datos - " + proyecto.getNombre());
            dialog.setHeaderText("Editar datos del CSV (Fila 2)");

            ButtonType btnGuardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, ButtonType.CANCEL);

            VBox contenido = new VBox(12);
            contenido.setPadding(new Insets(18));
            contenido.setMinWidth(500);

            Label lblInfo = new Label("⚠️ Nota: Los datos se encuentran en la fila 2 del archivo CSV");
            lblInfo.setStyle("-fx-font-size: 11px; -fx-text-fill: #FF6B6B; -fx-font-weight: bold;");
            contenido.getChildren().add(lblInfo);

            Label lblPath = new Label("📁 Archivo: " + archivoCSV.getAbsolutePath());
            lblPath.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");
            contenido.getChildren().add(lblPath);

            // Campo Usuario (RUT)
            HBox hboxUsuario = crearCampoEditable("Usuario:", datosCSV.getOrDefault("rut", ""));
            TextField txtUsuario = (TextField) hboxUsuario.getChildren().get(1);

            // Campo Contraseña
            HBox hboxPass = crearCampoEditable("Contraseña:", datosCSV.getOrDefault("contrasena", ""));
            TextField txtPass = (TextField) hboxPass.getChildren().get(1);

            // Campo Siniestro BCI
            HBox hboxSiniestBCI = crearCampoEditable("Siniestro BCI:", datosCSV.getOrDefault("siniestro_bci", ""));
            TextField txtSiniestBCI = (TextField) hboxSiniestBCI.getChildren().get(1);

            // Campo Siniestro Zenit
            HBox hboxSiniestZenit = crearCampoEditable("Siniestro Zenit:", datosCSV.getOrDefault("siniestro_zenit", ""));
            TextField txtSiniestZenit = (TextField) hboxSiniestZenit.getChildren().get(1);

            contenido.getChildren().addAll(
                new Separator(),
                hboxUsuario,
                hboxPass,
                hboxSiniestBCI,
                hboxSiniestZenit
            );

            dialog.getDialogPane().setContent(contenido);
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == btnGuardar) {
                    java.util.Map<String, String> nuevosDatos = new java.util.LinkedHashMap<>();
                    nuevosDatos.put("rut", txtUsuario.getText().trim());
                    nuevosDatos.put("contrasena", txtPass.getText().trim());
                    nuevosDatos.put("siniestro_bci", txtSiniestBCI.getText().trim());
                    nuevosDatos.put("siniestro_zenit", txtSiniestZenit.getText().trim());
                    return nuevosDatos;
                }
                return null;
            });

            Optional<java.util.Map<String, String>> resultado = dialog.showAndWait();
            resultado.ifPresent(nuevosDatos -> {
                try {
                    guardarCSVWebLiquidacion(archivoCSV, nuevosDatos);
                    agregarLog("✅ Datos del CSV actualizados correctamente en: " + archivoCSV.getAbsolutePath());
                    mostrarAlerta("Éxito", "Datos guardados correctamente en el CSV", Alert.AlertType.INFORMATION);
                } catch (Exception e) {
                    agregarLog("❌ Error guardando CSV: " + e.getMessage());
                    mostrarAlerta("Error", "Error al guardar los datos: " + e.getMessage(), Alert.AlertType.ERROR);
                }
            });

        } catch (Exception e) {
            agregarLog("❌ Error abriendo diálogo: " + e.getMessage());
            mostrarAlerta("Error", "Error al abrir el diálogo: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    // Crea un campo editable con etiqueta
    private HBox crearCampoEditable(String etiqueta, String valor) {
        HBox hbox = new HBox(10);
        hbox.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(etiqueta);
        lbl.setStyle("-fx-font-weight: bold; -fx-min-width: 150px;");
        TextField txt = new TextField(valor);
        txt.setStyle("-fx-padding: 8px;");
        HBox.setHgrow(txt, Priority.ALWAYS);
        hbox.getChildren().addAll(lbl, txt);
        return hbox;
    }

    // Lee los datos del CSV de Web de Liquidación
    private java.util.Map<String, String> leerCSVWebLiquidacion(java.io.File archivoCSV) throws Exception {
        java.util.Map<String, String> datos = new java.util.LinkedHashMap<>();
        java.util.List<String> lineas = java.nio.file.Files.readAllLines(archivoCSV.toPath());

        if (lineas.size() < 2) {
            throw new Exception("El archivo CSV no tiene suficientes filas");
        }

        // Leer fila 2 (índice 1), columna A
        String filaData = lineas.get(1);
        String[] partes = filaData.split(",");

        if (partes.length > 0) {
            // Asumir formato: rut,contraseña,siniestro_bci,siniestro_zenit
            datos.put("rut", partes.length > 0 ? partes[0].trim() : "");
            datos.put("contrasena", partes.length > 1 ? partes[1].trim() : "");
            datos.put("siniestro_bci", partes.length > 2 ? partes[2].trim() : "");
            datos.put("siniestro_zenit", partes.length > 3 ? partes[3].trim() : "");
        }

        return datos;
    }

    // Guarda los datos en el CSV de Web de Liquidación
    private void guardarCSVWebLiquidacion(java.io.File archivoCSV, java.util.Map<String, String> datos) throws Exception {
        java.util.List<String> lineas = java.nio.file.Files.readAllLines(archivoCSV.toPath());

        // Construir nueva fila 2 - 4 campos, pero preservar el resto si existen
        String[] filaParts = lineas.get(1).split(",");
        StringBuilder nuevaFila = new StringBuilder();
        nuevaFila.append(datos.get("rut")).append(",")
                 .append(datos.get("contrasena")).append(",")
                 .append(datos.get("siniestro_bci")).append(",")
                 .append(datos.get("siniestro_zenit"));
        
        // Si había más columnas, preservarlas
        if (filaParts.length > 4) {
            for (int i = 4; i < filaParts.length; i++) {
                nuevaFila.append(",").append(filaParts[i]);
            }
        }

        // Reemplazar fila 2
        if (lineas.size() < 2) {
            lineas.add(nuevaFila.toString());
        } else {
            lineas.set(1, nuevaFila.toString());
        }

        // Guardar archivo
        java.nio.file.Files.write(archivoCSV.toPath(), lineas);
    }

    // Diálogo para editar el CSV del proyecto 21 (Mesa de Repuestos) - Búsqueda flexible
    private void abrirDialogoCSVMesaRepuestos(ProyectoAutomatizacion proyecto) {
        try {
            // Ruta base para buscar el archivo CSV
            String rutaBaseSearchs = "C:\\Automatizaciones_V2\\Siniestros\\21- Mesa de Repuestos\\Archivos\\data";
            
            // Buscar el archivo CSVBCISeguros.csv de forma flexible
            java.io.File archivoCSV = buscarArchivoCSVFlexible(new java.io.File(rutaBaseSearchs), "CSVBCISeguros.csv");

            if (archivoCSV == null || !archivoCSV.exists()) {
                mostrarAlerta("Error", "No se encontró el archivo CSVBCISeguros.csv en:\n" + rutaBaseSearchs + 
                             "\n\nVerifica que el archivo exista en las subcarpetas.", Alert.AlertType.ERROR);
                return;
            }

            // Leer datos actuales del CSV
            java.util.Map<String, String> datosCSV = leerCSVMesaRepuestos(archivoCSV);

            Dialog<java.util.Map<String, String>> dialog = new Dialog<>();
            dialog.setTitle("Configurar Datos - " + proyecto.getNombre());
            dialog.setHeaderText("Editar datos del CSV (Fila 2)");

            ButtonType btnGuardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, ButtonType.CANCEL);

            VBox contenido = new VBox(12);
            contenido.setPadding(new Insets(18));
            contenido.setMinWidth(500);

            Label lblInfo = new Label("⚠️ Nota: Los datos se encuentran en la fila 2 del archivo CSV");
            lblInfo.setStyle("-fx-font-size: 11px; -fx-text-fill: #9C27B0; -fx-font-weight: bold;");
            contenido.getChildren().add(lblInfo);

            Label lblPath = new Label("📁 Archivo: " + archivoCSV.getAbsolutePath());
            lblPath.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");
            contenido.getChildren().add(lblPath);

            // Campo Usuario
            HBox hboxUsuario = crearCampoEditable("Usuario:", datosCSV.getOrDefault("rut", ""));
            TextField txtUsuario = (TextField) hboxUsuario.getChildren().get(1);

            // Campo Contraseña
            HBox hboxPass = crearCampoEditable("Contraseña:", datosCSV.getOrDefault("contrasena", ""));
            TextField txtPass = (TextField) hboxPass.getChildren().get(1);

            contenido.getChildren().addAll(
                new Separator(),
                hboxUsuario,
                hboxPass
            );

            dialog.getDialogPane().setContent(contenido);
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == btnGuardar) {
                    java.util.Map<String, String> nuevosDatos = new java.util.LinkedHashMap<>();
                    nuevosDatos.put("rut", txtUsuario.getText().trim());
                    nuevosDatos.put("contrasena", txtPass.getText().trim());
                    return nuevosDatos;
                }
                return null;
            });

            Optional<java.util.Map<String, String>> resultado = dialog.showAndWait();
            resultado.ifPresent(nuevosDatos -> {
                try {
                    guardarCSVMesaRepuestos(archivoCSV, nuevosDatos);
                    agregarLog("✅ Datos del CSV actualizados correctamente en: " + archivoCSV.getAbsolutePath());
                    mostrarAlerta("Éxito", "Datos guardados correctamente en el CSV", Alert.AlertType.INFORMATION);
                } catch (Exception e) {
                    agregarLog("❌ Error guardando CSV: " + e.getMessage());
                    mostrarAlerta("Error", "Error al guardar los datos: " + e.getMessage(), Alert.AlertType.ERROR);
                }
            });

        } catch (Exception e) {
            agregarLog("❌ Error abriendo diálogo: " + e.getMessage());
            mostrarAlerta("Error", "Error al abrir el diálogo: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    // Busca un archivo de forma flexible (recursiva) dentro de una carpeta
    private java.io.File buscarArchivoCSVFlexible(java.io.File carpeta, String nombreArchivo) {
        if (!carpeta.exists() || !carpeta.isDirectory()) {
            return null;
        }

        // Buscar en la carpeta actual
        java.io.File[] archivos = carpeta.listFiles();
        if (archivos != null) {
            for (java.io.File archivo : archivos) {
                if (archivo.isFile() && archivo.getName().equalsIgnoreCase(nombreArchivo)) {
                    return archivo;
                }
            }

            // Buscar de forma recursiva en subcarpetas
            for (java.io.File archivo : archivos) {
                if (archivo.isDirectory()) {
                    java.io.File encontrado = buscarArchivoCSVFlexible(archivo, nombreArchivo);
                    if (encontrado != null) {
                        return encontrado;
                    }
                }
            }
        }

        return null;
    }

    // Lee los datos del CSV de Mesa de Repuestos
    private java.util.Map<String, String> leerCSVMesaRepuestos(java.io.File archivoCSV) throws Exception {
        java.util.Map<String, String> datos = new java.util.LinkedHashMap<>();
        java.util.List<String> lineas = java.nio.file.Files.readAllLines(archivoCSV.toPath());

        if (lineas.size() < 2) {
            throw new Exception("El archivo CSV no tiene suficientes filas");
        }

        // Leer fila 2 (índice 1)
        String filaData = lineas.get(1);
        String[] partes = filaData.split(",");

        if (partes.length > 0) {
            // Solo RUT y Contraseña
            datos.put("rut", partes.length > 0 ? partes[0].trim() : "");
            datos.put("contrasena", partes.length > 1 ? partes[1].trim() : "");
        }

        return datos;
    }

    // Guarda los datos en el CSV de Mesa de Repuestos
    private void guardarCSVMesaRepuestos(java.io.File archivoCSV, java.util.Map<String, String> datos) throws Exception {
        java.util.List<String> lineas = java.nio.file.Files.readAllLines(archivoCSV.toPath());

        // Construir nueva fila 2 - Solo RUT y Contraseña, preservar el resto de columnas si existen
        String[] filaParts = lineas.get(1).split(",");
        StringBuilder nuevaFila = new StringBuilder();
        nuevaFila.append(datos.get("rut")).append(",").append(datos.get("contrasena"));
        
        // Si había más columnas, preservarlas
        if (filaParts.length > 2) {
            for (int i = 2; i < filaParts.length; i++) {
                nuevaFila.append(",").append(filaParts[i]);
            }
        }

        // Reemplazar fila 2
        if (lineas.size() < 2) {
            lineas.add(nuevaFila.toString());
        } else {
            lineas.set(1, nuevaFila.toString());
        }

        // Guardar archivo
        java.nio.file.Files.write(archivoCSV.toPath(), lineas);
    }

    // Diálogo modal para configurar credenciales de proyectos especiales
    private void abrirDialogoCredencialesEspeciales(ProyectoAutomatizacion proyecto) {
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

            // Botón para ver y copiar contraseña actual
            Button btnVerContrasenaActual = new Button("👁️ Ver Contraseña Actual");
            btnVerContrasenaActual.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
            btnVerContrasenaActual.setOnAction(e -> {
                String contrasenaActual = "";
                String usuarioActual = "";
                if (nombre.contains("zenit")) {
                    contrasenaActual = cred.getPasword();
                    usuarioActual = cred.getUser();
                } else if (nombre.contains("corredores")) {
                    contrasenaActual = cred.getPasword2();
                    usuarioActual = cred.getUser();
                } else {
                    contrasenaActual = cred.getPasword();
                    usuarioActual = cred.getUser();
                }

                final String contrasenaFinal = contrasenaActual;
                final String usuarioFinal = usuarioActual;
                
                if (contrasenaActual == null || contrasenaActual.isEmpty()) {
                    mostrarAlerta("Credenciales", "No hay contraseña configurada actualmente.", Alert.AlertType.INFORMATION);
                } else {
                    // Crear un diálogo con opción de copiar
                    Dialog<Void> dialogoCred = new Dialog<>();
                    dialogoCred.setTitle("Credenciales Actuales");
                    dialogoCred.setHeaderText("Credenciales para: " + proyecto.getNombre());
                    
                    VBox contenidoCred = new VBox(12);
                    contenidoCred.setPadding(new Insets(15));
                    contenidoCred.setMinWidth(400);
                    
                    // Mostrar usuario
                    HBox hboxUser = new HBox(8);
                    hboxUser.setAlignment(Pos.CENTER_LEFT);
                    Label lblUser = new Label("Usuario:");
                    lblUser.setStyle("-fx-font-weight: bold;");
                    TextField txtUserDisplay = new TextField(usuarioFinal != null ? usuarioFinal : "");
                    txtUserDisplay.setEditable(false);
                    Button btnCopiarUser = new Button("📋 Copiar");
                    btnCopiarUser.setStyle("-fx-padding: 5;");
                    btnCopiarUser.setOnAction(e2 -> {
                        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                        content.putString(usuarioFinal);
                        clipboard.setContent(content);
                        mostrarAlerta("Copiado", "Usuario copiado al portapapeles", Alert.AlertType.INFORMATION);
                    });
                    hboxUser.getChildren().addAll(lblUser, txtUserDisplay, btnCopiarUser);
                    
                    // Mostrar contraseña
                    HBox hboxPass = new HBox(8);
                    hboxPass.setAlignment(Pos.CENTER_LEFT);
                    Label lblPass = new Label("Contraseña:");
                    lblPass.setStyle("-fx-font-weight: bold;");
                    TextField txtPassDisplay = new TextField(contrasenaFinal);
                    txtPassDisplay.setEditable(false);
                    Button btnCopiarPass = new Button("📋 Copiar");
                    btnCopiarPass.setStyle("-fx-padding: 5;");
                    btnCopiarPass.setOnAction(e2 -> {
                        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                        content.putString(contrasenaFinal);
                        clipboard.setContent(content);
                        mostrarAlerta("Copiado", "Contraseña copiada al portapapeles", Alert.AlertType.INFORMATION);
                    });
                    hboxPass.getChildren().addAll(lblPass, txtPassDisplay, btnCopiarPass);
                    
                    // Botón para copiar ambos datos
                    Button btnCopiarTodo = new Button("📋 Copiar Todo (usuario:contraseña)");
                    btnCopiarTodo.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8;");
                    btnCopiarTodo.setMaxWidth(Double.MAX_VALUE);
                    btnCopiarTodo.setOnAction(e2 -> {
                        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                        String datos = (usuarioFinal != null ? usuarioFinal : "") + ":" + contrasenaFinal;
                        content.putString(datos);
                        clipboard.setContent(content);
                        mostrarAlerta("Copiado", "Datos copiados al portapapeles", Alert.AlertType.INFORMATION);
                    });
                    
                    contenidoCred.getChildren().addAll(hboxUser, hboxPass, new Separator(), btnCopiarTodo);
                    dialogoCred.getDialogPane().setContent(contenidoCred);
                    dialogoCred.getDialogPane().getButtonTypes().add(ButtonType.OK);
                    dialogoCred.showAndWait();
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
                vboxCred.getChildren().add(new Label("RUT:"));
                TextField txtRut = new TextField(cred.getUser() != null ? cred.getUser() : "");
                campos.put("rut", txtRut);
                vboxCred.getChildren().add(txtRut);

                vboxCred.getChildren().add(new Label("Usuario:"));
                TextField txtUser2 = new TextField(cred.getUser2());
                campos.put("user2", txtUser2);
                vboxCred.getChildren().add(txtUser2);

                vboxCred.getChildren().add(new Label("Contraseña:"));
                PasswordField txtPassword2 = new PasswordField();
                txtPassword2.setText(cred.getPasword());
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
                        // RUT va a datos1.user
                        if (campos.containsKey("rut")) credActualizada.setUser(((TextField) campos.get("rut")).getText());
                        // Usuario va a datos2.user2
                        if (campos.containsKey("user2")) credActualizada.setUser2(((TextField) campos.get("user2")).getText());
                        // Contraseña va a datos1.pasword Y datos2.pasword2
                        if (campos.containsKey("password2")) {
                            String password = ((PasswordField) campos.get("password2")).getText();
                            credActualizada.setPasword(password);
                            credActualizada.setPasword2(password);
                        }
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
            .filter(p -> !isProyectoDeshabilitado(p.getNombre()))
            .collect(Collectors.toList());

        if (seleccionados.isEmpty()) {
            mostrarAlerta("Advertencia", "No hay proyectos seleccionados en la vista actual", Alert.AlertType.WARNING);
            return;
        }

        ejecutarProyectos(seleccionados);
    }
    
    private void ejecutarPorArea() {
        String areaSeleccionada = cboFiltroArea.getValue();
        String empresaSeleccionada = cboFiltroEmpresa != null ? cboFiltroEmpresa.getValue() : null;
        if (areaSeleccionada == null || areaSeleccionada.equals("Todas")) {
            mostrarAlerta("Advertencia", "Selecciona un Area especifica", Alert.AlertType.WARNING);
            return;
        }
        
        List<ProyectoAutomatizacion> porArea = proyectos.stream()
            .filter(p -> empresaSeleccionada == null || "Todas".equals(empresaSeleccionada) || empresaSeleccionada.equals(p.getEmpresa()))
            .filter(p -> p.getArea().equals(areaSeleccionada))
            .filter(p -> !isProyectoDeshabilitado(p.getNombre()))
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
        cancelRequested = false;
        btnEjecutarSeleccionados.setDisable(true);
        btnCancelarEjecucion.setDisable(false);
        btnAgregar.setDisable(true);
        btnEliminar.setDisable(true);

        // Guardar la lista de proyectos en ejecución para poder cancelarlos después
        proyectosEnEjecucion = new ArrayList<>(listaProyectos);
        
        // Guardar los proyectos que se ejecutarán para desmarcarlos después
        List<ProyectoAutomatizacion> proyectosAEjecutar = new ArrayList<>(listaProyectos);

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

        // Resetear estado de proyectos cancelados a PENDIENTE para permitir re-ejecución
        for (ProyectoAutomatizacion proyecto : listaProyectos) {
            if (proyecto.getEstado() == EstadoEjecucion.CANCELADO || 
                proyecto.getEstado() == EstadoEjecucion.FALLIDO ||
                proyecto.getEstado() == EstadoEjecucion.EJECUTANDO) {
                proyecto.setEstado(EstadoEjecucion.PENDIENTE);
                proyecto.resetearReintentos();
            }
            // Sincronizar estado de retry de TODOS los proyectos con el valor global
            proyecto.setRetryHabilitado(retryGlobalHabilitado);
            proyecto.setIntentoActual(0);
        }

        // Registrar tiempo de inicio
        tiempoInicioEjecucion = System.currentTimeMillis();

        // Ejecutar en hilo separado
        threadEjecucion = new Thread(() -> {
            try {
                outer:
                for (TipoVPN tipoVPN : ordenEjecucion) {
                    // Verificar si se canceló antes de procesar el siguiente grupo
                    if (!ejecutando || cancelRequested || Thread.currentThread().isInterrupted()) {
                        break outer;
                    }
                    
                    List<ProyectoAutomatizacion> grupoVPN = grupos.get(tipoVPN);

                    // Mostrar popup de VPN si es necesario
                    if (tipoVPN != TipoVPN.SIN_VPN && tipoVPN != TipoVPN.HIBRIDO) {
                        mostrarPopupVPN(tipoVPN, true);
                    }

                    // Ejecutar proyectos del grupo
                    for (ProyectoAutomatizacion proyecto : grupoVPN) {
                        if (!ejecutando || cancelRequested || Thread.currentThread().isInterrupted()) {
                            break outer;
                        }
                        ejecutarProyectoSync(proyecto);
                    }

                    // Mostrar popup de desconexin si es necesario
                    if (tipoVPN != TipoVPN.SIN_VPN && tipoVPN != TipoVPN.HIBRIDO) {
                        mostrarPopupVPN(tipoVPN, false);
                    }
                }

                Platform.runLater(() -> {
                    agregarLog("\n========================================");
                    agregarLog(" Ejecucion COMPLETADA");

                    // Calcular y mostrar tiempo total
                    long tiempoFin = System.currentTimeMillis();
                    long duracionMs = tiempoFin - tiempoInicioEjecucion;
                    String tiempoTotal = formatearTiempoTotal(duracionMs);
                    agregarLog(" Tiempo total de ejecucion: " + tiempoTotal);

                    agregarLog("========================================\n");

                    // Desmarcar los checkboxes de los proyectos ejecutados
                    for (ProyectoAutomatizacion proyecto : proyectosAEjecutar) {
                        proyecto.setSeleccionado(false);
                    }
                    tablaProyectos.refresh();
                    guardarProyectos();

                    finalizarEjecucion();
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    agregarLog(" Error en la Ejecucion: " + e.getMessage());
                    finalizarEjecucion();
                });
            }
        });
        threadEjecucion.start();
    }
    
    private Map<TipoVPN, List<ProyectoAutomatizacion>> agruparPorVPN(List<ProyectoAutomatizacion> proyectos) {
        Map<TipoVPN, List<ProyectoAutomatizacion>> grupos = new HashMap<>();
        for (TipoVPN tipo : TipoVPN.values()) {
            grupos.put(tipo, new ArrayList<>());
        }
        
        int proyectosAgrupados = 0;
        for (ProyectoAutomatizacion proyecto : proyectos) {
            if (isProyectoDeshabilitado(proyecto.getNombre())) {
                agregarLog(" [DEBUG] Proyecto deshabilitado saltado: " + proyecto.getNombre());
                continue;
            }
            grupos.get(proyecto.getTipoVPN()).add(proyecto);
            proyectosAgrupados++;
        }
        agregarLog(" [DEBUG] Total de proyectos agrupados: " + proyectosAgrupados + " de " + proyectos.size());
        
        return grupos;
    }
    
    private List<TipoVPN> determinarOrdenVPN(Map<TipoVPN, List<ProyectoAutomatizacion>> grupos) {
        List<TipoVPN> orden = new ArrayList<>();
        
        // Si VPN BCI tiene ms proyectos que Sin VPN, ejecutar primero
        int sinVPN = grupos.get(TipoVPN.SIN_VPN).size();
        int hibrido = grupos.get(TipoVPN.HIBRIDO).size();
        int vpnBCI = grupos.get(TipoVPN.VPN_BCI).size();
        
        if (vpnBCI > sinVPN && vpnBCI > 0) {
            if (vpnBCI > 0) orden.add(TipoVPN.VPN_BCI);
            if (sinVPN > 0) orden.add(TipoVPN.SIN_VPN);
            if (hibrido > 0) orden.add(TipoVPN.HIBRIDO);
        } else {
            if (sinVPN > 0) orden.add(TipoVPN.SIN_VPN);
            if (hibrido > 0) orden.add(TipoVPN.HIBRIDO);
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
        final boolean[] exitoso = {false};
        
        // Si retry está habilitado globalmente, usar la lógica de reintentos
        // retryGlobalHabilitado es la única fuente de verdad para todos los proyectos
        if (!cancelRequested && retryGlobalHabilitado) {
            ejecutarConReintentos(proyecto, lock, terminado, exitoso);
        } else {
            // Ejecución normal sin reintentos
            ejecutarProyectoUnaVez(proyecto, lock, terminado, exitoso);
        }
        
        // Esperar a que termine
        synchronized (lock) {
            while (!terminado[0] && !cancelRequested) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
    
    private void ejecutarProyectoUnaVez(ProyectoAutomatizacion proyecto, Object lock, boolean[] terminado, boolean[] exitoso) {
        // Verificar si se canceló antes de comenzar
        if (!ejecutando || cancelRequested || proyecto.getEstado() == EstadoEjecucion.CANCELADO) {
            Platform.runLater(() -> {
                if (proyecto.getEstado() != EstadoEjecucion.CANCELADO) {
                    proyecto.setEstado(EstadoEjecucion.CANCELADO);
                }
                tablaProyectos.refresh();
                actualizarEstadisticas();
                guardarProyectos();
            });
            synchronized (lock) {
                terminado[0] = true;
                lock.notify();
            }
            return;
        }
        
        Platform.runLater(() -> {
            proyecto.setEstado(EstadoEjecucion.EJECUTANDO);
            proyecto.setIntentoActual(1);
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
                    exitoso[0] = proyecto.getEstado() == EstadoEjecucion.EXITOSO;
                    lock.notify();
                }
            }
        );
    }
    
    private void ejecutarConReintentos(ProyectoAutomatizacion proyecto, Object lock, boolean[] terminado, boolean[] exitoso) {
        final int maxReintentos = 3;
        
        for (int intento = 1; intento <= maxReintentos; intento++) {
            // Verificar si se canceló la ejecución ANTES de cambiar el estado a EJECUTANDO
            if (!ejecutando || cancelRequested || proyecto.getEstado() == EstadoEjecucion.CANCELADO || Thread.currentThread().isInterrupted()) {
                Platform.runLater(() -> {
                    if (proyecto.getEstado() != EstadoEjecucion.CANCELADO) {
                        proyecto.setEstado(EstadoEjecucion.CANCELADO);
                    }
                    tablaProyectos.refresh();
                    actualizarEstadisticas();
                    guardarProyectos();
                });
                break; // SALIR del loop de reintentos
            }
            
            final int intentoActual = intento;
            
            // Solo cambiar a EJECUTANDO si NO fue cancelado
            Platform.runLater(() -> {
                proyecto.setEstado(EstadoEjecucion.EJECUTANDO);
                proyecto.setIntentoActual(intentoActual);
                tablaProyectos.refresh();
                agregarLog("🔄 Intento " + intentoActual + "/" + maxReintentos + " para: " + proyecto.getNombre());
            });
            
            final boolean[] intentoTerminado = {false};
            final Object intentoLock = new Object();
            
            ejecutor.ejecutarProyecto(proyecto, 
                mensaje -> Platform.runLater(() -> agregarLog(mensaje)),
                () -> {
                    synchronized (intentoLock) {
                        intentoTerminado[0] = true;
                        intentoLock.notify();
                    }
                }
            );
            
            // Esperar a que termine este intento
            synchronized (intentoLock) {
                while (!intentoTerminado[0]) {
                    try {
                        intentoLock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            // Verificar nuevamente si se canceló durante la ejecución o si el thread fue interrumpido
            if (!ejecutando || cancelRequested || proyecto.getEstado() == EstadoEjecucion.CANCELADO || Thread.currentThread().isInterrupted()) {
                Platform.runLater(() -> {
                    if (proyecto.getEstado() != EstadoEjecucion.CANCELADO) {
                        proyecto.setEstado(EstadoEjecucion.CANCELADO);
                    }
                    tablaProyectos.refresh();
                    actualizarEstadisticas();
                    guardarProyectos();
                });
                break; // SALIR del loop de reintentos
            }
            
            // Verificar si fue exitoso
            if (proyecto.getEstado() == EstadoEjecucion.EXITOSO) {
                Platform.runLater(() -> {
                    agregarLog("✅ Proyecto completado exitosamente en intento " + intentoActual);
                    tablaProyectos.refresh();
                    actualizarEstadisticas();
                    guardarProyectos();
                });
                exitoso[0] = true;
                break; // Salir del loop si fue exitoso
            } else if (intento < maxReintentos) {
                // Verificar ANTES de preparar el siguiente reintento
                if (!ejecutando || cancelRequested || proyecto.getEstado() == EstadoEjecucion.CANCELADO || Thread.currentThread().isInterrupted()) {
                    Platform.runLater(() -> {
                        if (proyecto.getEstado() != EstadoEjecucion.CANCELADO) {
                            proyecto.setEstado(EstadoEjecucion.CANCELADO);
                        }
                        tablaProyectos.refresh();
                        actualizarEstadisticas();
                        guardarProyectos();
                    });
                    break; // SALIR del loop de reintentos
                }
                
                // Si no fue exitoso y no es el último intento, esperar un poco antes de reintentar
                Platform.runLater(() -> {
                    agregarLog("⏳ Preparando reintento " + (intentoActual + 1) + "...");
                });
                try {
                    Thread.sleep(2000); // Esperar 2 segundos entre reintentos
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // Si se interrumpe el sleep, salir inmediatamente
                    Platform.runLater(() -> {
                        if (proyecto.getEstado() != EstadoEjecucion.CANCELADO) {
                            proyecto.setEstado(EstadoEjecucion.CANCELADO);
                        }
                        tablaProyectos.refresh();
                        actualizarEstadisticas();
                        guardarProyectos();
                    });
                    break;
                }
            }
        }
        
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
    
    private void cancelarEjecucion() {
        // No desactivar 'ejecutando' aquí: esperar confirmación de cierre antes de permitir nuevas ejecuciones
        cancelRequested = true;
        agregarLog("🚫 EJECUCIÓN CANCELADA - Iniciando detención. Verificando cierre de procesos...");
        
        // Marcar todos los proyectos restantes como CANCELADOS
        for (ProyectoAutomatizacion proyecto : proyectosEnEjecucion) {
            if (proyecto.getEstado() == EstadoEjecucion.PENDIENTE) {
                proyecto.setEstado(EstadoEjecucion.CANCELADO);
                agregarLog("  ⚠️ Proyecto cancelado: " + proyecto.getNombre());
            }
        }
        
        tablaProyectos.refresh();
        guardarProyectos();
        
        // Interrumpir el thread de ejecución para que se detenga inmediatamente
        if (threadEjecucion != null && threadEjecucion.isAlive()) {
            threadEjecucion.interrupt();
        }
        
        ejecutor.detener();

        // Ejecutar la verificación de cierre en background para no bloquear la UI
        new Thread(() -> {
            com.orquestador.servicio.ProcessRegistry registry = com.orquestador.servicio.ProcessRegistry.getInstance();
            long start = System.currentTimeMillis();
            long timeout = 15_000; // 15s máximo para intentar limpieza
            boolean ok = false;

            while (System.currentTimeMillis() - start < timeout) {
                if (!ejecutor.isEjecutando() && registry.isEmpty()) {
                    ok = true;
                    break;
                }
                // Intentar forzar cierre leve si aún hay procesos
                if (!registry.isEmpty()) {
                    registry.killAll();
                }
                try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }

            if (!ok) {
                // Último intento forzado
                registry.killAll();
            }

            // Ahora notificar en la UI que ya se puede ejecutar de nuevo
            Platform.runLater(() -> {
                if (!registry.isEmpty()) {
                    agregarLog("⚠️ Algunos procesos no pudieron cerrarse correctamente, se forzó cierre final.");
                } else {
                    agregarLog("✅ Todos los procesos finalizaron. Ejecución cancelada exitosamente.");
                }
                // Restaurar estado UI
                finalizarEjecucion();
            });
        }).start();
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
        cancelRequested = false;
        threadEjecucion = null; // Limpiar la referencia al thread
        proyectosEnEjecucion.clear(); // Limpiar la lista de proyectos en ejecución
        btnEjecutarSeleccionados.setDisable(false);
        btnCancelarEjecucion.setDisable(true);
        btnAgregar.setDisable(false);
        btnEliminar.setDisable(false);
        actualizarEstadisticas();
    }
    
    private void aplicarFiltro() {
        String filtroEmpresa = cboFiltroEmpresa != null ? cboFiltroEmpresa.getValue() : null;
        String filtroArea = cboFiltroArea != null ? cboFiltroArea.getValue() : null;
        String filtroVpn = cboFiltroVPN != null ? cboFiltroVPN.getValue() : null;
        boolean soloSeleccionados = vistaCompacta; // respeta la vista compacta de la empresa en curso

        proyectosFiltrados.setPredicate(p -> {
            boolean empresaOk = true;
            boolean areaOk = true;
            boolean vpnOk = true;
            boolean seleccionOk = true;

            // Filtro de empresa: cada empresa es un universo independiente
            if (filtroEmpresa != null && !filtroEmpresa.equals("Todas")) {
                empresaOk = p.getEmpresa() != null && p.getEmpresa().equals(filtroEmpresa);
            }
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
            // Vista compacta: SOLO proyectos seleccionados de la empresa en filtro
            if (soloSeleccionados) {
                seleccionOk = p.isSeleccionado();
            }
            return empresaOk && areaOk && vpnOk && seleccionOk;
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

        // Forzar actualización visual de la tabla
        tablaProyectos.refresh();

        // Aplicar filtro actual (respeta empresa, área, VPN y vista compacta) y actualizar estadísticas
        aplicarFiltro();
        actualizarEstadisticas();

        agregarLog("✅ Tabla limpiada y lista para nueva ejecución - " + proyectos.size() + " proyecto(s)");
    }

    private void normalizarEmpresasEnProyectos() {
        for (ProyectoAutomatizacion proyecto : proyectos) {
            if (proyecto.getEmpresa() == null || proyecto.getEmpresa().trim().isEmpty()) {
                proyecto.setEmpresa(EMPRESA_DEFAULT);
            }
        }
    }

    private java.util.Set<String> obtenerEmpresasDesdeProyectos() {
        java.util.Set<String> empresas = new java.util.LinkedHashSet<>();
        for (ProyectoAutomatizacion proyecto : proyectos) {
            if (proyecto.getEmpresa() != null && !proyecto.getEmpresa().trim().isEmpty()) {
                empresas.add(proyecto.getEmpresa().trim());
            }
        }
        return empresas;
    }

    private void refrescarEmpresasDisponibles(String seleccionPreferida) {
        if (cboFiltroEmpresa == null) return;

        empresasRegistradas.add(EMPRESA_DEFAULT);
        empresasRegistradas.addAll(obtenerEmpresasDesdeProyectos());

        java.util.List<String> items = new java.util.ArrayList<>();
        items.add("Todas");
        items.addAll(empresasRegistradas);

        cboFiltroEmpresa.getItems().setAll(items);

        String seleccionActual = seleccionPreferida != null ? seleccionPreferida : cboFiltroEmpresa.getValue();
        if (seleccionActual != null && cboFiltroEmpresa.getItems().contains(seleccionActual)) {
            cboFiltroEmpresa.setValue(seleccionActual);
        } else {
            cboFiltroEmpresa.setValue(EMPRESA_DEFAULT);
        }
    }

    private void agregarEmpresa() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nueva empresa");
        dialog.setHeaderText("Agregar empresa al selector");
        dialog.setContentText("Nombre de empresa:");

        Optional<String> resultado = dialog.showAndWait();
        if (resultado.isEmpty()) return;

        String nuevaEmpresa = resultado.get().trim();
        if (nuevaEmpresa.isEmpty()) {
            mostrarAlerta("Empresa inválida", "El nombre de empresa no puede estar vacío.", Alert.AlertType.WARNING);
            return;
        }

        empresasRegistradas.add(nuevaEmpresa);
        refrescarEmpresasDisponibles(nuevaEmpresa);
        guardarPreferencias();
        aplicarFiltro();
        agregarLog("✅ Empresa agregada al selector: " + nuevaEmpresa);
    }

    private void quitarEmpresaSeleccionada() {
        if (cboFiltroEmpresa == null) return;
        String empresa = cboFiltroEmpresa.getValue();

        if (empresa == null || empresa.equals("Todas")) {
            mostrarAlerta("Selecciona empresa", "Selecciona una empresa específica para quitarla.", Alert.AlertType.WARNING);
            return;
        }
        if (EMPRESA_DEFAULT.equals(empresa)) {
            mostrarAlerta("Acción no permitida", "No puedes quitar la empresa base '" + EMPRESA_DEFAULT + "'.", Alert.AlertType.WARNING);
            return;
        }

        long usados = proyectos.stream()
            .filter(p -> empresa.equals(p.getEmpresa()))
            .count();

        if (usados > 0) {
            mostrarAlerta("No se puede quitar", "La empresa tiene " + usados + " proyecto(s) asociado(s). Reasigna o elimina esos proyectos primero.", Alert.AlertType.WARNING);
            return;
        }

        empresasRegistradas.remove(empresa);
        refrescarEmpresasDisponibles(EMPRESA_DEFAULT);
        guardarPreferencias();
        aplicarFiltro();
        agregarLog("🗑️ Empresa removida del selector: " + empresa);
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
                        final java.util.List<String> advertencias = generador.getAdvertencias();
                        
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
                            if (advertencias != null && !advertencias.isEmpty()) {
                                for (String adv : advertencias) {
                                    agregarLog("    WARN: " + adv);
                                }
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
     * Genera un informe de ejecución en Excel con todos los proyectos
     */
    private void generarInformeExcel() {
        // Abrir FileChooser para seleccionar ubicación de guardado
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Guardar Informe de Ejecución");
        fileChooser.setInitialFileName("Informe_Ejecucion_" + 
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")) + ".xlsx");
        
        fileChooser.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("Archivo Excel", "*.xlsx")
        );
        
        // Intentar abrir en la carpeta de descargas por defecto
        String userHome = System.getProperty("user.home");
        File descargas = new File(userHome, "Downloads");
        if (!descargas.exists()) {
            descargas = new File(userHome, "Descargas");
        }
        if (descargas.exists()) {
            fileChooser.setInitialDirectory(descargas);
        }
        
        File archivoDestino = fileChooser.showSaveDialog(root.getScene().getWindow());
        
        if (archivoDestino == null) {
            return; // Usuario canceló
        }
        
        // Generar el Excel en un hilo separado para no bloquear la UI
        new Thread(() -> {
            try {
                // Crear workbook y hoja
                org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Informe de Ejecución");
                
                // Crear estilos
                org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
                org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerFont.setFontHeightInPoints((short) 12);
                headerFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.DARK_BLUE.getIndex());
                headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
                headerStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
                headerStyle.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);
                headerStyle.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
                headerStyle.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);
                headerStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
                
                org.apache.poi.ss.usermodel.CellStyle dataStyle = workbook.createCellStyle();
                dataStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
                dataStyle.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);
                dataStyle.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
                dataStyle.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);
                
                // Estilo para proyectos deshabilitados (texto rojo)
                org.apache.poi.ss.usermodel.CellStyle disabledStyle = workbook.createCellStyle();
                disabledStyle.cloneStyleFrom(dataStyle);
                org.apache.poi.ss.usermodel.Font redFont = workbook.createFont();
                redFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.RED.getIndex());
                redFont.setBold(true);
                disabledStyle.setFont(redFont);
                
                org.apache.poi.ss.usermodel.CellStyle dateStyle = workbook.createCellStyle();
                dateStyle.cloneStyleFrom(dataStyle);
                org.apache.poi.ss.usermodel.CreationHelper createHelper = workbook.getCreationHelper();
                dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));
                
                // Crear fila de encabezados
                org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
                String[] columnas = {"Nombre", "Área", "Retry", "Estado", "Última Ejecución", "Duración"};
                
                for (int i = 0; i < columnas.length; i++) {
                    org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                    cell.setCellValue(columnas[i]);
                    cell.setCellStyle(headerStyle);
                }
                
                // Agregar datos de todos los proyectos
                int rowNum = 1;
                for (ProyectoAutomatizacion proyecto : proyectos) {
                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                    
                    // Nombre
                    org.apache.poi.ss.usermodel.Cell cell0 = row.createCell(0);
                    cell0.setCellValue(proyecto.getNombre() != null ? proyecto.getNombre() : "");
                    cell0.setCellStyle(dataStyle);
                    
                    // Área
                    org.apache.poi.ss.usermodel.Cell cell1 = row.createCell(1);
                    cell1.setCellValue(proyecto.getArea() != null ? proyecto.getArea() : "");
                    cell1.setCellStyle(dataStyle);
                    
                    // Retry
                    org.apache.poi.ss.usermodel.Cell cell2 = row.createCell(2);
                    String retryText = proyecto.getFormatoRetry();
                    cell2.setCellValue(retryText != null && !retryText.isEmpty() ? retryText : "-");
                    cell2.setCellStyle(dataStyle);
                    
                    // Estado - Verificar si está deshabilitado
                    org.apache.poi.ss.usermodel.Cell cell3 = row.createCell(3);
                    boolean esDeshabilitado = isProyectoDeshabilitado(proyecto.getNombre());
                    if (esDeshabilitado) {
                        cell3.setCellValue("Deshabilitado");
                        cell3.setCellStyle(disabledStyle);
                    } else {
                        cell3.setCellValue(proyecto.getEstado() != null ? proyecto.getEstado().getDescripcion() : "Pendiente");
                        cell3.setCellStyle(dataStyle);
                    }
                    
                    // Última Ejecución
                    org.apache.poi.ss.usermodel.Cell cell4 = row.createCell(4);
                    if (proyecto.getUltimaEjecucion() != null) {
                        LocalDateTime ultimaEjecucion = proyecto.getUltimaEjecucion();
                        String fechaFormateada = ultimaEjecucion.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
                        cell4.setCellValue(fechaFormateada);
                    } else {
                        cell4.setCellValue("No ejecutado");
                    }
                    cell4.setCellStyle(dataStyle);
                    
                    // Duración (convertir segundos a minutos + segundos)
                    org.apache.poi.ss.usermodel.Cell cell5 = row.createCell(5);
                    if (proyecto.getDuracionSegundos() != null && proyecto.getDuracionSegundos() > 0) {
                        int totalSegundos = proyecto.getDuracionSegundos();
                        int minutos = totalSegundos / 60;
                        int segundos = totalSegundos % 60;
                        
                        String duracionFormato;
                        if (minutos > 0 && segundos > 0) {
                            duracionFormato = minutos + " min " + segundos + " seg";
                        } else if (minutos > 0) {
                            duracionFormato = minutos + " min";
                        } else {
                            duracionFormato = segundos + " seg";
                        }
                        cell5.setCellValue(duracionFormato);
                    } else {
                        cell5.setCellValue("-");
                    }
                    cell5.setCellStyle(dataStyle);
                }
                
                // ── Fila de Tiempo Total de Ejecución ──────────────────────────
                long totalSegundosTotal = 0;
                for (ProyectoAutomatizacion p : proyectos) {
                    if (p.getDuracionSegundos() != null && p.getDuracionSegundos() > 0) {
                        totalSegundosTotal += p.getDuracionSegundos();
                    }
                }

                // Estilo encabezado de la fila total (fondo azul oscuro, negrita, blanco)
                org.apache.poi.ss.usermodel.CellStyle totalLabelStyle = workbook.createCellStyle();
                totalLabelStyle.cloneStyleFrom(headerStyle);
                totalLabelStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT);

                // Estilo valor total (fondo amarillo, negrita)
                org.apache.poi.ss.usermodel.CellStyle totalValueStyle = workbook.createCellStyle();
                totalValueStyle.cloneStyleFrom(dataStyle);
                org.apache.poi.ss.usermodel.Font totalFont = workbook.createFont();
                totalFont.setBold(true);
                totalFont.setFontHeightInPoints((short) 11);
                totalValueStyle.setFont(totalFont);
                totalValueStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_YELLOW.getIndex());
                totalValueStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
                totalValueStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);

                org.apache.poi.ss.usermodel.Row totalRow = sheet.createRow(rowNum);

                // Celdas 0-4: etiqueta "Tiempo Total de Ejecución" (fusionadas)
                for (int i = 0; i < 5; i++) {
                    org.apache.poi.ss.usermodel.Cell tc = totalRow.createCell(i);
                    tc.setCellStyle(totalLabelStyle);
                    if (i == 0) tc.setCellValue("Tiempo Total de Ejecución");
                }
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum, rowNum, 0, 4));

                // Celda 5: valor formateado en horas y minutos
                long horas    = totalSegundosTotal / 3600;
                long minutos  = (totalSegundosTotal % 3600) / 60;
                long segundos = totalSegundosTotal % 60;

                String tiempoTotalStr;
                if (horas > 0) {
                    tiempoTotalStr = horas + " h " + minutos + " min " + segundos + " seg";
                } else if (minutos > 0) {
                    tiempoTotalStr = minutos + " min " + segundos + " seg";
                } else {
                    tiempoTotalStr = segundos + " seg";
                }

                org.apache.poi.ss.usermodel.Cell totalCell = totalRow.createCell(5);
                totalCell.setCellValue(tiempoTotalStr);
                totalCell.setCellStyle(totalValueStyle);
                // ────────────────────────────────────────────────────────────────

                // Ajustar ancho de columnas
                for (int i = 0; i < columnas.length; i++) {
                    sheet.autoSizeColumn(i);
                    // Agregar un poco más de espacio
                    int currentWidth = sheet.getColumnWidth(i);
                    sheet.setColumnWidth(i, currentWidth + 1000);
                }
                
                // Guardar archivo
                try (java.io.FileOutputStream fileOut = new java.io.FileOutputStream(archivoDestino)) {
                    workbook.write(fileOut);
                }
                workbook.close();
                
                // Mostrar mensaje de éxito y abrir archivo
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Informe Generado");
                    alert.setHeaderText("✅ Informe de Ejecución generado correctamente");
                    alert.setContentText(
                        "Archivo: " + archivoDestino.getName() + "\n" +
                        "Ubicación: " + archivoDestino.getParent() + "\n" +
                        "Proyectos incluidos: " + proyectos.size() + "\n\n" +
                        "¿Desea abrir el archivo?"
                    );
                    
                    ButtonType btnAbrir = new ButtonType("Abrir", ButtonBar.ButtonData.YES);
                    ButtonType btnCerrar = new ButtonType("Cerrar", ButtonBar.ButtonData.NO);
                    alert.getButtonTypes().setAll(btnAbrir, btnCerrar);
                    
                    alert.showAndWait().ifPresent(response -> {
                        if (response == btnAbrir) {
                            try {
                                java.awt.Desktop.getDesktop().open(archivoDestino);
                            } catch (Exception e) {
                                mostrarAlerta("Error", "No se pudo abrir el archivo: " + e.getMessage(), Alert.AlertType.ERROR);
                            }
                        }
                    });
                    
                    agregarLog("✓ Informe Excel generado: " + archivoDestino.getName());
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    mostrarAlerta("Error", 
                        "Error al generar el informe Excel:\n" + e.getMessage(), 
                        Alert.AlertType.ERROR);
                    e.printStackTrace();
                });
            }
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

    // ─────────────────────────────────────────────────────────────────────────
    //  Configuración de Correo por Área
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Abre el diálogo para crear/editar configuraciones de correo por área.
     * Cada área tiene sus propios destinatarios, CC, cuerpo y ruta de PDFs.
     */
    private void mostrarDialogoConfiguracionCorreo() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Configuración de Correo por Área");
        dialog.setHeaderText("Define los parámetros de envío de correo para cada área");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(820, 650);

        // ── Panel izquierdo: lista de áreas configuradas ──
        ListView<ConfiguracionCorreo> listaAreas = new ListView<>();
        listaAreas.getItems().addAll(configsCorreo);
        listaAreas.setPrefWidth(190);
        listaAreas.setPlaceholder(new Label("Sin configuraciones"));

        Button btnNuevaConfig  = new Button("➕ Nueva área");
        Button btnEliminarConfig = new Button("🗑 Eliminar");
        btnEliminarConfig.setStyle("-fx-text-fill: #c62828;");
        HBox botonesLista = new HBox(6, btnNuevaConfig, btnEliminarConfig);
        VBox panelLista = new VBox(6, new Label("Áreas configuradas:"), listaAreas, botonesLista);
        panelLista.setPadding(new Insets(8));

        // ── Panel derecho: formulario de edición ──
        Label lblAreaNombre   = new Label("Área:");
        ComboBox<String> cboArea = new ComboBox<>();
        // Cargar áreas disponibles desde proyectos + las ya configuradas
        java.util.TreeSet<String> areasDisponibles = new java.util.TreeSet<>();
        proyectos.forEach(p -> { if (p.getArea() != null) areasDisponibles.add(p.getArea()); });
        configsCorreo.forEach(c -> { if (c.getArea() != null) areasDisponibles.add(c.getArea()); });
        cboArea.getItems().addAll(areasDisponibles);
        cboArea.setEditable(true);
        cboArea.setPrefWidth(300);

        Label lblPara   = new Label("Para (separar con ;):");
        TextArea txtPara = new TextArea();
        txtPara.setPrefRowCount(2);
        txtPara.setWrapText(true);

        Label lblCC     = new Label("CC (separar con ;):");
        TextArea txtCC  = new TextArea();
        txtCC.setPrefRowCount(2);
        txtCC.setWrapText(true);

        Label lblAsunto  = new Label("Asunto  — Placeholders: {area}, {fecha}");
        TextField txtAsunto = new TextField();

        Label lblIntro   = new Label("Cuerpo — Introducción (antes de la lista):");
        TextArea txtIntro = new TextArea();
        txtIntro.setPrefRowCount(4);
        txtIntro.setWrapText(true);

        Label lblCierre  = new Label("Cuerpo — Cierre (después de la lista de proyectos):");
        TextArea txtCierre = new TextArea();
        txtCierre.setPrefRowCount(3);
        txtCierre.setWrapText(true);

        Label lblRutaPDF = new Label("Ruta de carpeta PDFs (solo PDF post-fecha de tarea se adjuntan):");
        TextField txtRutaPDF = new TextField();
        Button btnBrowsePDF = new Button("📁 Examinar");
        btnBrowsePDF.setOnAction(e -> {
            javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
            dc.setTitle("Seleccionar carpeta de PDF");
            if (!txtRutaPDF.getText().isBlank()) {
                File ini = new File(txtRutaPDF.getText());
                if (ini.exists()) dc.setInitialDirectory(ini);
            }
            File sel = dc.showDialog(dialog.getOwner());
            if (sel != null) txtRutaPDF.setText(sel.getAbsolutePath());
        });
        HBox rutaBox = new HBox(6, txtRutaPDF, btnBrowsePDF);
        HBox.setHgrow(txtRutaPDF, Priority.ALWAYS);

        Button btnGuardarForm = new Button("💾 Guardar esta configuración");
        btnGuardarForm.setStyle("-fx-background-color: #1565C0; -fx-text-fill: white; -fx-font-weight: bold;");

        VBox formulario = new VBox(8,
            lblAreaNombre, cboArea,
            lblPara, txtPara,
            lblCC, txtCC,
            lblAsunto, txtAsunto,
            lblIntro, txtIntro,
            lblCierre, txtCierre,
            lblRutaPDF, rutaBox,
            btnGuardarForm
        );
        formulario.setPadding(new Insets(8));
        ScrollPane scrollForm = new ScrollPane(formulario);
        scrollForm.setFitToWidth(true);

        // ── Cargar datos en el formulario al seleccionar un item ──
        Runnable cargarFormulario = () -> {
            ConfiguracionCorreo sel = listaAreas.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            cboArea.setValue(sel.getArea());
            txtPara.setText(sel.getDestinatariosString());
            txtCC.setText(sel.getCcString());
            txtAsunto.setText(sel.getAsunto() != null ? sel.getAsunto() : "");
            txtIntro.setText(sel.getCuerpoIntroduccion() != null ? sel.getCuerpoIntroduccion() : "");
            txtCierre.setText(sel.getCuerpoFinal() != null ? sel.getCuerpoFinal() : "");
            txtRutaPDF.setText(sel.getRutaPDF() != null ? sel.getRutaPDF() : "");
        };
        listaAreas.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> cargarFormulario.run());

        // ── Guardar formulario en el objeto seleccionado ──
        btnGuardarForm.setOnAction(e -> {
            ConfiguracionCorreo sel = listaAreas.getSelectionModel().getSelectedItem();
            if (sel == null) {
                mostrarAlerta("Sin selección", "Selecciona un área de la lista o crea una nueva.", Alert.AlertType.WARNING);
                return;
            }
            String areaVal = cboArea.getValue();
            if (areaVal == null || areaVal.isBlank()) {
                mostrarAlerta("Campo requerido", "El nombre del área no puede estar vacío.", Alert.AlertType.WARNING);
                return;
            }
            sel.setArea(areaVal.trim());
            sel.setDestinatariosDesdeString(txtPara.getText());
            sel.setCcDesdeString(txtCC.getText());
            sel.setAsunto(txtAsunto.getText().trim());
            sel.setCuerpoIntroduccion(txtIntro.getText());
            sel.setCuerpoFinal(txtCierre.getText());
            sel.setRutaPDF(txtRutaPDF.getText().trim());

            GestorConfiguracionCorreo.guardar(configsCorreo);
            listaAreas.refresh();
            agregarLog("💾 Configuración de correo guardada para área: " + areaVal.trim());
            mostrarAlerta("Guardado", "Configuración de correo guardada correctamente.", Alert.AlertType.INFORMATION);
        });

        // ── Nueva área ──
        btnNuevaConfig.setOnAction(e -> {
            TextInputDialog dlg = new TextInputDialog();
            dlg.setTitle("Nueva configuración");
            dlg.setHeaderText("Nombre del área:");
            dlg.setContentText("Área:");
            dlg.showAndWait().ifPresent(nombre -> {
                if (!nombre.isBlank()) {
                    ConfiguracionCorreo nuevo = new ConfiguracionCorreo(nombre.trim());
                    // Valores predeterminados del área Siniestros como ejemplo
                    if (nombre.trim().equalsIgnoreCase("Siniestros")) {
                        nuevo.setAsunto("[Pruebas de Disponibilidad] Validación de Operatividad Área {area} [{fecha}]");
                        nuevo.setDestinatariosDesdeString("cindy.berroteran@bciseguros.com; sebastian.vargas@bciseguros.com");
                        nuevo.setCcDesdeString("Soporte Nivel 1 <soporte@cliptecnologia.com>; Juan Andres Barraza Zaso <juan.barraza@bciseguros.com>; Marylennis De Los Angeles Franco Castro <marylennis.franco@bciseguros.com>");
                        nuevo.setRutaPDF("C:\\Users\\IARC\\Desktop\\Entregas Documentos Parchado\\PDF\\Siniestros");
                        nuevo.setCuerpoIntroduccion("Estimados,\nPor la presente, se adjunta el informe de validación de operatividad correspondiente a:");
                        nuevo.setCuerpoFinal("Realizado tras la reciente aplicación del parche. El informe detalla los resultados de las pruebas efectuadas, validando el correcto funcionamiento del sistema.");
                    }
                    configsCorreo.add(nuevo);
                    listaAreas.getItems().add(nuevo);
                    listaAreas.getSelectionModel().select(nuevo);
                    GestorConfiguracionCorreo.guardar(configsCorreo);
                }
            });
        });

        // ── Eliminar área ──
        btnEliminarConfig.setOnAction(e -> {
            ConfiguracionCorreo sel = listaAreas.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Eliminar la configuración del área \"" + sel.getArea() + "\"?",
                ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.YES) {
                    configsCorreo.remove(sel);
                    listaAreas.getItems().remove(sel);
                    GestorConfiguracionCorreo.guardar(configsCorreo);
                }
            });
        });

        SplitPane splitPane = new SplitPane(panelLista, scrollForm);
        splitPane.setDividerPositions(0.24);
        dialog.getDialogPane().setContent(splitPane);
        dialog.showAndWait();
    }

    /**
     * Inicia o detiene la automatización programada de ejecuciones
     */
    private void automatizarEjecucion() {
        // Recolectar proyectos seleccionados (checkbox en la tabla)
        List<ProyectoAutomatizacion> seleccionados = proyectos.stream()
                .filter(ProyectoAutomatizacion::isSeleccionado)
                .collect(Collectors.toList());

        if (seleccionados.isEmpty()) {
            mostrarAlerta("Sin selección", "Marca los checkbox de los proyectos que quieres programar y vuelve a presionar Automatizar.", Alert.AlertType.WARNING);
            return;
        }

        // Diálogo para fecha y hora
        Dialog<TareaProgramada> dialog = new Dialog<>();
        dialog.setTitle("Programar Automatización");
        dialog.setHeaderText("Configura fecha y hora para ejecutar los proyectos seleccionados");

        ButtonType btnProgramar = new ButtonType("Programar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnProgramar, ButtonType.CANCEL);

        VBox contenido = new VBox(10);
        contenido.setPadding(new Insets(15));

        Label lblNombre = new Label("Nombre de la tarea (opcional):");
        TextField txtNombre = new TextField();

        Label lblFecha = new Label("Fecha:");
        DatePicker datePicker = new DatePicker(java.time.LocalDate.now());

        Label lblHora = new Label("Hora (HH:mm):");
        TextField txtHora = new TextField("09");
        txtHora.setPrefWidth(50);
        txtHora.setPromptText("HH");
        TextField txtMin = new TextField("00");
        txtMin.setPrefWidth(50);
        txtMin.setPromptText("mm");
        // Permitir solo dígitos y máximo 2 caracteres
        txtHora.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;
            String filtered = newVal.replaceAll("[^0-9]", "");
            if (filtered.length() > 2) filtered = filtered.substring(0,2);
            if (!filtered.equals(newVal)) txtHora.setText(filtered);
        });
        // Al perder foco, formatear con ceros y validar rango 00-23
        txtHora.focusedProperty().addListener((obs, oldV, newV) -> {
            if (!newV) {
                String t = txtHora.getText() == null ? "" : txtHora.getText().trim();
                if (t.isEmpty()) {
                    txtHora.setText("00");
                } else {
                    try {
                        int v = Integer.parseInt(t);
                        if (v < 0 || v > 23) txtHora.setText("00");
                        else txtHora.setText(String.format("%02d", v));
                    } catch (NumberFormatException ex) {
                        txtHora.setText("00");
                    }
                }
            }
        });
        txtMin.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;
            String filtered = newVal.replaceAll("[^0-9]", "");
            if (filtered.length() > 2) filtered = filtered.substring(0,2);
            if (!filtered.equals(newVal)) txtMin.setText(filtered);
        });
        // Al perder foco, formatear con ceros y validar rango 00-59
        txtMin.focusedProperty().addListener((obs, oldV, newV) -> {
            if (!newV) {
                String t = txtMin.getText() == null ? "" : txtMin.getText().trim();
                if (t.isEmpty()) {
                    txtMin.setText("00");
                } else {
                    try {
                        int v = Integer.parseInt(t);
                        if (v < 0 || v > 59) txtMin.setText("00");
                        else txtMin.setText(String.format("%02d", v));
                    } catch (NumberFormatException ex) {
                        txtMin.setText("00");
                    }
                }
            }
        });
        HBox horaBox = new HBox(5, txtHora, new Label(":"), txtMin);

        Label lblInfo = new Label("La tarea se añadirá a la lista de tareas programadas y se ejecutará en la fecha/hora indicada.");
        lblInfo.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        // Opcion de modo: CSV only o ejecutar+reportes o ejecutar con correo
        Label lblModo = new Label("Modo de tarea:");
        RadioButton rbCsv = new RadioButton("Solo CSV de resultados (no ejecuta proyectos)");
        RadioButton rbExec = new RadioButton("Ejecutar proyectos y generar informes");
        RadioButton rbEmail = new RadioButton("Ejecutar con informe y enviar por correo");
        ToggleGroup tgModo = new ToggleGroup();
        rbCsv.setToggleGroup(tgModo);
        rbExec.setToggleGroup(tgModo);
        rbEmail.setToggleGroup(tgModo);
        rbExec.setSelected(true);

        contenido.getChildren().addAll(lblNombre, txtNombre, lblFecha, datePicker, lblHora, horaBox, lblModo, rbCsv, rbExec, rbEmail, lblInfo);
        dialog.getDialogPane().setContent(contenido);

        dialog.setResultConverter(btn -> {
            if (btn == btnProgramar) {
                // Parsear hora/minuto desde TextFields y validar rangos
                int horaVal;
                int minVal;
                try {
                    String htxt = txtHora.getText() == null ? "" : txtHora.getText();
                    String mtxt = txtMin.getText() == null ? "" : txtMin.getText();
                    if (htxt.isEmpty() || mtxt.isEmpty()) {
                        mostrarAlerta("Hora inválida", "Debes ingresar hora y minutos.", Alert.AlertType.WARNING);
                        return null;
                    }
                    horaVal = Integer.parseInt(htxt);
                    minVal = Integer.parseInt(mtxt);
                } catch (NumberFormatException ex) {
                    mostrarAlerta("Hora inválida", "Hora o minutos no son numéricos.", Alert.AlertType.WARNING);
                    return null;
                }
                if (horaVal < 0 || horaVal > 23 || minVal < 0 || minVal > 59) {
                    mostrarAlerta("Hora inválida", "Hora debe estar entre 00 y 23; minutos entre 00 y 59.", Alert.AlertType.WARNING);
                    return null;
                }

                LocalDateTime fechaHora = LocalDateTime.of(datePicker.getValue(), java.time.LocalTime.of(horaVal, minVal));
                if (fechaHora.isBefore(LocalDateTime.now())) {
                    mostrarAlerta("Fecha inválida", "La fecha y hora seleccionadas ya pasaron.", Alert.AlertType.WARNING);
                    return null;
                }
                List<String> nombres = seleccionados.stream().map(ProyectoAutomatizacion::getNombre).collect(Collectors.toList());
                String nombreTarea = txtNombre.getText() == null || txtNombre.getText().trim().isEmpty() ? "Tarea " + (programadorTareas.listarTareas().size()+1) : txtNombre.getText().trim();
                TareaProgramada tarea = new TareaProgramada(nombreTarea, nombres, fechaHora);
                if (rbCsv.isSelected()) {
                    tarea.setModo(TareaProgramada.Modo.CSV_ONLY);
                } else if (rbEmail.isSelected()) {
                    tarea.setModo(TareaProgramada.Modo.EXEC_WITH_EMAIL);
                } else {
                    tarea.setModo(TareaProgramada.Modo.EXEC_AND_REPORT);
                }
                return tarea;
            }
            return null;
        });

        Optional<TareaProgramada> res = dialog.showAndWait();
        res.ifPresent(tarea -> {
            programadorTareas.agregarTarea(tarea);
            agregarLog("🗓️ Tarea programada: " + tarea.getNombre() + " → " + tarea.getFechaHora().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " (" + tarea.getProyectos().size() + " proyectos)");
            mostrarAlerta("Programada", "La tarea fue añadida correctamente.", Alert.AlertType.INFORMATION);
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
        // Confirmar acción antes de comenzar la descarga automática
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Actualizar ChromeDriver");
        confirmacion.setHeaderText("¿Descargar e instalar la última versión de ChromeDriver?");
        
        String urls = "📥 DESCARGA MANUAL:\n" +
                      "• Chrome for Testing: https://googlechromelabs.github.io/chrome-for-testing/\n" +
                      "• ChromeDriver (oficial): https://chromedriver.chromium.org/downloads\n" +
                      "• Sitio alternativo: https://getwebdriver.com/chromedriver\n\n";
        
        confirmacion.setContentText(
            "Este proceso:\n" +
            "1. Detectará la versión de Chrome instalada\n" +
            "2. Descargará la última versión compatible de ChromeDriver\n" +
            "3. Actualizará PERMANENTEMENTE chromedriver.exe en todos los proyectos\n\n" +
            urls +
            "¿Desea continuar con la actualización automática?"
        );
        
        // Botón adicional para copiar URLs
        ButtonType btnCopiarURL = new ButtonType("Copiar URLs", ButtonBar.ButtonData.LEFT);
        confirmacion.getButtonTypes().add(0, btnCopiarURL);
        
        java.util.Optional<ButtonType> resultado = confirmacion.showAndWait();
        
        if (resultado.isPresent() && resultado.get() == btnCopiarURL) {
            // Copiar URLs al portapapeles
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString("Chrome for Testing: https://googlechromelabs.github.io/chrome-for-testing/\n" +
                            "ChromeDriver oficial: https://chromedriver.chromium.org/downloads\n" +
                            "Alternativo: https://getwebdriver.com/chromedriver");
            clipboard.setContent(content);
            
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle("URLs Copiadas");
            info.setHeaderText("URLs copiadas al portapapeles");
            info.setContentText("Puedes abrir tu navegador y pegar las URLs para descargar manualmente.");
            info.showAndWait();
            return;
        }
        
        if (!resultado.isPresent() || resultado.get() != ButtonType.OK) {
            return;
        }
        
        // Crear y mostrar diálogo de progreso
        Alert progressDialog = new Alert(Alert.AlertType.INFORMATION);
        progressDialog.setTitle("Actualizando ChromeDriver");
        progressDialog.setHeaderText("Descargando ChromeDriver...");
        progressDialog.setContentText("Iniciando proceso...");
        progressDialog.getButtonTypes().clear(); // Sin botones, no puede cerrarse
        
        TextArea progressArea = new TextArea();
        progressArea.setEditable(false);
        progressArea.setWrapText(true);
        progressArea.setMaxWidth(Double.MAX_VALUE);
        progressArea.setMaxHeight(Double.MAX_VALUE);
        progressArea.setPrefRowCount(15);
        progressDialog.getDialogPane().setExpandableContent(progressArea);
        progressDialog.getDialogPane().setExpanded(true);
        
        // Mostrar el diálogo sin bloquear
        progressDialog.show();

        // Hacer que la X cierre: si la ventana se cierra, interrumpir el hilo
        final Thread[] updaterThread = {null}; // Thread local para actualización
        Stage dialogStage = (Stage) progressDialog.getDialogPane().getScene().getWindow();
        dialogStage.setOnCloseRequest(evt -> {
            if (updaterThread[0] != null && updaterThread[0].isAlive()) {
                updaterThread[0].interrupt();
            }
            // Permitir que la ventana se cierre normalmente
        });

        // Realizar descarga y actualización en background
        updaterThread[0] = new Thread(() -> {
            StringBuilder progressLog = new StringBuilder();

            try {
                // Crear instancia del actualizador con callbacks
                com.orquestador.util.ChromeDriverUpdater updater = new com.orquestador.util.ChromeDriverUpdater();

                updater.setProgressCallback(new com.orquestador.util.ChromeDriverUpdater.ProgressCallback() {
                    @Override
                    public void onProgress(String message) {
                        progressLog.append(message).append("\n");
                        Platform.runLater(() -> {
                            progressArea.setText(progressLog.toString());
                            progressArea.setScrollTop(Double.MAX_VALUE);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        progressLog.append(error).append("\n");
                        Platform.runLater(() -> {
                            progressArea.setText(progressLog.toString());
                            progressArea.setScrollTop(Double.MAX_VALUE);
                        });
                    }

                    @Override
                    public void onComplete(File chromedriverFile) {
                        // No hacer nada aquí, se maneja en el hilo principal
                    }
                });

                // PASO 1: Verificar versiones actuales en proyectos
                progressLog.append("🔍 Verificando versiones actuales de ChromeDriver en proyectos...\n");
                Platform.runLater(() -> {
                    progressArea.setText(progressLog.toString());
                    progressDialog.setHeaderText("Verificando versiones...");
                });
                
                String versionMasComun = null;
                java.util.Map<String, Integer> versionesEncontradas = new java.util.HashMap<>();
                int totalDriversRevisados = 0;
                int driversConVersion = 0;
                
                for (ProyectoAutomatizacion proyecto : proyectos) {
                    if (proyecto.getRuta() == null || proyecto.getRuta().trim().isEmpty()) continue;
                    
                    java.io.File carpetaProyecto = new java.io.File(proyecto.getRuta());
                    java.util.List<java.io.File> driverEncontrados = buscarChromeDriver(carpetaProyecto);
                    
                    for (java.io.File driver : driverEncontrados) {
                        totalDriversRevisados++;
                        String version = com.orquestador.util.ChromeDriverUpdater.detectarVersionChromeDriver(driver);
                        if (version != null && !version.isEmpty()) {
                            versionesEncontradas.put(version, versionesEncontradas.getOrDefault(version, 0) + 1);
                            driversConVersion++;
                            
                            // Log para debugging
                            String msg = "   📌 " + proyecto.getNombre() + ": versión " + version + "\n";
                            progressLog.append(msg);
                            Platform.runLater(() -> progressArea.setText(progressLog.toString()));
                        }
                    }
                }
                
                progressLog.append("\n📊 Estadísticas:\n");
                progressLog.append("   Total de ChromeDrivers encontrados: " + totalDriversRevisados + "\n");
                progressLog.append("   ChromeDrivers con versión detectada: " + driversConVersion + "\n");
                Platform.runLater(() -> progressArea.setText(progressLog.toString()));
                
                // Obtener la versión más común
                if (!versionesEncontradas.isEmpty()) {
                    versionMasComun = versionesEncontradas.entrySet().stream()
                        .max(java.util.Map.Entry.comparingByValue())
                        .get().getKey();
                    
                    String msg = "   ✅ Versión más común instalada: " + versionMasComun + 
                               " (" + versionesEncontradas.get(versionMasComun) + " proyecto(s))\n";
                    progressLog.append(msg);
                    Platform.runLater(() -> progressArea.setText(progressLog.toString()));
                    
                    // Mostrar todas las versiones encontradas si hay más de una
                    if (versionesEncontradas.size() > 1) {
                        progressLog.append("   ⚠️ NOTA: Se encontraron múltiples versiones:\n");
                        for (java.util.Map.Entry<String, Integer> entry : versionesEncontradas.entrySet()) {
                            progressLog.append("      - " + entry.getKey() + " (" + entry.getValue() + " proyecto(s))\n");
                        }
                        Platform.runLater(() -> progressArea.setText(progressLog.toString()));
                    }
                } else {
                    progressLog.append("   ⚠️ No se pudo detectar versión en ningún ChromeDriver existente\n");
                    Platform.runLater(() -> progressArea.setText(progressLog.toString()));
                }
                
                // PASO 2: Obtener versión más reciente disponible
                String versionChrome = updater.detectarVersionChrome();
                if (versionChrome == null) {
                    Platform.runLater(() -> {
                        progressDialog.close();
                        Alert error = new Alert(Alert.AlertType.ERROR);
                        error.setTitle("Error");
                        error.setHeaderText("No se pudo detectar Chrome");
                        error.setContentText("No se pudo detectar la versión de Chrome instalada.");
                        error.showAndWait();
                    });
                    return;
                }
                
                String urlDescarga = updater.obtenerURLDescargaChromeDriver(versionChrome);
                if (urlDescarga == null) {
                    Platform.runLater(() -> {
                        progressDialog.close();
                        Alert error = new Alert(Alert.AlertType.ERROR);
                        error.setTitle("Error");
                        error.setHeaderText("No se pudo obtener ChromeDriver");
                        error.setContentText("No se pudo obtener la URL de descarga.");
                        error.showAndWait();
                    });
                    return;
                }
                
                // PASO 3: Comparar versiones (solo versiones mayores, ignorando builds menores)
                if (versionMasComun != null) {
                    int comparacion = com.orquestador.util.ChromeDriverUpdater.compararVersionesMayores(versionMasComun, versionChrome);
                    
                    if (comparacion >= 0) {
                        // Ya está actualizado - crear variables finales para el lambda
                        final String versionActual = versionMasComun;
                        final String versionDisponible = versionChrome;
                        
                        String msg = "\n✅ ¡Ya tienes la última versión!\n" +
                                   "   Versión instalada: " + versionActual + "\n" +
                                   "   Versión disponible: " + versionDisponible + "\n" +
                                   "   (Comparando versiones mayores: " + extraerVersionMayor(versionActual) + " vs " + extraerVersionMayor(versionDisponible) + ")\n" +
                                   "   No es necesario actualizar.\n";
                        progressLog.append(msg);
                        
                        Platform.runLater(() -> {
                            progressDialog.close();
                            Alert info = new Alert(Alert.AlertType.INFORMATION);
                            info.setTitle("Actualización no necesaria");
                            info.setHeaderText("✅ ChromeDriver ya está actualizado");
                            info.setContentText(
                                "Versión actual: " + versionActual + "\n" +
                                "Versión disponible: " + versionDisponible + "\n\n" +
                                "No es necesario actualizar.\n" +
                                "(Solo se actualizan cambios de versión mayor)"
                            );
                            
                            TextArea ta = new TextArea(progressLog.toString());
                            ta.setEditable(false);
                            ta.setWrapText(true);
                            info.getDialogPane().setExpandableContent(ta);
                            info.showAndWait();
                        });
                        return;
                    } else {
                        String msg = "🔼 Actualización disponible:\n" +
                                   "   Versión actual: " + versionMasComun + " (" + extraerVersionMayor(versionMasComun) + ")\n" +
                                   "   Versión nueva: " + versionChrome + " (" + extraerVersionMayor(versionChrome) + ")\n\n";
                        progressLog.append(msg);
                        Platform.runLater(() -> progressArea.setText(progressLog.toString()));
                    }
                } else {
                    // No se pudo detectar versión actual, proceder con la actualización
                    String msg = "⚠️ No se pudo verificar versión actual, procediendo con actualización...\n\n";
                    progressLog.append(msg);
                    Platform.runLater(() -> progressArea.setText(progressLog.toString()));
                }
                
                // PASO 4: Descargar nueva versión
                Platform.runLater(() -> progressDialog.setHeaderText("Descargando ChromeDriver..."));
                java.io.File nuevoDriver = updater.descargarChromeDriver(urlDescarga);

                if (Thread.currentThread().isInterrupted()) {
                    progressLog.append("\n✋ Proceso cancelado por el usuario antes de actualizar proyectos.\n");
                    Platform.runLater(() -> {
                        progressDialog.close();
                        Alert cancel = new Alert(Alert.AlertType.INFORMATION);
                        cancel.setTitle("Cancelado");
                        cancel.setHeaderText("Actualización cancelada");
                        cancel.setContentText("La actualización fue cancelada por el usuario.");
                        TextArea ta = new TextArea(progressLog.toString());
                        ta.setEditable(false);
                        ta.setWrapText(true);
                        cancel.getDialogPane().setExpandableContent(ta);
                        cancel.getDialogPane().setExpanded(true);
                        cancel.showAndWait();
                    });
                    return;
                }

                if (nuevoDriver == null || !nuevoDriver.exists()) {
                    Platform.runLater(() -> {
                        progressDialog.close();
                        Alert error = new Alert(Alert.AlertType.ERROR);
                        error.setTitle("Error");
                        error.setHeaderText("No se pudo descargar ChromeDriver");
                        error.setContentText("Revisa el log para más detalles.");

                        TextArea errorArea = new TextArea(progressLog.toString());
                        errorArea.setEditable(false);
                        errorArea.setWrapText(true);
                        error.getDialogPane().setExpandableContent(errorArea);
                        error.getDialogPane().setExpanded(true);
                        error.showAndWait();
                    });
                    return;
                }

                // Actualizar en todos los proyectos
                progressLog.append("\n🔄 Actualizando proyectos...\n");
                progressLog.append("⭐ Los archivos se copiarán de forma PERMANENTE en el disco\n");
                progressLog.append("⭐ Los cambios persisten después de cerrar el programa\n\n");
                Platform.runLater(() -> {
                    progressArea.setText(progressLog.toString());
                    progressDialog.setHeaderText("Actualizando proyectos...");
                });

                int actualizados = 0;
                int errores = 0;
                StringBuilder detalles = new StringBuilder();

                for (ProyectoAutomatizacion proyecto : proyectos) {
                    if (Thread.currentThread().isInterrupted()) break;

                    if (proyecto.getRuta() == null || proyecto.getRuta().trim().isEmpty()) {
                        continue;
                    }

                    try {
                        java.io.File carpetaProyecto = new java.io.File(proyecto.getRuta());
                        java.util.List<java.io.File> driverEncontrados = buscarChromeDriver(carpetaProyecto);

                        if (driverEncontrados.isEmpty()) {
                            String msg = "⚠️ " + proyecto.getNombre() + ": No se encontró chromedriver.exe\n";
                            detalles.append(msg);
                            progressLog.append(msg);
                            Platform.runLater(() -> progressArea.setText(progressLog.toString()));
                            continue;
                        }

                        for (java.io.File driverAntiguo : driverEncontrados) {
                            if (Thread.currentThread().isInterrupted()) break;
                            
                            boolean copiado = false;
                            String errorDetallado = "";
                            
                            // Intentar copiar con hasta 3 reintentos
                            for (int intento = 1; intento <= 3 && !copiado; intento++) {
                                try {
                                    // Si no es el primer intento, intentar cerrar procesos chromedriver
                                    if (intento > 1) {
                                        String msg = "   🔄 Intento " + intento + "/3 para " + proyecto.getNombre() + "...\n";
                                        progressLog.append(msg);
                                        Platform.runLater(() -> progressArea.setText(progressLog.toString()));
                                        
                                        // Intentar matar procesos chromedriver.exe
                                        try {
                                            cerrarProcesosChromeDriver();
                                            Thread.sleep(1000); // Esperar 1 segundo
                                        } catch (Exception ignored) {}
                                    }
                                    
                                    java.nio.file.Files.copy(
                                        nuevoDriver.toPath(),
                                        driverAntiguo.toPath(),
                                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                                    );
                                    String msg = "✅ " + proyecto.getNombre() + 
                                               " [PERMANENTE]: " + driverAntiguo.getAbsolutePath().replace(proyecto.getRuta(), "...") + "\n";
                                    detalles.append(msg);
                                    progressLog.append(msg);
                                    Platform.runLater(() -> progressArea.setText(progressLog.toString()));
                                    actualizados++;
                                    copiado = true;
                                    
                                } catch (java.nio.file.FileSystemException e) {
                                    // Error de sistema de archivos (archivo bloqueado, permisos, etc.)
                                    String msgError = e.getMessage();
                                    if (msgError != null && msgError.contains("being used by another process")) {
                                        errorDetallado = "Archivo bloqueado (en uso por otro proceso)";
                                    } else if (msgError != null && msgError.contains("Access is denied")) {
                                        errorDetallado = "Acceso denegado (permisos insuficientes)";
                                    } else {
                                        errorDetallado = "Error de sistema: " + (msgError != null ? msgError : e.getClass().getSimpleName());
                                    }
                                    
                                    if (intento == 3) {
                                        // Último intento fallido
                                        String msg = "❌ " + proyecto.getNombre() + 
                                                   ": " + errorDetallado + "\n" +
                                                   "   Ruta: " + driverAntiguo.getAbsolutePath() + "\n" +
                                                   "   Solución: Cierra todas las automatizaciones en ejecución e intenta nuevamente\n";
                                        detalles.append(msg);
                                        progressLog.append(msg);
                                        Platform.runLater(() -> progressArea.setText(progressLog.toString()));
                                        errores++;
                                    }
                                    
                                } catch (Exception e) {
                                    // Otros errores
                                    errorDetallado = e.getClass().getSimpleName() + ": " + 
                                                   (e.getMessage() != null ? e.getMessage() : "Error desconocido");
                                    
                                    if (intento == 3) {
                                        String msg = "❌ " + proyecto.getNombre() + 
                                                   ": " + errorDetallado + "\n" +
                                                   "   Ruta: " + driverAntiguo.getAbsolutePath() + "\n";
                                        detalles.append(msg);
                                        progressLog.append(msg);
                                        Platform.runLater(() -> progressArea.setText(progressLog.toString()));
                                        errores++;
                                    }
                                }
                            }
                        }

                    } catch (Exception e) {
                        String msg = "❌ " + proyecto.getNombre() + 
                                   ": Error - " + e.getMessage() + "\n";
                        detalles.append(msg);
                        progressLog.append(msg);
                        Platform.runLater(() -> progressArea.setText(progressLog.toString()));
                        errores++;
                    }
                }

                // Si fue interrumpido, informar
                if (Thread.currentThread().isInterrupted()) {
                    progressLog.append("\n✋ Proceso cancelado por el usuario.\n");
                }

                // Mostrar resultado final
                final int totalActualizados = actualizados;
                final int totalErrores = errores;
                final String mensajeDetalles = detalles.toString();

                Platform.runLater(() -> {
                    progressDialog.close();

                    Alert alertResultado = new Alert(
                        totalErrores == 0 ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING
                    );
                    alertResultado.setTitle("Actualización completada");
                    alertResultado.setHeaderText("ChromeDriver actualizado PERMANENTEMENTE");

                    String resumen = "✅ Proyectos actualizados: " + totalActualizados + "\n";
                    if (totalErrores > 0) {
                        resumen += "❌ Errores: " + totalErrores + "\n";
                    }
                    resumen += "\n⭐ LOS ARCHIVOS SE COPIARON DE FORMA PERMANENTE\n";
                    resumen += "Los cambios persisten después de cerrar el programa.\n\n";
                    resumen += "📥 Descarga manual si es necesario:\n";
                    resumen += "• https://googlechromelabs.github.io/chrome-for-testing/\n";
                    resumen += "• https://chromedriver.chromium.org/downloads\n\n";
                    resumen += "Detalles:";

                    alertResultado.setContentText(resumen);

                    TextArea textArea = new TextArea(mensajeDetalles);
                    textArea.setEditable(false);
                    textArea.setWrapText(true);
                    textArea.setMaxWidth(Double.MAX_VALUE);
                    textArea.setMaxHeight(Double.MAX_VALUE);

                    alertResultado.getDialogPane().setExpandableContent(textArea);
                    alertResultado.getDialogPane().setExpanded(true);
                    alertResultado.showAndWait();
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    progressDialog.close();
                    Alert error = new Alert(Alert.AlertType.ERROR);
                    error.setTitle("Error");
                    error.setHeaderText("Error durante la actualización");
                    error.setContentText("Error: " + e.getMessage());

                    TextArea errorArea = new TextArea(progressLog.toString() + "\n\nException: " + e.toString());
                    errorArea.setEditable(false);
                    errorArea.setWrapText(true);
                    error.getDialogPane().setExpandableContent(errorArea);
                    error.getDialogPane().setExpanded(true);
                    error.showAndWait();
                });
                e.printStackTrace();
            }

        }, "ChromeDriverUpdater-Thread");
        
        // Iniciar thread (esta funcionalidad fue deshabilitada pero el código permanece por compatibilidad)
        updaterThread[0].start();
    }
    
    
    /**
     * Procesa la actualización de ChromeDriver a partir de un archivo
     */
    private void procesarActualizacionChromeDriver(File archivoSeleccionado) {
        if (archivoSeleccionado == null || !archivoSeleccionado.exists()) {
            return;
        }
        
        // Confirmar que es chromedriver.exe
        if (!archivoSeleccionado.getName().equalsIgnoreCase("chromedriver.exe")) {
            Alert error = new Alert(Alert.AlertType.ERROR);
            error.setTitle("Archivo incorrecto");
            error.setHeaderText("El archivo debe ser chromedriver.exe");
            error.setContentText("Seleccionaste: " + archivoSeleccionado.getName());
            error.showAndWait();
            return;
        }
        
        // Detectar versión del driver seleccionado
        String versionDriver = com.orquestador.util.ChromeDriverUpdater.detectarVersionChromeDriver(archivoSeleccionado);
        String infoVersion = versionDriver != null && !versionDriver.isEmpty() 
            ? "Versión detectada: " + versionDriver 
            : "No se pudo detectar la versión";
        
        // Confirmar acción
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Confirmar instalación");
        confirmacion.setHeaderText("¿Copiar este ChromeDriver a todos los proyectos?");
        confirmacion.setContentText(
            "Archivo: " + archivoSeleccionado.getName() + "\n" +
            infoVersion + "\n\n" +
            "⭐ Se copiará PERMANENTEMENTE a todos los proyectos que tengan chromedriver.exe\n" +
            "Los archivos existentes serán reemplazados.\n\n" +
            "¿Continuar?"
        );
        
        if (confirmacion.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        
        // Copiar a todos los proyectos
        StringBuilder log = new StringBuilder();
        log.append("📂 Actualizando chromedriver.exe en proyectos...\n");
        if (versionDriver != null && !versionDriver.isEmpty()) {
            log.append("📌 Versión: ").append(versionDriver).append("\n\n");
        }
        
        int actualizados = 0;
        int errores = 0;
        int proyectosSinDriver = 0;
        
        for (ProyectoAutomatizacion proyecto : proyectos) {
            if (proyecto.getRuta() == null || proyecto.getRuta().trim().isEmpty()) {
                continue;
            }
            
            try {
                java.io.File carpetaProyecto = new java.io.File(proyecto.getRuta());
                java.util.List<java.io.File> driverEncontrados = buscarChromeDriver(carpetaProyecto);
                
                if (driverEncontrados.isEmpty()) {
                    proyectosSinDriver++;
                    continue;
                }
                
                for (java.io.File driverDestino : driverEncontrados) {
                    try {
                        // Intentar cerrar procesos antes de copiar
                        cerrarProcesosChromeDriver();
                        Thread.sleep(300);
                        
                        java.nio.file.Files.copy(
                            archivoSeleccionado.toPath(),
                            driverDestino.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING
                        );
                        
                        log.append("✅ ").append(proyecto.getNombre()).append("\n");
                        actualizados++;
                        
                    } catch (Exception e) {
                        log.append("❌ ").append(proyecto.getNombre())
                           .append(": ").append(e.getMessage()).append("\n");
                        errores++;
                    }
                }
                
            } catch (Exception e) {
                log.append("❌ ").append(proyecto.getNombre())
                   .append(": ").append(e.getMessage()).append("\n");
                errores++;
            }
        }
        
        // Mostrar resultado
        Alert resultado = new Alert(
            errores == 0 ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING
        );
        resultado.setTitle("Actualización de ChromeDriver");
        resultado.setHeaderText(
            actualizados > 0 
                ? String.format("✅ %d proyecto%s actualizado%s correctamente", actualizados, actualizados == 1 ? "" : "s", actualizados == 1 ? "" : "s")
                : "⚠️ No se pudo actualizar ningún proyecto"
        );
        
        String resumen = "";
        if (actualizados > 0) {
            resumen += String.format("✅ ChromeDriver actualizado en %d proyecto%s\n", actualizados, actualizados == 1 ? "" : "s");
        }
        if (errores > 0) {
            resumen += String.format("❌ %d error%s durante la actualización\n", errores, errores == 1 ? "" : "es");
        }
        if (proyectosSinDriver > 0) {
            resumen += String.format("ℹ️ %d proyecto%s sin chromedriver.exe (omitido%s)\n", 
                proyectosSinDriver, 
                proyectosSinDriver == 1 ? "" : "s",
                proyectosSinDriver == 1 ? "" : "s");
        }
        if (versionDriver != null && !versionDriver.isEmpty()) {
            resumen += "\n📌 Versión instalada: " + versionDriver + "\n";
        }
        resumen += "\n⭐ Los archivos se copiaron PERMANENTEMENTE\n";
        resumen += "Los cambios persisten después de cerrar el programa.\n\n";
        resumen += "Ver detalles abajo ↓";
        
        resultado.setContentText(resumen);
        
        TextArea textArea = new TextArea(log.toString());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        textArea.setPrefRowCount(20);
        
        resultado.getDialogPane().setExpandableContent(textArea);
        resultado.getDialogPane().setExpanded(true);
        resultado.showAndWait();
        
        agregarLog(String.format("✓ ChromeDriver actualizado en %d proyecto%s", actualizados, actualizados == 1 ? "" : "s"));
    }
    
    /**
     * Permite cargar manualmente un chromedriver.exe y copiarlo a todos los proyectos
     */
    private void cargarChromeDriverManual() {
        // Mostrar información sobre dónde descargar
        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Actualizar ChromeDriver");
        info.setHeaderText("Descarga ChromeDriver desde estas páginas oficiales:");
        info.setContentText(
            "📥 PÁGINAS DE DESCARGA:\n\n" +
            "1. Chrome for Testing (RECOMENDADO):\n" +
            "   https://googlechromelabs.github.io/chrome-for-testing/\n\n" +
            "2. ChromeDriver Oficial:\n" +
            "   https://chromedriver.chromium.org/downloads\n\n" +
            "3. Sitio alternativo:\n" +
            "   https://getwebdriver.com/chromedriver\n\n" +
            "⚠️ IMPORTANTE: Descarga la versión que coincida con tu Chrome.\n" +
            "Para ver tu versión: chrome://version en Chrome.\n\n" +
            "Haz clic en OK para seleccionar el archivo chromedriver.exe\n" +
            "O arrastra y suelta el archivo sobre el botón de ChromeDriver."
        );
        
        ButtonType btnCopiarURLs = new ButtonType("Copiar URLs", ButtonBar.ButtonData.LEFT);
        info.getButtonTypes().add(0, btnCopiarURLs);
        
        java.util.Optional<ButtonType> result = info.showAndWait();
        
        if (result.isPresent() && result.get() == btnCopiarURLs) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(
                "Chrome for Testing: https://googlechromelabs.github.io/chrome-for-testing/\n" +
                "ChromeDriver Oficial: https://chromedriver.chromium.org/downloads\n" +
                "Alternativo: https://getwebdriver.com/chromedriver"
            );
            clipboard.setContent(content);
            
            Alert copied = new Alert(Alert.AlertType.INFORMATION);
            copied.setTitle("URLs Copiadas");
            copied.setHeaderText("URLs copiadas al portapapeles");
            copied.setContentText("Abre tu navegador y pega las URLs para descargar.\nDespués vuelve aquí para cargar el archivo.");
            copied.showAndWait();
            return;
        }
        
        if (!result.isPresent() || result.get() != ButtonType.OK) {
            return;
        }
        
        // Abrir selector de archivos
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Seleccionar chromedriver.exe");
        fileChooser.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("ChromeDriver", "chromedriver.exe")
        );
        
        // Intentar abrir en la carpeta de descargas por defecto
        String userHome = System.getProperty("user.home");
        File descargas = new File(userHome, "Downloads");
        if (!descargas.exists()) {
            descargas = new File(userHome, "Descargas");
        }
        if (descargas.exists()) {
            fileChooser.setInitialDirectory(descargas);
        }
        
        File archivoSeleccionado = fileChooser.showOpenDialog(root.getScene().getWindow());
        
        // Procesar el archivo seleccionado
        procesarActualizacionChromeDriver(archivoSeleccionado);
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

    /**
     * Intenta cerrar todos los procesos chromedriver.exe en ejecución (Windows 11)
     */
    private void cerrarProcesosChromeDriver() {
        try {
            // En Windows 11, usar taskkill para cerrar todos los procesos chromedriver.exe
            // /F = forzar terminación, /IM = nombre de imagen (proceso)
            ProcessBuilder pb = new ProcessBuilder("taskkill", "/F", "/IM", "chromedriver.exe");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Leer la salida (opcional, para debugging)
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Silenciosamente leer la salida
                    // System.out.println("[taskkill] " + line);
                }
            }
            
            process.waitFor();
        } catch (Exception e) {
            // Ignorar errores (puede que no haya procesos corriendo)
            // System.err.println("No se pudieron cerrar procesos chromedriver: " + e.getMessage());
        }
    }

    /**
     * Extrae solo la versión mayor (X.Y.Z) de una versión completa (X.Y.Z.W)
     */
    private String extraerVersionMayor(String version) {
        if (version == null || version.isEmpty()) return "";
        String[] parts = version.split("\\.");
        if (parts.length >= 3) {
            return parts[0] + "." + parts[1] + "." + parts[2];
        }
        return version;
    }

    public Parent getRoot() {
        return root;
    }
    
    /**
     * Alterna entre vista compacta (solo proyectos seleccionados) y vista normal (todos los proyectos)
     */
    private void alternarVistaCompacta() {
        aplicarEstadoVistaCompacta(!vistaCompacta, true);
    }

    /**
     * Aplica el estado de vista compacta (sincroniza UI) y opcionalmente guarda la preferencia.
     * Delega SIEMPRE a aplicarFiltro() para que el filtro de empresa se respete.
     */
    private void aplicarEstadoVistaCompacta(boolean activar, boolean guardar) {
        vistaCompacta = activar;

        if (vistaCompacta) {
            btnVistaCompacta.setStyle("-fx-background-color: #FF5722; -fx-text-fill: white; -fx-font-weight: bold;");
            btnVistaCompacta.setText("📋 Expandir Vista");
            agregarLog("✓ Vista compacta ACTIVADA - Mostrando solo proyectos seleccionados de la empresa actual");
        } else {
            btnVistaCompacta.setStyle("-fx-background-color: #673AB7; -fx-text-fill: white; -fx-font-weight: bold;");
            btnVistaCompacta.setText("📦 Vista Compacta");
            agregarLog("✓ Vista compacta DESACTIVADA - Mostrando todos los proyectos");
        }

        // Siempre reconstruir el predicado combinado (empresa + área + vpn + selección)
        aplicarFiltro();
        tablaProyectos.refresh();

        if (guardar) {
            guardarPreferencias();
        }
    }

    private static final String PREF_FILE = System.getProperty("user.home") + File.separator + ".orquestador.properties";

    private void cargarPreferencias() {
        try {
            File f = new File(PREF_FILE);
            if (!f.exists()) return;
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(f)) {
                props.load(fis);
            }
            
            // PRIMERO: Cargar proyectos deshabilitados
            String disabled = props.getProperty("proyectosDeshabilitados");
            if (disabled != null && !disabled.trim().isEmpty()) {
                String[] parts = disabled.split(";;");
                for (String s : parts) {
                    String t = s.trim();
                    if (!t.isEmpty()) proyectosDeshabilitados.add(t);
                }
            }
            
            // SEGUNDO: Cargar proyectos seleccionados ANTES de activar vista compacta
            // La clave guardada es "empresa::nombre" para evitar mezcla entre empresas.
            // Para compatibilidad con preferencias antiguas (solo nombre) se acepta también el formato viejo.
            String seleccionados = props.getProperty("proyectosSeleccionados");
            if (seleccionados != null && !seleccionados.trim().isEmpty()) {
                String[] parts = seleccionados.split(";");
                Set<String> clavesSeleccionadas = new java.util.HashSet<>();
                for (String s : parts) {
                    String t = s.trim();
                    if (!t.isEmpty()) clavesSeleccionadas.add(t);
                }
                // Aplicar la selección a los proyectos cargados respetando la empresa
                for (ProyectoAutomatizacion proyecto : proyectos) {
                    String claveNueva  = (proyecto.getEmpresa() != null ? proyecto.getEmpresa() : "") + "::" + proyecto.getNombre();
                    String claveVieja  = proyecto.getNombre(); // backward compat
                    proyecto.setSeleccionado(clavesSeleccionadas.contains(claveNueva)
                                          || clavesSeleccionadas.contains(claveVieja));
                }
            }
            
            // TERCERO: Cargar estado de vista compacta DESPUÉS de tener los proyectos seleccionados
            String v = props.getProperty("vistaCompacta");
            if (v != null && v.equalsIgnoreCase("true")) {
                // Aplicar sin sobrescribir el fichero (guardar=false)
                aplicarEstadoVistaCompacta(true, false);
            }
            
            // CUARTO: Refrescar la tabla para mostrar los cambios
            String empresasGuardadas = props.getProperty("empresasRegistradas");
            if (empresasGuardadas != null && !empresasGuardadas.trim().isEmpty()) {
                for (String empresa : empresasGuardadas.split(";;")) {
                    String limpia = empresa.trim();
                    if (!limpia.isEmpty()) empresasRegistradas.add(limpia);
                }
            }
            empresasRegistradas.addAll(obtenerEmpresasDesdeProyectos());
            String empresaSeleccionada = props.getProperty("empresaSeleccionada", EMPRESA_DEFAULT);
            refrescarEmpresasDisponibles(empresaSeleccionada);
            aplicarFiltro();

            // QUINTO: Refrescar la tabla para mostrar los cambios
            if (tablaProyectos != null) {
                tablaProyectos.refresh();
            }
        } catch (Exception e) {
            // No interrumpir la aplicación por un error en preferencias
            System.out.println("Advertencia: no se pudieron cargar preferencias: " + e.getMessage());
        }
    }

    private void guardarPreferencias() {
        try {
            Properties props = new Properties();
            // Guardar estado de vista compacta
            props.setProperty("vistaCompacta", Boolean.toString(vistaCompacta));
            // Guardar proyectos deshabilitados
            String joined = String.join(";;", proyectosDeshabilitados);
            props.setProperty("proyectosDeshabilitados", joined);
            // Guardar proyectos seleccionados con clave "empresa::nombre" para evitar mezcla entre empresas
            String seleccionados = proyectos.stream()
                .filter(ProyectoAutomatizacion::isSeleccionado)
                .map(p -> (p.getEmpresa() != null ? p.getEmpresa() : "") + "::" + p.getNombre())
                .collect(Collectors.joining(";;"));
            props.setProperty("proyectosSeleccionados", seleccionados);
            props.setProperty("empresasRegistradas", String.join(";;", empresasRegistradas));
            props.setProperty("empresaSeleccionada", cboFiltroEmpresa != null && cboFiltroEmpresa.getValue() != null
                ? cboFiltroEmpresa.getValue() : EMPRESA_DEFAULT);
            File f = new File(PREF_FILE);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                props.store(fos, "Orquestador preferencias");
            }
        } catch (Exception e) {
            System.out.println("Advertencia: no se pudieron guardar preferencias: " + e.getMessage());
        }
    }

    private boolean isProyectoDeshabilitado(String nombre) {
        if (nombre == null) return false;
        return proyectosDeshabilitados.contains(nombre);
    }

    private void setProyectoDeshabilitado(String nombre, boolean deshabilitado) {
        if (nombre == null) return;
        if (deshabilitado) proyectosDeshabilitados.add(nombre);
        else proyectosDeshabilitados.remove(nombre);
        guardarPreferencias();
    }
    
    /**
     * Convierte milisegundos a un formato legible como "1 hora 15 minutos 30 segundos"
     */
    private String formatearTiempoTotal(long duracionMs) {
        long duracionSegundos = duracionMs / 1000;
        
        long horas = duracionSegundos / 3600;
        long minutos = (duracionSegundos % 3600) / 60;
        long segundos = duracionSegundos % 60;
        
        StringBuilder sb = new StringBuilder();
        
        if (horas > 0) {
            sb.append(horas).append(horas == 1 ? " hora" : " horas");
        }
        
        if (minutos > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(minutos).append(minutos == 1 ? " minuto" : " minutos");
        }
        
        if (segundos > 0 || sb.length() == 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(segundos).append(segundos == 1 ? " segundo" : " segundos");
        }
        
        return sb.toString();
    }

    /**
     * Adjunta un pequeño popup informativo que se muestra si el cursor permanece 3 segundos sobre el botón
     */
    private void attachHoverInfo(Button btn, String descripcion) {
        if (btn == null || descripcion == null || descripcion.trim().isEmpty()) return;

        // Crear popup y contenido
        Popup popup = new Popup();
        Label lbl = new Label(descripcion);
        lbl.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #333; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 8; -fx-font-size: 12px; -fx-text-fill: #222;");
        lbl.setWrapText(true);
        lbl.setMaxWidth(340);
        popup.getContent().add(lbl);

        PauseTransition delay = new PauseTransition(Duration.seconds(3));
        delay.setOnFinished(ev -> {
            try {
                if (btn.isHover()) {
                    Point2D pos = btn.localToScreen(0, btn.getHeight());
                    if (pos != null) popup.show(btn, pos.getX(), pos.getY());
                }
            } catch (Exception ignored) {}
        });

        btn.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> delay.playFromStart());
        btn.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            delay.stop();
            if (popup.isShowing()) popup.hide();
        });
        // Asegurar que si se hace click también se oculte
        btn.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (popup.isShowing()) popup.hide();
        });
    }
}

