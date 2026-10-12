package com.orquestador.servicio;

import com.orquestador.modelo.Proyecto;
import com.orquestador.utilidades.GestorImagenes;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlCursor;
import javax.xml.namespace.QName;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Servicio para generar documentos Word y PDF
 */
public class GeneradorDocumentos {
    
    private static final double ANCHO_CM = 16.53;
    private static final double ALTO_CM = 9.53;
    private static final String PATRON_FECHA = "[Fecha]"; // Placeholder para la fecha actual - conserva formato del esqueleto (cuadro de texto)
    private static final String PATRON_IMAGENES = "[IMAGENES]"; // Placeholder único para todas las imágenes
    private static final String PATRON_MANUAL = "[MANUAL]"; // Placeholder para nombre del manual (sin número inicial)
    private static final String PATRON_MANUAL2 = "[MANUAL2]"; // Placeholder para nombre del manual (sin número inicial) - conserva formato del esqueleto (cuadro de texto)
    private static final String PATRON_MANUAL3 = "[MANUAL3]"; // Placeholder para nombre del manual (sin número inicial) - Calibri, tamaño 11, negro, negrita
    
    // Ruta global del template universal
    private static String RUTA_TEMPLATE_GLOBAL = resolverTemplateDefecto();

    private static String resolverTemplateDefecto() {
        String fijo = "c:\\MIs Respaldos\\OrquestadorYGeneradorReportes\\Esqueleto\\Esqueleto Manuales BCI.docx";
        String appPath = System.getProperty("jpackage.app-path");
        List<String> bases = new ArrayList<>();
        if (appPath != null) bases.add(new File(appPath).getParent());
        bases.add(System.getProperty("user.dir"));
        for (String base : bases) {
            if (base == null) continue;
            File f = new File(base, "Esqueleto" + File.separator + "Esqueleto Manuales BCI.docx");
            if (f.exists()) return f.getAbsolutePath();
        }
        return fijo;
    }
    
    private final Proyecto proyecto;
    private StringBuilder resumenGeneracion;
    private final List<String> advertencias = new ArrayList<>();
    
    public GeneradorDocumentos(Proyecto proyecto) {
        this.proyecto = proyecto;
        this.resumenGeneracion = new StringBuilder();
    }

    public List<String> getAdvertencias() {
        return advertencias;
    }
    
    /**
     * Establece la ruta global del template universal
     */
    public static void setRutaTemplateGlobal(String ruta) {
        RUTA_TEMPLATE_GLOBAL = ruta;
    }
    
    /**
     * Obtiene la ruta global del template universal
     */
    public static String getRutaTemplateGlobal() {
        return RUTA_TEMPLATE_GLOBAL;
    }

    private void registrarAdvertenciaImagenes(String etiquetaInforme, int insertadas, int esperadas) {
        if (esperadas > 0 && insertadas < esperadas) {
            String etiqueta = (etiquetaInforme == null || etiquetaInforme.isEmpty()) ? "principal" : etiquetaInforme;
            advertencias.add("Informe " + etiqueta + ": imagenes insertadas " + insertadas + "/" + esperadas);
        }
    }

    private void guardarAdvertenciasArchivo(String rutaSalidaWord, String timestamp) {
        if (advertencias.isEmpty()) return;
        try {
            String nombreBase = proyecto.getNombre().replaceAll("[^a-zA-Z0-9._-]", "_");
            String nombreArchivo = nombreBase + "_warnings_" + timestamp + ".txt";
            File salida = new File(rutaSalidaWord, nombreArchivo);
            StringBuilder contenido = new StringBuilder();
            contenido.append("WARNINGS - IMAGENES FALTANTES\n");
            contenido.append("Proyecto: ").append(proyecto.getNombre()).append("\n");
            contenido.append("Fecha: ").append(java.time.LocalDateTime.now()).append("\n\n");
            for (String adv : advertencias) {
                contenido.append("- ").append(adv).append("\n");
            }
            java.nio.file.Files.write(salida.toPath(), contenido.toString().getBytes("UTF-8"));
        } catch (Exception e) {
            // No interrumpir la generación por fallas de escritura de warnings
        }
    }
    
    /**
     * Genera el documento Word y PDF
     */
    public boolean generar() {
        // Si el proyecto tiene configurados múltiples informes, usar el generador múltiple
        if (proyecto.getInformes() != null && !proyecto.getInformes().isEmpty()) {
            return generarMultiplesInformes();
        }
        
        // Caso normal: un solo informe (compatibilidad con versión anterior)
        long inicio = System.currentTimeMillis();
        XWPFDocument document = null;
        String rutaWord = null;
        
        try {
            // 1. Validar imágenes disponibles
            List<File> imagenesValidas = validarImagenes();
            if (imagenesValidas.isEmpty()) {
                proyecto.setEstado("FALLIDO");
                proyecto.setMensajeError("No se encontraron imágenes válidas para generar el documento");
                return false;
            }

            int esperadas = proyecto.getImagenesSeleccionadas() != null ? proyecto.getImagenesSeleccionadas().size() : 0;
            registrarAdvertenciaImagenes("principal", imagenesValidas.size(), esperadas);
            
            // 2. Crear ruta de salida con nombre único
            String timestamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "_" + 
                             java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HHmmss"));
            String nombreFinal = proyecto.getNombre() + "_" + timestamp;
            
            // Crear directorios para Word y PDF
            File dirSalidaWord = new File(proyecto.getRutaSalidaWord());
            if (!dirSalidaWord.exists()) {
                dirSalidaWord.mkdirs();
            }
            
            File dirSalidaPdf = new File(proyecto.getRutaSalidaPdf());
            if (!dirSalidaPdf.exists()) {
                dirSalidaPdf.mkdirs();
            }
            
            rutaWord = proyecto.getRutaSalidaWord() + File.separator + nombreFinal + ".docx";
            
            // 3. Copiar template universal a archivo final
            String rutaTemplate = RUTA_TEMPLATE_GLOBAL;
            if (rutaTemplate == null || rutaTemplate.isEmpty()) {
                // Fallback: usar template del proyecto si no hay ruta global configurada
                rutaTemplate = proyecto.getRutaTemplateWord();
            }
            Files.copy(Paths.get(rutaTemplate), Paths.get(rutaWord), StandardCopyOption.REPLACE_EXISTING);
            
            // 4. Abrir el documento copiado y procesarlo
            try (FileInputStream fis = new FileInputStream(rutaWord)) {
                document = new XWPFDocument(fis);
                
                // 5. Insertar imágenes en marcador único [IMAGENES]
                insertarImagenesEnMarcador(document, imagenesValidas);
                
                // 6. Reemplazar fecha y manual
                actualizarFecha(document);
                actualizarManual(document);
                actualizarManual2(document);
                actualizarManual3(document);
                // Reemplazo adicional en cuadros de texto (text boxes / mc:AlternateContent)
                reemplazarPlaceholdersEnCuadrosTexto(document, obtenerNombreManual());
                
                // 8. Guardar documento Word
                try (FileOutputStream fos = new FileOutputStream(rutaWord)) {
                    document.write(fos);
                }
                
                document.close();
            }
            
            // 9. Convertir a PDF
            String rutaPdf = convertirAPdf(rutaWord);
            
            // 10. Actualizar estado
            proyecto.setEstado("EXITOSO");
            proyecto.setDocumentoWordGenerado(rutaWord);
            proyecto.setDocumentoPdfGenerado(rutaPdf);
            long tiempoTotal = System.currentTimeMillis() - inicio;
            proyecto.setTiempoGeneracion(tiempoTotal);
            
            System.out.println("\n=== DEBUG GENERACIÓN ===");
            System.out.println("Ruta Word guardada: " + proyecto.getDocumentoWordGenerado());
            System.out.println("Ruta PDF guardada: " + proyecto.getDocumentoPdfGenerado());
            System.out.println("Estado: " + proyecto.getEstado());
            System.out.println("======================\n");
            
            generarResumen(imagenesValidas.size(), tiempoTotal);
            guardarAdvertenciasArchivo(proyecto.getRutaSalidaWord(), timestamp);
            
            return true;
            
        } catch (Exception e) {
            proyecto.setEstado("FALLIDO");
            proyecto.setMensajeError("Error inesperado: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Valida las imágenes según la secuencia de tiempo
     */
    private List<File> validarImagenes() {
        List<File> imagenesValidas = new ArrayList<>();
        List<String> alertas = new ArrayList<>();
        
        // DETECTAR PROYECTO MANUAL: Flag esProyectoManual indica que las imágenes se cargaron manualmente
        boolean esProyectoManual = proyecto.isEsProyectoManual();
        
        System.out.println("[DEBUG VALIDACION] Proyecto manual: " + esProyectoManual);
        System.out.println("[DEBUG VALIDACION] Ruta imagenes: " + proyecto.getRutaImagenes());
        System.out.println("[DEBUG VALIDACION] Patrones/Rutas guardadas: " + proyecto.getImagenesSeleccionadas());
        
        if (esProyectoManual) {
            // Para proyectos manuales, las imágenes ya están guardadas con rutas absolutas
            // Solo necesitamos validar que existan y convertirlas a File
            for (String rutaAbsoluta : proyecto.getImagenesSeleccionadas()) {
                File imagen = new File(rutaAbsoluta);
                if (imagen.exists() && imagen.isFile()) {
                    imagenesValidas.add(imagen);
                    System.out.println("[DEBUG VALIDACION MANUAL] Imagen válida: " + imagen.getName());
                } else {
                    alertas.add("⚠️ Imagen no encontrada: " + rutaAbsoluta);
                    System.out.println("[DEBUG VALIDACION MANUAL] Imagen NO encontrada: " + rutaAbsoluta);
                }
            }
            
            System.out.println("[DEBUG VALIDACION MANUAL] Total imágenes válidas: " + imagenesValidas.size());
        } else {
            // PROYECTO AUTOMATIZADO: Buscar por patrones
            // Si imagenesSeleccionadas contiene nombres completos con timestamp (ej: "0_Login_20260319_123456_1.png"),
            // extraer solo el prefijo/patrón para que la búsqueda funcione independientemente del timestamp.
            List<String> patronesNormalizados = new ArrayList<>();
            for (String patron : proyecto.getImagenesSeleccionadas()) {
                // Intentar extraer el patrón directamente del nombre guardado (que puede ser un nombre completo)
                String patronNormalizado = GestorImagenes.extraerPatron(patron);
                if (patronNormalizado != null) {
                    // Era un nombre completo: se extrajo el prefijo sin timestamp → funciona con cualquier ejecución futura
                    patronesNormalizados.add(patronNormalizado);
                    System.out.println("[DEBUG VALIDACION] Patron '" + patron + "' normalizado a: '" + patronNormalizado + "'");
                } else {
                    // Ya es un prefijo/patrón sin timestamp, usarlo tal cual
                    patronesNormalizados.add(patron);
                    System.out.println("[DEBUG VALIDACION] Patron '" + patron + "' usado como prefijo sin cambios");
                }
            }
            
            System.out.println("[DEBUG VALIDACION] Patrones NORMALIZADOS: " + patronesNormalizados);
            
            for (String patron : patronesNormalizados) {
                System.out.println("[DEBUG VALIDACION] Buscando patron: '" + patron + "'");
                
                List<File> imagenesPatron = GestorImagenes.obtenerImagenesPorPatron(
                    proyecto.getRutaImagenes(), patron);
                
                System.out.println("[DEBUG VALIDACION] Imagenes encontradas para '" + patron + "': " + imagenesPatron.size());
                if (!imagenesPatron.isEmpty()) {
                    System.out.println("[DEBUG VALIDACION] Primera imagen: " + imagenesPatron.get(0).getName());
                }
                
                if (imagenesPatron.isEmpty()) {
                    alertas.add("⚠️ No se encontraron imágenes para: " + patron);
                    continue;
                }
                
                // Validar rango de tiempo
                Map<String, Object> validacion = GestorImagenes.validarRangoTiempo(imagenesPatron);
                List<File> validas = (List<File>) validacion.get("imagenesValidas");
                List<String> alertasValidacion = (List<String>) validacion.get("alertas");
                
                alertas.addAll(alertasValidacion);
                
                if (validas.isEmpty()) {
                    alertas.add("⚠️ Imagen " + patron + " fuera de rango de tiempo permitido");
                } else {
                    imagenesValidas.add(validas.get(0)); // Tomar la más reciente
                }
            }
        }
        
        // Si hay alertas, agregarlas al proyecto
        if (!alertas.isEmpty()) {
            String mensajeAlertas = String.join("\n", alertas);
            proyecto.setMensajeError(mensajeAlertas);
        }
        
        // Validar cantidad
        if (imagenesValidas.size() != proyecto.getImagenesSeleccionadas().size()) {
            String alerta = "⚠️ Se esperaban " + proyecto.getImagenesSeleccionadas().size() + 
                          " imágenes pero solo se encontraron " + imagenesValidas.size();
            if (proyecto.getMensajeError() != null) {
                proyecto.setMensajeError(proyecto.getMensajeError() + "\n" + alerta);
            } else {
                proyecto.setMensajeError(alerta);
            }
        }
        
        return imagenesValidas;
    }
    
    /**
     * MÉTODO OBSOLETO - Eliminado, ya no se usa adaptación de placeholders
     */
    /**
     * Inserta todas las imágenes en el marcador único [IMAGENES]
     * Mantiene el formato y tamaño actual de las imágenes
     */
    private void insertarImagenesEnMarcador(XWPFDocument document, List<File> imagenes) throws Exception {
        System.out.println("[INFO] Insertar imágenes en marcador [IMAGENES]: " + imagenes.size() + " imágenes");
        
        // Buscar el marcador [IMAGENES] en párrafos
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            if (reemplazarMarcadorConImagenes(paragraph, imagenes)) {
                System.out.println("[INFO] Marcador encontrado y reemplazado en párrafo");
                return;
            }
        }
        
        // Buscar en tablas
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        if (reemplazarMarcadorConImagenes(paragraph, imagenes)) {
                            System.out.println("[INFO] Marcador encontrado y reemplazado en celda de tabla");
                            return;
                        }
                    }
                }
            }
        }
        
        System.out.println("[WARNING] No se encontró el marcador [IMAGENES] en el documento");
    }
    
    /**
     * Reemplaza el marcador [IMAGENES] con todas las imágenes en un párrafo
     */
    private boolean reemplazarMarcadorConImagenes(XWPFParagraph paragraph, List<File> imagenes) throws Exception {
        String texto = paragraph.getText();
        if (texto == null || !texto.contains(PATRON_IMAGENES)) {
            return false;
        }
        
        // Limpiar el párrafo actual
        List<XWPFRun> runs = paragraph.getRuns();
        for (int i = runs.size() - 1; i >= 0; i--) {
            paragraph.removeRun(i);
        }
        
        // Centrar el párrafo que contiene las imágenes
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        
        // Insertar cada imagen: 2 imágenes por página
        for (int i = 0; i < imagenes.size(); i++) {
            XWPFRun run = paragraph.createRun();
            
            double anchoPuntos = ANCHO_CM * 28.3464567;
            double altoPuntos = ALTO_CM * 28.3464567;
            int anchoEMU = Units.toEMU(anchoPuntos);
            int altoEMU = Units.toEMU(altoPuntos);
            
            try (FileInputStream fis = new FileInputStream(imagenes.get(i))) {
                run.addPicture(fis, XWPFDocument.PICTURE_TYPE_PNG,
                    imagenes.get(i).getName(), anchoEMU, altoEMU);
            }
            
            // Separación entre imágenes (excepto después de la última)
            if (i < imagenes.size() - 1) {
                if ((i + 1) % 2 == 0) {
                    // Después de la 2ª imagen de la página: salto de página
                    run.addBreak(org.apache.poi.xwpf.usermodel.BreakType.PAGE);
                } else {
                    // Entre la 1ª y 2ª imagen de la página: doble salto de línea (doble espaciado)
                    run.addBreak();
                    run.addBreak();
                }
            }
        }
        
        System.out.println("[INFO] Insertadas " + imagenes.size() + " imágenes en marcador (2 por página)");
        return true;
    }
    
    /**
     * MÉTODO OBSOLETO - Mantener para compatibilidad
     */
    private boolean insertarImagenes(List<File> imagenes) {
        try {
            File templateFile = new File(proyecto.getRutaTemplateWord());
            if (!templateFile.exists()) {
                return false;
            }
            try (FileInputStream fis = new FileInputStream(templateFile);
                 XWPFDocument document = new XWPFDocument(fis)) {
                for (int i = 0; i < imagenes.size(); i++) {
                    String placeholder = "[Imagen" + (i + 1) + "]";
                    if (!insertarImagenEnDocumento(document, placeholder, imagenes.get(i))) {
                        return false;
                    }
                }
                // Actualizar fecha
                actualizarFecha(document);
                // Guardar temporalmente
                try (FileOutputStream fos = new FileOutputStream(templateFile)) {
                    document.write(fos);
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Inserta una imagen en el documento reemplazando el placeholder
     */
    private boolean insertarImagenEnDocumento(XWPFDocument document, String placeholder, File imagen) {
        try {
            // Buscar en párrafos
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                if (reemplazarImagenEnParrafo(paragraph, placeholder, imagen)) {
                    return true;
                }
            }
            
            // Buscar en tablas
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
                            if (reemplazarImagenEnParrafo(paragraph, placeholder, imagen)) {
                                return true;
                            }
                        }
                    }
                }
            }
            
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Reemplaza un placeholder por una imagen en un párrafo
     */
    private boolean reemplazarImagenEnParrafo(XWPFParagraph paragraph, String placeholder, File imagen) {
        try {
            // Buscar placeholder incluso si está fragmentado en varios runs
            List<XWPFRun> runs = paragraph.getRuns();
            if (runs == null || runs.isEmpty()) return false;

            // Construir texto concatenado y mapa de offsets por run
            StringBuilder sb = new StringBuilder();
            List<Integer> runStart = new ArrayList<>();
            for (XWPFRun r : runs) {
                runStart.add(sb.length());
                String t = r.getText(0);
                if (t != null) sb.append(t);
            }

            String fullText = sb.toString();
            int idx = fullText.indexOf(placeholder);
            if (idx == -1) {
                return false;
            }

            // Determinar rango de runs que contienen el placeholder
            int firstRun = -1, lastRun = -1;
            for (int i = 0; i < runStart.size(); i++) {
                int start = runStart.get(i);
                int end = (i + 1 < runStart.size()) ? runStart.get(i + 1) : fullText.length();
                if (start <= idx && idx < end) {
                    firstRun = i;
                }
                if (start < idx + placeholder.length() && idx + placeholder.length() <= end) {
                    lastRun = i;
                }
            }
            // En casos donde placeholder abarca varios runs
            if (firstRun == -1) firstRun = 0;
            if (lastRun == -1) lastRun = runs.size() - 1;

            // Eliminar runs desde firstRun hasta lastRun (inclusive)
            for (int rem = lastRun; rem >= firstRun; rem--) {
                paragraph.removeRun(rem);
            }

            // Insertar nuevo run en la posición firstRun
            XWPFRun nuevoRun = paragraph.insertNewRun(firstRun);
            paragraph.setAlignment(ParagraphAlignment.CENTER);

            double anchoPuntos = ANCHO_CM * 28.3464567;
            double altoPuntos = ALTO_CM * 28.3464567;
            int anchoEMU = Units.toEMU(anchoPuntos);
            int altoEMU = Units.toEMU(altoPuntos);

            try (FileInputStream fis = new FileInputStream(imagen)) {
                nuevoRun.addPicture(fis, XWPFDocument.PICTURE_TYPE_PNG,
                    imagen.getName(), anchoEMU, altoEMU);
            }

            System.out.println("✓ Imagen insertada: " + imagen.getName() + " en párrafo (runs " + firstRun + "-" + lastRun + ")");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Actualiza la fecha en el documento (cuerpo, tablas, cuadros de texto,
     * encabezados y pies) conservando el formato definido en el esqueleto.
     */
    private void actualizarFecha(XWPFDocument document) {
        String fechaActual = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        // Buscar en todos los párrafos (cuerpo, tablas, cuadros de texto,
        // encabezados y pies), conservando el formato de cada run
        for (XWPFParagraph paragraph : obtenerTodosLosParrafos(document)) {
            actualizarFechaEnParrafo(paragraph, fechaActual);
        }

        // Refuerzo a nivel de nodos w:t: alcanza placeholders dentro de controles
        // de contenido (sdt) y cuadros de texto, conservando el formato del esqueleto
        Map<String, String> reemplazoFecha = new LinkedHashMap<>();
        reemplazoFecha.put(PATRON_FECHA, fechaActual);
        reemplazarTextoPorNombreElemento(document.getDocument().getBody(), reemplazoFecha);
        try {
            for (XWPFHeader header : document.getHeaderList()) {
                reemplazarTextoPorNombreElemento(header._getHdrFtr(), reemplazoFecha);
            }
        } catch (Exception e) {
            System.out.println("[WARNING] No se pudo actualizar fecha en encabezados: " + e.getMessage());
        }
        try {
            for (XWPFFooter footer : document.getFooterList()) {
                reemplazarTextoPorNombreElemento(footer._getHdrFtr(), reemplazoFecha);
            }
        } catch (Exception e) {
            System.out.println("[WARNING] No se pudo actualizar fecha en pies: " + e.getMessage());
        }
    }

    /**
     * Reemplaza un placeholder en una parte del documento (encabezado/pie)
     * recorriendo los nodos w:t y aplica el formato indicado al run que lo
     * contiene (funciona dentro de sdt y cuadros de texto).
     */
    private void reemplazarEnParteConFormato(XmlObject raiz, String placeholder, String replacement,
            String fontFamily, int fontSize, boolean bold, String color) {
        if (raiz == null) return;
        final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
        QName qT = new QName(W_NS, "t");
        XmlCursor cursor = raiz.newCursor();
        try {
            while (cursor.hasNextToken()) {
                XmlCursor.TokenType tok = cursor.toNextToken();
                if (tok.isStart() && qT.equals(cursor.getName())) {
                    String texto = cursor.getTextValue();
                    if (texto != null && texto.contains(placeholder)) {
                        cursor.setTextValue(texto.replace(placeholder, replacement));
                        org.w3c.dom.Node runNode = cursor.getDomNode().getParentNode();
                        if (runNode instanceof org.w3c.dom.Element) {
                            aplicarFormatoRunXml((org.w3c.dom.Element) runNode,
                                fontFamily, fontSize, bold, color);
                        }
                    }
                }
            }
        } finally {
            cursor.dispose();
        }
    }

    /**
     * Aplica formato (fuente, tamaño en puntos, negrita, color) al rPr de un run w:r.
     */
    private void aplicarFormatoRunXml(org.w3c.dom.Element runEl, String fontFamily,
            int fontSize, boolean bold, String color) {
        final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
        org.w3c.dom.Document doc = runEl.getOwnerDocument();
        org.w3c.dom.Element rPr = buscarHijoPorNombre(runEl, "rPr");
        if (rPr == null) {
            rPr = doc.createElementNS(W_NS, "w:rPr");
            runEl.insertBefore(rPr, runEl.getFirstChild());
        }
        asegurarHijoRPr(doc, rPr, W_NS, "rFonts",
            new String[][]{{"w:ascii", fontFamily}, {"w:hAnsi", fontFamily}, {"w:cs", fontFamily}});
        if (bold) {
            asegurarHijoRPr(doc, rPr, W_NS, "b", new String[][]{{"w:val", "1"}});
            asegurarHijoRPr(doc, rPr, W_NS, "bCs", new String[][]{{"w:val", "1"}});
        } else {
            asegurarHijoRPr(doc, rPr, W_NS, "b", new String[][]{{"w:val", "0"}});
            asegurarHijoRPr(doc, rPr, W_NS, "bCs", new String[][]{{"w:val", "0"}});
        }
        asegurarHijoRPr(doc, rPr, W_NS, "color", new String[][]{{"w:val", color}});
        String medias = String.valueOf(fontSize * 2);
        asegurarHijoRPr(doc, rPr, W_NS, "sz", new String[][]{{"w:val", medias}});
        asegurarHijoRPr(doc, rPr, W_NS, "szCs", new String[][]{{"w:val", medias}});
    }

    private org.w3c.dom.Element buscarHijoPorNombre(org.w3c.dom.Element padre, String localName) {
        org.w3c.dom.NodeList hijos = padre.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            org.w3c.dom.Node n = hijos.item(i);
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE && localName.equals(n.getLocalName())) {
                return (org.w3c.dom.Element) n;
            }
        }
        return null;
    }

    /**
     * Asegura que exista un hijo con el tag indicado dentro de rPr y le fija
     * los atributos dados (en namespace w).
     */
    private void asegurarHijoRPr(org.w3c.dom.Document doc, org.w3c.dom.Element rPr,
            String wNs, String localName, String[][] attrs) {
        org.w3c.dom.Element el = buscarHijoPorNombre(rPr, localName);
        if (el == null) {
            el = doc.createElementNS(wNs, "w:" + localName);
            rPr.appendChild(el);
        }
        if (attrs != null) {
            for (String[] a : attrs) {
                el.setAttributeNS(wNs, a[0], a[1]);
            }
        }
    }

    /**
     * Actualiza la fecha en un párrafo específico conservando el formato
     * original del run (p.ej. el definido en el cuadro de texto del esqueleto)
     */
    private void actualizarFechaEnParrafo(XWPFParagraph paragraph, String fechaActual) {
        reemplazarPlaceholderPreservandoFormato(paragraph, PATRON_FECHA, fechaActual);
    }
    
    /**
     * Extrae el nombre del manual sin el prefijo numérico
     * Ejemplo: "01- Soporte del sistema IRIS ONLINE" -> "Soporte del sistema IRIS ONLINE"
     */
    private String obtenerNombreManual() {
        String nombreCompleto = proyecto.getNombre();
        if (nombreCompleto == null || nombreCompleto.isEmpty()) {
            return "";
        }
        
        // Buscar el patrón de número al inicio: "XX- " o "XX-" donde XX son dígitos
        // Ejemplos: "01- ", "02-", "10- ", etc.
        String nombreSinNumero = nombreCompleto.replaceFirst("^\\d+\\s*-\\s*", "");
        
        return nombreSinNumero.trim();
    }
    
    /**
     * Actualiza el placeholder [MANUAL] en todo el documento
     * (incluye cuerpo, tablas, encabezados, pies y cuadros de texto)
     */
    private void actualizarManual(XWPFDocument document) {
        String nombreManual = obtenerNombreManual();
        for (XWPFParagraph paragraph : obtenerTodosLosParrafos(document)) {
            actualizarManualEnParrafo(paragraph, nombreManual);
        }
        // Refuerzo a nivel de nodos w:t: alcanza placeholders dentro de
        // controles de contenido (sdt) y cuadros de texto con el mismo formato
        reemplazarEnParteConFormato(document.getDocument().getBody(), PATRON_MANUAL, nombreManual,
            "Calibri", 24, false, "000000");
    }
    
    /**
     * Actualiza el placeholder [MANUAL] en un párrafo específico
     * Formato: Calibri, tamaño 24, negro
     */
    private void actualizarManualEnParrafo(XWPFParagraph paragraph, String nombreManual) {
        reemplazarPlaceholderConFormato(paragraph, PATRON_MANUAL, nombreManual,
            "Calibri", 24, false, false, "000000");
    }
    
    /**
     * Actualiza el placeholder [MANUAL2] en todo el documento conservando el
     * formato definido en el esqueleto (cuadro de texto).
     * (incluye cuerpo, tablas, encabezados, pies y cuadros de texto)
     */
    private void actualizarManual2(XWPFDocument document) {
        String nombreManual = obtenerNombreManual();
        for (XWPFParagraph paragraph : obtenerTodosLosParrafos(document)) {
            actualizarManual2EnParrafo(paragraph, nombreManual);
        }
        // Refuerzo a nivel de nodos w:t: alcanza placeholders dentro de controles
        // de contenido (sdt) y cuadros de texto, conservando el formato del esqueleto
        Map<String, String> reemplazoManual2 = new LinkedHashMap<>();
        reemplazoManual2.put(PATRON_MANUAL2, nombreManual);
        reemplazarTextoPorNombreElemento(document.getDocument().getBody(), reemplazoManual2);
        try {
            for (XWPFHeader header : document.getHeaderList()) {
                reemplazarTextoPorNombreElemento(header._getHdrFtr(), reemplazoManual2);
            }
            for (XWPFFooter footer : document.getFooterList()) {
                reemplazarTextoPorNombreElemento(footer._getHdrFtr(), reemplazoManual2);
            }
        } catch (Exception ignore) {}
    }
    
    /**
     * Actualiza el placeholder [MANUAL2] en un párrafo específico conservando
     * el formato original del run (p.ej. el definido en el cuadro de texto)
     */
    private void actualizarManual2EnParrafo(XWPFParagraph paragraph, String nombreManual) {
        reemplazarPlaceholderPreservandoFormato(paragraph, PATRON_MANUAL2, nombreManual);
    }
    
    /**
     * Actualiza el placeholder [MANUAL3] en todo el documento
     * Formato: Calibri, tamaño 11, negro, negrita
     * (incluye cuerpo, tablas, encabezados, pies y cuadros de texto)
     */
    private void actualizarManual3(XWPFDocument document) {
        String nombreManual = obtenerNombreManual();
        for (XWPFParagraph paragraph : obtenerTodosLosParrafos(document)) {
            actualizarManual3EnParrafo(paragraph, nombreManual);
        }
        // Refuerzo a nivel de nodos w:t (sdt / cuadros de texto)
        reemplazarEnParteConFormato(document.getDocument().getBody(), PATRON_MANUAL3, nombreManual,
            "Consolas", 9, true, "000000");
    }
    
    /**
     * Recolecta TODOS los párrafos del documento recorriendo el XML con XmlCursor.
     * Captura párrafos del cuerpo, tablas, cuadros de texto (text boxes),
     * encabezados y pies de página, sin depender del motor XPath.
     */
    private List<XWPFParagraph> obtenerTodosLosParrafos(XWPFDocument document) {
        List<XWPFParagraph> resultado = new ArrayList<>();
        
        // 1. Cuerpo del documento (incluye tablas y cuadros de texto anidados)
        recolectarParrafosDeParte(document.getDocument().getBody(), document, resultado);
        
        // 2. Encabezados
        try {
            for (XWPFHeader header : document.getHeaderList()) {
                recolectarParrafosDeParte(header._getHdrFtr(), header, resultado);
            }
        } catch (Exception e) {
            System.out.println("[WARNING] No se pudieron leer encabezados: " + e.getMessage());
        }
        
        // 3. Pies de página
        try {
            for (XWPFFooter footer : document.getFooterList()) {
                recolectarParrafosDeParte(footer._getHdrFtr(), footer, resultado);
            }
        } catch (Exception e) {
            System.out.println("[WARNING] No se pudieron leer pies de página: " + e.getMessage());
        }
        
        return resultado;
    }
    
    /**
     * Recorre con XmlCursor todos los tokens de una parte del documento y envuelve
     * cada elemento CTP (párrafo) encontrado, sin importar su anidamiento
     * (tablas, cuadros de texto, etc.).
     */
    private void recolectarParrafosDeParte(XmlObject raiz, org.apache.poi.xwpf.usermodel.IBody parte,
            List<XWPFParagraph> resultado) {
        if (raiz == null) return;
        XmlCursor cursor = raiz.newCursor();
        try {
            while (cursor.hasNextToken()) {
                XmlCursor.TokenType tok = cursor.toNextToken();
                if (tok.isStart()) {
                    XmlObject obj = cursor.getObject();
                    if (obj instanceof CTP) {
                        try {
                            resultado.add(new XWPFParagraph((CTP) obj, parte));
                        } catch (Exception ignore) {
                            // Ignorar párrafos que no se puedan envolver
                        }
                    }
                }
            }
        } finally {
            cursor.dispose();
        }
    }
    
    /**
     * Actualiza el placeholder [MANUAL3] en un párrafo específico
     * Formato: Calibri, tamaño 11, negro, negrita
     */
    private void actualizarManual3EnParrafo(XWPFParagraph paragraph, String nombreManual) {
        reemplazarPlaceholderConFormato(paragraph, PATRON_MANUAL3, nombreManual,
            "Consolas", 9, true, false, "000000");
    }
    
    /**
     * Reemplaza un placeholder de texto que puede estar fragmentado en múltiples runs.
     * Reconstruye el texto completo del párrafo, localiza el placeholder, lo reemplaza
     * y aplica el formato especificado. Preserva la estructura de runs para no alterar
     * el layout del párrafo (espaciado, márgenes, etc.).
     */
    private void reemplazarPlaceholderConFormato(XWPFParagraph paragraph, String placeholder,
            String replacement, String fontFamily, int fontSize, boolean bold, boolean italic, String color) {
        reemplazarPlaceholderEnParrafo(paragraph, placeholder, replacement,
            fontFamily, fontSize, bold, italic, color, true);
    }

    /**
     * Reemplaza un placeholder conservando el formato original de los runs
     * (no modifica fuente, tamaño, negrita ni color). Se usa para placeholders
     * dentro de cuadros de texto del esqueleto, cuyo formato debe respetarse.
     */
    private void reemplazarPlaceholderPreservandoFormato(XWPFParagraph paragraph, String placeholder,
            String replacement) {
        reemplazarPlaceholderEnParrafo(paragraph, placeholder, replacement,
            null, 0, false, false, null, false);
    }

    /**
     * Núcleo del reemplazo de placeholders a nivel de párrafo. Si
     * aplicarFormato es true, aplica el formato indicado a los runs
     * modificados; si es false, conserva el formato original de cada run.
     */
    private void reemplazarPlaceholderEnParrafo(XWPFParagraph paragraph, String placeholder,
            String replacement, String fontFamily, int fontSize, boolean bold, boolean italic,
            String color, boolean aplicarFormato) {
        // Repetir mientras el placeholder exista en el párrafo (múltiples ocurrencias)
        int guardia = 0;
        while (guardia++ < 100) {
            List<XWPFRun> runs = paragraph.getRuns();
            if (runs == null || runs.isEmpty()) return;

            // Reconstruir texto completo y mapa de offsets por run
            StringBuilder sb = new StringBuilder();
            List<Integer> runStart = new ArrayList<>();
            for (XWPFRun r : runs) {
                runStart.add(sb.length());
                String t = r.getText(0);
                if (t != null) sb.append(t);
            }
            String fullText = sb.toString();
            int idx = fullText.indexOf(placeholder);
            if (idx == -1) return; // no hay (más) ocurrencias

            int endIdx = idx + placeholder.length();

            // Determinar primer y último run que abarca el placeholder
            int firstRun = -1, lastRun = -1;
            for (int i = 0; i < runStart.size(); i++) {
                int start = runStart.get(i);
                int end = (i + 1 < runStart.size()) ? runStart.get(i + 1) : fullText.length();
                if (start <= idx && idx < end) firstRun = i;
                if (start < endIdx && endIdx <= end) lastRun = i;
            }
            if (firstRun == -1) firstRun = 0;
            if (lastRun == -1) lastRun = runs.size() - 1;

            // Extraer prefijo (texto antes del placeholder en el primer run)
            int firstStart = runStart.get(firstRun);
            String firstText = runs.get(firstRun).getText(0);
            if (firstText == null) firstText = "";
            int prefLen = Math.max(0, Math.min(idx - firstStart, firstText.length()));
            String prefix = firstText.substring(0, prefLen);

            // Extraer sufijo (texto después del placeholder en el último run)
            int lastStart = runStart.get(lastRun);
            String lastText = runs.get(lastRun).getText(0);
            if (lastText == null) lastText = "";
            int sufStart = endIdx - lastStart;
            String suffix = (sufStart >= 0 && sufStart <= lastText.length()) ? lastText.substring(sufStart) : "";

            // Estrategia: MODIFICAR runs en lugar de eliminarlos para preservar estructura
            // Si el placeholder está en un solo run
            if (firstRun == lastRun) {
                XWPFRun run = runs.get(firstRun);
                run.setText(prefix + replacement + suffix, 0);
                if (aplicarFormato) {
                    aplicarFormatoARun(run, fontFamily, fontSize, bold, italic, color);
                }
            } else {
                // Placeholder fragmentado en múltiples runs
                // Primer run: prefijo + reemplazo
                XWPFRun first = runs.get(firstRun);
                first.setText(prefix + replacement, 0);
                if (aplicarFormato) {
                    aplicarFormatoARun(first, fontFamily, fontSize, bold, italic, color);
                }

                // Runs intermedios: vaciar (no eliminar para preservar estructura)
                for (int i = firstRun + 1; i < lastRun; i++) {
                    runs.get(i).setText("", 0);
                }

                // Último run: solo sufijo
                XWPFRun last = runs.get(lastRun);
                last.setText(suffix, 0);
            }
        }
    }
    
    /**
     * Aplica formato a un run solo si los parámetros no son nulos/cero
     */
    private void aplicarFormatoARun(XWPFRun run, String fontFamily, int fontSize, 
            boolean bold, boolean italic, String color) {
        if (fontFamily != null) run.setFontFamily(fontFamily);
        if (fontSize > 0) run.setFontSize(fontSize);
        run.setBold(bold);
        run.setItalic(italic);
        if (color != null) run.setColor(color);
    }
    
    /**
     * Reemplaza los placeholders [MANUAL], [MANUAL2] y [MANUAL3] dentro de cuadros
     * de texto (text boxes) y cualquier otro contenido que XMLBeans carga sin tipar
     * (p.ej. dentro de mc:AlternateContent). Opera a nivel de nodos <w:t> usando el
     * NOMBRE del elemento (no el tipo), por lo que funciona sin importar el anidamiento.
     * Respeta el formato definido en el template para ese cuadro de texto.
     */
    private void reemplazarPlaceholdersEnCuadrosTexto(XWPFDocument document, String nombreManual) {
        Map<String, String> reemplazos = new LinkedHashMap<>();
        reemplazos.put(PATRON_MANUAL2, nombreManual);
        reemplazos.put(PATRON_MANUAL3, nombreManual);
        reemplazos.put(PATRON_MANUAL, nombreManual);
        
        // Cuerpo
        reemplazarTextoPorNombreElemento(document.getDocument().getBody(), reemplazos);
        
        // Encabezados
        try {
            for (XWPFHeader header : document.getHeaderList()) {
                reemplazarTextoPorNombreElemento(header._getHdrFtr(), reemplazos);
            }
        } catch (Exception ignore) {}
        
        // Pies de página
        try {
            for (XWPFFooter footer : document.getFooterList()) {
                reemplazarTextoPorNombreElemento(footer._getHdrFtr(), reemplazos);
            }
        } catch (Exception ignore) {}
    }
    
    /**
     * Recorre con XmlCursor todos los nodos <w:t> de una parte del documento
     * (identificados por su QName) y aplica los reemplazos de texto indicados.
     * No depende del tipado de XMLBeans, por lo que alcanza texto dentro de
     * cuadros de texto y mc:AlternateContent.
     */
    private void reemplazarTextoPorNombreElemento(XmlObject raiz, Map<String, String> reemplazos) {
        if (raiz == null) return;
        final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
        QName qT = new QName(W_NS, "t");
        
        XmlCursor cursor = raiz.newCursor();
        try {
            while (cursor.hasNextToken()) {
                XmlCursor.TokenType tok = cursor.toNextToken();
                if (tok.isStart() && qT.equals(cursor.getName())) {
                    String texto = cursor.getTextValue();
                    if (texto != null && !texto.isEmpty()) {
                        String nuevo = texto;
                        for (Map.Entry<String, String> e : reemplazos.entrySet()) {
                            if (nuevo.contains(e.getKey())) {
                                nuevo = nuevo.replace(e.getKey(), e.getValue());
                            }
                        }
                        if (!nuevo.equals(texto)) {
                            cursor.setTextValue(nuevo);
                        }
                    }
                }
            }
        } finally {
            cursor.dispose();
        }
        reemplazarFragmentadosPorParrafoXml(raiz, reemplazos);
    }

    /**
     * Reemplaza placeholders partidos en varios runs (p.ej. "[" + "MANUAL2" + "]")
     * dentro de párrafos w:p ubicados en cuadros de texto. Junta el texto de los
     * w:t de los runs del párrafo, y escribe el reemplazo en el primer w:t afectado
     * (conservando su formato), vaciando los demás fragmentos del placeholder.
     */
    private void reemplazarFragmentadosPorParrafoXml(XmlObject raiz, Map<String, String> reemplazos) {
        if (raiz == null) return;
        final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
        QName qP = new QName(W_NS, "p");
        QName qR = new QName(W_NS, "r");
        QName qT = new QName(W_NS, "t");

        List<List<XmlObject>> parrafos = new ArrayList<>();
        XmlCursor cursor = raiz.newCursor();
        try {
            while (cursor.hasNextToken()) {
                XmlCursor.TokenType tok = cursor.toNextToken();
                if (tok.isStart() && qP.equals(cursor.getName())) {
                    List<XmlObject> nodosT = new ArrayList<>();
                    XmlCursor c = cursor.newCursor();
                    try {
                        if (c.toFirstChild()) {
                            do {
                                if (qR.equals(c.getName())) {
                                    XmlCursor c2 = c.newCursor();
                                    try {
                                        if (c2.toFirstChild()) {
                                            do {
                                                if (qT.equals(c2.getName())) nodosT.add(c2.getObject());
                                            } while (c2.toNextSibling());
                                        }
                                    } finally {
                                        c2.dispose();
                                    }
                                }
                            } while (c.toNextSibling());
                        }
                    } finally {
                        c.dispose();
                    }
                    if (nodosT.size() > 1) parrafos.add(nodosT);
                }
            }
        } finally {
            cursor.dispose();
        }

        for (List<XmlObject> nodosT : parrafos) {
            for (Map.Entry<String, String> e : reemplazos.entrySet()) {
                for (int guardia = 0; guardia < 50; guardia++) {
                    List<String> textos = new ArrayList<>();
                    StringBuilder sb = new StringBuilder();
                    for (XmlObject o : nodosT) {
                        XmlCursor tc = o.newCursor();
                        try { textos.add(tc.getTextValue()); } finally { tc.dispose(); }
                        sb.append(textos.get(textos.size() - 1));
                    }
                    int idx = sb.toString().indexOf(e.getKey());
                    if (idx == -1) break;
                    int fin = idx + e.getKey().length();
                    // El valor se escribe en el fragmento con mayor parte del placeholder
                    // (p.ej. "Fecha" en "[" + "Fecha" + "]"), que es el que lleva el formato del esqueleto
                    int destino = -1, mejorSolape = -1, pos = 0;
                    for (int i = 0; i < nodosT.size(); i++) {
                        int ini = pos, finT = pos + textos.get(i).length();
                        pos = finT;
                        int solape = Math.min(finT, fin) - Math.max(ini, idx);
                        if (solape > mejorSolape) { mejorSolape = solape; destino = i; }
                    }
                    pos = 0;
                    for (int i = 0; i < nodosT.size(); i++) {
                        String t = textos.get(i);
                        int ini = pos, finT = pos + t.length();
                        pos = finT;
                        if (finT <= idx || ini >= fin) continue;
                        String antes = ini < idx ? t.substring(0, idx - ini) : "";
                        String despues = finT > fin ? t.substring(fin - ini) : "";
                        String nuevo = (i == destino) ? antes + e.getValue() + despues : antes + despues;
                        XmlCursor tc = nodosT.get(i).newCursor();
                        try { tc.setTextValue(nuevo); } finally { tc.dispose(); }
                    }
                }
            }
        }
    }
    
    /**
     * Genera los archivos finales (Word y PDF)
     */
    /**
     * MÉTODO OBSOLETO - Ya no se usa
     */
    private String generarArchivosFinales() {
        return null;
    }
    
    /**
     * Convierte Word a PDF usando PowerShell y guarda en carpeta específica
     */
    private String convertirAPdf(String rutaWord) {
        try {
            // Obtener nombre del archivo sin path
            String nombreArchivo = new File(rutaWord).getName().replace(".docx", ".pdf");
            
            // Ruta completa del PDF en la carpeta específica
            String rutaPdf = proyecto.getRutaSalidaPdf() + File.separator + nombreArchivo;
            File archivoPdf = new File(rutaPdf);
            
            String rutaWordAbsoluta = new File(rutaWord).getAbsolutePath();
            String rutaPdfAbsoluta = archivoPdf.getAbsolutePath();
            
            System.out.println("\n[DEBUG] Conversión a PDF:");
            System.out.println("[DEBUG] Word origen: " + rutaWordAbsoluta);
            System.out.println("[DEBUG] PDF destino: " + rutaPdfAbsoluta);
            
            // Script PowerShell simplificado - compatible con todas las versiones de Word
            // Parámetros ExportAsFixedFormat:
            // - OutputFileName: ruta del PDF
            // - ExportFormat: 17 = wdExportFormatPDF
            // - OpenAfterExport: false
            // - OptimizeFor: 0 = wdExportOptimizeForPrint (MÁXIMA CALIDAD)
            String script = String.format(
                "$ErrorActionPreference = 'Stop'; " +
                "try { " +
                "  $word = New-Object -ComObject Word.Application; " +
                "  $word.Visible = $false; " +
                "  $doc = $word.Documents.Open('%s'); " +
                "  $doc.ExportAsFixedFormat('%s', 17, $false, 0); " +
                "  $doc.Close($false); " +
                "  $word.Quit(); " +
                "  [System.Runtime.Interopservices.Marshal]::ReleaseComObject($doc) | Out-Null; " +
                "  [System.Runtime.Interopservices.Marshal]::ReleaseComObject($word) | Out-Null; " +
                "  exit 0; " +
                "} catch { " +
                "  if ($word) { try { $word.Quit() } catch {} }; " +
                "  Write-Error $_.Exception.Message; " +
                "  exit 1; " +
                "}",
                rutaWordAbsoluta.replace("\\", "\\\\"),
                rutaPdfAbsoluta.replace("\\", "\\\\")
            );
            
            String powershellExe = "powershell.exe";
            String powershellAbsoluto = System.getenv("SystemRoot") + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";
            if (new File(powershellAbsoluto).exists()) {
                powershellExe = powershellAbsoluto;
            }
            ProcessBuilder pb = new ProcessBuilder(powershellExe, "-Command", script);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            // Registrar proceso para seguimiento global
            com.orquestador.servicio.ProcessRegistry.getInstance().register(process);

            try {
                // Leer salida en background para evitar bloqueos por buffers llenos
                StringBuilder output = new StringBuilder();
                Thread outReader = new Thread(() -> {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            output.append(line).append(System.lineSeparator());
                        }
                    } catch (IOException ignored) {}
                });
                outReader.setDaemon(true);
                outReader.start();

                // Esperar con timeout razonable para evitar bloqueo indefinido
                boolean finished = process.waitFor(60, TimeUnit.SECONDS);
                if (!finished) {
                    // Timeout: forzar cierre y reportar
                    process.destroyForcibly();
                    proyecto.setMensajeError("Timeout al convertir a PDF (Word COM no respondió)");
                    System.out.println("[DEBUG] Conversión a PDF: TIMEOUT\n" + output.toString());
                    return null;
                }

                int exitCode = process.exitValue();

                // Asegurar que el lector de salida termine
                try { outReader.join(2000); } catch (InterruptedException ignored) {}

                if (exitCode == 0 && archivoPdf.exists()) {
                    return rutaPdf;
                }

                System.out.println("[DEBUG] Conversión a PDF finalizada con exitCode=" + exitCode + "\nSalida:\n" + output.toString());
                return null;
            } finally {
                // Asegurar desregistro del proceso
                com.orquestador.servicio.ProcessRegistry.getInstance().unregister(process);
                try { process.destroyForcibly(); } catch (Exception ex) { }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Cuenta la cantidad de placeholders [Imagen1], [Imagen2], etc. en el documento
     */
    private int contarPlaceholdersEnDocumento(XWPFDocument document) {
        int contador = 0;
        
        // Buscar en párrafos
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String texto = paragraph.getText();
            if (texto != null) {
                // Buscar [Imagen1], [Imagen2], ..., [Imagen99]
                for (int i = 1; i <= 99; i++) {
                    if (texto.contains("[Imagen" + i + "]")) {
                        contador = Math.max(contador, i);
                    }
                }
            }
        }
        
        // Buscar en tablas también
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        String texto = paragraph.getText();
                        if (texto != null) {
                            for (int i = 1; i <= 99; i++) {
                                if (texto.contains("[Imagen" + i + "]")) {
                                    contador = Math.max(contador, i);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return contador;
    }
    
    /**
     * Genera un resumen de la generación
     */
    private void generarResumen(int cantidadImagenes, long tiempoMs) {
        resumenGeneracion.setLength(0);
        resumenGeneracion.append("========================================\n");
        resumenGeneracion.append("RESUMEN DE GENERACIÓN\n");
        resumenGeneracion.append("========================================\n\n");
        resumenGeneracion.append("📁 Proyecto: ").append(proyecto.getNombre()).append("\n");
        resumenGeneracion.append("✅ Estado: ").append(proyecto.getEstado()).append("\n");
        resumenGeneracion.append("📷 Imágenes: ").append(cantidadImagenes).append(" insertadas\n");
        resumenGeneracion.append("📄 Word: ").append(proyecto.getDocumentoWordGenerado()).append("\n");
        if (proyecto.getDocumentoPdfGenerado() != null) {
            resumenGeneracion.append("📑 PDF: ").append(proyecto.getDocumentoPdfGenerado()).append("\n");
        }
        if (!advertencias.isEmpty()) {
            resumenGeneracion.append("⚠ Warnings:\n");
            for (String adv : advertencias) {
                resumenGeneracion.append("  - ").append(adv).append("\n");
            }
        }
        resumenGeneracion.append("⏱️  Tiempo: ").append(tiempoMs / 1000.0).append(" segundos\n");
        resumenGeneracion.append("========================================\n");
    }
    
    /**
     * Obtiene el resumen de la generación
     */
    public String obtenerResumen() {
        return resumenGeneracion.toString();
    }
    
    /**
     * Genera múltiples informes desde una sola ejecución
     * Cada informe tiene su template y filtra las imágenes según su patrón
     */
    private boolean generarMultiplesInformes() {
        long inicio = System.currentTimeMillis();
        String timestampMulti = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "_" + 
                             java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HHmmss"));
        int informesGenerados = 0;
        List<String> documentosWordGenerados = new ArrayList<>();
        List<String> documentosPdfGenerados = new ArrayList<>();
        
        try {
            proyecto.setMensajeError("MULTI: Iniciando generación múltiple con " + proyecto.getInformes().size() + " informes adicionales");
            
            // PRIMERO: Generar el informe PRINCIPAL (configuración base del proyecto)
            
            if (proyecto.getRutaTemplateWord() != null && !proyecto.getRutaTemplateWord().isEmpty()) {
                // Usar el método de validación existente para obtener imágenes del proyecto principal
                List<File> imagenesInformePrincipal = validarImagenes();
                
                if (!imagenesInformePrincipal.isEmpty()) {
                    // Crear configuración temporal para el informe principal usando los datos base del proyecto
                    com.orquestador.modelo.ConfiguracionInforme informePrincipal = new com.orquestador.modelo.ConfiguracionInforme();
                    informePrincipal.setTemplateWord(proyecto.getRutaTemplateWord());
                    // NO establecer nombreArchivo para el informe principal - usar nombre del proyecto + template
                    // (nombreArchivo solo se usa cuando está explícitamente configurado en informes adicionales)
                    // Convertir las rutas de los File a Strings para la configuración
                    List<String> rutasImagenes = new ArrayList<>();
                    for (File img : imagenesInformePrincipal) {
                        rutasImagenes.add(img.getAbsolutePath());
                    }
                    informePrincipal.setImagenesSeleccionadas(rutasImagenes);

                    int esperadasPrincipal = proyecto.getImagenesSeleccionadas() != null ? proyecto.getImagenesSeleccionadas().size() : 0;
                    registrarAdvertenciaImagenes("principal", imagenesInformePrincipal.size(), esperadasPrincipal);
                    
                    // Generar informe principal (número 1)
                    boolean exitosoPrincipal = generarInformeIndividual(informePrincipal, imagenesInformePrincipal, 1, documentosWordGenerados, documentosPdfGenerados);
                    if (exitosoPrincipal) {
                        informesGenerados++;
                    }
                }
            }
            
            // SEGUNDO: Iterar sobre cada informe ADICIONAL
            for (int i = 0; i < proyecto.getInformes().size(); i++) {
                com.orquestador.modelo.ConfiguracionInforme informe = proyecto.getInformes().get(i);
                
                System.out.println("=== LOOP INFORME #" + (i+2) + " ===");
                System.out.println("Template: " + (informe.getTemplateWord() != null ? informe.getTemplateWord() : "NULL"));
                System.out.println("Patrón: " + (informe.getPatronImagenes() != null ? informe.getPatronImagenes() : "NULL"));
                System.out.println("Imgs selec: " + (informe.getImagenesSeleccionadas() != null ? informe.getImagenesSeleccionadas().size() : "NULL"));
                
                // Validar configuración del informe
                if (informe.getTemplateWord() == null || informe.getTemplateWord().trim().isEmpty()) {
                    System.out.println("SKIP: Template vacío");
                    continue;
                }
                
                // Obtener imágenes del informe (usar lista seleccionada si existe, sino buscar por patrón)
                List<File> imagenesInforme;
                if (informe.getImagenesSeleccionadas() != null && !informe.getImagenesSeleccionadas().isEmpty()) {
                    // Usar imágenes seleccionadas manualmente
                    imagenesInforme = new ArrayList<>();
                    for (String rutaImagen : informe.getImagenesSeleccionadas()) {
                        File f = new File(rutaImagen);
                        if (f.exists()) {
                            imagenesInforme.add(f);
                        }
                    }
                    // Si no encontró ninguna imagen seleccionada, buscar por patrón como fallback
                    if (imagenesInforme.isEmpty() && informe.getPatronImagenes() != null && !informe.getPatronImagenes().trim().isEmpty()) {
                        imagenesInforme = buscarImagenesPorPatron(informe.getPatronImagenes());
                    }
                } else if (informe.getPatronImagenes() != null && !informe.getPatronImagenes().trim().isEmpty()) {
                    // Buscar imágenes por patrón
                    imagenesInforme = buscarImagenesPorPatron(informe.getPatronImagenes());
                    System.out.println("Buscar por patrón '" + informe.getPatronImagenes() + "' encontró: " + imagenesInforme.size() + " imágenes");
                } else {
                    System.out.println("SKIP: Informe sin imágenes ni patrón");
                    continue;
                }
                
                if (imagenesInforme.isEmpty()) {
                    System.out.println("SKIP: No se encontraron imágenes para patrón '" + informe.getPatronImagenes() + "'");
                    System.out.println("Carpeta buscada: " + proyecto.getRutaImagenes());
                    continue;
                }

                if (informe.getImagenesSeleccionadas() != null && !informe.getImagenesSeleccionadas().isEmpty()) {
                    String etiqueta = informe.getNombreArchivo();
                    if (etiqueta == null || etiqueta.trim().isEmpty()) {
                        etiqueta = new File(informe.getTemplateWord()).getName();
                    }
                    registrarAdvertenciaImagenes(etiqueta, imagenesInforme.size(), informe.getImagenesSeleccionadas().size());
                }
                
                // Generar el informe individual
                System.out.println("Llamando a generarInformeIndividual con " + imagenesInforme.size() + " imágenes...");
                boolean exitoso = generarInformeIndividual(informe, imagenesInforme, i + 2, documentosWordGenerados, documentosPdfGenerados);
                System.out.println("Resultado generarInformeIndividual: " + (exitoso ? "EXITOSO" : "FALLÓ"));
                
                if (exitoso) {
                    informesGenerados++;
                }
            }
            
            // Actualizar estado del proyecto
            if (informesGenerados > 0) {
                proyecto.setEstado("EXITOSO");
                
                // Guardar la lista de documentos generados (concatenados)
                if (!documentosWordGenerados.isEmpty()) {
                    proyecto.setDocumentoWordGenerado(String.join("; ", documentosWordGenerados));
                }
                if (!documentosPdfGenerados.isEmpty()) {
                    proyecto.setDocumentoPdfGenerado(String.join("; ", documentosPdfGenerados));
                }
                
                long tiempoTotal = System.currentTimeMillis() - inicio;
                proyecto.setTiempoGeneracion(tiempoTotal);

                generarResumen(informesGenerados, tiempoTotal);
                guardarAdvertenciasArchivo(proyecto.getRutaSalidaWord(), timestampMulti);
                
                return true;
            } else {
                proyecto.setEstado("FALLIDO");
                proyecto.setMensajeError("No se pudo generar ningún informe. Verifica las configuraciones.");
                return false;
            }
            
        } catch (Exception e) {
            proyecto.setEstado("FALLIDO");
            proyecto.setMensajeError("Error en generación múltiple: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Busca imágenes en la carpeta configurada que coincidan con el patrón dado
     */
    private List<File> buscarImagenesPorPatron(String patron) {
        List<File> imagenesEncontradas = new ArrayList<>();
        
        try {
            File dirImagenes = new File(proyecto.getRutaImagenes());
            if (!dirImagenes.exists() || !dirImagenes.isDirectory()) {
                System.out.println("[DEBUG BUSQUEDA] Directorio no existe: " + proyecto.getRutaImagenes());
                return imagenesEncontradas;
            }
            
            File[] archivos = dirImagenes.listFiles();
            if (archivos == null) {
                return imagenesEncontradas;
            }
            
            // Filtrar archivos por patrón
            for (File archivo : archivos) {
                String nombre = archivo.getName();
                
                // Verificar si el nombre comienza con el patrón
                if (nombre.startsWith(patron)) {
                    imagenesEncontradas.add(archivo);
                    System.out.println("[DEBUG BUSQUEDA] Match: " + nombre);
                }
            }
            
            // Ordenar por nombre (timestamp)
            imagenesEncontradas.sort(Comparator.comparing(File::getName));
            
        } catch (Exception e) {
            System.out.println("[DEBUG BUSQUEDA] Error: " + e.getMessage());
        }
        
        return imagenesEncontradas;
    }
    
    /**
     * Genera un informe individual con su template y sus imágenes
     */
    private boolean generarInformeIndividual(com.orquestador.modelo.ConfiguracionInforme informe, 
                                            List<File> imagenes, 
                                            int numeroInforme,
                                            List<String> documentosWordGenerados,
                                            List<String> documentosPdfGenerados) {
        XWPFDocument document = null;
        String rutaWord = null;
        
        try {
            // Crear nombre único para el archivo
            String timestamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "_" + 
                             java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HHmmss"));
            
            // Usar nombreArchivo si existe, sino usar solo el nombre del proyecto
            String nombreFinal;
            if (informe.getNombreArchivo() != null && !informe.getNombreArchivo().trim().isEmpty()) {
                nombreFinal = informe.getNombreArchivo().trim() + "_" + timestamp;
            } else {
                // Usar solo el nombre del proyecto (sin el nombre del template)
                nombreFinal = proyecto.getNombre() + "_" + timestamp;
            }
            
            // Crear directorios
            File dirSalidaWord = new File(proyecto.getRutaSalidaWord());
            if (!dirSalidaWord.exists()) {
                dirSalidaWord.mkdirs();
            }
            
            File dirSalidaPdf = new File(proyecto.getRutaSalidaPdf());
            if (!dirSalidaPdf.exists()) {
                dirSalidaPdf.mkdirs();
            }
            
            rutaWord = proyecto.getRutaSalidaWord() + File.separator + nombreFinal + ".docx";
            
            // Copiar template universal
            String rutaTemplate = RUTA_TEMPLATE_GLOBAL;
            if (rutaTemplate == null || rutaTemplate.isEmpty()) {
                // Fallback: usar template del informe si no hay ruta global configurada
                rutaTemplate = informe.getTemplateWord();
            }
            Files.copy(Paths.get(rutaTemplate), Paths.get(rutaWord), StandardCopyOption.REPLACE_EXISTING);
            
            // Abrir y procesar el documento
            try (FileInputStream fis = new FileInputStream(rutaWord)) {
                document = new XWPFDocument(fis);
                
                // Insertar imágenes en marcador único [IMAGENES]
                insertarImagenesEnMarcador(document, imagenes);
                
                // Actualizar fecha y manual
                actualizarFecha(document);
                actualizarManual(document);
                actualizarManual2(document);
                actualizarManual3(document);
                // Reemplazo adicional en cuadros de texto (text boxes / mc:AlternateContent)
                reemplazarPlaceholdersEnCuadrosTexto(document, obtenerNombreManual());
                
                // Guardar Word
                try (FileOutputStream fos = new FileOutputStream(rutaWord)) {
                    document.write(fos);
                }
                
                document.close();
            }
            
            // Convertir a PDF
            String rutaPdf = convertirAPdf(rutaWord);
            
            // Registrar documentos generados
            documentosWordGenerados.add(rutaWord);
            if (rutaPdf != null) {
                documentosPdfGenerados.add(rutaPdf);
            }
            
            System.out.println("[DEBUG INDIVIDUAL] Informe #" + numeroInforme + " generado exitosamente");
            System.out.println("[DEBUG INDIVIDUAL] Word: " + rutaWord);
            System.out.println("[DEBUG INDIVIDUAL] PDF: " + rutaPdf);
            
            return true;
            
        } catch (Exception e) {
            System.out.println("[DEBUG INDIVIDUAL] Error en informe #" + numeroInforme + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}

