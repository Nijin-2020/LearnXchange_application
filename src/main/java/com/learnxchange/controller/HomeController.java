package com.learnxchange.controller;

import com.learnxchange.model.LearningSession;
import com.learnxchange.model.MatchFilter;
import com.learnxchange.model.MatchResult;
import com.learnxchange.model.User;
import com.learnxchange.service.*;
import com.learnxchange.ui.UiUtil;
import com.learnxchange.util.AppContext;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Dashboard: level and XP, profile strength, quick stats, top matches and achievements. */
public class HomeController implements Refreshable {
    private static final Map<String, String> ICONS = Map.of(
            "first-swap", "M7 12 H24 M24 12 L19 7 M24 12 L19 17 M25 21 H8 M8 21 L13 16 M8 21 L13 26",
            "regular", "M4 6 H20 V20 H4 Z M4 10 H20 M8 3 V7 M16 3 V7",
            "well-rounded", "M4 4 H10 V10 H4 Z M14 4 H20 V10 H14 Z M4 14 H10 V20 H4 Z M14 14 H20 V20 H14 Z",
            "favorite", "M12 3 L14.7 8.6 L20.8 9.5 L16.4 13.8 L17.4 19.9 L12 17 L6.6 19.9 L7.6 13.8 L3.2 9.5 L9.3 8.6 Z",
            "open-book", "M4 5 H11 V19 H4 Z M13 5 H20 V19 H13 Z",
            "trusted", "M12 3 L19 6 V11 C19 16 16 19 12 21 C8 19 5 16 5 11 V6 Z M9 12 L11 14 L15 10");

    private final ProgressService progressService = new ProgressService();
    private final MatchingService matching = new MatchingService();
    private final SwapService swaps = new SwapService();
    private final SessionService sessions = new SessionService();
    private final ProfileService profiles = new ProfileService();

    private final VBox root = new VBox(18);
    private Consumer<String> navigate = key -> { };
    private Consumer<MatchResult> viewMatch = m -> { };

    public void setNavigate(Consumer<String> navigate) { this.navigate = navigate; }
    public void setViewMatch(Consumer<MatchResult> viewMatch) { this.viewMatch = viewMatch; }

    @Override public Parent getView() { return root; }

    @Override
    public void refresh() {
        UiUtil.run(() -> {
            User me = profiles.getUser(AppContext.getCurrentUser().getId());
            AppContext.setCurrentUser(me);
            ProgressService.Progress prog = progressService.forUser(me);
            long pending = swaps.pendingIncomingCount(me);
            long upcoming = sessions.sessionsOf(me).stream()
                    .filter(s -> s.status() == LearningSession.Status.SCHEDULED).count();
            List<MatchResult> matches = matching.findMatches(me, MatchFilter.NONE);

            String first = me.getName().trim().split("\\s+")[0];
            VBox hello = new VBox(4, UiUtil.h1(greeting() + ", " + first),
                    UiUtil.sub(pending > 0
                            ? "You have " + pending + " swap request" + (pending == 1 ? "" : "s") + " waiting."
                            : "Here is how your swapping journey is going."));

            Node levelCard = levelCard(prog, me, pending, upcoming);
            Node strength = strengthCard(prog);
            HBox.setHgrow(levelCard, Priority.ALWAYS);
            HBox.setHgrow(strength, Priority.ALWAYS);
            ((Region) levelCard).setPrefWidth(700);
            ((Region) strength).setPrefWidth(500);
            ((Region) levelCard).setMaxWidth(Double.MAX_VALUE);
            ((Region) strength).setMaxWidth(Double.MAX_VALUE);
            HBox top = new HBox(18, levelCard, strength);

            root.getChildren().setAll(hello, top, matchesCard(matches), achievementsCard(prog));
            UiUtil.reveal(root.getChildren());
        });
    }

    private static String greeting() {
        int h = LocalTime.now().getHour();
        return h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
    }

    private VBox levelCard(ProgressService.Progress prog, User me, long pending, long upcoming) {
        boolean maxed = prog.nextLevelXp() == prog.levelStartXp();
        double span = Math.max(1, prog.nextLevelXp() - prog.levelStartXp());
        double fraction = maxed ? 1 : (prog.xp() - prog.levelStartXp()) / span;

        HBox meta = new HBox(UiUtil.muted(prog.xp() + " XP"), UiUtil.spacer(),
                UiUtil.muted(maxed ? "Top level reached" : (prog.nextLevelXp() - prog.xp()) + " XP to " + prog.nextLevelName()));
        VBox info = new VBox(8, UiUtil.h2(prog.levelName()), meta, UiUtil.xpBar(fraction));
        HBox.setHgrow(info, Priority.ALWAYS);
        HBox head = new HBox(20, UiUtil.levelBadge(prog.level(), 84), info);
        head.setAlignment(Pos.CENTER_LEFT);

        HBox tiles = new HBox(12,
                tile(String.valueOf(pending), "Waiting requests", "requests"),
                tile(String.valueOf(upcoming), "Upcoming sessions", "sessions"),
                tile(String.valueOf(prog.completedSessions()), "Swaps completed", "sessions"),
                tile(me.getRatingCount() == 0 ? "-" : String.format("%.1f", me.getAvgRating()),
                        me.getRatingCount() == 0 ? "No reviews yet" : "Rating (" + me.getRatingCount() + ")", "profile"));
        return UiUtil.card(head, tiles);
    }

    private Node tile(String value, String caption, String target) {
        Label v = new Label(value);
        v.getStyleClass().add("stat-mini-value");
        VBox t = new VBox(2, v, UiUtil.muted(caption));
        t.getStyleClass().add("stat-mini");
        t.setCursor(Cursor.HAND);
        t.setOnMouseClicked(e -> navigate.accept(target));
        HBox.setHgrow(t, Priority.ALWAYS);
        t.setMaxWidth(Double.MAX_VALUE);
        t.setPrefWidth(120);
        return t;
    }

    private VBox strengthCard(ProgressService.Progress prog) {
        VBox steps = new VBox(8);
        if (prog.nextSteps().isEmpty()) {
            steps.getChildren().add(UiUtil.sub("Your profile is complete. Strong profiles get better matches."));
        } else {
            for (String step : prog.nextSteps()) {
                Circle dot = new Circle(8, Color.TRANSPARENT);
                dot.setStroke(UiUtil.line());
                dot.setStrokeWidth(2);
                Label l = new Label(step);
                l.setWrapText(true);
                HBox row = new HBox(10, dot, l);
                row.setAlignment(Pos.CENTER_LEFT);
                steps.getChildren().add(row);
            }
            Button improve = UiUtil.small(UiUtil.primary("Improve profile"));
            improve.setOnAction(e -> navigate.accept("profile"));
            steps.getChildren().add(improve);
        }
        HBox.setHgrow(steps, Priority.ALWAYS);
        HBox body = new HBox(18, UiUtil.ring(prog.completeness(), 96), steps);
        body.setAlignment(Pos.CENTER_LEFT);
        return UiUtil.card(UiUtil.h2("Profile strength"), body);
    }

    private VBox matchesCard(List<MatchResult> matches) {
        Button all = UiUtil.small(new Button("See all"));
        all.setOnAction(e -> navigate.accept("matches"));
        HBox header = new HBox(UiUtil.h2("Your top matches"), UiUtil.spacer(), all);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox card = UiUtil.card(header);
        card.setSpacing(0);
        if (matches.isEmpty()) {
            Label none = UiUtil.sub("No matches yet. Add what you can teach and what you want to learn to see people here.");
            VBox.setMargin(none, new javafx.geometry.Insets(10, 0, 0, 0));
            card.getChildren().add(none);
            return card;
        }
        for (MatchResult m : matches.stream().limit(3).toList()) {
            String line = m.theyTeachMe().isEmpty()
                    ? "Wants to learn " + String.join(", ", m.iTeachThem())
                    : "Teaches you " + String.join(", ", m.theyTeachMe());
            Label name = new Label(m.user().getName());
            name.getStyleClass().add("h3");
            VBox who = new VBox(0, name, UiUtil.muted(line));
            Label pct = new Label(Math.round(m.percentage()) + "%");
            pct.getStyleClass().add("mini-pct");
            Button view = UiUtil.small(new Button("View"));
            view.setOnAction(e -> viewMatch.accept(m));
            HBox row = new HBox(12, UiUtil.avatar(m.user().getId(), m.user().getName(), 38), who, UiUtil.spacer(), pct, view);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("row-line");
            card.getChildren().add(row);
        }
        return card;
    }

    private VBox achievementsCard(ProgressService.Progress prog) {
        FlowPane grid = new FlowPane(12, 12);
        for (ProgressService.Badge b : prog.badges()) {
            SVGPath icon = new SVGPath();
            icon.setContent(ICONS.getOrDefault(b.id(), ICONS.get("favorite")));
            icon.setFill(Color.TRANSPARENT);
            icon.setStroke(b.earned() ? UiUtil.green() : UiUtil.mutedColor());
            icon.setStrokeWidth(2);
            icon.setStrokeLineCap(StrokeLineCap.ROUND);
            icon.setStrokeLineJoin(StrokeLineJoin.ROUND);
            icon.setScaleX(0.9);
            icon.setScaleY(0.9);
            StackPane ico = new StackPane(icon);
            ico.getStyleClass().addAll("achv-ico", b.earned() ? "achv-ico-earned" : "achv-ico-locked");

            Label name = new Label(b.name());
            name.getStyleClass().add("h3");
            Label desc = UiUtil.muted(b.earned() ? "Earned" : b.description());
            desc.setWrapText(true);
            VBox text = new VBox(1, name, desc);
            HBox tile = new HBox(12, ico, text);
            tile.setAlignment(Pos.CENTER_LEFT);
            tile.setPrefWidth(255);
            tile.setMinWidth(255);
            tile.getStyleClass().addAll("achv", b.earned() ? "achv-earned" : "achv-locked");
            if (!b.earned()) tile.setOpacity(0.6);
            grid.getChildren().add(tile);
        }
        return UiUtil.card(UiUtil.h2("Achievements"), grid);
    }
}
