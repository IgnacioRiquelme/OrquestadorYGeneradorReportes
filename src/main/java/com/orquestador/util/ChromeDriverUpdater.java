package com.orquestador.util;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Clase utilitaria para detectar versión de Chrome y descargar automáticamente ChromeDriver
 * Optimizada para Windows 11 de 64 bits
 */
public class ChromeDriverUpdater {
    
    private static final String CHROME_FOR_TESTING_API = "https://googlechromelabs.github.io/chrome-for-testing/last-known-good-versions-with-downloads.json";
    private static final String CHROME_STABLE_API = "https://googlechromelabs.github.io/chrome-for-testing/LATEST_RELEASE_STABLE";
    
    private ProgressCallback progressCallback;
    
    public interface ProgressCallback {
        void onProgress(String message);
        void onError(String error);
        void onComplete(File chromedriverFile);
    }
    
    public ChromeDriverUpdater() {
    }
    
    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }
    
    private void notifyProgress(String message) {
        if (progressCallback != null) {
            progressCallback.onProgress(message);
        }
    }
    
    private void notifyError(String error) {
        if (progressCallback != null) {
            progressCallback.onError(error);
        }
    }
    
    private void notifyComplete(File file) {
        if (progressCallback != null) {
            progressCallback.onComplete(file);
        }
    }
    
    /**
     * Detecta la versión de Chrome instalada en el sistema Windows 11
     */
    public String detectarVersionChrome() {
        notifyProgress("🔍 Detectando versión de Google Chrome en Windows 11...");
        
        List<String> posiblesRutas = new ArrayList<>();
        // Rutas comunes en Windows 11 x64
        posiblesRutas.add("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe");
        posiblesRutas.add("C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe");
        posiblesRutas.add(System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\Application\\chrome.exe");
        posiblesRutas.add(System.getenv("PROGRAMFILES") + "\\Google\\Chrome\\Application\\chrome.exe");
        posiblesRutas.add(System.getenv("PROGRAMFILES(X86)") + "\\Google\\Chrome\\Application\\chrome.exe");
        
        for (String ruta : posiblesRutas) {
            try {
                File chromeExe = new File(ruta);
                if (chromeExe.exists()) {
                    String version = obtenerVersionChromeDesdeArchivo(chromeExe);
                    if (version != null && !version.isEmpty()) {
                        notifyProgress("✅ Chrome detectado en Windows 11: versión " + version);
                        return version;
                    }
                }
            } catch (Exception e) {
                // Continuar con la siguiente ruta
            }
        }
        
        // Intentar desde el registro de Windows 11
        try {
            String version = obtenerVersionChromeDesdeRegistro();
            if (version != null && !version.isEmpty()) {
                notifyProgress("✅ Chrome detectado desde registro de Windows 11: versión " + version);
                return version;
            }
        } catch (Exception e) {
            // Ignorar
        }
        
        notifyError("⚠️ No se pudo detectar la versión de Chrome instalada en Windows 11");
        return null;
    }
    
    /**
     * Obtiene la versión de Chrome desde el archivo ejecutable
     */
    private String obtenerVersionChromeDesdeArchivo(File chromeExe) {
        try {
            // Buscar el archivo de versión en la misma carpeta
            File parentDir = chromeExe.getParentFile();
            
            // Listar carpetas con números de versión (ej: 120.0.6099.109)
            File[] versionDirs = parentDir.listFiles(f -> f.isDirectory() && f.getName().matches("\\d+\\.\\d+\\.\\d+\\.\\d+"));
            
            if (versionDirs != null && versionDirs.length > 0) {
                // Tomar la versión más reciente (última carpeta)
                String version = versionDirs[versionDirs.length - 1].getName();
                return version;
            }
            
            // Método alternativo: ejecutar chrome.exe --version
            ProcessBuilder pb = new ProcessBuilder(chromeExe.getAbsolutePath(), "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    // Extraer versión del formato "Google Chrome 120.0.6099.109"
                    Pattern pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+)");
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                }
            }
            
            process.waitFor();
        } catch (Exception e) {
            // Ignorar
        }
        
        return null;
    }
    
    /**
     * Obtiene la versión de Chrome desde el registro de Windows 11
     */
    private String obtenerVersionChromeDesdeRegistro() {
        try {
            // Comando para Windows 11 (compatible con 10/11)
            String[] commands = {
                "reg", "query", 
                "HKEY_CURRENT_USER\\Software\\Google\\Chrome\\BLBeacon",
                "/v", "version"
            };
            
            ProcessBuilder pb = new ProcessBuilder(commands);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("version")) {
                        // Extraer versión
                        Pattern pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+)");
                        Matcher matcher = pattern.matcher(line);
                        if (matcher.find()) {
                            return matcher.group(1);
                        }
                    }
                }
            }
            
            process.waitFor();
        } catch (Exception e) {
            // Ignorar
        }
        
        return null;
    }
    
    /**
     * Obtiene la URL de descarga de ChromeDriver compatible con la versión de Chrome
     */
    public String obtenerURLDescargaChromeDriver(String versionChrome) {
        notifyProgress("🌐 Buscando ChromeDriver compatible...");
        
        try {
            // Extraer versión mayor (ej: 120 de 120.0.6099.109)
            String versionMayor = versionChrome.split("\\.")[0];
            
            // Primero intentar obtener la última versión estable desde la nueva API
            URL url = new URL(CHROME_FOR_TESTING_API);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    // Parsear JSON
                    JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
                    JsonObject channels = json.getAsJsonObject("channels");
                    JsonObject stable = channels.getAsJsonObject("Stable");
                    String stableVersion = stable.get("version").getAsString();
                    
                    notifyProgress("📦 Versión estable de ChromeDriver encontrada: " + stableVersion);
                    
                    // Obtener URL de descarga para win64 (priorizar 64 bits para Windows 11)
                    JsonObject downloads = stable.getAsJsonObject("downloads");
                    JsonArray chromedriver = downloads.getAsJsonArray("chromedriver");
                    
                    String win64Url = null;
                    String win32Url = null;
                    
                    for (JsonElement element : chromedriver) {
                        JsonObject download = element.getAsJsonObject();
                        String platform = download.get("platform").getAsString();
                        String downloadUrl = download.get("url").getAsString();
                        
                        if (platform.equals("win64")) {
                            win64Url = downloadUrl;
                        } else if (platform.equals("win32")) {
                            win32Url = downloadUrl;
                        }
                    }
                    
                    // PRIORIZAR win64 para Windows 11 de 64 bits
                    if (win64Url != null) {
                        notifyProgress("✅ URL de descarga encontrada para win64 (Windows 11 x64)");
                        return win64Url;
                    } else if (win32Url != null) {
                        notifyProgress("⚠️ Solo disponible win32 (se usará versión de 32 bits)");
                        return win32Url;
                    }
                }
            }
            
            conn.disconnect();
            
        } catch (Exception e) {
            notifyError("❌ Error al obtener URL de ChromeDriver: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Descarga y extrae ChromeDriver en una carpeta temporal
     */
    public File descargarChromeDriver(String downloadUrl) {
        notifyProgress("⬇️ Descargando ChromeDriver desde: " + downloadUrl);
        
        File tempDir = null;
        File chromedriverFile = null;
        
        try {
            // Crear directorio temporal
            tempDir = Files.createTempDirectory("chromedriver_download").toFile();
            tempDir.deleteOnExit();
            
            // Descargar archivo ZIP
            URL url = new URL(downloadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                notifyError("❌ Error al descargar: código de respuesta " + responseCode);
                return null;
            }
            
            long totalSize = conn.getContentLengthLong();
            notifyProgress("📦 Tamaño del archivo: " + formatBytes(totalSize));
            
            File zipFile = new File(tempDir, "chromedriver.zip");
            
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(zipFile)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalRead = 0;
                int lastProgress = 0;
                
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                    
                    if (totalSize > 0) {
                        int progress = (int) ((totalRead * 100) / totalSize);
                        if (progress > lastProgress + 10) { // Actualizar cada 10%
                            notifyProgress("⬇️ Descargando... " + progress + "%");
                            lastProgress = progress;
                        }
                    }
                }
            }
            
            conn.disconnect();
            notifyProgress("✅ Descarga completada");
            
            // Extraer ZIP
            notifyProgress("📂 Extrayendo archivo...");
            chromedriverFile = extraerChromeDriver(zipFile, tempDir);
            
            if (chromedriverFile != null && chromedriverFile.exists()) {
                notifyProgress("✅ ChromeDriver extraído correctamente");
                notifyComplete(chromedriverFile);
                return chromedriverFile;
            } else {
                notifyError("❌ No se encontró chromedriver.exe en el archivo descargado");
                return null;
            }
            
        } catch (Exception e) {
            notifyError("❌ Error al descargar ChromeDriver: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Extrae chromedriver.exe del archivo ZIP
     */
    private File extraerChromeDriver(File zipFile, File destDir) throws IOException {
        File chromedriverExe = null;
        
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            
            while ((entry = zis.getNextEntry()) != null) {
                String fileName = entry.getName();
                
                // Buscar chromedriver.exe en cualquier nivel del ZIP
                if (fileName.endsWith("chromedriver.exe") || fileName.endsWith("chromedriver")) {
                    File outputFile = new File(destDir, "chromedriver.exe");
                    
                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    
                    chromedriverExe = outputFile;
                    break;
                }
                
                zis.closeEntry();
            }
        }
        
        return chromedriverExe;
    }
    
    /**
     * Formatea bytes a formato legible
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    /**
     * Proceso completo: detectar Chrome, descargar ChromeDriver y retornar el archivo
     */
    public File actualizarAutomaticamente() {
        try {
            // 1. Detectar versión de Chrome
            String versionChrome = detectarVersionChrome();
            if (versionChrome == null || versionChrome.isEmpty()) {
                notifyError("❌ No se pudo detectar la versión de Chrome instalada");
                return null;
            }
            
            // 2. Obtener URL de descarga
            String downloadUrl = obtenerURLDescargaChromeDriver(versionChrome);
            if (downloadUrl == null || downloadUrl.isEmpty()) {
                notifyError("❌ No se pudo obtener la URL de descarga de ChromeDriver");
                return null;
            }
            
            // 3. Descargar ChromeDriver
            File chromedriverFile = descargarChromeDriver(downloadUrl);
            
            return chromedriverFile;
            
        } catch (Exception e) {
            notifyError("❌ Error en el proceso de actualización: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Detecta la versión de un archivo chromedriver.exe específico
     */
    public static String detectarVersionChromeDriver(File chromedriverExe) {
        if (chromedriverExe == null || !chromedriverExe.exists()) {
            return null;
        }
        
        try {
            // Ejecutar chromedriver.exe --version
            ProcessBuilder pb = new ProcessBuilder(chromedriverExe.getAbsolutePath(), "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    // Formato típico: "ChromeDriver 131.0.6778.85 (hash...)" o "ChromeDriver 145.0.7632.46"
                    Pattern pattern = Pattern.compile("ChromeDriver\\s+(\\d+\\.\\d+\\.\\d+\\.\\d+)");
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                    
                    // Alternativo: solo números con puntos
                    pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+)");
                    matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                }
            }
            
            process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            process.destroy();
        } catch (Exception e) {
            // Silenciosamente fallar
            System.err.println("No se pudo detectar versión de " + chromedriverExe.getName() + ": " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Compara dos versiones en formato X.Y.Z.W
     * @return -1 si v1 < v2, 0 si v1 == v2, 1 si v1 > v2
     */
    public static int compararVersiones(String v1, String v2) {
        if (v1 == null || v2 == null) return 0;
        
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            
            if (num1 < num2) return -1;
            if (num1 > num2) return 1;
        }
        
        return 0;
    }
    
    /**
     * Compara solo las versiones mayores (X.Y.Z) ignorando el último dígito (build)
     * Útil para evitar actualizaciones por cambios menores de build
     * @return -1 si v1 < v2, 0 si v1 == v2, 1 si v1 > v2
     */
    public static int compararVersionesMayores(String v1, String v2) {
        if (v1 == null || v2 == null) return 0;
        
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        
        // Comparar solo los primeros 3 dígitos (X.Y.Z), ignorar el último (build)
        int length = Math.min(3, Math.max(parts1.length, parts2.length));
        for (int i = 0; i < length; i++) {
            int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            
            if (num1 < num2) return -1;
            if (num1 > num2) return 1;
        }
        
        return 0;
    }
    
    /**
     * Busca recursivamente chromedriver.exe en una carpeta
     */
    public static List<File> buscarChromeDriverEnCarpeta(File carpeta) {
        List<File> resultados = new ArrayList<>();
        
        if (!carpeta.exists() || !carpeta.isDirectory()) {
            return resultados;
        }
        
        File[] archivos = carpeta.listFiles();
        if (archivos == null) {
            return resultados;
        }
        
        for (File archivo : archivos) {
            if (archivo.isFile() && archivo.getName().equalsIgnoreCase("chromedriver.exe")) {
                resultados.add(archivo);
            } else if (archivo.isDirectory()) {
                resultados.addAll(buscarChromeDriverEnCarpeta(archivo));
            }
        }
        
        return resultados;
    }
    
    /**
     * Copia el archivo chromedriver a una ruta específica
     */
    public static void copiarChromeDriver(File origen, File destino) throws IOException {
        Files.copy(
            origen.toPath(),
            destino.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        );
    }
}
