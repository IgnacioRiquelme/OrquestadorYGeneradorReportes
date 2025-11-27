package com.orquestador.servicio;

import com.orquestador.modelo.ProyectoAutomatizacion;
import com.orquestador.modelo.ProyectoAutomatizacion.EstadoEjecucion;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * Servicio para ejecutar proyectos de automatizacin
 */
public class EjecutorAutomatizaciones {

    private boolean ejecutando = false;
    private Process procesoActual;

    /**
     * Ejecuta un proyecto de automatizacin en una ventana CMD maximizada
     */
    public void ejecutarProyecto(ProyectoAutomatizacion proyecto,
                                  Consumer<String> logCallback,
                                  Runnable onFinish) {

        proyecto.setEstado(EstadoEjecucion.EJECUTANDO);
        proyecto.setUltimaEjecucion(LocalDateTime.now());

        long inicio = System.currentTimeMillis();

        new Thread(() -> {
            try {
                // Limpiar procesos antes de ejecutar
                limpiarProcesos(logCallback);

                // PASO 1: Limpiar imágenes de la carpeta de capturas antes de ejecutar
                limpiarImagenesAnteriores(proyecto, logCallback);

                // Crear script temporal para ejecutar
                File scriptTemp = crearScriptEjecucion(proyecto);
                
                // Crear un archivo marker para detectar cuando termina
                File markerFile = new File(scriptTemp.getParentFile(), scriptTemp.getName() + ".done");
                if (markerFile.exists()) markerFile.delete();

                // Ejecutar en CMD maximizado - CAMBIO CRTICO: usar /wait para que espere
                ProcessBuilder pb = new ProcessBuilder(
                    "cmd.exe", "/c", "start", "\"" + proyecto.getNombre() + "\"", "/wait", "/max", 
                    "cmd.exe", "/c", scriptTemp.getAbsolutePath() + " & echo DONE > \"" + markerFile.getAbsolutePath() + "\""
                );
                pb.directory(new File(proyecto.getRuta()));

                logCallback.accept(" Ejecutando: " + proyecto.getNombre());
                logCallback.accept(" Ruta: " + proyecto.getRuta());

                procesoActual = pb.start();
                
                // Esperar a que el proceso termine
                int exitCode = procesoActual.waitFor();
                
                // Esperar tambin a que aparezca el archivo marker (confirma que el script termin)
                int maxWait = 300; // 5 minutos mximo
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

                // Calcular duracin
                long fin = System.currentTimeMillis();
                int duracion = (int) ((fin - inicio) / 1000);
                proyecto.setDuracionSegundos(duracion);

                // Actualizar estado segn resultado
                if (exitCode == 0) {
                    proyecto.setEstado(EstadoEjecucion.EXITOSO);
                    logCallback.accept(" " + proyecto.getNombre() + " - EXITOSO (" + duracion + "s)");
                } else {
                    proyecto.setEstado(EstadoEjecucion.FALLIDO);
                    proyecto.setMensajeError("Exit code: " + exitCode);
                    logCallback.accept(" " + proyecto.getNombre() + " - FALLIDO (" + duracion + "s)");
                }

                // GENERAR LOG DESPUÉS DE LA EJECUCIÓN con los reportes de Maven/Newman
                generarLogDeReportes(proyecto, exitCode, duracion);

                // Limpiar despus de ejecutar
                scriptTemp.delete();
                markerFile.delete();
                limpiarProcesos(logCallback);

            } catch (InterruptedException e) {
                proyecto.setEstado(EstadoEjecucion.CANCELADO);
                logCallback.accept(" " + proyecto.getNombre() + " - CANCELADO");
            } catch (Exception e) {
                proyecto.setEstado(EstadoEjecucion.FALLIDO);
                proyecto.setMensajeError(e.getMessage());
                logCallback.accept(" Error: " + e.getMessage());
            } finally {
                procesoActual = null;
                if (onFinish != null) {
                    onFinish.run();
                }
            }
        }).start();
    }

    /**
     * Crea un script .bat temporal para la ejecucin
     */
    private File crearScriptEjecucion(ProyectoAutomatizacion proyecto) throws IOException {
        File scriptTemp = File.createTempFile("exec_", ".bat");
        File exitCodeFile = new File(scriptTemp.getParentFile(), scriptTemp.getName() + ".exitcode");
        File logFile = new File(scriptTemp.getParentFile(), scriptTemp.getName().replace(".bat", ".log"));
        
        // Guardar ruta del log en el proyecto
        proyecto.setRutaLogEjecucion(logFile.getAbsolutePath());

        StringBuilder script = new StringBuilder();
        script.append("@echo off\n");
        script.append("chcp 65001 >nul\n");
        script.append("cd /d \"").append(proyecto.getRuta()).append("\"\n");
        script.append("echo.\n");
        script.append("echo ========================================\n");
        script.append("echo   ").append(proyecto.getNombre()).append("\n");
        script.append("echo   Area: ").append(proyecto.getArea()).append("\n");
        script.append("echo   VPN: ").append(proyecto.getTipoVPN().getDescripcion()).append("\n");
        script.append("echo ========================================\n");
        script.append("echo.\n");
        
        // Comando según tipo de ejecución - EJECUCIÓN COMPLETAMENTE NORMAL (cero cambios)
        if (proyecto.getTipoEjecucion() == ProyectoAutomatizacion.TipoEjecucion.MAVEN) {
            script.append("echo Ejecutando mvn test...\n");
            script.append("echo.\n");
            script.append("call mvn test\n");
        } else if (proyecto.getTipoEjecucion() == ProyectoAutomatizacion.TipoEjecucion.NEWMAN) {
            script.append("echo Ejecutando newman...\n");
            script.append("echo.\n");
            script.append("call newman run collection.json\n");
        } else {
            script.append("echo Ejecutando mvn test + newman...\n");
            script.append("echo.\n");
            script.append("call mvn test\n");
            script.append("echo.\n");
            script.append("echo Ejecutando newman...\n");
            script.append("call newman run collection.json\n");
        }
        
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

        java.nio.file.Files.write(scriptTemp.toPath(), script.toString().getBytes("UTF-8"));
        return scriptTemp;
    }

    /**
     * Limpia procesos de CMD y Chrome antes/despus de la ejecucin
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

            // Para Maven: buscar los reportes de Surefire
            if (proyecto.getTipoEjecucion() == ProyectoAutomatizacion.TipoEjecucion.MAVEN || 
                proyecto.getTipoEjecucion() == ProyectoAutomatizacion.TipoEjecucion.MAVEN_NEWMAN) {
                
                File surefireReportsDir = new File(proyecto.getRuta() + File.separator + "test-output" + File.separator + "surefire-reports");
                if (surefireReportsDir.exists()) {
                    logContent.append("REPORTES DE MAVEN (Surefire):\n");
                    logContent.append("========================================\n");
                    
                    // Buscar archivos .txt en surefire-reports
                    File[] reportFiles = surefireReportsDir.listFiles((dir, name) -> name.endsWith(".txt"));
                    if (reportFiles != null && reportFiles.length > 0) {
                        for (File reportFile : reportFiles) {
                            logContent.append("\n--- ").append(reportFile.getName()).append(" ---\n");
                            try {
                                String content = new String(java.nio.file.Files.readAllBytes(reportFile.toPath()), "UTF-8");
                                logContent.append(content).append("\n");
                            } catch (Exception e) {
                                logContent.append("Error leyendo reporte: ").append(e.getMessage()).append("\n");
                            }
                        }
                    } else {
                        logContent.append("No se encontraron reportes .txt\n");
                    }
                    logContent.append("\n");
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
     * Limpia todas las imágenes de la carpeta de capturas antes de ejecutar la automatización
     * Esto asegura que solo estén las imágenes de la ejecución actual
     */
    private void limpiarImagenesAnteriores(ProyectoAutomatizacion proyecto, Consumer<String> logCallback) {
        try {
            // Usar la ruta de imágenes configurada si existe, sino usar ruta por defecto
            String rutaCapturas = proyecto.getRutaImagenes();

            if (rutaCapturas == null || rutaCapturas.trim().isEmpty()) {
                // Determinar la carpeta por defecto según el tipo de ejecución
                if (proyecto.getTipoEjecucion() == ProyectoAutomatizacion.TipoEjecucion.MAVEN ||
                    proyecto.getTipoEjecucion() == ProyectoAutomatizacion.TipoEjecucion.MAVEN_NEWMAN) {
                    // Para Maven: test-output/capturaPantalla
                    rutaCapturas = proyecto.getRuta() + File.separator + "test-output" + File.separator + "capturaPantalla";
                } else if (proyecto.getTipoEjecucion() == ProyectoAutomatizacion.TipoEjecucion.NEWMAN) {
                    // Para Newman: capturas (asumido, ajustar según necesidad)
                    rutaCapturas = proyecto.getRuta() + File.separator + "capturas";
                }
            }

            if (rutaCapturas == null || rutaCapturas.trim().isEmpty()) {
                logCallback.accept("  ⚠ No se pudo determinar carpeta de capturas");
                return;
            }
            
            File carpetaCapturas = new File(rutaCapturas);
            
            // Si no existe la carpeta, no hay nada que limpiar
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
                logCallback.accept("  ℹ No hay imágenes anteriores para limpiar");
                return;
            }
            
            // Eliminar todas las imágenes
            int eliminadas = 0;
            for (File imagen : imagenes) {
                if (imagen.delete()) {
                    eliminadas++;
                }
            }
            
            logCallback.accept("  🗑️ Limpiadas " + eliminadas + " imágenes anteriores de: " + carpetaCapturas.getName());
            
        } catch (Exception e) {
            logCallback.accept("  ⚠ Error al limpiar imágenes anteriores: " + e.getMessage());
        }
    }
    
    /**
     * Detiene la ejecucin actual
     */
    public void detener() {
        if (procesoActual != null && procesoActual.isAlive()) {
            procesoActual.destroy();
        }
    }

    public boolean isEjecutando() {
        return ejecutando;
    }

    public void setEjecutando(boolean ejecutando) {
        this.ejecutando = ejecutando;
    }
}