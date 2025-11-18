package com.orquestador.utilidades;

/**
 * Excepciones personalizadas del generador de documentos
 */
public class ExcepcionesGenerador {
    
    /**
     * ExcepciÃ³n cuando falla la validaciÃ³n de proyecto
     */
    public static class ProyectoInvalidoException extends Exception {
        public ProyectoInvalidoException(String mensaje) {
            super("Proyecto invÃ¡lido: " + mensaje);
        }
        
        public ProyectoInvalidoException(String mensaje, Throwable causa) {
            super("Proyecto invÃ¡lido: " + mensaje, causa);
        }
    }
    
    /**
     * ExcepciÃ³n cuando no se encuentran imÃ¡genes
     */
    public static class ImagenesNoEncontradasException extends Exception {
        public ImagenesNoEncontradasException(String patron) {
            super("No se encontraron imÃ¡genes para el patrÃ³n: " + patron);
        }
    }
    
    /**
     * ExcepciÃ³n cuando falla la generaciÃ³n del documento
     */
    public static class GeneracionFailedException extends Exception {
        public GeneracionFailedException(String mensaje) {
            super("FallÃ³ la generaciÃ³n del documento: " + mensaje);
        }
        
        public GeneracionFailedException(String mensaje, Throwable causa) {
            super("FallÃ³ la generaciÃ³n del documento: " + mensaje, causa);
        }
    }
    
    /**
     * ExcepciÃ³n cuando falla la conversiÃ³n a PDF
     */
    public static class ConversionPdfFailedException extends Exception {
        public ConversionPdfFailedException(String archivo) {
            super("No se pudo convertir a PDF: " + archivo);
        }
        
        public ConversionPdfFailedException(String archivo, Throwable causa) {
            super("No se pudo convertir a PDF: " + archivo, causa);
        }
    }
    
    /**
     * ExcepciÃ³n cuando la configuraciÃ³n es invÃ¡lida
     */
    public static class ConfiguracionInvalidaException extends Exception {
        public ConfiguracionInvalidaException(String campo) {
            super("ConfiguraciÃ³n invÃ¡lida: " + campo);
        }
    }
}

