package com.learnxchange;

import com.learnxchange.service.AuthService;
import com.learnxchange.ui.SceneManager;
import com.learnxchange.ui.UiUtil;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.InputStream;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        // Optional brand fonts: drop the .ttf files into src/main/resources/fonts (see README there).
        for (String f : new String[]{"BricolageGrotesque-Bold.ttf", "DMSans-Regular.ttf", "DMSans-Bold.ttf"}) {
            try (InputStream in = App.class.getResourceAsStream("/fonts/" + f)) {
                if (in != null) Font.loadFont(in, 13);
            } catch (Exception ignored) {
                // fall back to system fonts
            }
        }

        SceneManager.init(stage);
        try {
            new AuthService().ensureDefaultAdmin();
        } catch (Exception e) {
            e.printStackTrace();
            UiUtil.error("Cannot connect to the database.\n\nMake sure MySQL is running, schema.sql has been "
                    + "executed, and src/main/resources/db.properties has the right URL, user and password.");
            Platform.exit();
            return;
        }
        SceneManager.showLogin();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
