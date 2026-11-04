package com.learnxchange.ui;

import com.learnxchange.controller.LoginController;
import com.learnxchange.controller.MainController;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public final class SceneManager {
    private static Stage stage;
    private static MainController activeMain;

    private SceneManager() { }

    public static void init(Stage primaryStage) { stage = primaryStage; }

    public static void showLogin() {
        stopMain();
        show(new LoginController().getView(), "LearnXchange", 1000, 650);
    }

    public static void showMain() {
        reloadMain(0);
    }

    /** Rebuilds the main window (used after a theme change) on the given tab. */
    public static void reloadMain(int tabIndex) {
        stopMain();
        activeMain = new MainController(tabIndex);
        show(activeMain.getView(), "LearnXchange", 1180, 760);
    }

    private static void stopMain() {
        if (activeMain != null) activeMain.stop();
        activeMain = null;
    }

    private static void show(Parent root, String title, double width, double height) {
        StackPane host = new StackPane(root); // host also carries toast messages
        host.getStyleClass().add("app-root");
        UiUtil.applyTheme(host);
        UiUtil.setToastHost(host);
        Scene scene = new Scene(host, width, height);
        String css = UiUtil.css();
        if (css != null) scene.getStylesheets().add(css);
        stage.setTitle(title);
        stage.setScene(scene);
        stage.setMinWidth(940);
        stage.setMinHeight(600);
        stage.centerOnScreen();
        stage.show();
    }
}
