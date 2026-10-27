package com.orquestador.util;

import com.google.gson.*;
import com.orquestador.modelo.Credenciales;
import com.orquestador.modelo.ProyectoAutomatizacion;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Gestor para cargar y guardar credenciales de los proyectos especiales
 * Actualmente maneja: 15 - Contactenos BCI Seguros, 16 - Contactenos Zenit Seguros, 17 - Contactenos Corredores Generales
 */
public class GestorCredenciales {
    
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    /**
     * Obtiene la ruta del archivo Principal.json para cada proyecto.
     * Primero intenta la ruta estándar (src/main/resources/testdata); si no
     * existe —proyectos espejo con profundidades de carpeta variables— busca
     * recursivamente desde la ruta del proyecto y sus directorios padre.
     */
    private static String obtenerRutaPrincipalJson(ProyectoAutomatizacion proyecto) {
        String ruta = proyecto.getRuta();
        String rutaRelativa = "src/main/resources/testdata/Principal.json";

        // Normalizar separadores
        ruta = ruta.replace('/', '\\');
        if (!ruta.endsWith("\\")) {
            ruta += "\\";
        }

        String pathFull = ruta + rutaRelativa.replace('/', '\\');
        if (new File(pathFull).exists()) {
            return pathFull;
        }

        // Fallback: búsqueda recursiva SOLO dentro del árbol del proyecto.
        // Las automatizaciones resuelven el JSON como ./src/main/resources/testdata/
        // relativo a su directorio de trabajo (= proyecto.getRuta()); subir a
        // directorios padre encontraría archivos de proyectos hermanos.
        File encontrado = buscarPrincipalJsonRecursivo(new File(ruta));
        if (encontrado != null) {
            return encontrado.getAbsolutePath();
        }

        // Último recurso: devolver la ruta estándar (para mensajes de error)
        return pathFull;
    }

    /**
     * Búsqueda recursiva de Principal.json. Prefiere rutas bajo
     * src/main/resources/testdata e ignora las copias compiladas en target/
     * y carpetas de dependencias (node_modules, .git).
     */
    private static File buscarPrincipalJsonRecursivo(File dir) {
        if (dir == null || !dir.isDirectory()) return null;
        File[] hijos = dir.listFiles();
        if (hijos == null) return null;

        File candidato = null;
        for (File f : hijos) {
            if (f.isFile() && f.getName().equalsIgnoreCase("Principal.json")) {
                String path = f.getAbsolutePath().replace('/', '\\').toLowerCase();
                if (path.contains("src\\main\\resources\\testdata")) return f;
                if (!path.contains("\\target\\") && candidato == null) candidato = f;
            } else if (f.isDirectory()) {
                String nombreDir = f.getName().toLowerCase();
                if (nombreDir.equals("target") || nombreDir.equals("node_modules")
                        || nombreDir.equals(".git")) continue;
                File r = buscarPrincipalJsonRecursivo(f);
                if (r != null) {
                    String path = r.getAbsolutePath().replace('/', '\\').toLowerCase();
                    if (path.contains("src\\main\\resources\\testdata")) return r;
                    if (candidato == null) candidato = r;
                }
            }
        }
        return candidato;
    }
    
    /**
     * Verifica si un proyecto es uno de los 4 especiales
     */
    public static boolean esProyectoEspecial(ProyectoAutomatizacion proyecto) {
        if (proyecto == null) return false;
        String nombre = proyecto.getNombre().toLowerCase();
        // Excluir explícitamente proyectos que contienen 'vida'
        if (nombre.contains("vida")) return false;

        // Detectar proyectos especiales por palabras clave de los proyectos que todavía son especiales
        boolean esContactenos = nombre.contains("contactenos") || nombre.contains("contáctenos");
        boolean esTipoValido = nombre.contains("bci") || nombre.contains("zenit") || nombre.contains("corredores");
        return esContactenos && esTipoValido;
    }
    
    /**
     * Carga credenciales desde el archivo Principal.json del proyecto
     */
    public static Credenciales cargarCredenciales(ProyectoAutomatizacion proyecto) throws IOException {
        if (!esProyectoEspecial(proyecto)) {
            return new Credenciales();
        }
        
        String ruta = obtenerRutaPrincipalJson(proyecto);
        File archivo = new File(ruta);
        
        if (!archivo.exists()) {
            return new Credenciales();
        }
        
        try (FileReader reader = new FileReader(archivo)) {
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            Credenciales cred = new Credenciales();
            
            // Extraer sólo usuario/contraseña (estandarizado)
            // Preferir estructura anidada utilizada por algunos proyectos: "datos1": { "user": ..., "pasword": ... }
            if (jsonObject.has("datos1") && jsonObject.get("datos1").isJsonObject()) {
                JsonObject datos1 = jsonObject.getAsJsonObject("datos1");
                if (datos1.has("user")) {
                    cred.setUser(datos1.get("user").getAsString());
                }
                if (datos1.has("pasword")) {
                    cred.setPasword(datos1.get("pasword").getAsString());
                }
                if (datos1.has("nAtencionBci")) {
                    cred.setNAtencionBci(datos1.get("nAtencionBci").getAsString());
                }
            } else {
                if (jsonObject.has("user")) {
                    cred.setUser(jsonObject.get("user").getAsString());
                }
                if (jsonObject.has("pasword")) {
                    cred.setPasword(jsonObject.get("pasword").getAsString());
                }
            }

            // Cuentas alternativas
            // Cuentas alternativas: también soportar cuando están dentro de datos2 (estructura de Corredores)
            if (jsonObject.has("user2")) {
                cred.setUser2(jsonObject.get("user2").getAsString());
            }
            if (jsonObject.has("pasword2")) {
                cred.setPasword2(jsonObject.get("pasword2").getAsString());
            }
            if (jsonObject.has("rut")) {
                cred.setRut(jsonObject.get("rut").getAsString());
            }
            if (jsonObject.has("datos2") && jsonObject.get("datos2").isJsonObject()) {
                JsonObject datos2 = jsonObject.getAsJsonObject("datos2");
                if (datos2.has("user2")) {
                    cred.setUser2(datos2.get("user2").getAsString());
                }
                if (datos2.has("pasword2")) {
                    cred.setPasword2(datos2.get("pasword2").getAsString());
                }
                if (datos2.has("rut")) {
                    cred.setRut(datos2.get("rut").getAsString());
                }
            }
            
            return cred;
        } catch (Exception e) {
            System.err.println("Error cargando credenciales: " + e.getMessage());
            return new Credenciales();
        }
    }
    
    /**
     * Guarda credenciales en el archivo Principal.json del proyecto
     */
    public static void guardarCredenciales(ProyectoAutomatizacion proyecto, Credenciales cred) throws IOException {
        if (!esProyectoEspecial(proyecto)) {
            return;
        }

        String ruta = obtenerRutaPrincipalJson(proyecto);
        File archivo = new File(ruta);

        // Crear directorios si no existen
        if (!archivo.exists()) {
            archivo.getParentFile().mkdirs();
        }

        JsonObject jsonObject;

        // Si el archivo existe, leerlo; si no, crear un objeto vacío
        if (archivo.exists()) {
            try (FileReader reader = new FileReader(archivo)) {
                jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                // Si hay error leyendo, crear objeto vacío
                jsonObject = new JsonObject();
            }
        } else {
            jsonObject = new JsonObject();
        }

        // Guardar sólo campos de usuario/contraseña.
        // Para proyectos tipo BCI o Zenit preferimos siempre usar el objeto "datos1" (crear si es necesario),
        // así replicamos la lógica del proyecto 15 para el proyecto 16.
        String nombreProyecto = proyecto.getNombre() != null ? proyecto.getNombre().toLowerCase() : "";
        boolean esCorredores = nombreProyecto.contains("corredores");
        boolean usarDatos1 = nombreProyecto.contains("bci") || nombreProyecto.contains("zenit") || esCorredores;

        if (usarDatos1) {
            // Solo actualizar campos existentes dentro de datos1. No crear campos nuevos.
            if (jsonObject.has("datos1") && jsonObject.get("datos1").isJsonObject()) {
                JsonObject datos1 = jsonObject.getAsJsonObject("datos1");
                if (esCorredores) {
                    // Para corredores: datos1.user = RUT, datos1.pasword = contraseña
                    if (cred.getUser() != null && datos1.has("user")) {
                        datos1.addProperty("user", cred.getUser());
                    }
                    if (cred.getPasword() != null && datos1.has("pasword")) {
                        datos1.addProperty("pasword", cred.getPasword());
                    }
                } else {
                    // Para BCI/Zenit
                    if (cred.getUser() != null && datos1.has("user")) {
                        datos1.addProperty("user", cred.getUser());
                    }
                    if (cred.getPasword() != null && datos1.has("pasword")) {
                        datos1.addProperty("pasword", cred.getPasword());
                    }
                }
                // dejar intactos otros campos dentro de datos1
            } else {
                // datos1 no existe: no crear ni escribir nada para respetar la regla de "solo actualizar"
            }
        } else {
            // Mantener compatibilidad: actualizar en la raíz solo si las propiedades ya existen
            if (cred.getUser() != null && jsonObject.has("user")) {
                jsonObject.addProperty("user", cred.getUser());
            }
            if (cred.getPasword() != null && jsonObject.has("pasword")) {
                jsonObject.addProperty("pasword", cred.getPasword());
            }
        }

        // Cuentas alternativas: para proyectos 'corredores' actualizamos dentro de datos2 si existe
        String nombreProyectoLower = proyecto.getNombre() != null ? proyecto.getNombre().toLowerCase() : "";
        if (esCorredores) {
            if (jsonObject.has("datos2") && jsonObject.get("datos2").isJsonObject()) {
                JsonObject datos2 = jsonObject.getAsJsonObject("datos2");
                // Para corredores: datos2.user2 = Usuario, datos2.pasword2 = contraseña
                if (cred.getUser2() != null && datos2.has("user2")) datos2.addProperty("user2", cred.getUser2());
                if (cred.getPasword2() != null && datos2.has("pasword2")) datos2.addProperty("pasword2", cred.getPasword2());
            } else {
                // Si no existe datos2, actualizar en la raíz
                if (cred.getUser2() != null && jsonObject.has("user2")) jsonObject.addProperty("user2", cred.getUser2());
                if (cred.getPasword2() != null && jsonObject.has("pasword2")) jsonObject.addProperty("pasword2", cred.getPasword2());
            }
        } else {
            // Mantener comportamiento anterior para otros proyectos: actualizar en la raíz solo si las propiedades ya existen
            String[] camposEsperados = obtenerCamposEsperados(proyecto);
            boolean esperaUser2 = Arrays.asList(camposEsperados).contains("user2");
            if (esperaUser2) {
                if (cred.getUser2() != null && jsonObject.has("user2")) jsonObject.addProperty("user2", cred.getUser2());
                if (cred.getPasword2() != null && jsonObject.has("pasword2")) jsonObject.addProperty("pasword2", cred.getPasword2());
            }
        }

        // Escribir JSON actualizado
        try (FileWriter writer = new FileWriter(archivo)) {
            writer.write(gson.toJson(jsonObject));
            writer.flush();
        } catch (Exception e) {
            System.err.println("Error guardando credenciales: " + e.getMessage());
            throw new IOException("No se pudieron guardar las credenciales", e);
        }
    }
    
    /**
     * Obtiene una descripción de qué campos espera cada proyecto
     */
    public static String[] obtenerCamposEsperados(ProyectoAutomatizacion proyecto) {
        String nombre = proyecto.getNombre().toLowerCase();
        
        if (nombre.contains("zenit")) {
            return new String[]{"user", "pasword"};
        } else if (nombre.contains("corredores")) {
            // No usar cuentas alternativas por defecto: devolver user/pasword
            return new String[]{"user", "pasword"};
        } else {
            // BCI por defecto
            return new String[]{"user", "pasword"};
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Credenciales genéricas (cualquier proyecto con login)
    // ──────────────────────────────────────────────────────────────────────

    /** Campo de credencial editable (JSON: ruta tipo "datos1.user"; CSV: índice de columna). */
    public static class CampoCredencial {
        public final String clave;
        public final String etiqueta;
        public String valor;
        public final boolean esPassword;

        public CampoCredencial(String clave, String etiqueta, String valor, boolean esPassword) {
            this.clave = clave;
            this.etiqueta = etiqueta;
            this.valor = valor;
            this.esPassword = esPassword;
        }
    }

    // Claves que representan credenciales de login
    private static final Pattern CLAVE_CRED = Pattern.compile("(?i).*(user|usuario|pasword|password|contrase[ñn]|clave).*");
    private static final Pattern CLAVE_RUT  = Pattern.compile("(?i)rut.*");
    private static final Pattern CLAVE_PASS = Pattern.compile("(?i).*(pasword|contrasen|clave|password).*");

    /**
     * Ubica el archivo de credenciales del proyecto buscando SOLO dentro de
     * su árbol (las automatizaciones las cargan relativas a user.dir). Se
     * evalúan todos los candidatos y se devuelve el de mayor prioridad que
     * realmente contenga campos de credencial:
     *  Principal.json, CSVBCISeguros.csv, config.json, credenciales.csv,
     *  Unitario.xlsx/datapool.xlsx, cualquier .json bajo testdata y
     *  cualquier .csv/.xlsx bajo una carpeta "data".
     */
    public static File encontrarArchivoCredenciales(ProyectoAutomatizacion proyecto) {
        if (proyecto == null || proyecto.getRuta() == null || proyecto.getRuta().trim().isEmpty()) {
            return null;
        }
        List<File> candidatos = new ArrayList<>();
        recolectarArchivosCredenciales(new File(proyecto.getRuta()), candidatos);
        candidatos.sort(java.util.Comparator.comparingInt(GestorCredenciales::prioridadArchivo));
        for (File f : candidatos) {
            try {
                if (!leerCamposCredenciales(f).isEmpty()) {
                    return f;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** Recolecta archivos candidatos a contener credenciales dentro del proyecto. */
    private static void recolectarArchivosCredenciales(File dir, List<File> out) {
        if (dir == null || !dir.isDirectory()) return;
        File[] hijos = dir.listFiles();
        if (hijos == null) return;
        for (File f : hijos) {
            if (f.isFile()) {
                String n = f.getName().toLowerCase();
                if (n.startsWith("~$") || n.startsWith(".")) continue;
                String parent = f.getParentFile().getName().toLowerCase();
                if (n.equals("principal.json") || n.equals("csvbciseguros.csv")
                        || n.equals("config.json") || n.equals("credenciales.csv")
                        || n.equals("unitario.xlsx") || n.equals("datapool.xlsx")
                        || (n.endsWith(".json") && parent.equals("testdata"))
                        || (n.endsWith(".csv") && parent.equals("data"))
                        || (n.endsWith(".xlsx") && parent.equals("data"))) {
                    out.add(f);
                }
            } else if (f.isDirectory()) {
                String n = f.getName().toLowerCase();
                if (n.equals("target") || n.equals("node_modules") || n.equals(".git")
                        || n.equals(".idea") || n.equals(".vscode") || n.startsWith(".venv")) continue;
                recolectarArchivosCredenciales(f, out);
            }
        }
    }

    /** Menor número = mayor prioridad al elegir el archivo de credenciales. */
    private static int prioridadArchivo(File f) {
        String p = f.getAbsolutePath().replace('/', '\\').toLowerCase();
        String n = f.getName().toLowerCase();
        if (n.equals("principal.json") && p.contains("src\\main\\resources\\testdata")) return 0;
        if (n.equals("principal.json")) return 1;
        if (n.equals("csvbciseguros.csv") && p.contains("\\data\\")) return 2;
        if (n.equals("csvbciseguros.csv")) return 3;
        if (n.equals("unitario.xlsx") || n.equals("datapool.xlsx")) return 4;
        if (n.equals("config.json")) return 5;
        if (n.endsWith(".json")) return 6;
        if (n.endsWith(".xlsx")) return 7;
        return 8;
    }

    /**
     * Indica si el proyecto tiene un archivo de credenciales con campos
     * de login editables (user/pasword/clave/rut/usuario).
     */
    public static boolean proyectoTieneCredenciales(ProyectoAutomatizacion proyecto) {
        File archivo = encontrarArchivoCredenciales(proyecto);
        if (archivo == null) return false;
        try {
            return !leerCamposCredenciales(archivo).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Lee los campos de credenciales editables del archivo del proyecto.
     * JSON: claves que contengan user/usuario/pasword/contrasen/clave, más
     * claves rut* dentro de objetos que ya tengan alguna credencial.
     * CSV: columnas del encabezado con esos mismos patrones (fila 2 editable).
     */
    public static List<CampoCredencial> leerCamposCredenciales(File archivo) throws IOException {
        List<CampoCredencial> campos = new ArrayList<>();
        String nombre = archivo.getName().toLowerCase();
        if (nombre.endsWith(".json")) {
            String texto = new String(Files.readAllBytes(archivo.toPath()), StandardCharsets.UTF_8);
            int fin = finPrimerObjetoJson(texto);
            String cuerpo = fin > 0 ? texto.substring(texto.indexOf('{'), fin) : texto;
            JsonObject json = JsonParser.parseString(cuerpo).getAsJsonObject();
            recolectarCamposJson(json, "", campos);
        } else if (nombre.endsWith(".xlsx")) {
            try (java.io.FileInputStream in = new java.io.FileInputStream(archivo);
                 org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(in)) {
                org.apache.poi.ss.usermodel.Sheet sh = wb.getSheetAt(0);
                org.apache.poi.ss.usermodel.Row header = sh != null ? sh.getRow(0) : null;
                org.apache.poi.ss.usermodel.Row fila = sh != null ? sh.getRow(1) : null;
                if (header == null || fila == null) return campos;
                int cols = header.getLastCellNum();
                boolean hayCred = false;
                for (int i = 0; i < cols; i++) {
                    if (CLAVE_CRED.matcher(textoCelda(header.getCell(i))).matches()) { hayCred = true; break; }
                }
                if (!hayCred) return campos;
                for (int i = 0; i < cols; i++) {
                    String h = textoCelda(header.getCell(i));
                    if (CLAVE_CRED.matcher(h).matches() || CLAVE_RUT.matcher(h).matches()) {
                        campos.add(new CampoCredencial("0:" + i, h, textoCelda(fila.getCell(i)),
                            CLAVE_PASS.matcher(h).matches()));
                    }
                }
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Error leyendo xlsx", e);
            }
        } else {
            List<String> lineas = Files.readAllLines(archivo.toPath());
            if (lineas.size() >= 2) {
                String[] headers = lineas.get(0).split(",", -1);
                String[] fila = lineas.get(1).split(",", -1);
                // El CSV cuenta como credenciales solo si tiene al menos una
                // columna user/usuario/pasword/contrasenha/clave; las columnas
                // rut* se agregan solo si ya existe una credencial real (un
                // "rut" solo suele ser dato del caso, no login).
                boolean hayCred = false;
                for (String h : headers) {
                    if (CLAVE_CRED.matcher(h.trim()).matches()) { hayCred = true; break; }
                }
                if (hayCred) {
                    for (int i = 0; i < headers.length; i++) {
                        String h = headers[i].trim();
                        if (CLAVE_CRED.matcher(h).matches() || CLAVE_RUT.matcher(h).matches()) {
                            String valor = i < fila.length ? fila[i].trim() : "";
                            campos.add(new CampoCredencial(String.valueOf(i), h, valor,
                                CLAVE_PASS.matcher(h).matches()));
                        }
                    }
                }
            }
        }
        return campos;
    }

    private static void recolectarCamposJson(JsonObject obj, String path, List<CampoCredencial> out) {
        boolean objTieneCred = obj.keySet().stream().anyMatch(k -> CLAVE_CRED.matcher(k).matches());
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            String key = e.getKey();
            String ruta = path.isEmpty() ? key : path + "." + key;
            if (e.getValue().isJsonObject()) {
                recolectarCamposJson(e.getValue().getAsJsonObject(), ruta, out);
            } else if (CLAVE_CRED.matcher(key).matches()
                    || (objTieneCred && CLAVE_RUT.matcher(key).matches())) {
                out.add(new CampoCredencial(ruta, etiquetaCampo(key, path),
                    e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : "",
                    CLAVE_PASS.matcher(key).matches()));
            }
        }
    }

    private static String etiquetaCampo(String key, String path) {
        String friendly;
        String k = key.toLowerCase();
        if (k.equals("user") || k.equals("usuario")) friendly = "Usuario";
        else if (k.equals("user2") || k.equals("usuario2")) friendly = "Usuario 2";
        else if (k.equals("pasword") || k.equals("contrasena") || k.equals("clave")) friendly = "Contraseña";
        else if (k.equals("pasword2") || k.equals("contrasena2") || k.equals("clave2")) friendly = "Contraseña 2";
        else if (k.startsWith("rut")) friendly = "RUT";
        else friendly = key;
        return path.isEmpty() ? friendly : friendly + " (" + path + ")";
    }

    /**
     * Guarda los campos editados: JSON por ruta (datos1.user), CSV por índice
     * de columna en la fila 2.
     */
    public static void guardarCamposCredenciales(File archivo, List<CampoCredencial> campos) throws IOException {
        String nombre = archivo.getName().toLowerCase();
        if (nombre.endsWith(".json")) {
            // Leer solo el primer objeto JSON y preservar cualquier texto
            // posterior (algunos testdata traen un segundo documento anexado).
            String texto = new String(Files.readAllBytes(archivo.toPath()), StandardCharsets.UTF_8);
            int fin = finPrimerObjetoJson(texto);
            JsonObject json;
            String cola = "";
            if (fin > 0) {
                json = JsonParser.parseString(texto.substring(texto.indexOf('{'), fin)).getAsJsonObject();
                cola = texto.substring(fin);
            } else {
                json = JsonParser.parseString(texto).getAsJsonObject();
            }
            for (CampoCredencial c : campos) {
                String[] partes = c.clave.split("\\.");
                JsonObject padre = json;
                boolean ok = true;
                for (int i = 0; i < partes.length - 1; i++) {
                    if (padre.has(partes[i]) && padre.get(partes[i]).isJsonObject()) {
                        padre = padre.getAsJsonObject(partes[i]);
                    } else {
                        ok = false;
                        break;
                    }
                }
                if (ok && padre.has(partes[partes.length - 1])) {
                    padre.addProperty(partes[partes.length - 1], c.valor);
                }
            }
            Files.write(archivo.toPath(), (gson.toJson(json) + cola).getBytes(StandardCharsets.UTF_8));
        } else if (nombre.endsWith(".xlsx")) {
            try (java.io.FileInputStream in = new java.io.FileInputStream(archivo);
                 org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(in)) {
                for (CampoCredencial c : campos) {
                    String[] partes = c.clave.split(":");
                    int hoja = Integer.parseInt(partes[0]);
                    int col = Integer.parseInt(partes[1]);
                    org.apache.poi.ss.usermodel.Sheet sh = wb.getSheetAt(hoja);
                    org.apache.poi.ss.usermodel.Row fila = sh.getRow(1);
                    if (fila == null) fila = sh.createRow(1);
                    org.apache.poi.ss.usermodel.Cell cell = fila.getCell(col);
                    if (cell == null) cell = fila.createCell(col);
                    // Preservar tipo numérico cuando el valor sigue siendo número
                    if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                            && c.valor != null && c.valor.matches("-?\\d+(\\.\\d+)?")) {
                        cell.setCellValue(Double.parseDouble(c.valor));
                    } else {
                        cell.setCellValue(c.valor == null ? "" : c.valor);
                    }
                }
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(archivo)) {
                    wb.write(out);
                }
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Error guardando xlsx", e);
            }
        } else {
            List<String> lineas = Files.readAllLines(archivo.toPath());
            if (lineas.size() < 2) return;
            String[] fila = lineas.get(1).split(",", -1);
            for (CampoCredencial c : campos) {
                int idx = Integer.parseInt(c.clave);
                if (idx < fila.length) {
                    fila[idx] = c.valor;
                }
            }
            lineas.set(1, String.join(",", fila));
            Files.write(archivo.toPath(), lineas, StandardCharsets.UTF_8);
        }
    }

    /** Índice justo después del primer objeto JSON balanceado, o -1. */
    private static int finPrimerObjetoJson(String texto) {
        int inicio = texto.indexOf('{');
        if (inicio < 0) return -1;
        int profundidad = 0;
        boolean enStr = false, esc = false;
        for (int i = inicio; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (enStr) {
                if (esc) esc = false;
                else if (c == '\\') esc = true;
                else if (c == '"') enStr = false;
            } else {
                if (c == '"') enStr = true;
                else if (c == '{') profundidad++;
                else if (c == '}' && --profundidad == 0) return i + 1;
            }
        }
        return -1;
    }

    /** Texto de una celda Excel (números enteros sin decimales). */
    private static String textoCelda(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case NUMERIC:
                double d = cell.getNumericCellValue();
                return d == Math.floor(d) && !Double.isInfinite(d)
                        ? String.valueOf((long) d) : String.valueOf(d);
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA: return cell.getCellFormula();
            default: return cell.getStringCellValue().trim();
        }
    }
}
