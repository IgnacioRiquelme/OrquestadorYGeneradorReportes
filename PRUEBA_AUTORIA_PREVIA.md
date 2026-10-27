# Evidencia de Autoría Previa — Orquestador y Generador de Reportes

> **Nota:** Este documento NO es asesoría legal. Es un respaldo informal para conversar
> con un abogado laboral y/o presentar a un nuevo empleador. Antes de firmar cualquier
> contrato, valida esto con un profesional.

## 1. Resumen de titularidad

- **Autor:** Ignacio Riquelme (cuenta GitHub personal `IgnacioRiquelme`).
- **Repositorio:** https://github.com/IgnacioRiquelme/OrquestadorYGeneradorReportes (privado/personal, no alojado en infraestructura de ningún empleador).
- **Herramientas usadas:** equipo y cuentas personales, fuera de recursos corporativos.
- **Motivo de creación:** proyecto personal para automatizar tareas repetitivas de mi propio trabajo diario, no encomendado ni exigido por ningún empleador, y fuera del alcance de mis funciones contractuales (no fui contratado como desarrollador).

## 2. Cronología según historial de Git

- **Primer commit:** `2025-11-18` — "Initial commit: Orquestador de Automatizaciones - Project setup and configuration"
- **Último commit registrado (a la fecha de este documento):** `2026-03-20`
- **Total de commits:** 28
- **Rango de desarrollo continuo:** ~4 meses, con mensajes de commit que describen incrementalmente cada funcionalidad agregada.

Historial completo exportado en `git_history_export.txt` (hash, fecha, autor, mensaje de cada commit) para respaldo.

## 3. Fortalezas y limitaciones de esta evidencia

**A favor:**
- El repositorio vive en una cuenta de GitHub **personal**, no corporativa. GitHub registra server-side la fecha real de cada `push`, lo cual es más difícil de falsificar que la fecha local de un commit.
- El volumen y progresión de commits (28 commits en ~4 meses, con mensajes descriptivos de funcionalidades incrementales) es consistente con desarrollo genuino y gradual, no con algo armado de un día para otro para "simular" antigüedad.

**Limitaciones a tener en cuenta:**
- El email configurado en git (`tu-email@ejemplo.com`) es un placeholder, no tu correo real — esto **debilita** la trazabilidad de autoría en los commits locales. Recomendación: configura tu email real en git de aquí en adelante (`git config user.email "tu_correo_real@dominio.com"`) para los próximos commits. Los commits pasados no deberían reescribirse (reescribir historial es contraproducente: parece manipulación de evidencia).
- Las fechas de commit locales **pueden ser editadas por el propio usuario**, por lo que un commit local, por sí solo, no es prueba irrefutable. Lo que sí es más difícil de manipular es la fecha de `push` que registra GitHub en sus servidores — por eso es importante que el repo esté efectivamente subido (no solo local) desde antes de tu nueva relación laboral.

## 4. Recomendaciones adicionales para reforzar la evidencia

1. **No modifiques el historial de git** (no hagas rebase/squash de estos commits). Historial intacto = más creíble.
2. Considera generar un respaldo adicional con timestamp independiente antes de iniciar el nuevo trabajo:
   - Un archivo `.zip` del proyecto + su hash (SHA-256), enviado por correo a ti mismo (el header del correo trae fecha/hora del servidor de tu proveedor de email, independiente de ti).
   - Alternativamente, mantener el repo en GitHub y usar la fecha de "Insights → Commits" o los metadatos del repo como respaldo visible por terceros.
3. Limpia del código toda referencia específica a la operación de tu empleador actual (nombres de VPN, áreas, rutas de archivos, nombre de la empresa) — no porque afecte tu titularidad, sino por respeto a la confidencialidad de información interna que conociste por tu cargo.
4. Guarda una copia de este documento y del `git_history_export.txt` fuera del repositorio (por ejemplo, en tu correo personal o almacenamiento personal), fechada, como respaldo adicional.

## 5. Borrador de declaración para el nuevo empleador

Ver sección siguiente — para presentar como anexo o correo al momento de firmar el contrato.
