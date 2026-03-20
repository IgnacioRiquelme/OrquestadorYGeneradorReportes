package com.orquestador.maestro;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * App Maestra — punto de entrada independiente para gestión de licencias.
 * Ejecutar con: java -cp orquestador-maestro.jar com.orquestador.maestro.AppMaestra
 */
public class AppMaestra extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        LicenciaManagerController controller = new LicenciaManagerController();

        Scene scene = new Scene((javafx.scene.Parent) controller.buildRoot(), 900, 620);
        primaryStage.setTitle("App Maestra — Gestión de Licencias");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(760);
        primaryStage.setMinHeight(480);
        primaryStage.show();
    }
}
