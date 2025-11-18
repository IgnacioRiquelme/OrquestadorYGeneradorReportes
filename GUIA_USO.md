# 🚀 GUÍA RÁPIDA DE USO
## OrquestadorYGeneradorReportes

---

## ⚡ Inicio Rápido

### 1. Ejecutar la Aplicación

**Opción A - Con Maven:**
```bash
mvn javafx:run
```

**Opción B - Con ejecutar.bat:**
```bash
ejecutar.bat
```

**Opción C - Con VBS (Oculta ventana):**
```
Doble clic en OrquestadorAutomatizaciones.vbs
```

---

## 📝 Primer Uso

### 1. Agregar un Proyecto

1. Clic en "➕ Agregar Proyecto"
2. Completar información básica:
   - **Nombre**: Nombre descriptivo del proyecto
   - **Ruta**: Carpeta del proyecto Maven/Newman
   - **Área**: Clientes, Comercial, Integraciones o Siniestros
   - **VPN**: Tipo de VPN requerida
   - **Tipo**: Maven, Newman o ambos

### 2. Configurar para Generación de Informes

Para generar informes, cada proyecto necesita:

1. **Editar el proyecto** en la tabla (doble clic en celdas)
2. O **editar el JSON** directamente en:
   ```
   %USERPROFILE%\AppData\Local\OrquestadorAutomatizaciones\proyectos.json
   ```

Agregar estos campos:
```json
{
  "rutaImagenes": "C:\\ruta\\al\\proyecto\\test-output\\capturaPantalla",
  "rutaTemplateWord": "C:\\templates\\template_evidencias.docx",
  "rutaSalidaWord": "C:\\salidas\\word",
  "rutaSalidaPdf": "C:\\salidas\\pdf",
  "imagenesSeleccionadas": [
    "t0001_1_Login_",
    "t0002_2_Dashboard_",
    "t0003_3_Resultado_"
  ]
}
```

---

## 🎯 Flujo de Trabajo Típico

### Caso 1: Solo Ejecutar Automatizaciones

1. ✅ Marcar proyectos a ejecutar (checkbox)
2. ✅ Clic en "▶ Ejecutar Seleccionados"
3. ✅ Seguir instrucciones de VPN si aplica
4. ✅ Ver resultados en el log
5. ✅ Ver capturas con "🖼 Ver Capturas"

### Caso 2: Ejecutar y Generar Informes

1. ✅ Configurar proyectos con datos de informes
2. ✅ Marcar proyectos
3. ✅ Ejecutar automatizaciones ("▶ Ejecutar Seleccionados")
4. ✅ Esperar a que finalicen
5. ✅ Generar informes ("📄 Generar Informes")
6. ✅ Revisar Word y PDF en carpetas configuradas

### Caso 3: Solo Generar Informes (Ya hay capturas)

1. ✅ Marcar proyectos que ya tienen capturas
2. ✅ Clic en "📄 Generar Informes"
3. ✅ Ver progreso en el log
4. ✅ Revisar documentos generados

---

## 🔧 Configuración del Template Word

### Crear Template

1. Abrir Word
2. Diseñar el documento con tu formato
3. Insertar placeholders:
   - `[Fecha]` → Se reemplaza con fecha actual
   - `[Imagen1]` → Primera imagen
   - `[Imagen2]` → Segunda imagen
   - `[Imagen3]` → Tercera imagen
   - etc.

4. Guardar como `.docx`

### Ejemplo de Template:
```
Evidencias de Prueba
Fecha: [Fecha]

1. Paso Login
[Imagen1]

2. Paso Dashboard
[Imagen2]

3. Paso Validación
[Imagen3]
```

**Nota**: El sistema ajusta automáticamente la cantidad de placeholders según las imágenes encontradas.

---

## 📸 Formato de Imágenes

### Naming Convention:
```
patron_YYYYMMDD_HHMMSS.png
```

### Ejemplos:
```
t0001_1_Login_20241116_143025.png
t0002_2_Dashboard_20241116_143030.png
t0003_3_Resultado_20241116_143035.png
```

### Patrones en Configuración:
```json
"imagenesSeleccionadas": [
  "t0001_1_Login_",
  "t0002_2_Dashboard_",
  "t0003_3_Resultado_"
]
```

**Importante**: El sistema usa el patrón (sin fecha) para buscar la imagen más reciente.

---

## 🔍 Búsqueda de Imágenes

El sistema:
1. ✅ Busca imágenes que coincidan con cada patrón
2. ✅ Selecciona la más reciente de cada patrón
3. ✅ Valida que esté dentro de 10 minutos de diferencia
4. ✅ Alerta si hay imágenes fuera de rango

---

## 🌐 Gestión de VPN

### Tipos de VPN:

1. **Sin VPN**
   - Proyectos que no requieren VPN
   - Se ejecutan directamente

2. **VPN BCI**
   - Requiere conexión a VPN BCI
   - El sistema muestra alerta para conectar
   - Espera confirmación del usuario

3. **VPN Clip**
   - Requiere conexión a VPN Clip
   - El sistema muestra alerta para conectar
   - Espera confirmación del usuario

### Flujo Automático:

1. Sistema agrupa proyectos por VPN
2. Determina orden óptimo de ejecución
3. Muestra alertas de conexión/desconexión
4. Espera confirmación del usuario
5. Continúa con siguiente grupo

---

## 📊 Áreas de Proyectos

Organiza tus proyectos por área:
- **Clientes**: Proyectos relacionados con clientes
- **Comercial**: Automatizaciones comerciales
- **Integraciones**: Pruebas de integración
- **Siniestros**: Gestión de siniestros

**Beneficio**: Puedes ejecutar todos los proyectos de un área con "▶ Ejecutar por Área"

---

## 🎨 Interfaz

### Botones Principales:

| Botón | Función |
|-------|---------|
| ➕ Agregar Proyecto | Agregar nuevo proyecto |
| 🗑️ Eliminar Seleccionados | Eliminar proyectos marcados |
| 🔄 Refrescar | Recargar proyectos desde JSON |
| ▶ Ejecutar Seleccionados | Ejecutar proyectos marcados |
| ▶ Ejecutar por Área | Ejecutar todos de un área |
| ⏹ Detener | Detener ejecución en curso |
| 🖼 Ver Capturas | Ver galería de capturas |
| 📄 Generar Informes | Generar Word y PDF |

### Filtros:
- **Todas**: Mostrar todos los proyectos
- **Clientes**: Solo proyectos de clientes
- **Comercial**: Solo proyectos comerciales
- **Integraciones**: Solo integraciones
- **Siniestros**: Solo siniestros

---

## 📈 Estadísticas

En el footer se muestran:
- **Total**: Cantidad total de proyectos
- **Seleccionados**: Proyectos marcados
- **Exitosos**: Última ejecución exitosa
- **Fallidos**: Última ejecución fallida

---

## 📝 Logs

El panel de log muestra:
- ✅ Inicio/fin de ejecuciones
- ✅ Cambios de VPN
- ✅ Progreso de cada proyecto
- ✅ Resultados (exitoso/fallido)
- ✅ Generación de informes
- ✅ Errores y advertencias

**Timestamp**: Cada log incluye hora exacta `[HH:mm:ss]`

---

## 🎯 Tips y Trucos

### 1. Rutas por Defecto
Si no configuras rutas de informes, el sistema usa:
- **rutaImagenes**: `{ruta_proyecto}/test-output/capturaPantalla`
- **rutaSalidaWord**: `{ruta_proyecto}`
- **rutaSalidaPdf**: `{ruta_proyecto}`

### 2. Checkbox en Header
- Marca el checkbox del header de la tabla para seleccionar/deseleccionar TODOS los proyectos

### 3. Edición Rápida
- Doble clic en celdas de la tabla para editar directamente

### 4. Ver Capturas Recientes
- Solo se muestran capturas posteriores a la última ejecución

### 5. Calidad PDF
- Los PDF se generan con máxima calidad (sin compresión de imágenes)

---

## ❌ Troubleshooting

### Problema: No genera PDF
**Solución**: Instalar Microsoft Word en el sistema

### Problema: No encuentra imágenes
**Verificar**:
1. Ruta de imágenes correcta
2. Formato de nombres de archivo
3. Imágenes recientes (últimos 10 minutos)

### Problema: Error de compilación
**Solución**:
```bash
mvn clean compile
```

### Problema: Error de ejecución
**Solución**:
```bash
mvn clean install
mvn javafx:run
```

---

## 📞 Soporte

Para más información, revisa:
- `README.md` - Documentación completa
- `RESUMEN_INTEGRACION.md` - Detalles técnicos de integración
- `config/proyectos.ejemplo.json` - Ejemplos de configuración

---

## 🏁 Conclusión

Con esta guía puedes:
1. ✅ Agregar y configurar proyectos
2. ✅ Ejecutar automatizaciones con gestión de VPN
3. ✅ Generar informes con evidencias
4. ✅ Ver y gestionar capturas de pantalla
5. ✅ Monitorear todo el proceso con logs detallados

**¡Éxito en tus automatizaciones!** 🚀
