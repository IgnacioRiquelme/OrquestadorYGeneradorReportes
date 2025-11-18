# 📁 ESTRUCTURA DEL PROYECTO INTEGRADO
## OrquestadorYGeneradorReportes

---

## 🗂️ Estructura de Directorios

```
OrquestadorYGeneradorReportes/
│
├── 📄 pom.xml                                    # Configuración Maven con todas las dependencias
├── 📄 ejecutar.bat                               # Script para ejecutar la aplicación
├── 📄 OrquestadorAutomatizaciones.vbs           # Script VBS para ejecutar sin ventana
├── 📄 INTEGRACION.txt                           # Notas de integración original
├── 📄 README.md                                  # Documentación completa del proyecto
├── 📄 RESUMEN_INTEGRACION.md                    # Resumen técnico de la integración
├── 📄 GUIA_USO.md                               # Guía rápida de uso
├── 📄 ESTRUCTURA_PROYECTO.md                    # Este archivo
│
├── 📁 config/
│   └── 📄 proyectos.ejemplo.json                # Ejemplo de configuración de proyectos
│
├── 📁 src/
│   └── 📁 main/
│       ├── 📁 java/
│       │   └── 📁 com/
│       │       └── 📁 orquestador/
│       │           │
│       │           ├── 📁 app/
│       │           │   └── 📄 Main.java                      # Punto de entrada de la aplicación
│       │           │
│       │           ├── 📁 modelo/
│       │           │   ├── 📄 Proyecto.java                  # Modelo para generación de documentos
│       │           │   └── 📄 ProyectoAutomatizacion.java   # Modelo para orquestación
│       │           │
│       │           ├── 📁 servicio/
│       │           │   ├── 📄 EjecutorAutomatizaciones.java # Servicio de ejecución de proyectos
│       │           │   └── 📄 GeneradorDocumentos.java      # Servicio de generación Word/PDF
│       │           │
│       │           ├── 📁 ui/
│       │           │   └── 📄 ControladorPrincipal.java     # Controlador de interfaz JavaFX
│       │           │
│       │           ├── 📁 util/
│       │           │   ├── 📄 GestorConfiguracion.java      # Gestión de persistencia JSON
│       │           │   └── 📄 LocalDateTimeAdapter.java     # Adapter para Gson
│       │           │
│       │           └── 📁 utilidades/
│       │               ├── 📄 ExcepcionesGenerador.java     # Excepciones personalizadas
│       │               └── 📄 GestorImagenes.java           # Utilidades para manejo de imágenes
│       │
│       └── 📁 resources/
│           └── 📁 fxml/
│               └── (archivos FXML futuros si se necesitan)
│
├── 📁 target/                                    # Archivos compilados (generado por Maven)
│   ├── 📁 classes/
│   ├── 📁 generated-sources/
│   └── 📁 maven-status/
│
└── 📁 .vscode/                                   # Configuración de VS Code
    └── 📄 settings.json

```

---

## 📄 Descripción de Archivos Principales

### 🔧 Configuración

| Archivo | Descripción |
|---------|-------------|
| `pom.xml` | Configuración Maven: dependencias (JavaFX, POI, Gson, Logback), plugins, propiedades |
| `ejecutar.bat` | Script Windows para ejecutar la aplicación con Maven |
| `OrquestadorAutomatizaciones.vbs` | Script para ejecutar sin mostrar ventana de consola |

### 📚 Documentación

| Archivo | Descripción |
|---------|-------------|
| `README.md` | Documentación completa: características, uso, configuración |
| `RESUMEN_INTEGRACION.md` | Detalle técnico de cambios realizados en la integración |
| `GUIA_USO.md` | Guía rápida de inicio y uso del sistema |
| `ESTRUCTURA_PROYECTO.md` | Este archivo - estructura del proyecto |
| `INTEGRACION.txt` | Notas originales sobre la integración |

### 🎯 Código Fuente - Punto de Entrada

| Archivo | Líneas | Descripción |
|---------|--------|-------------|
| `app/Main.java` | ~50 | Inicializa JavaFX y carga la ventana principal |

### 📦 Código Fuente - Modelos

| Archivo | Líneas | Descripción |
|---------|--------|-------------|
| `modelo/Proyecto.java` | ~150 | Modelo para generación de documentos con imágenes |
| `modelo/ProyectoAutomatizacion.java` | ~231 | Modelo completo para orquestación y generación |

**Campos en ProyectoAutomatizacion:**
- Automatización: `nombre`, `ruta`, `area`, `tipoVPN`, `tipoEjecucion`, `estado`, etc.
- Generación: `rutaImagenes`, `rutaTemplateWord`, `rutaSalidaWord`, `rutaSalidaPdf`, `imagenesSeleccionadas`

### ⚙️ Código Fuente - Servicios

| Archivo | Líneas | Descripción |
|---------|--------|-------------|
| `servicio/EjecutorAutomatizaciones.java` | ~200 | Ejecuta proyectos Maven/Newman, gestiona procesos |
| `servicio/GeneradorDocumentos.java` | ~558 | Genera Word y PDF con validación de imágenes |

**Funcionalidades de GeneradorDocumentos:**
- Validación de imágenes por timestamp (10 min)
- Ajuste dinámico de placeholders
- Inserción de imágenes con formato correcto (16.53 x 9.53 cm)
- Conversión a PDF con máxima calidad
- Actualización de fecha automática

### 🖥️ Código Fuente - Interfaz

| Archivo | Líneas | Descripción |
|---------|--------|-------------|
| `ui/ControladorPrincipal.java` | ~856 | Interfaz JavaFX completa con todas las funcionalidades |

**Componentes de la Interfaz:**
- Tabla de proyectos editable
- Botones de acción (agregar, eliminar, ejecutar, generar, ver capturas)
- Área de logs en tiempo real
- Estadísticas en footer
- Filtros por área
- Diálogos de VPN

### 🔧 Código Fuente - Utilidades

| Archivo | Líneas | Descripción |
|---------|--------|-------------|
| `util/GestorConfiguracion.java` | ~120 | Persistencia JSON de proyectos |
| `util/LocalDateTimeAdapter.java` | ~30 | Adapter para serializar LocalDateTime con Gson |
| `utilidades/GestorImagenes.java` | ~220 | Búsqueda, validación y manejo de imágenes |
| `utilidades/ExcepcionesGenerador.java` | ~60 | Excepciones personalizadas del sistema |

**Funcionalidades de GestorImagenes:**
- Extracción de patrones únicos
- Validación de rango de tiempo
- Búsqueda de imagen más reciente
- Cálculo de diferencia de timestamps

### 📋 Configuración

| Archivo | Descripción |
|---------|-------------|
| `config/proyectos.ejemplo.json` | Ejemplo completo de configuración con 2 proyectos |

---

## 📊 Estadísticas del Proyecto

### Código Fuente

| Métrica | Valor |
|---------|-------|
| Total de archivos Java | 10 |
| Total de líneas de código | ~2,500 |
| Paquetes | 5 (`app`, `modelo`, `servicio`, `ui`, `util`, `utilidades`) |
| Clases principales | 10 |
| Enums | 3 (`TipoVPN`, `TipoEjecucion`, `EstadoEjecucion`) |

### Dependencias

| Dependencia | Versión | Uso |
|-------------|---------|-----|
| JavaFX | 21.0.2 | Interfaz gráfica |
| Apache POI | 5.2.3 | Manipulación de Word |
| Gson | 2.10.1 | Serialización JSON |
| Commons IO | 2.8.0 | Utilidades de archivos |
| SLF4J | 2.0.5 | Logging API |
| Logback | 1.4.11 | Implementación de logging |

### Funcionalidades

| Categoría | Cantidad |
|-----------|----------|
| Servicios principales | 2 |
| Modelos de datos | 2 |
| Utilidades | 4 |
| Tipos de VPN | 3 |
| Tipos de ejecución | 3 |
| Estados posibles | 5 |
| Áreas de negocio | 4 |

---

## 🔄 Flujo de Datos

### 1. Persistencia
```
proyectos.json ↔ GestorConfiguracion ↔ ProyectoAutomatizacion (memoria)
```

### 2. Ejecución de Automatizaciones
```
ControladorPrincipal → EjecutorAutomatizaciones → Process (Maven/Newman)
```

### 3. Generación de Documentos
```
ProyectoAutomatizacion → Proyecto → GeneradorDocumentos → Word/PDF
```

### 4. Validación de Imágenes
```
Carpeta de imágenes → GestorImagenes → Validación → GeneradorDocumentos
```

---

## 🎨 Arquitectura

### Patrón de Diseño
- **MVC (Model-View-Controller)**
  - Model: `Proyecto`, `ProyectoAutomatizacion`
  - View: JavaFX (generada programáticamente)
  - Controller: `ControladorPrincipal`

### Separación de Responsabilidades
- **app**: Inicialización
- **modelo**: Estructuras de datos
- **servicio**: Lógica de negocio
- **ui**: Interfaz de usuario
- **util**: Utilidades generales
- **utilidades**: Utilidades específicas del dominio

---

## 📦 Artefactos Generados

### Durante Compilación (target/)
```
target/
├── classes/                    # Clases compiladas (.class)
├── generated-sources/          # Fuentes generadas
└── maven-status/              # Estado de compilación Maven
```

### Durante Ejecución
```
%USERPROFILE%\AppData\Local\OrquestadorAutomatizaciones\
└── proyectos.json             # Configuración persistida
```

### Durante Generación de Documentos
```
{rutaSalidaWord}/
└── {proyecto}_{timestamp}.docx

{rutaSalidaPdf}/
└── {proyecto}_{timestamp}.pdf
```

---

## 🔐 Archivos de Configuración de Usuario

| Ubicación | Archivo | Propósito |
|-----------|---------|-----------|
| `%APPDATA%\Local\OrquestadorAutomatizaciones\` | `proyectos.json` | Lista de proyectos |
| `config/` | `proyectos.ejemplo.json` | Plantilla de ejemplo |

---

## 🚀 Puntos de Extensión

### Para Agregar Nuevas Funcionalidades

1. **Nuevo tipo de VPN**:
   - Editar enum `TipoVPN` en `ProyectoAutomatizacion.java`
   - Actualizar lógica en `ControladorPrincipal.mostrarPopupVPN()`

2. **Nuevo tipo de ejecución**:
   - Editar enum `TipoEjecucion` en `ProyectoAutomatizacion.java`
   - Actualizar lógica en `EjecutorAutomatizaciones.ejecutarProyecto()`

3. **Nueva área de negocio**:
   - Agregar opción en ComboBox de `ControladorPrincipal.crearHeader()`

4. **Nuevo formato de documento**:
   - Extender `GeneradorDocumentos.java`
   - Agregar método de conversión correspondiente

---

## 📝 Convenciones de Código

### Nombres de Clases
- **PascalCase**: `ProyectoAutomatizacion`, `GeneradorDocumentos`

### Nombres de Métodos
- **camelCase**: `ejecutarProyecto()`, `generarInformes()`

### Nombres de Variables
- **camelCase**: `rutaImagenes`, `documentoWordGenerado`

### Constantes
- **UPPER_SNAKE_CASE**: `ANCHO_CM`, `RANGO_TIEMPO_MINUTOS`

---

## 🎯 Resumen

Este proyecto integrado combina:
- ✅ 10 archivos Java principales
- ✅ ~2,500 líneas de código
- ✅ 6 dependencias Maven
- ✅ 2 funcionalidades principales (Orquestación + Generación)
- ✅ Interfaz JavaFX completa
- ✅ Documentación exhaustiva

**Resultado**: Sistema completo y funcional para orquestar automatizaciones y generar reportes profesionales.

---

**Última Actualización**: 16 de Noviembre 2025
