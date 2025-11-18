# Orquestador y Generador de Reportes - Integración Completa

## Descripción
Este proyecto integra completamente las funcionalidades de:
- **OrquestadorAutomatizaciones**: Orquestación y ejecución secuencial de proyectos de automatización con gestión de VPN
- **GeneradorDocumentosAutomatico**: Generación automática de documentos Word y PDF con evidencias de pruebas

## Características Principales

### 1. Orquestación de Automatizaciones
- ✅ Ejecución secuencial de proyectos Maven/Newman
- ✅ Gestión automática de VPN (Sin VPN, VPN BCI, VPN Clip)
- ✅ Agrupación por área (Clientes, Comercial, Integraciones, Siniestros)
- ✅ Visualización de capturas de pantalla
- ✅ Estadísticas de ejecución

### 2. Generación de Documentos
- ✅ Generación automática de Word y PDF
- ✅ Inserción automática de imágenes con validación de timestamps
- ✅ Soporte para múltiples patrones de imágenes
- ✅ Ajuste dinámico de placeholders según cantidad de imágenes
- ✅ Conversión a PDF con máxima calidad

### 3. Integración Completa
- ✅ Botón "Generar Informes" en la interfaz principal
- ✅ Configuración unificada por proyecto
- ✅ Procesamiento batch de múltiples proyectos
- ✅ Logs detallados de todas las operaciones

## Configuración de Proyectos

### Campos para Automatización
- `nombre`: Nombre del proyecto
- `ruta`: Ruta del proyecto Maven/Newman
- `area`: Área del proyecto (Clientes, Comercial, etc.)
- `tipoVPN`: Tipo de VPN requerida (SIN_VPN, VPN_BCI, VPN_CLIP)
- `tipoEjecucion`: Tipo de ejecución (MAVEN, NEWMAN, MAVEN_NEWMAN)

### Campos para Generación de Informes
- `rutaImagenes`: Carpeta donde se encuentran las capturas (default: `{ruta}/test-output/capturaPantalla`)
- `rutaTemplateWord`: Ruta del template Word (.docx)
- `rutaSalidaWord`: Carpeta de salida para documentos Word
- `rutaSalidaPdf`: Carpeta de salida para documentos PDF
- `imagenesSeleccionadas`: Lista de patrones de imágenes a incluir (ej: `["t0001_1_Login_", "t0002_2_Dashboard_"]`)

### Ejemplo de Configuración
```json
{
  "nombre": "Proyecto Test",
  "ruta": "C:\\proyectos\\test",
  "area": "Clientes",
  "tipoVPN": "SIN_VPN",
  "tipoEjecucion": "MAVEN",
  "rutaImagenes": "C:\\proyectos\\test\\test-output\\capturaPantalla",
  "rutaTemplateWord": "C:\\templates\\evidencias.docx",
  "rutaSalidaWord": "C:\\salidas\\word",
  "rutaSalidaPdf": "C:\\salidas\\pdf",
  "imagenesSeleccionadas": [
    "t0001_Login_",
    "t0002_Dashboard_"
  ]
}
```

## Uso del Sistema

### 1. Ejecutar Automatizaciones
1. Seleccionar proyectos marcando el checkbox
2. Clic en "▶ Ejecutar Seleccionados" o "▶ Ejecutar por Área"
3. Seguir las instrucciones de conexión/desconexión VPN
4. Ver resultados en el log

### 2. Generar Informes
1. Seleccionar proyectos que ya tienen capturas
2. Clic en "📄 Generar Informes"
3. El sistema genera Word y PDF automáticamente
4. Ver resultados en las carpetas configuradas

### 3. Ver Capturas
1. Seleccionar un proyecto ejecutado
2. Clic en "🖼 Ver Capturas"
3. Se muestra galería de imágenes de la última ejecución
4. Clic en imagen para ver en tamaño completo

## Formato de Nombres de Imágenes

Las imágenes deben seguir el formato:
```
patron_YYYYMMDD_HHMMSS.png
```

Ejemplos:
- `t0001_1_Login_20241116_143025.png`
- `t0002_2_Dashboard_20241116_143030.png`

El sistema valida que las imágenes estén dentro de un rango de 10 minutos.

## Template Word

El template debe contener:
- Placeholder `[Fecha]` que será reemplazado por la fecha actual
- Placeholders `[Imagen1]`, `[Imagen2]`, etc. donde se insertarán las imágenes

El sistema ajusta automáticamente la cantidad de placeholders según las imágenes encontradas.

## Dependencias

- Java 16
- JavaFX 21.0.2
- Apache POI 5.2.3
- Gson 2.10.1
- Maven 3.x

## Compilación

```bash
mvn clean compile
```

## Ejecución

```bash
mvn javafx:run
```

O usar el archivo `ejecutar.bat`

## Ubicación de Configuraciones

- Proyectos: `%USERPROFILE%\AppData\Local\OrquestadorAutomatizaciones\proyectos.json`
- Ejemplo: `config/proyectos.ejemplo.json`

## Logs

El sistema genera logs detallados en tiempo real en el panel inferior de la interfaz, incluyendo:
- Inicio/fin de ejecuciones
- Conexiones VPN
- Generación de documentos
- Errores y advertencias

## Notas Importantes

1. **VPN**: El sistema pausa y muestra alertas cuando necesita cambio de VPN
2. **Imágenes**: Valida que las capturas sean recientes (máximo 10 minutos de diferencia)
3. **Word**: Debe estar instalado Microsoft Word para la conversión a PDF
4. **Calidad PDF**: Configurado para máxima calidad de imagen (sin compresión)

## Autor
Sistema integrado a partir de OrquestadorAutomatizaciones y GeneradorDocumentosAutomatico

## Fecha
Noviembre 2025
