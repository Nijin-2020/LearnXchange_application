package com.learnxchange.controller;

import com.learnxchange.service.ProgressService;
import com.learnxchange.ui.UiUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.prefs.Preferences;

/** Remembers the last level each user reached and celebrates when they go up. */
public final class LevelUp {
    private static final Preferences PREFS = Preferences.userNodeForPackage(LevelUp.class);

    private LevelUp() { }

    public static void check(int userId, ProgressService.Progress p) {
        String key = "level-" + userId;
        int previous = 0;
        try {
            previous = PREFS.getInt(key, 0);
            PREFS.putInt(key, p.level());
        } catch (Exception ignored) {
            // preferences unavailable: skip celebration
        }
        if (previous > 0 && p.level() > previous) Platform.runLater(() -> show(p));
    }

    private static void show(ProgressService.Progress p) {
        Label title = UiUtil.h2("Level up! You're a " + p.levelName());
        Label sub = UiUtil.sub("Every swap and every review makes your profile stronger. Keep going.");
        sub.setMaxWidth(340);
        VBox box = new VBox(14, UiUtil.levelBadge(p.level(), 84), title, sub);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(28));
        box.setPrefWidth(420);
        UiUtil.confetti();
        UiUtil.dialog("Level up", box, new ButtonType("Nice", ButtonBar.ButtonData.OK_DONE));
    }
}
