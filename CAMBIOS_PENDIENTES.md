# Cambios Pendientes para Selector Visual de Imágenes

## ✅ Cambios Completados

1. **Columna "Reporte" agregada** - Muestra ✅ Generado cuando el informe está creado
2. **Método `mostrarSelectorImagenesVisual()` agregado** - Permite seleccionar imágenes visualmente en orden
3. **Tamaño de diálogos aumentado** - De 600px a 850px de ancho y 700px de alto

## 📝 Cambios Pendientes (Correcciones Ortográficas)

### En ControladorPrincipal.java - Buscar y reemplazar:

1. **Línea ~321**: `"Nuevo Proyecto de Automatizacin"` → `"Nuevo Proyecto de Automatización"`
2. **Línea ~337**: `"Ruta de imgenes"` → `"Ruta de imágenes"`
3. **Línea ~376**: `"Configuracin para Generacin de Informes"` → `"📄 Configuración para Generación de Informes"`
4. **Línea ~381**: `"Seleccionar carpeta de imgenes"` → `"Seleccionar carpeta de imágenes"`
5. **Línea ~486**: `"rea:"` → `"Área:"`
6. **Línea ~492**: `"Tipo de ejecucin:"` → `"Tipo de ejecución:"`
7. **Línea ~498**: `"Ruta de imgenes:"` → `"Ruta de imágenes:"`
8. **Línea ~431**: `"Seleccionar imgenes manualmente"` → `"Seleccionar imágenes manualmente"`
9. **Línea ~436**: `"Selecciona una carpeta de imgenes para ver patrones disponibles"` → `"Selecciona una carpeta de imágenes para ver patrones disponibles"`

### Lo mismo para el método `editarProyecto()` (líneas ~590-850)

## 🔧 Integración del Selector Visual

### Paso 1: Modificar el checkbox de selección manual (después de línea ~431)

```java
        // Checkbox para selección manual
        CheckBox chkSeleccionar = new CheckBox("Seleccionar imágenes manualmente");
        
        // Botón para abrir selector visual
        Button btnSelectorVisual = new Button("🖼️ Abrir Selector de Imágenes");
        btnSelectorVisual.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        btnSelectorVisual.setVisible(false);
        btnSelectorVisual.setManaged(false);
        
        // Lista para almacenar las imágenes seleccionadas manualmente
        List<String> imagenesSeleccionadasManualmente = new ArrayList<>();
        
        // Cuando se activa el checkbox, mostrar el botón del selector
        chkSeleccionar.selectedProperty().addListener((obs, oldVal, newVal) -> {
            btnSelectorVisual.setVisible(newVal);
            btnSelectorVisual.setManaged(newVal);
            scrollPatrones.setVisible(!newVal);
            scrollPatrones.setManaged(!newVal);
        });
        
        // Acción del botón selector visual
        btnSelectorVisual.setOnAction(e -> {
            List<String> seleccionadas = mostrarSelectorImagenesVisual(txtRutaImagenes.getText(), imagenesSeleccionadasManualmente);
            imagenesSeleccionadasManualmente.clear();
            imagenesSeleccionadasManualmente.addAll(seleccionadas);
            if (!seleccionadas.isEmpty()) {
                mostrarAlerta("Imágenes seleccionadas", "Se seleccionaron " + seleccionadas.size() + " imágenes en orden", Alert.AlertType.INFORMATION);
            }
        });
```

### Paso 2: Agregar el botón al layout (después del checkbox, línea ~520)

```java
        contenido.getChildren().add(chkSeleccionar);
        contenido.getChildren().add(btnSelectorVisual); // NUEVO
        contenido.getChildren().add(new Label("Patrones disponibles:"));
        contenido.getChildren().add(scrollPatrones);
```

### Paso 3: Modificar la lógica de guardado (en dialog.setResultConverter, línea ~551)

```java
                // Capturar imágenes seleccionadas
                List<String> imagenesSeleccionadas = new ArrayList<>();
                if (chkSeleccionar.isSelected()) {
                    // Usar las imágenes del selector visual
                    imagenesSeleccionadas.addAll(imagenesSeleccionadasManualmente);
                } else {
                    // Usar todas las imágenes de los patrones marcados
                    for (javafx.scene.Node node : listaPatrones.getChildren()) {
                        if (node instanceof CheckBox) {
                            CheckBox cb = (CheckBox) node;
                            if (cb.isSelected()) {
                                imagenesSeleccionadas.add(cb.getText());
                            }
                        }
                    }
                }
                proyecto.setImagenesSeleccionadas(imagenesSeleccionadas);
```

### Paso 4: Repetir los mismos cambios en el método `editarProyecto()` (líneas ~590-900)

## 🎯 Resultado Final

Cuando el usuario marque "Seleccionar imágenes manualmente":
1. Aparecerá el botón "🖼️ Abrir Selector de Imágenes"
2. Al hacer clic, se abrirá una ventana modal con:
   - Panel superior: Imágenes seleccionadas en orden
   - Panel inferior: Imágenes disponibles del último set
   - Botón "➕ Imagen 1, 2, 3..." para ir agregando en orden
   - Las imágenes ya seleccionadas se marcan como "✓ Seleccionada"
   - Botón "❌ Quitar" para eliminar de la lista
3. Click en cualquier imagen para verla en tamaño completo
4. Al aceptar, las imágenes quedan guardadas en el orden seleccionado

## 📌 Nota Importante

El método `mostrarSelectorImagenesVisual()` ya está implementado al final de la clase (línea ~1412).
Solo falta integrarlo con el checkbox y botón en los diálogos de agregar/editar proyecto.
