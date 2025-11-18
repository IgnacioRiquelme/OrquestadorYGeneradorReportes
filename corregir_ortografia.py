# Script para corregir ortografía y agregar selector visual
import codecs
import re

archivo = r'C:\Users\IARC\Desktop\OrquestadorYGeneradorReportes\src\main\java\com\orquestador\ui\ControladorPrincipal.java'

# Leer archivo
with codecs.open(archivo, 'r', encoding='utf-8') as f:
    contenido = f.read()

# Correcciones ortográficas
correcciones = [
    ('Nuevo Proyecto de Automatizacin', 'Nuevo Proyecto de Automatización'),
    ('Ruta de imgenes', 'Ruta de imágenes'),
    ('Configuracin para Generacin de Informes', '📄 Configuración para Generación de Informes'),
    ('Seleccionar carpeta de imgenes', 'Seleccionar carpeta de imágenes'),
    ('"rea:"', '"Área:"'),
    ('Tipo de ejecucin:', 'Tipo de ejecución:'),
    ('Seleccionar imgenes manualmente', 'Seleccionar imágenes manualmente'),
    ('Selecciona una carpeta de imgenes para ver patrones disponibles', 'Selecciona una carpeta de imágenes para ver patrones disponibles'),
]

for viejo, nuevo in correcciones:
    contenido = contenido.replace(viejo, nuevo)

# Guardar
with codecs.open(archivo, 'w', encoding='utf-8') as f:
    f.write(contenido)

print("✅ Correcciones aplicadas exitosamente")
