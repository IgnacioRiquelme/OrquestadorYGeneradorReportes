package com.orquestador.utilidades;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utilidades para buscar y validar imágenes
 */
public class GestorImagenes {
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    /**
     * Busca todos los patrones únicos de imágenes en una carpeta
     */
    public static List<String> obtenerPatronesUnicos(String rutaCarpeta) {
        File dir = new File(rutaCarpeta);
        
        if (!dir.exists() || !dir.isDirectory()) {
            return new ArrayList<>();
        }
        
        File[] archivos = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".png"));
        
        if (archivos == null || archivos.length == 0) {
            return new ArrayList<>();
        }
        
        Set<String> patrones = new TreeSet<>();
        
        for (File archivo : archivos) {
            String patron = extraerPatron(archivo.getName());
            if (patron != null && !patron.isEmpty()) {
                patrones.add(patron);
            }
        }
        
        return new ArrayList<>(patrones);
    }
    
    /**
     * Extrae el patrón de una imagen (todo antes de la fecha/hora)
     * Ejemplo: "t0001_1_Login_20251111_235030.png" → "t0001_1_Login_"
     */
    public static String extraerPatron(String nombreArchivo) {
        // Patrón: cualquier cosa antes de _YYYYMMDD_HHMMSS.png
        Pattern pattern = Pattern.compile("^(.+?)_(\\d{8}_\\d{6})\\.png$");
        Matcher matcher = pattern.matcher(nombreArchivo);
        
        if (matcher.matches()) {
            return matcher.group(1) + "_";
        }
        
        return null;
    }
    
    /**
     * Extrae el timestamp de un nombre de archivo
     * Ejemplo: "t0001_1_Login_20251111_235030.png" → "20251111_235030"
     */
    public static String extraerTimestamp(String nombreArchivo) {
        Pattern pattern = Pattern.compile("_(\\d{8}_\\d{6})\\.png$");
        Matcher matcher = pattern.matcher(nombreArchivo);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }
    
    /**
     * Busca la imagen más reciente con un patrón específico
     */
    public static File buscarImagenMasReciente(String rutaCarpeta, String patron) {
        File dir = new File(rutaCarpeta);
        
        if (!dir.exists() || !dir.isDirectory()) {
            return null;
        }
        
        File[] archivos = dir.listFiles((d, name) -> 
            name.toLowerCase().endsWith(".png") && name.contains(patron));
        
        if (archivos == null || archivos.length == 0) {
            return null;
        }
        
        return Arrays.stream(archivos)
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null);
    }
    
    /**
     * Obtiene todas las imágenes de un patrón ordenadas por timestamp
     */
    public static List<File> obtenerImagenesPorPatron(String rutaCarpeta, String patron) {
        File dir = new File(rutaCarpeta);
        
        if (!dir.exists() || !dir.isDirectory()) {
            return new ArrayList<>();
        }
        
        File[] archivos = dir.listFiles((d, name) -> 
            name.toLowerCase().endsWith(".png") && name.contains(patron));
        
        if (archivos == null || archivos.length == 0) {
            return new ArrayList<>();
        }
        
        return Arrays.stream(archivos)
                .sorted((a, b) -> Long.compare(b.lastModified(), a.lastModified()))
                .collect(Collectors.toList());
    }
    
    /**
     * Valida las imágenes de una secuencia
     * Retorna un mapa con información de validación
     * NOTA: Se eliminó la restricción de tiempo para permitir tests largos y sets antiguos
     */
    public static Map<String, Object> validarRangoTiempo(List<File> imagenes) {
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("valido", true);
        resultado.put("alertas", new ArrayList<String>());
        resultado.put("imagenesValidas", new ArrayList<File>());
        
        if (imagenes == null || imagenes.isEmpty()) {
            return resultado;
        }
        
        // Simplemente aceptamos todas las imágenes encontradas
        // El ControladorPrincipal ya se encarga de seleccionar el último set correcto
        List<File> imagenesValidas = new ArrayList<>();
        
        for (File imagen : imagenes) {
            String timestamp = extraerTimestamp(imagen.getName());
            
            if (timestamp != null) {
                imagenesValidas.add(imagen);
            }
        }
        
        resultado.put("imagenesValidas", imagenesValidas);
        
        return resultado;
    }
    
    /**
     * Convierte un timestamp al formato LocalDateTime
     */
    private static LocalDateTime parseTimestamp(String timestamp) {
        try {
            return LocalDateTime.parse(timestamp, TIMESTAMP_FORMAT);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
    
    /**
     * Calcula la diferencia en minutos entre dos timestamps
     */
    public static long calcularDiferenciaMinutos(String timestamp1, String timestamp2) {
        LocalDateTime fecha1 = parseTimestamp(timestamp1);
        LocalDateTime fecha2 = parseTimestamp(timestamp2);
        
        return Math.abs(java.time.temporal.ChronoUnit.MINUTES.between(fecha1, fecha2));
    }
    
    /**
     * Elimina todas las imágenes PNG de una carpeta
     */
    public static int eliminarTodasLasImagenes(String rutaCarpeta) {
        File dir = new File(rutaCarpeta);
        
        if (!dir.exists() || !dir.isDirectory()) {
            return 0;
        }
        
        File[] archivos = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".png"));
        
        if (archivos == null) {
            return 0;
        }
        
        int eliminados = 0;
        for (File archivo : archivos) {
            if (archivo.delete()) {
                eliminados++;
            }
        }
        
        return eliminados;
    }
}
