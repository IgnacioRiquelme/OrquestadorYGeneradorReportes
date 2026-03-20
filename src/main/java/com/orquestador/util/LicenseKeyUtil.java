package com.orquestador.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Utilidad para generar y validar claves de licencia offline firmadas con HMAC-SHA256.
 *
 * Formato de clave:
 *   BASE64URL(payload JSON) . BASE64URL(firma HMAC-SHA256)
 *
 * Payload JSON:
 *   { "id": "...", "exp": "2026-03-01", "n": "Nombre", "e": "Empresa",
 *     "r": "RUT", "t": "Teléfono", "m": "Email" }
 *
 * El secreto nunca viaja por la red — está embebido en el código de ambas apps.
 * Proteger con ProGuard en producción.
 */
public final class LicenseKeyUtil {

    // ⚠ SECRETO COMPARTIDO entre la app distribuida y la app maestra.
    //    Cambiar antes de distribuir a producción. Proteger con ProGuard.
    private static final String HMAC_SECRET = "BCI$0rq_S3cr3t_K3y_2026#!";
    private static final String ALGO = "HmacSHA256";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private LicenseKeyUtil() {}

    // ─── Generación de clave ───────────────────────────────────────────────────

    /**
     * Genera una clave de licencia firmada.
     *
     * @param installationId  ID único de la instalación destino
     * @param diasValidez     Días de validez desde hoy
     * @param nombre          Nombre del titular (puede ser null)
     * @param empresa         Empresa (puede ser null)
     * @param rut             RUT (puede ser null)
     * @param telefono        Teléfono (puede ser null)
     * @param email           Email (puede ser null)
     * @return Clave de licencia lista para entregar al usuario
     */
    public static String generarClave(String installationId,
                                       int diasValidez,
                                       String nombre,
                                       String empresa,
                                       String rut,
                                       String telefono,
                                       String email) throws Exception {
        LocalDate expiration = LocalDate.now().plusDays(diasValidez);
        String payload = buildPayloadJson(installationId, expiration, nombre, empresa, rut, telefono, email);
        String payloadB64 = base64url(payload.getBytes(StandardCharsets.UTF_8));
        String firma = base64url(hmac(payloadB64));
        return payloadB64 + "." + firma;
    }

    // ─── Validación de clave ──────────────────────────────────────────────────

    public static class ResultadoValidacion {
        public final boolean valida;
        public final String  mensajeError;
        public final LocalDate expiracion;
        public final String    nombre;
        public final String    empresa;

        public ResultadoValidacion(boolean valida, String mensajeError,
                                   LocalDate expiracion, String nombre, String empresa) {
            this.valida       = valida;
            this.mensajeError = mensajeError;
            this.expiracion   = expiracion;
            this.nombre       = nombre;
            this.empresa      = empresa;
        }
    }

    /**
     * Valida una clave de licencia contra el installationId actual del equipo.
     */
    public static ResultadoValidacion validarClave(String clave, String installationIdActual) {
        try {
            String[] partes = clave.trim().split("\\.");
            if (partes.length != 2) {
                return new ResultadoValidacion(false, "Formato de clave inválido", null, null, null);
            }

            // 1. Verificar integridad de la firma
            String payloadB64 = partes[0];
            String firmaRecibida = partes[1];
            String firmaEsperada = base64url(hmac(payloadB64));
            if (!firmaEsperada.equals(firmaRecibida)) {
                return new ResultadoValidacion(false, "Firma inválida — clave no autorizada", null, null, null);
            }

            // 2. Decodificar payload
            String payloadJson = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
            Map<String, String> data = parseSimpleJson(payloadJson);

            // 3. Verificar InstallationID
            String idEnClave = data.get("id");
            if (!installationIdActual.equals(idEnClave)) {
                return new ResultadoValidacion(false,
                    "Esta licencia pertenece a otra instalación", null, null, null);
            }

            // 4. Verificar expiración
            LocalDate exp = LocalDate.parse(data.get("exp"), DATE_FMT);
            if (LocalDate.now().isAfter(exp)) {
                return new ResultadoValidacion(false,
                    "Licencia expirada el " + exp.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    exp, data.get("n"), data.get("e"));
            }

            return new ResultadoValidacion(true, null, exp, data.get("n"), data.get("e"));

        } catch (Exception e) {
            return new ResultadoValidacion(false, "Error al procesar la clave: " + e.getMessage(), null, null, null);
        }
    }

    // ─── Installation ID basado en hardware ──────────────────────────────────

    /**
     * Genera un ID de instalación reproducible ligado al hardware del equipo:
     * combina MAC address de la interfaz principal + nombre del equipo.
     * El mismo equipo siempre generará el mismo ID.
     */
    public static String generarInstallationId() {
        try {
            StringBuilder hwInfo = new StringBuilder();

            // Nombre del PC
            hwInfo.append(System.getenv("COMPUTERNAME") != null
                    ? System.getenv("COMPUTERNAME") : "UNKNOWN");
            hwInfo.append("|");

            // MAC address de la primera interfaz de red activa (no loopback, no virtual)
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) continue;
                    byte[] mac = ni.getHardwareAddress();
                    if (mac != null && mac.length > 0) {
                        StringBuilder macStr = new StringBuilder();
                        for (byte b : mac) macStr.append(String.format("%02X", b));
                        hwInfo.append(macStr);
                        break;
                    }
                }
            }

            // Derivar UUID determinista con HMAC del hardware info
            byte[] digest = hmacRaw(hwInfo.toString(), HMAC_SECRET);
            // Formatear como UUID legible
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16 && i < digest.length; i++) {
                sb.append(String.format("%02x", digest[i]));
                if (i == 3 || i == 5 || i == 7 || i == 9) sb.append('-');
            }
            return sb.toString();

        } catch (Exception e) {
            // Fallback: UUID aleatorio (no reproducible, pero funciona)
            return UUID.randomUUID().toString();
        }
    }

    // ─── Helpers internos ─────────────────────────────────────────────────────

    private static String buildPayloadJson(String id, LocalDate exp,
                                            String n, String e, String r, String t, String m) {
        StringBuilder sb = new StringBuilder("{");
        appendField(sb, "id", id, true);
        appendField(sb, "exp", exp.format(DATE_FMT), false);
        if (n != null && !n.isBlank()) appendField(sb, "n", n, false);
        if (e != null && !e.isBlank()) appendField(sb, "e", e, false);
        if (r != null && !r.isBlank()) appendField(sb, "r", r, false);
        if (t != null && !t.isBlank()) appendField(sb, "t", t, false);
        if (m != null && !m.isBlank()) appendField(sb, "m", m, false);
        sb.append("}");
        return sb.toString();
    }

    private static void appendField(StringBuilder sb, String key, String val, boolean first) {
        if (!first) sb.append(",");
        sb.append("\"").append(key).append("\":\"")
          .append(val.replace("\\", "\\\\").replace("\"", "\\\""))
          .append("\"");
    }

    /** Parser JSON mínimo para el payload controlado (evita dependencia de GSON en util) */
    private static Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        String content = json.trim();
        if (content.startsWith("{")) content = content.substring(1);
        if (content.endsWith("}")) content = content.substring(0, content.length() - 1);
        // Dividir por comas no dentro de comillas
        String[] pairs = content.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String pair : pairs) {
            String[] kv = pair.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", 2);
            if (kv.length == 2) {
                String k = kv[0].trim().replaceAll("^\"|\"$", "");
                String v = kv[1].trim().replaceAll("^\"|\"$", "");
                map.put(k, v);
            }
        }
        return map;
    }

    private static byte[] hmac(String data) throws Exception {
        return hmacRaw(data, HMAC_SECRET);
    }

    private static byte[] hmacRaw(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance(ALGO);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGO));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    // ─── Bypass Maestro ───────────────────────────────────────────────────────

    private static final String MASTER_PAYLOAD = "ORQUESTADOR_MASTER_BYPASS_V1";

    /**
     * Genera el token que se guarda en el archivo ~/.orquestador_master.key.
     * Es el HMAC-SHA256(MASTER_PAYLOAD, HMAC_SECRET) en base64url.
     */
    public static String generarBypassMaestro() throws Exception {
        return base64url(hmac(MASTER_PAYLOAD));
    }

    /**
     * Valida que el contenido del archivo ~/.orquestador_master.key es auténtico.
     */
    public static boolean esBypassMaestroValido(String token) {
        try {
            String esperado = generarBypassMaestro();
            return esperado.equals(token);
        } catch (Exception e) {
            return false;
        }
    }
}
