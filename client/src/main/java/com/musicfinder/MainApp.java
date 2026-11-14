package com.musicfinder;

import com.musicfinder.navigation.Navigator;
import com.musicfinder.navigation.Screen;
import com.musicfinder.network.ClientConnection;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.io.IOException;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        String host = System.getProperty("music.server.host", "localhost");
        int port = Integer.getInteger("music.server.port", 12345);
        try {
            ClientConnection.getInstance().connect(host, port);
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR,
                    "Không kết nối được tới server " + host + ":" + port + "\n" + e.getMessage()).showAndWait();
            Platform.exit();
            return;
        }

        Navigator.initialize(stage);
        Navigator.goTo(Screen.LOGIN);

        stage.centerOnScreen();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
