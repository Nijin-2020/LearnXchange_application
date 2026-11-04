package com.learnxchange.controller;

import com.learnxchange.model.LearningSession;
import com.learnxchange.model.User;
import com.learnxchange.service.RatingService;
import com.learnxchange.service.SessionService;
import com.learnxchange.ui.UiUtil;
import com.learnxchange.util.AppContext;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class SessionsController implements Refreshable {
    private final SessionService sessions = new SessionService();
    private final RatingService ratings = new RatingService();

    private final VBox root = new VBox(14);
    private final VBox list = new VBox(12);

    public SessionsController() {
        root.getChildren().addAll(
                new VBox(4, UiUtil.h1("Sessions"),
                        UiUtil.sub("Mark a session completed when it's done, then rate each other.")),
                list);
    }

    @Override public Parent getView() { return root; }

    @Override
    public void refresh() {
        UiUtil.run(() -> {
            User me = AppContext.getCurrentUser();
            List<LearningSession> all = sessions.sessionsOf(me);
            list.getChildren().clear();
            if (all.isEmpty()) {
                list.getChildren().add(UiUtil.emptyState("No sessions yet",
                        "Accept a swap request, then schedule a session from the Requests tab."));
                return;
            }
            for (LearningSession s : all) list.getChildren().add(card(s, me));
            UiUtil.reveal(list.getChildren());
        });
    }

    private Node card(LearningSession s, User me) {
        String partner = s.otherUserName(me.getId());
        int partnerId = s.otherUserId(me.getId());

        Label day = new Label(String.valueOf(s.date().getDayOfMonth()));
        day.getStyleClass().add("datebox-day");
        Label month = new Label(s.date().format(DateTimeFormatter.ofPattern("MMM")));
        month.getStyleClass().add("datebox-month");
        VBox datebox = new VBox(0, day, month);
        datebox.setAlignment(Pos.CENTER);
        datebox.getStyleClass().add("datebox");

        HBox top = new HBox(10, UiUtil.avatar(partnerId, partner, 30), UiUtil.h3(partner), UiUtil.spacer(),
                UiUtil.pill(s.status().name()));
        top.setAlignment(Pos.CENTER_LEFT);
        Label swap = new Label(s.swapSummary().replace("<->", "for"));
        Label where = UiUtil.muted(s.time().format(DateTimeFormatter.ofPattern("HH:mm")) + ", "
                + (s.mode() == LearningSession.Mode.ONLINE ? "online" : "in person") + ": " + s.locationOrLink());
        where.setWrapText(true);
        VBox main = new VBox(6, top, swap, where);
        HBox.setHgrow(main, Priority.ALWAYS);

        VBox actions = new VBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setMinWidth(130);
        if (s.status() == LearningSession.Status.SCHEDULED) {
            Button done = UiUtil.small(UiUtil.primary("Mark completed"));
            done.setMaxWidth(Double.MAX_VALUE);
            done.setOnAction(e -> UiUtil.run(() -> {
                sessions.markCompleted(me, s.id());
                refresh();
                UiUtil.info("Marked completed. Rate your partner next.");
                UiUtil.progressChanged();
            }));
            Button cancel = UiUtil.small(new Button("Cancel"));
            cancel.setMaxWidth(Double.MAX_VALUE);
            cancel.setOnAction(e -> {
                if (!UiUtil.confirm("Cancel this session?")) return;
                UiUtil.run(() -> {
                    sessions.cancel(me, s.id());
                    refresh();
                });
            });
            actions.getChildren().addAll(done, cancel);
        } else if (s.status() == LearningSession.Status.COMPLETED) {
            Button rate = UiUtil.small(UiUtil.primary("Rate " + partner.split(" ")[0]));
            rate.setMaxWidth(Double.MAX_VALUE);
            rate.setOnAction(e -> ratePartner(s, me));
            actions.getChildren().add(rate);
        }

        HBox card = new HBox(16, datebox, main, actions);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("card");
        return card;
    }

    private void ratePartner(LearningSession s, User me) {
        ToggleGroup group = new ToggleGroup();
        HBox scale = new HBox(8);
        for (int n = 1; n <= 5; n++) {
            ToggleButton b = new ToggleButton(String.valueOf(n));
            b.setUserData(n);
            b.setToggleGroup(group);
            b.getStyleClass().add("slot-toggle");
            b.setMinWidth(52);
            scale.getChildren().add(b);
        }
        group.getToggles().get(4).setSelected(true);
        group.selectedToggleProperty().addListener((o, oldT, newT) -> { if (newT == null) oldT.setSelected(true); });
        TextArea feedback = new TextArea();
        feedback.setPromptText("How was the session? (optional)");
        feedback.setPrefRowCount(4);
        feedback.setWrapText(true);

        VBox box = new VBox(14, UiUtil.h2("Rate " + s.otherUserName(me.getId())),
                UiUtil.field("How did it go? (1 is poor, 5 is excellent)", scale),
                UiUtil.field("Feedback", feedback));
        box.setPadding(new javafx.geometry.Insets(20));
        box.setPrefWidth(440);

        if (UiUtil.showDialog("Rate your partner", box)) {
            UiUtil.run(() -> {
                int score = (Integer) group.getSelectedToggle().getUserData();
                ratings.rate(me, s.id(), score, feedback.getText());
                UiUtil.info("Thanks for your feedback!");
                UiUtil.progressChanged();
            });
        }
    }
}
