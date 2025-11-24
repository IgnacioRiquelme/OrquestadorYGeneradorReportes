package com.orquestador.servicio;

import com.orquestador.modelo.Proyecto;
import com.orquestador.utilidades.GestorImagenes;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;
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
    private static final String PATRON_FECHA = "[Fecha]"; // Placeholder para la fecha actual
    
    private final Proyecto proyecto;
    private StringBuilder resumenGeneracion;
    
    public GeneradorDocumentos(Proyecto proyecto) {
        this.proyecto = proyecto;
        this.resumenGeneracion = new StringBuilder();
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
            
            // 3. Copiar template a archivo final
            Files.copy(Paths.get(proyecto.getRutaTemplateWord()), Paths.get(rutaWord), StandardCopyOption.REPLACE_EXISTING);
            
            // 4. Abrir el documento copiado y procesarlo
            try (FileInputStream fis = new FileInputStream(rutaWord)) {
                document = new XWPFDocument(fis);
                
                // 5. Adaptar placeholders
                buscarYAdaptarPlaceholders(document, imagenesValidas.size());
                
                // 6. Insertar imágenes
                insertarImagenesEnDocumento(document, imagenesValidas);
                
                // 7. Reemplazar fecha
                actualizarFecha(document);
                
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
            // Normalizar patrones antiguos: si tienen números largos, eliminarlos
            List<String> patronesNormalizados = new ArrayList<>();
            for (String patron : proyecto.getImagenesSeleccionadas()) {
                // Crear un nombre de archivo ficticio para poder usar extraerPatron
                String nombreFicticio = patron + "20250101_000000.png";
                String patronNormalizado = GestorImagenes.extraerPatron(nombreFicticio);
                if (patronNormalizado != null) {
                    patronesNormalizados.add(patronNormalizado);
                    System.out.println("[DEBUG VALIDACION] Patron '" + patron + "' normalizado a: '" + patronNormalizado + "'");
                } else {
                    patronesNormalizados.add(patron);
                    System.out.println("[DEBUG VALIDACION] Patron '" + patron + "' sin cambios (no se pudo normalizar)");
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
     * Adapta los placeholders en el documento según la cantidad de imágenes
     */
    private boolean adaptarPlaceholders(int cantidadImagenes) {
        try {
            File templateFile = new File(proyecto.getRutaTemplateWord());
            if (!templateFile.exists()) {
                return false;
            }
            
            try (FileInputStream fis = new FileInputStream(templateFile);
                 XWPFDocument document = new XWPFDocument(fis)) {
                
                buscarYAdaptarPlaceholders(document, cantidadImagenes);
                
                // Actualizar el archivo template temporalmente
                try (FileOutputStream fos = new FileOutputStream(templateFile)) {
                    document.write(fos);
                }
                
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Busca y adapta los placeholders de imágenes
     */
    private void buscarYAdaptarPlaceholders(XWPFDocument document, int cantidadImagenes) {
        boolean placeholdersEncontrados = false;
        
        // Buscar en párrafos
        for (int i = 0; i < document.getParagraphs().size(); i++) {
            XWPFParagraph paragraph = document.getParagraphs().get(i);
            String texto = paragraph.getText();
            
            // Buscar el párrafo que contiene [Imagen1] - ahí es donde ajustamos
            if (texto.contains("[Imagen1]")) {
                placeholdersEncontrados = true;
                
                // Obtener posición del párrafo actual
                int posicion = document.getPosOfParagraph(paragraph);

                // Eliminar el párrafo original
                document.removeBodyElement(posicion);

                // Crear nuevos párrafos individuales para cada placeholder
                for (int j = 0; j < cantidadImagenes; j++) {
                    XWPFParagraph nuevoParrafo = document.insertNewParagraph(
                        document.getDocument().getBody().insertNewP(posicion + j).newCursor()
                    );
                    XWPFRun run = nuevoParrafo.createRun();
                    run.setText("[Imagen" + (j + 1) + "]");
                    nuevoParrafo.setAlignment(ParagraphAlignment.CENTER);
                }

                // Eliminar párrafos siguientes que tengan placeholders sobrantes (por si hay plantillas con más)
                List<XWPFParagraph> allParas = document.getParagraphs();
                for (int k = posicion + cantidadImagenes; k < allParas.size(); k++) {
                    XWPFParagraph nextPara = allParas.get(k);
                    String nextTexto = nextPara.getText();
                    for (int img = cantidadImagenes + 1; img <= 50; img++) {
                        if (nextTexto != null && nextTexto.contains("[Imagen" + img + "]")) {
                            document.removeBodyElement(document.getPosOfParagraph(nextPara));
                            allParas = document.getParagraphs();
                            k = posicion + cantidadImagenes - 1; // reiniciar desde el primer posible
                            break;
                        }
                    }
                }

                break; // Ya procesamos, salir del loop
            }
        }
        
        // Buscar en tablas también
        if (!placeholdersEncontrados) {
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
                            String texto = paragraph.getText();
                            if (texto.contains("[Imagen1]")) {
                                adaptarPlaceholderEnCelda(cell, cantidadImagenes);
                                placeholdersEncontrados = true;
                                break;
                            }
                        }
                        if (placeholdersEncontrados) break;
                    }
                    if (placeholdersEncontrados) break;
                }
                if (placeholdersEncontrados) break;
            }
        }
    }
    
    /**
     * Adapta placeholders dentro de una celda de tabla
     */
    private void adaptarPlaceholderEnCelda(XWPFTableCell cell, int cantidadImagenes) {
        // Limpiar todos los párrafos de la celda
        while (cell.getParagraphs().size() > 0) {
            cell.removeParagraph(0);
        }
        
        // Crear nuevos párrafos con los placeholders
        for (int i = 1; i <= cantidadImagenes; i++) {
            XWPFParagraph paragraph = cell.addParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText("[Imagen" + i + "]");
        }
    }
    
    /**
     * Inserta las imágenes en el documento
     */
    /**
     * Inserta imágenes en el documento ya abierto
     */
    private void insertarImagenesEnDocumento(XWPFDocument document, List<File> imagenes) throws Exception {
        for (int i = 0; i < imagenes.size(); i++) {
            String placeholder = "[Imagen" + (i + 1) + "]";
            insertarImagenEnDocumento(document, placeholder, imagenes.get(i));
        }
        
        // Actualizar fecha
        actualizarFecha(document);
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
            // Buscar el placeholder específico en cada RUN individual
            List<XWPFRun> runs = paragraph.getRuns();
            int runConPlaceholder = -1;
            
            System.out.println("\n=== BUSCANDO: " + placeholder + " ===");
            System.out.println("Total de runs en párrafo: " + runs.size());
            
            for (int i = 0; i < runs.size(); i++) {
                XWPFRun run = runs.get(i);
                String textoRun = run.getText(0);
                System.out.println("  Run[" + i + "]: " + (textoRun != null ? textoRun : "null"));
                
                if (textoRun != null && textoRun.contains(placeholder)) {
                    runConPlaceholder = i;
                    System.out.println("  ✓ ENCONTRADO en run " + i);
                    break;
                }
            }
            
            if (runConPlaceholder == -1) {
                System.out.println("  ✗ NO ENCONTRADO");
                return false;
            }
            
            // Eliminar el run del placeholder Y el break que le sigue (si existe)
            System.out.println("Eliminando run " + runConPlaceholder + " (placeholder)");
            paragraph.removeRun(runConPlaceholder);
            
            // Si hay un run siguiente y está vacío o es un break, eliminarlo también
            if (runConPlaceholder < paragraph.getRuns().size()) {
                XWPFRun siguienteRun = paragraph.getRuns().get(runConPlaceholder);
                String textoSiguiente = siguienteRun.getText(0);
                if (textoSiguiente == null || textoSiguiente.trim().isEmpty()) {
                    System.out.println("Eliminando run " + runConPlaceholder + " (break)");
                    paragraph.removeRun(runConPlaceholder);
                }
            }
            
            // Crear nuevo run EN LA MISMA POSICIÓN donde estaba el placeholder
            XWPFRun nuevoRun = paragraph.insertNewRun(runConPlaceholder);
            
            // Configurar alineación CENTER del párrafo
            paragraph.setAlignment(ParagraphAlignment.CENTER);
            
            // Insertar imagen en el nuevo run
            double anchoPuntos = ANCHO_CM * 28.3464567;
            double altoPuntos = ALTO_CM * 28.3464567;
            int anchoEMU = Units.toEMU(anchoPuntos);
            int altoEMU = Units.toEMU(altoPuntos);
            
            try (FileInputStream fis = new FileInputStream(imagen)) {
                nuevoRun.addPicture(fis, XWPFDocument.PICTURE_TYPE_PNG, 
                    imagen.getName(), anchoEMU, altoEMU);
                System.out.println("✓ Imagen insertada: " + imagen.getName());
            }
            
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Actualiza la fecha en el documento
     */
    private void actualizarFecha(XWPFDocument document) {
        String fechaActual = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        
        // Buscar en párrafos
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            actualizarFechaEnParrafo(paragraph, fechaActual);
        }
        
        // Buscar en tablas
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        actualizarFechaEnParrafo(paragraph, fechaActual);
                    }
                }
            }
        }
    }
    
    /**
     * Actualiza la fecha en un párrafo específico
     */
    private void actualizarFechaEnParrafo(XWPFParagraph paragraph, String fechaActual) {
        for (XWPFRun run : paragraph.getRuns()) {
            String texto = run.getText(0);
            if (texto != null && texto.contains(PATRON_FECHA)) {
                run.setText(texto.replace(PATRON_FECHA, fechaActual), 0);
                // Aplicar formato: Times New Roman, tamaño 20, negrita
                run.setFontFamily("Times New Roman");
                run.setFontSize(20);
                run.setBold(true);
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
            
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", script);
            pb.redirectErrorStream(true);
            Process process = pb.start();

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
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
            
            // Copiar template
            Files.copy(Paths.get(informe.getTemplateWord()), Paths.get(rutaWord), StandardCopyOption.REPLACE_EXISTING);
            
            // Abrir y procesar el documento
            try (FileInputStream fis = new FileInputStream(rutaWord)) {
                document = new XWPFDocument(fis);
                
                // Adaptar placeholders
                buscarYAdaptarPlaceholders(document, imagenes.size());
                
                // Insertar imágenes
                insertarImagenesEnDocumento(document, imagenes);
                
                // Actualizar fecha
                actualizarFecha(document);
                
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

