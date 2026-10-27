package com.orquestador.servicio;

import com.orquestador.modelo.ProyectoAutomatizacion;
import com.orquestador.modelo.ProyectoAutomatizacion.EstadoEjecucion;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Servicio para ejecutar proyectos de automatizacin
 */
public class EjecutorAutomatizaciones {

    private volatile boolean ejecutando = false;
    private volatile Process procesoActual;
    private volatile Thread hiloActual;
    private volatile ProyectoAutomatizacion proyectoActual;

    /**
     * Ejecuta un proyecto de automatizacin en una ventana CMD maximizada
     */
    public void ejecutarProyecto(ProyectoAutomatizacion proyecto,
                                  Consumer<String> logCallback,
                                  Runnable onFinish) {

        synchronized (this) {
            if (ejecutando) {
                if (logCallback != null) logCallback.accept("Ya hay una ejecución en curso. Espera o cancela la ejecución actual.");
                return;
            }
            ejecutando = true;
            proyectoActual = proyecto;
        }

        proyecto.setEstado(EstadoEjecucion.EJECUTANDO);
        proyecto.setUltimaEjecucion(LocalDateTime.now());

        long inicio = System.currentTimeMillis();

        new Thread(() -> {
            hiloActual = Thread.currentThread();
            try {
                // Limpiar procesos antes de ejecutar
                limpiarProcesos(logCallback);

                // PASO 1: Archivar imágenes de la carpeta de capturas antes de ejecutar
                archivarImagenesAnteriores(proyecto, logCallback);

                // Resolver el directorio real de ejecución (soporta proyectos
                // con el código anidado en una subcarpeta, ej: .../auxilia)
                File dirEjecucion = resolverDirectorioEjecucion(proyecto);
                if (dirEjecucion != null && !dirEjecucion.getAbsolutePath().equalsIgnoreCase(proyecto.getRuta())) {
                    if (logCallback != null) logCallback.accept("  ℹ Código en subcarpeta detectado - ejecutando en: " + dirEjecucion.getAbsolutePath());
                }

                // Crear script temporal para ejecutar
                File scriptTemp = crearScriptEjecucion(proyecto, dirEjecucion);
                
                // Crear un archivo marker para detectar cuando termina
                File markerFile = new File(scriptTemp.getParentFile(), scriptTemp.getName() + ".done");
                if (markerFile.exists()) markerFile.delete();

                // Ejecutar en CMD maximizado - usar /wait para que espere
                ProcessBuilder pb = new ProcessBuilder(
                    "cmd.exe", "/c", "start", "\"" + proyecto.getNombre() + "\"", "/wait", "/max", 
                    "cmd.exe", "/c", scriptTemp.getAbsolutePath() + " & echo DONE > \"" + markerFile.getAbsolutePath() + "\""
                );
                pb.directory(dirEjecucion);

                if (logCallback != null) {
                    logCallback.accept(" Ejecutando: " + proyecto.getNombre());
                    logCallback.accept(" Ruta: " + proyecto.getRuta());
                }

                procesoActual = pb.start();
                // Registrar proceso en el registry para asegurar cierre global
                com.orquestador.servicio.ProcessRegistry.getInstance().register(procesoActual);
                
                // Esperar a que el proceso termine
                int exitCode = procesoActual.waitFor();
                
                // Esperar tambien a que aparezca el archivo marker (confirma que el script termino)
                int maxWait = 300; // 5 minutos maximo
                int waited = 0;
                while (!markerFile.exists() && waited < maxWait) {
                    Thread.sleep(1000);
                    waited++;
                }

                // Leer el exit code del script si existe
                File exitCodeFile = new File(scriptTemp.getParentFile(), scriptTemp.getName() + ".exitcode");
                if (exitCodeFile.exists()) {
                    String content = new String(java.nio.file.Files.readAllBytes(exitCodeFile.toPath())).trim();
                    try {
                        exitCode = Integer.parseInt(content);
                    } catch (NumberFormatException e) {
                        // Usar el exit code del proceso
                    }
                    exitCodeFile.delete();
                }

                // Calcular duracion
                long fin = System.currentTimeMillis();
                int duracion = (int) ((fin - inicio) / 1000);
                proyecto.setDuracionSegundos(duracion);

                // Actualizar estado segun resultado
                if (exitCode == 0) {
                    proyecto.setEstado(EstadoEjecucion.EXITOSO);
                    if (logCallback != null) logCallback.accept(" " + proyecto.getNombre() + " - EXITOSO (" + duracion + "s)");
                } else {
                    proyecto.setEstado(EstadoEjecucion.FALLIDO);
                    proyecto.setMensajeError("Exit code: " + exitCode);
                    if (logCallback != null) logCallback.accept(" " + proyecto.getNombre() + " - FALLIDO (" + duracion + "s)");
                }

                // GENERAR LOG DESPUÉS DE LA EJECUCIÓN con los reportes de Maven/Newman
                generarLogDeReportes(proyecto, exitCode, duracion);

                // Limpiar despues de ejecutar
                scriptTemp.delete();
                markerFile.delete();
                limpiarProcesos(logCallback);

            } catch (InterruptedException e) {
                // Hilo interrumpido: intentar finalizar procesos y marcar cancelado
                if (proyecto != null) proyecto.setEstado(EstadoEjecucion.CANCELADO);
                if (logCallback != null) logCallback.accept(" " + proyecto.getNombre() + " - CANCELADO");
                killProcessTree(logCallback);
            } catch (Exception e) {
                if (proyecto != null) proyecto.setEstado(EstadoEjecucion.FALLIDO);
                if (proyecto != null) proyecto.setMensajeError(e.getMessage());
                if (logCallback != null) logCallback.accept(" Error: " + e.getMessage());
            } finally {
                // Asegurar limpieza de estado
                if (procesoActual != null) {
                    com.orquestador.servicio.ProcessRegistry.getInstance().unregister(procesoActual);
                }
                procesoActual = null;
                hiloActual = null;
                synchronized (this) {
                    ejecutando = false;
                    proyectoActual = null;
                }
                if (onFinish != null) {
                    onFinish.run();
                }
            }
        }).start();
    }

    /**
     * Resuelve el directorio real donde debe ejecutarse el proyecto.
     * Si la ruta configurada no contiene el archivo marcador correspondiente
     * (pom.xml para Maven/Maven+Newman, collection.json para Newman), busca
     * en las subcarpetas de primer nivel. Devuelve null si la ruta es vacía.
     */
    private File resolverDirectorioEjecucion(ProyectoAutomatizacion proyecto) {
        String ruta = proyecto.getRuta();
        if (ruta == null || ruta.trim().isEmpty()) return null;
        File base = new File(ruta);
        if (!base.isDirectory()) return base;

        String marcador = proyecto.getTipoEjecucion() == ProyectoAutomatizacion.TipoEjecucion.NEWMAN
                ? "collection.json" : "pom.xml";
        if (new File(base, marcador).exists()) return base;

        File[] subs = base.listFiles(File::isDirectory);
        if (subs == null) return base;
        java.util.Arrays.sort(subs);
        for (File sub : subs) {
            if (new File(sub, marcador).exists()) return sub;
        }
        return base;
    }

    /**
     * Crea un script .bat temporal para la ejecución
     */
    private File crearScriptEjecucion(ProyectoAutomatizacion proyecto, File dirEjecucion) throws IOException {
        File scriptTemp = File.createTempFile("exec_", ".bat");
        File exitCodeFile = new File(scriptTemp.getParentFile(), scriptTemp.getName() + ".exitcode");
        File logFile = new File(scriptTemp.getParentFile(), scriptTemp.getName().replace(".bat", ".log"));

        // Guardar ruta del log en el proyecto
        proyecto.setRutaLogEjecucion(logFile.getAbsolutePath());

        StringBuilder script = new StringBuilder();
        script.append("@echo off\n");
        script.append("chcp 65001 >nul\n");
        String dirTrabajo = dirEjecucion != null ? dirEjecucion.getAbsolutePath() : proyecto.getRuta();
        script.append("cd /d \"").append(dirTrabajo).append("\"\n");
        script.append("echo.\n");
        script.append("echo ========================================\n");
        script.append("echo   ").append(proyecto.getNombre()).append("\n");
        script.append("echo   Area: ").append(proyecto.getArea()).append("\n");
        script.append("echo   VPN: ").append(proyecto.getTipoVPN().getDescripcion()).append("\n");
        script.append("echo ========================================\n");
        script.append("echo.\n");

        // Detectar Maven - usar ruta fija directamente (mas confiable que 'where' dentro de la ventana cmd anidada)
        script.append("if exist \"C:\\apache-maven-3.9.11\\bin\\mvn.cmd\" (\n");
        script.append("    set MVN_CMD=C:\\apache-maven-3.9.11\\bin\\mvn.cmd\n");
        script.append(") else (\n");
        script.append("    set MVN_CMD=mvn\n");
        script.append(")\n");
        script.append("echo.\n");

        // Comando según tipo de ejecución - EJECUCIÓN COMPLETAMENTE NORMAL (cero cambios)
        if (proyecto.getTipoEjecucion() == ProyectoAutomatizacion.TipoEjecucion.MAVEN) {
            script.append("echo Ejecutando mvn test...\n");
            script.append("echo.\n");
            script.append("call \"%MVN_CMD%\" test\n");
        } else if (proyecto.getTipoEjecucion() == ProyectoAutomatizacion.TipoEjecucion.NEWMAN) {
            script.append("echo Ejecutando newman...\n");
            script.append("echo.\n");
            script.append("call newman run collection.json\n");
        } else {
            script.append("echo Ejecutando mvn test + newman...\n");
            script.append("echo.\n");
            script.append("call \"%MVN_CMD%\" test\n");
            script.append("echo.\n");
            script.append("echo Ejecutando newman...\n");
            script.append("call newman run collection.json\n");
        }

        script.append("echo.\n");
        script.append("echo ========================================\n");
        script.append("set TEST_RESULT=%ERRORLEVEL%\n");
        script.append("echo.\n");
        script.append("echo ========================================\n");
        script.append("if %TEST_RESULT% EQU 0 (\n");
        script.append("    echo   RESULTADO: EXITOSO\n");
        script.append(") else (\n");
        script.append("    echo   RESULTADO: FALLIDO\n");
        script.append("    echo   Exit Code: %TEST_RESULT%\n");
        script.append(")\n");
        script.append("echo ========================================\n");
        script.append("echo.\n");

        script.append("echo %TEST_RESULT% > \"").append(exitCodeFile.getAbsolutePath()).append("\"\n");
        script.append("timeout /t 3 >nul\n");
        script.append("exit /b %TEST_RESULT%\n");

        // Escribir el .bat en codificación compatible con cmd de Windows para evitar problemas
        // con caracteres y posibles corrupciones de líneas como "echo." -> "cho."
        java.nio.file.Files.write(scriptTemp.toPath(), script.toString().getBytes("Cp1252"));
        return scriptTemp;
    }

    /**
     * Limpia procesos de CMD y Chrome antes/después de la ejecución
     */
    private void limpiarProcesos(Consumer<String> logCallback) {
        try {
            // Cerrar Chrome
            ProcessBuilder pbChrome = new ProcessBuilder("taskkill", "/F", "/IM", "chrome.exe", "/T");
            pbChrome.start().waitFor();

            // Cerrar ChromeDriver
            ProcessBuilder pbDriver = new ProcessBuilder("taskkill", "/F", "/IM", "chromedriver.exe", "/T");
            pbDriver.start().waitFor();

            logCallback.accept(" Procesos limpiados");
            Thread.sleep(1000); // Esperar un segundo

        } catch (Exception e) {
            // No es crtico si falla
            logCallback.accept(" Advertencia al limpiar procesos: " + e.getMessage());
        }
    }

    /**
     * Genera un log completo después de la ejecución recolectando los reportes generados
     */
    private void generarLogDeReportes(ProyectoAutomatizacion proyecto, int exitCode, int duracion) {
        if (proyecto.getRutaLogEjecucion() == null) return;
        
        try {
            StringBuilder logContent = new StringBuilder();
            logContent.append("========================================\n");
            logContent.append("RESUMEN DE EJECUCION\n");
            logContent.append("========================================\n");
            logContent.append("Proyecto: ").append(proyecto.getNombre()).append("\n");
            logContent.append("Area: ").append(proyecto.getArea()).append("\n");
            logContent.append("VPN: ").append(proyecto.getTipoVPN().getDescripcion()).append("\n");
            logContent.append("Tipo: ").append(proyecto.getTipoEjecucion()).append("\n");
            logContent.append("Duracion: ").append(duracion).append(" segundos\n");
            logContent.append("Resultado: ").append(exitCode == 0 ? "EXITOSO" : "FALLIDO (Exit Code: " + exitCode + ")").append("\n");
            logContent.append("Fecha: ").append(java.time.LocalDateTime.now()).append("\n");
            logContent.append("========================================\n\n");

            // Para Maven: buscar los reportes de Surefire y otros logs
            if (proyecto.getTipoEjecucion() == ProyectoAutomatizacion.TipoEjecucion.MAVEN ||
                proyecto.getTipoEjecucion() == ProyectoAutomatizacion.TipoEjecucion.MAVEN_NEWMAN) {

                File dirEjecucion = resolverDirectorioEjecucion(proyecto);
                String baseDir = dirEjecucion != null ? dirEjecucion.getAbsolutePath() : proyecto.getRuta();

                // Surefire queda en target/surefire-reports (Maven) y a veces en
                // test-output/surefire-reports (TestNG). Los .txt traen el
                // stacktrace real del fallo — son la info relevante del log.
                for (String sub : new String[]{"target" + File.separator + "surefire-reports",
                                               "test-output" + File.separator + "surefire-reports"}) {
                    File surefireReportsDir = new File(baseDir, sub);
                    if (!surefireReportsDir.exists()) continue;
                    File[] reportFiles = surefireReportsDir.listFiles((dir, name) -> name.endsWith(".txt"));
                    if (reportFiles == null || reportFiles.length == 0) continue;
                    logContent.append("REPORTES DE MAVEN (").append(sub).append("):\n");
                    logContent.append("========================================\n");
                    java.util.Arrays.sort(reportFiles,
                        java.util.Comparator.comparingLong(File::lastModified).reversed());
                    for (File reportFile : reportFiles) {
                        logContent.append("\n--- ").append(reportFile.getName()).append(" ---\n");
                        try {
                            String content = new String(java.nio.file.Files.readAllBytes(reportFile.toPath()), "UTF-8");
                            if (content.length() > 200_000) {
                                content = content.substring(0, 200_000) + "\n... [truncado]";
                            }
                            logContent.append(content).append("\n");
                        } catch (Exception e) {
                            logContent.append("Error leyendo reporte: ").append(e.getMessage()).append("\n");
                        }
                    }
                    logContent.append("\n");
                }

                // Reporte más reciente (ExtentReports / ReporteAutomatizacion / surefire)
                File ultimoReporte = buscarUltimoReporte(proyecto);
                if (ultimoReporte != null) {
                    logContent.append("REPORTE MAS RECIENTE EN EL PROYECTO:\n");
                    logContent.append("========================================\n");
                    logContent.append(ultimoReporte.getAbsolutePath()).append("\n\n");
                }

                // Buscar archivos .log adicionales en test-output
                File testOutputDir = new File(baseDir + File.separator + "test-output");
                if (testOutputDir.exists()) {
                    File[] logFiles = testOutputDir.listFiles((dir, name) -> name.endsWith(".log"));
                    if (logFiles != null && logFiles.length > 0) {
                        logContent.append("LOGS ADICIONALES (test-output):\n");
                        logContent.append("========================================\n");
                        for (File logFile : logFiles) {
                            logContent.append("\n--- ").append(logFile.getName()).append(" ---\n");
                            try {
                                String content = new String(java.nio.file.Files.readAllBytes(logFile.toPath()), "UTF-8");
                                logContent.append(content).append("\n");
                            } catch (Exception e) {
                                logContent.append("Error leyendo log: ").append(e.getMessage()).append("\n");
                            }
                        }
                        logContent.append("\n");
                    }
                }
            }

            // Guardar el log
            java.nio.file.Files.write(
                new File(proyecto.getRutaLogEjecucion()).toPath(),
                logContent.toString().getBytes("UTF-8")
            );
            
        } catch (Exception e) {
            // Log silencioso - no interrumpir la ejecución
        }
    }

    /**
     * Archiva las imágenes de la carpeta de capturas antes de ejecutar la automatización.
     * En vez de borrarlas, las mueve a "<capturas>/../evidencias/<yyyyMMdd_HHmmss>/"
     * donde el timestamp corresponde a la fecha/hora de la ejecución que las generó
     * (última modificación de las imágenes). Así la carpeta de capturas queda vacía
     * para la nueva ejecución y las evidencias quedan consultables.
     * También purga las carpetas de evidencias con más de 30 días.
     */
    private void archivarImagenesAnteriores(ProyectoAutomatizacion proyecto, Consumer<String> logCallback) {
        try {
            // Usar la ruta de imágenes configurada si existe, sino usar ruta por defecto
            String rutaCapturas = proyecto.getRutaImagenes();

            if (rutaCapturas == null || rutaCapturas.trim().isEmpty()) {
                File dirEjecucion = resolverDirectorioEjecucion(proyecto);
                String baseDir = dirEjecucion != null ? dirEjecucion.getAbsolutePath() : proyecto.getRuta();
                // Determinar la carpeta por defecto según el tipo de ejecución
                if (proyecto.getTipoEjecucion() == ProyectoAutomatizacion.TipoEjecucion.MAVEN ||
                    proyecto.getTipoEjecucion() == ProyectoAutomatizacion.TipoEjecucion.MAVEN_NEWMAN) {
                    // Para Maven: test-output/capturaPantalla
                    rutaCapturas = baseDir + File.separator + "test-output" + File.separator + "capturaPantalla";
                } else if (proyecto.getTipoEjecucion() == ProyectoAutomatizacion.TipoEjecucion.NEWMAN) {
                    // Para Newman: capturas (asumido, ajustar según necesidad)
                    rutaCapturas = baseDir + File.separator + "capturas";
                }
            }

            if (rutaCapturas == null || rutaCapturas.trim().isEmpty()) {
                logCallback.accept("  ⚠ No se pudo determinar carpeta de capturas");
                return;
            }

            File carpetaCapturas = new File(rutaCapturas);
            // La carpeta histórica vive en la raíz de cada proyecto con nombre
            // "Historial Evidencias": nunca dentro ni junto a la carpeta de
            // capturas (que podría llamarse "evidencias"), así no se mezclan
            // los archivos archivados con los de la ejecución actual
            String rutaProyecto = proyecto.getRuta();
            if (rutaProyecto == null || rutaProyecto.trim().isEmpty()) {
                logCallback.accept("  ⚠ Proyecto sin ruta, se eliminarán las imágenes anteriores");
                File[] imgs = carpetaCapturas.listFiles((d, n) ->
                    n.toLowerCase().endsWith(".png") || n.toLowerCase().endsWith(".jpg") || n.toLowerCase().endsWith(".jpeg"));
                if (imgs != null) for (File img : imgs) img.delete();
                return;
            }
            File carpetaEvidencias = new File(rutaProyecto, "Historial Evidencias");

            // Purga: eliminar carpetas de evidencias con más de 30 días
            purgarEvidenciasAntiguas(carpetaEvidencias, logCallback);

            // Si no existe la carpeta, no hay nada que archivar
            if (!carpetaCapturas.exists() || !carpetaCapturas.isDirectory()) {
                logCallback.accept("  ℹ Carpeta de capturas no existe (se creará en la ejecución)");
                return;
            }

            // Listar todas las imágenes
            File[] imagenes = carpetaCapturas.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".png") ||
                name.toLowerCase().endsWith(".jpg") ||
                name.toLowerCase().endsWith(".jpeg")
            );

            if (imagenes == null || imagenes.length == 0) {
                logCallback.accept("  ℹ No hay imágenes anteriores para archivar");
                return;
            }

            // Timestamp = fecha/hora de la ejecución que generó las capturas
            long fechaEjecucion = 0;
            for (File imagen : imagenes) {
                fechaEjecucion = Math.max(fechaEjecucion, imagen.lastModified());
            }
            if (fechaEjecucion <= 0) fechaEjecucion = System.currentTimeMillis();
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss")
                    .format(new java.util.Date(fechaEjecucion));

            File destino = new File(carpetaEvidencias, stamp);
            int sufijo = 1;
            while (destino.exists()) {
                destino = new File(carpetaEvidencias, stamp + "_" + (sufijo++));
            }
            if (!destino.mkdirs()) {
                logCallback.accept("  ⚠ No se pudo crear carpeta de evidencias, se eliminarán las imágenes");
                for (File imagen : imagenes) imagen.delete();
                return;
            }

            // Mover todas las imágenes a la carpeta de evidencias
            int archivadas = 0;
            for (File imagen : imagenes) {
                File dest = new File(destino, imagen.getName());
                if (imagen.renameTo(dest)) {
                    archivadas++;
                } else {
                    // Si no se puede mover (ej. otro volumen), copiar y borrar
                    try {
                        java.nio.file.Files.copy(imagen.toPath(), dest.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        if (imagen.delete()) archivadas++;
                    } catch (Exception ex) {
                        // Último recurso: borrar para no mezclar con la nueva ejecución
                        imagen.delete();
                    }
                }
            }

            logCallback.accept("  � Archivadas " + archivadas + " imágenes en: Historial Evidencias" + File.separator + destino.getName());

        } catch (Exception e) {
            logCallback.accept("  ⚠ Error al archivar imágenes anteriores: " + e.getMessage());
        }
    }

    /**
     * Elimina las carpetas de evidencias con más de 30 días.
     * Solo purga carpetas cuyo nombre empiece con el formato yyyyMMdd_HHmmss
     * (las generadas por el orquestador); el resto se deja intacto.
     */
    private void purgarEvidenciasAntiguas(File carpetaEvidencias, Consumer<String> logCallback) {
        try {
            if (carpetaEvidencias == null || !carpetaEvidencias.isDirectory()) return;
            File[] carpetas = carpetaEvidencias.listFiles(File::isDirectory);
            if (carpetas == null || carpetas.length == 0) return;

            long limite = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000;
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss");
            sdf.setLenient(false);

            int eliminadas = 0;
            for (File carpeta : carpetas) {
                String nombre = carpeta.getName();
                if (nombre.length() < 15) continue;
                long fechaCarpeta;
                try {
                    fechaCarpeta = sdf.parse(nombre.substring(0, 15)).getTime();
                } catch (Exception ex) {
                    continue; // No es una carpeta de evidencias generada por nosotros
                }
                if (fechaCarpeta < limite && borrarDirectorioRecursivo(carpeta)) {
                    eliminadas++;
                }
            }
            if (eliminadas > 0) {
                logCallback.accept("  🗑️ Purgadas " + eliminadas + " carpeta(s) de evidencias con más de 30 días");
            }
        } catch (Exception e) {
            logCallback.accept("  ⚠ Error al purgar evidencias antiguas: " + e.getMessage());
        }
    }

    private boolean borrarDirectorioRecursivo(File dir) {
        File[] contenido = dir.listFiles();
        if (contenido != null) {
            for (File f : contenido) {
                if (f.isDirectory()) {
                    borrarDirectorioRecursivo(f);
                } else {
                    f.delete();
                }
            }
        }
        return dir.delete();
    }

    /**
     * Detiene la ejecución actual
     */
    public void detener() {
        // Interrumpir el hilo de ejecución si existe
        try {
            Thread t = hiloActual;
            if (t != null && t.isAlive()) {
                t.interrupt();
            }

            // Forzar cierre del proceso y su árbol de hijos
            killProcessTree(null);

            // Asegurar que el registry quede vacío (esperar un máximo)
            com.orquestador.servicio.ProcessRegistry registry = com.orquestador.servicio.ProcessRegistry.getInstance();
            registry.killAll();
            registry.waitForEmpty(10_000);

            // Marcar proyecto como cancelado si aplica, pero NO cambiar el flag `ejecutando` aquí.
            // El controlador de UI será responsable de marcar `ejecutando=false` cuando verifique
            // que todos los procesos del registry han finalizado.
            synchronized (this) {
                if (proyectoActual != null) {
                    proyectoActual.setEstado(EstadoEjecucion.CANCELADO);
                }
                proyectoActual = null;
            }
        } catch (Exception e) {
            // No interrumpir la ejecución por errores al intentar detener
        }
    }

    /**
     * Intenta finalizar el proceso actual y todo su árbol de procesos (Windows: taskkill fallback).
     */
    private void killProcessTree(Consumer<String> logCallback) {
        try {
            if (procesoActual == null) return;

            ProcessHandle ph = procesoActual.toHandle();
            long pid = ph.pid();

            // Primero intentar destruir los descendientes
            ph.descendants().forEach(h -> {
                try { h.destroyForcibly(); } catch (Exception ex) { }
            });

            // Intentar destruir el proceso principal
            try { ph.destroyForcibly(); } catch (Exception ex) { }

            // Fallback: usar taskkill por si queda algo
            try {
                new ProcessBuilder("taskkill", "/PID", String.valueOf(pid), "/F", "/T").start().waitFor();
            } catch (Exception ex) { }

            if (logCallback != null) logCallback.accept(" Procesos terminados (PID " + pid + ")");
        } catch (Exception e) {
            if (logCallback != null) logCallback.accept(" Error al forzar cierre de procesos: " + e.getMessage());
        }
    }

    public boolean isEjecutando() {
        return ejecutando;
    }

    public void setEjecutando(boolean ejecutando) {
        this.ejecutando = ejecutando;
    }

    /**
     * Busca el reporte más reciente de la última ejecución dentro del árbol
     * del proyecto: .txt/.log bajo surefire-reports y .html bajo carpetas de
     * reportes (ExtentReports, reporte, reports). Devuelve el más reciente
     * por fecha de modificación, o null si no hay ninguno.
     */
    public static File buscarUltimoReporte(ProyectoAutomatizacion proyecto) {
        if (proyecto == null || proyecto.getRuta() == null || proyecto.getRuta().trim().isEmpty()) {
            return null;
        }
        List<File> candidatos = new ArrayList<>();
        recolectarReportes(new File(proyecto.getRuta()), candidatos, 0);
        return candidatos.stream()
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null);
    }

    private static void recolectarReportes(File dir, List<File> out, int profundidad) {
        if (dir == null || !dir.isDirectory() || profundidad > 12) return;
        File[] hijos = dir.listFiles();
        if (hijos == null) return;
        for (File f : hijos) {
            if (f.isFile()) {
                String n = f.getName().toLowerCase();
                File padre = f.getParentFile();
                String carpeta = padre.getName().toLowerCase();
                File abuelo = padre.getParentFile();
                String carpetaAbuelo = abuelo != null ? abuelo.getName().toLowerCase() : "";
                boolean surefire = carpeta.equals("surefire-reports")
                        && (n.endsWith(".txt") || n.endsWith(".log"));
                boolean htmlReporte = n.endsWith(".html")
                        && (carpeta.contains("report") || carpetaAbuelo.contains("report"));
                if (surefire || htmlReporte) {
                    out.add(f);
                }
            } else {
                String n = f.getName().toLowerCase();
                if (n.equals(".git") || n.equals("node_modules")
                        || n.equals("historial evidencias")) continue;
                recolectarReportes(f, out, profundidad + 1);
            }
        }
    }
}