package com.orquestador.app;

import com.orquestador.ui.ControladorPrincipal;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Clase principal de la aplicacin
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            ControladorPrincipal controlador = new ControladorPrincipal();
            Scene scene = new Scene(controlador.getRoot(), 1400, 800);
            
            primaryStage.setTitle("Orquestador de Automatizaciones BCI Seguros");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1200);
            primaryStage.setMinHeight(700);
            primaryStage.show();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
