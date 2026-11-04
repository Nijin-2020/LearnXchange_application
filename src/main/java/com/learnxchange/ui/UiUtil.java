package com.learnxchange.ui;

import com.learnxchange.util.DataAccessException;
import com.learnxchange.util.ServiceException;
import javafx.animation.*;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.util.Duration;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.prefs.Preferences;

/** Shared look-and-feel helpers: theme, toasts, dialogs, animations and the small visual components. */
public final class UiUtil {
    public static final String[] LEVELS = {"Beginner", "Basic", "Comfortable", "Advanced", "Expert"};

    private static final Preferences PREFS = Preferences.userNodeForPackage(UiUtil.class);
    private static boolean dark = loadDark();
    private static StackPane toastHost;
    private static Label currentToast;
    private static Runnable progressHook;

    private UiUtil() { }

    /* ---------------- theme ---------------- */
    private static boolean loadDark() {
        try { return "dark".equals(PREFS.get("theme", "light")); } catch (Exception e) { return false; }
    }

    public static boolean isDark() { return dark; }

    public static void toggleTheme() {
        dark = !dark;
        try { PREFS.put("theme", dark ? "dark" : "light"); } catch (Exception ignored) { /* not persisted */ }
    }

    /** Adds or removes the "dark" style class so the CSS colour variables switch. */
    public static void applyTheme(Node node) {
        node.getStyleClass().remove("dark");
        if (dark) node.getStyleClass().add("dark");
    }

    public static Color blue() { return dark ? Color.web("#7686ff") : Color.web("#3d4dff"); }
    public static Color line() { return dark ? Color.web("#25304e") : Color.web("#dbe0ee"); }
    public static Color mutedColor() { return dark ? Color.web("#9aa5c7") : Color.web("#5d6785"); }
    public static Color green() { return dark ? Color.web("#4fd9b9") : Color.web("#0b8f75"); }
    public static Color onBlue() { return dark ? Color.web("#0b1020") : Color.WHITE; }

    /** Round icon button that flips light/dark; afterToggle should rebuild the current screen. */
    public static Button themeButton(Runnable afterToggle) {
        Button b = new Button();
        b.getStyleClass().add("icon-btn");
        b.setGraphic(themeIcon());
        b.setTooltip(new Tooltip("Toggle dark mode"));
        b.setOnAction(e -> {
            toggleTheme();
            afterToggle.run();
        });
        return b;
    }

    private static Node themeIcon() {
        Color c = mutedColor();
        Node icon;
        if (dark) { // sun
            Circle core = new Circle(12, 12, 4);
            core.setFill(Color.TRANSPARENT);
            core.setStroke(c);
            core.setStrokeWidth(2);
            SVGPath rays = new SVGPath();
            rays.setContent("M12 2 V4 M12 20 V22 M4.9 4.9 L6.3 6.3 M17.7 17.7 L19.1 19.1 M2 12 H4 M20 12 H22 M4.9 19.1 L6.3 17.7 M17.7 6.3 L19.1 4.9");
            rays.setFill(Color.TRANSPARENT);
            rays.setStroke(c);
            rays.setStrokeWidth(2);
            rays.setStrokeLineCap(StrokeLineCap.ROUND);
            icon = new Group(core, rays);
        } else { // moon
            SVGPath moon = new SVGPath();
            moon.setContent("M21 12.8 A9 9 0 1 1 11.2 3 A7 7 0 0 0 21 12.8 Z");
            moon.setFill(Color.TRANSPARENT);
            moon.setStroke(c);
            moon.setStrokeWidth(2);
            moon.setStrokeLineJoin(StrokeLineJoin.ROUND);
            icon = moon;
        }
        icon.setScaleX(0.75);
        icon.setScaleY(0.75);
        StackPane box = new StackPane(icon);
        box.setMinSize(20, 20);
        box.setPrefSize(20, 20);
        box.setMaxSize(20, 20);
        return box;
    }

    /* ---------------- stylesheet / dialogs / toasts ---------------- */
    public static String css() {
        var url = UiUtil.class.getResource("/styles.css");
        return url == null ? null : url.toExternalForm();
    }

    private static void style(DialogPane pane) {
        String css = css();
        if (css != null) pane.getStylesheets().add(css);
        applyTheme(pane);
    }

    public static void setToastHost(StackPane host) { toastHost = host; currentToast = null; }

    /** Screens that can earn XP call this; the main window recalculates the level and may celebrate. */
    public static void setProgressHook(Runnable hook) { progressHook = hook; }

    public static void progressChanged() {
        if (progressHook != null) {
            try { progressHook.run(); } catch (Exception ignored) { /* cosmetic */ }
        }
    }

    public static void info(String message) { toast(message, false); }
    public static void error(String message) { toast(message, true); }

    public static void toast(String message, boolean error) {
        if (toastHost == null) {
            Alert a = new Alert(error ? Alert.AlertType.ERROR : Alert.AlertType.INFORMATION, message, ButtonType.OK);
            a.setHeaderText(null);
            style(a.getDialogPane());
            a.showAndWait();
            return;
        }
        if (currentToast != null) toastHost.getChildren().remove(currentToast);
        Label l = new Label(message);
        l.setWrapText(true);
        l.setMaxWidth(560);
        l.getStyleClass().addAll("toast", error ? "toast-error" : "toast-ok");
        l.setMouseTransparent(true);
        l.setOpacity(0);
        StackPane.setAlignment(l, Pos.BOTTOM_CENTER);
        StackPane.setMargin(l, new Insets(0, 0, 28, 0));
        toastHost.getChildren().add(l);
        currentToast = l;

        FadeTransition in = new FadeTransition(Duration.millis(220), l);
        in.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(220), l);
        slide.setFromY(18);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(in, slide).play();

        PauseTransition wait = new PauseTransition(Duration.seconds(error ? 4.5 : 2.8));
        FadeTransition fade = new FadeTransition(Duration.millis(300), l);
        fade.setToValue(0);
        fade.setOnFinished(e -> {
            toastHost.getChildren().remove(l);
            if (currentToast == l) currentToast = null;
        });
        wait.setOnFinished(e -> fade.play());
        wait.play();
    }

    public static boolean confirm(String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        a.setHeaderText(null);
        style(a.getDialogPane());
        return a.showAndWait().filter(b -> b == ButtonType.YES).isPresent();
    }

    /** Runs an action and turns any failure into a friendly toast instead of a crash. */
    public static void run(Runnable action) {
        try {
            action.run();
        } catch (ServiceException e) {
            error(e.getMessage());
        } catch (DataAccessException e) {
            e.printStackTrace();
            error("A database error occurred. Check that MySQL is running and db.properties is correct.");
        } catch (Exception e) {
            e.printStackTrace();
            error("Unexpected error: " + e.getMessage());
        }
    }

    /** Generic styled dialog. Buttons whose ButtonData is a default action get the primary style. */
    public static Optional<ButtonType> dialog(String title, Node content, ButtonType... buttons) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(title);
        DialogPane pane = d.getDialogPane();
        pane.setContent(content);
        pane.getButtonTypes().addAll(buttons);
        style(pane);
        for (ButtonType bt : buttons) {
            Node b = pane.lookupButton(bt);
            if (b != null && bt.getButtonData().isDefaultButton()) b.getStyleClass().add("primary");
        }
        return d.showAndWait();
    }

    public static boolean showDialog(String title, Node content) {
        return dialog(title, content, ButtonType.OK, ButtonType.CANCEL).filter(b -> b == ButtonType.OK).isPresent();
    }

    public static GridPane form() {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(12);
        g.setPadding(new Insets(20));
        return g;
    }

    /* ---------------- animation ---------------- */
    /** Fades and slides the nodes in one after another. */
    public static void reveal(List<? extends Node> nodes) {
        int i = 0;
        for (Node n : nodes) {
            n.setOpacity(0);
            n.setTranslateY(14);
            FadeTransition f = new FadeTransition(Duration.millis(380), n);
            f.setToValue(1);
            TranslateTransition t = new TranslateTransition(Duration.millis(380), n);
            t.setToY(0);
            t.setInterpolator(Interpolator.EASE_OUT);
            ParallelTransition p = new ParallelTransition(f, t);
            p.setDelay(Duration.millis(Math.min(i, 8) * 55L));
            p.play();
            i++;
        }
    }

    public static void liftOnHover(Node n) {
        TranslateTransition up = new TranslateTransition(Duration.millis(140), n);
        up.setToY(-3);
        TranslateTransition down = new TranslateTransition(Duration.millis(140), n);
        down.setToY(0);
        n.setOnMouseEntered(e -> { down.stop(); up.playFromStart(); });
        n.setOnMouseExited(e -> { up.stop(); down.playFromStart(); });
    }

    /** Celebration: coloured confetti falling over the whole window. */
    public static void confetti() {
        if (toastHost == null) return;
        Pane layer = new Pane();
        layer.setMouseTransparent(true);
        toastHost.getChildren().add(layer);
        double w = Math.max(400, toastHost.getWidth());
        double h = Math.max(400, toastHost.getHeight());
        Color[] colors = {Color.web("#3d4dff"), Color.web("#0b8f75"), Color.web("#ffb02e"), Color.web("#d6335c"), Color.web("#7686ff")};
        Random rnd = new Random();
        ParallelTransition all = new ParallelTransition();
        for (int i = 0; i < 60; i++) {
            Rectangle r = new Rectangle(8, 13, colors[i % colors.length]);
            r.setArcWidth(3);
            r.setArcHeight(3);
            r.setLayoutX(rnd.nextDouble() * w);
            r.setLayoutY(-20);
            Duration dur = Duration.seconds(1.8 + rnd.nextDouble() * 1.4);
            Duration delay = Duration.seconds(rnd.nextDouble() * 0.5);
            TranslateTransition fall = new TranslateTransition(dur, r);
            fall.setByY(h + 60);
            fall.setByX(rnd.nextDouble() * 180 - 90);
            fall.setDelay(delay);
            RotateTransition spin = new RotateTransition(dur, r);
            spin.setByAngle(rnd.nextDouble() * 720 - 360);
            spin.setDelay(delay);
            all.getChildren().addAll(fall, spin);
            layer.getChildren().add(r);
        }
        all.setOnFinished(e -> toastHost.getChildren().remove(layer));
        all.play();
    }

    /* ---------------- text ---------------- */
    public static Label h1(String t) { return styled(t, "h1"); }
    public static Label h2(String t) { return styled(t, "h2"); }
    public static Label h3(String t) { return styled(t, "h3"); }
    public static Label muted(String t) { return styled(t, "muted"); }
    public static Label hint(String t) { Label l = styled(t, "hint"); l.setWrapText(true); return l; }
    public static Label sub(String t) { Label l = styled(t, "muted"); l.setWrapText(true); return l; }

    private static Label styled(String text, String cls) {
        Label l = new Label(text);
        l.getStyleClass().add(cls);
        return l;
    }

    public static VBox field(String label, Node input) {
        return new VBox(5, styled(label, "field-label"), input);
    }

    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    public static Button primary(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("primary");
        return b;
    }

    public static Button small(Button b) { b.getStyleClass().add("small"); return b; }
    public static Button danger(Button b) { b.getStyleClass().add("danger"); return b; }

    public static VBox card(Node... children) {
        VBox v = new VBox(14, children);
        v.getStyleClass().add("card");
        return v;
    }

    public static VBox emptyState(String title, String message) {
        VBox v = new VBox(6, h3(title), sub(message));
        v.setAlignment(Pos.CENTER);
        v.getStyleClass().add("empty");
        return v;
    }

    /* ---------------- visual components ---------------- */
    public static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String w : String.valueOf(name == null ? "?" : name).trim().split("\\s+")) {
            if (!w.isEmpty() && sb.length() < 2) sb.append(Character.toUpperCase(w.charAt(0)));
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255));
    }

    /** Round avatar whose colour is derived from the user id. */
    public static Node avatar(int id, String name, double size) {
        double hue = (id * 47L) % 360;
        Color bg = dark ? Color.hsb(hue, 0.40, 0.28) : Color.hsb(hue, 0.14, 0.97);
        Color fg = dark ? Color.hsb(hue, 0.25, 0.95) : Color.hsb(hue, 0.60, 0.36);
        Circle c = new Circle(size / 2, bg);
        Label l = new Label(initials(name));
        l.setStyle("-fx-text-fill: " + hex(fg) + "; -fx-font-weight: bold; -fx-font-size: " + Math.round(size * 0.36) + "px;");
        StackPane sp = new StackPane(c, l);
        sp.setMinSize(size, size);
        sp.setPrefSize(size, size);
        sp.setMaxSize(size, size);
        return sp;
    }

    /** Circular gauge that animates up to the value, with the percentage in the middle. */
    public static Node ring(double percent, double size) {
        double stroke = size >= 90 ? 9 : 7;
        double r = (size - stroke) / 2;
        double c = size / 2;
        double p = Math.max(0, Math.min(100, percent));
        Circle track = new Circle(c, c, r);
        track.setFill(Color.TRANSPARENT);
        track.setStroke(line());
        track.setStrokeWidth(stroke);
        Pane pane = new Pane(track);
        if (p > 0) {
            Arc arc = new Arc(c, c, r, r, 90, 0);
            arc.setType(ArcType.OPEN);
            arc.setFill(Color.TRANSPARENT);
            arc.setStroke(blue());
            arc.setStrokeWidth(stroke);
            arc.setStrokeLineCap(StrokeLineCap.ROUND);
            pane.getChildren().add(arc);
            Timeline tl = new Timeline(new KeyFrame(Duration.millis(1000),
                    new KeyValue(arc.lengthProperty(), -360 * p / 100.0, Interpolator.EASE_OUT)));
            tl.setDelay(Duration.millis(150));
            tl.play();
        }
        pane.setMinSize(size, size);
        pane.setPrefSize(size, size);
        pane.setMaxSize(size, size);
        Label l = new Label(Math.round(percent) + "%");
        l.getStyleClass().add("ring-label");
        if (size >= 90) l.setStyle("-fx-font-size: 20px;");
        StackPane sp = new StackPane(pane, l);
        sp.setMinSize(size, size);
        sp.setMaxSize(size, size);
        return sp;
    }

    /** Horizontal progress bar (0..1) that animates from empty. */
    public static Node xpBar(double fraction) {
        Region fill = new Region();
        fill.getStyleClass().add("xp-fill");
        fill.setMinWidth(0);
        StackPane track = new StackPane(fill);
        track.getStyleClass().add("xp-track");
        track.setAlignment(Pos.CENTER_LEFT);
        track.setMinHeight(12);
        track.setPrefHeight(12);
        track.setMaxHeight(12);
        DoubleProperty p = new SimpleDoubleProperty(0);
        fill.maxWidthProperty().bind(track.widthProperty().multiply(p));
        fill.prefWidthProperty().bind(track.widthProperty().multiply(p));
        Timeline tl = new Timeline(new KeyFrame(Duration.millis(1100),
                new KeyValue(p, Math.max(0, Math.min(1, fraction)), Interpolator.EASE_OUT)));
        tl.setDelay(Duration.millis(200));
        tl.play();
        return track;
    }

    public static Node levelBadge(int level, double size) {
        Rectangle bg = new Rectangle(size, size);
        bg.setArcWidth(size * 0.62);
        bg.setArcHeight(size * 0.62);
        bg.setFill(blue());
        bg.setEffect(new DropShadow(14, 0, 5, Color.color(blue().getRed(), blue().getGreen(), blue().getBlue(), 0.4)));
        Label l = new Label(String.valueOf(level));
        l.setStyle("-fx-text-fill: " + hex(onBlue()) + "; -fx-font-weight: bold; -fx-font-size: " + Math.round(size * 0.42) + "px;");
        StackPane sp = new StackPane(bg, l);
        sp.setMinSize(size, size);
        sp.setMaxSize(size, size);
        return sp;
    }

    /** Two opposing arrows, used between the "get" and "give" sides. */
    public static Node swapGlyph(Color color, double scale) {
        SVGPath p = new SVGPath();
        p.setContent("M7 12 H24 M24 12 L19 7 M24 12 L19 17 M25 21 H8 M8 21 L13 16 M8 21 L13 26");
        p.setFill(Color.TRANSPARENT);
        p.setStroke(color);
        p.setStrokeWidth(2.4);
        p.setStrokeLineCap(StrokeLineCap.ROUND);
        p.setStrokeLineJoin(StrokeLineJoin.ROUND);
        p.setScaleX(scale);
        p.setScaleY(scale);
        StackPane sp = new StackPane(p);
        sp.setMinWidth(30);
        sp.setPrefWidth(30);
        return sp;
    }

    public static Node brandMark(double size) {
        Rectangle bg = new Rectangle(size, size);
        bg.setArcWidth(size * 0.56);
        bg.setArcHeight(size * 0.56);
        bg.setFill(blue());
        Node glyph = swapGlyph(onBlue(), size / 32.0);
        StackPane sp = new StackPane(bg, glyph);
        sp.setMinSize(size, size);
        sp.setMaxSize(size, size);
        return sp;
    }

    public static Node dots(int level) {
        HBox h = new HBox(3);
        h.setAlignment(Pos.CENTER_LEFT);
        for (int i = 1; i <= 5; i++) h.getChildren().add(new Circle(4.5, i <= level ? blue() : line()));
        Tooltip.install(h, new Tooltip("Level " + level + " of 5: " + LEVELS[Math.max(1, Math.min(5, level)) - 1]));
        return h;
    }

    /** onTint = chip sits on a coloured "get/give" box (translucent chip); otherwise a neutral chip. */
    public static FlowPane chips(List<String> items, boolean onTint, String emptyText) {
        FlowPane fp = new FlowPane(6, 6);
        fp.setPrefWrapLength(180);
        if (items == null || items.isEmpty()) {
            Label l = new Label(emptyText);
            l.getStyleClass().add("chip-none");
            fp.getChildren().add(l);
        } else {
            for (String s : items) {
                Label l = new Label(s);
                l.getStyleClass().add(onTint ? "chip" : "chip-plain");
                fp.getChildren().add(l);
            }
        }
        return fp;
    }

    public static Label tag(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("tag");
        return l;
    }

    /** The coloured "You get" / "You give" box. */
    public static VBox side(String title, boolean get, Node content) {
        Label lab = new Label(title);
        lab.getStyleClass().addAll("side-label", get ? "side-label-get" : "side-label-give");
        VBox box = new VBox(6, lab, content);
        box.getStyleClass().addAll("side", get ? "side-get" : "side-give");
        return box;
    }

    /** Equal-width get/give pair with the swap arrows between them. */
    public static HBox swapPair(VBox get, VBox give) {
        get.setMaxWidth(Double.MAX_VALUE);
        give.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(get, Priority.ALWAYS);
        HBox.setHgrow(give, Priority.ALWAYS);
        get.setPrefWidth(180);
        give.setPrefWidth(180);
        HBox h = new HBox(10, get, swapGlyph(mutedColor(), 0.8), give);
        h.setAlignment(Pos.CENTER);
        return h;
    }

    public static Label pill(String status) {
        String s = status.toLowerCase();
        Label l = new Label(s.substring(0, 1).toUpperCase() + s.substring(1));
        l.getStyleClass().add("pill");
        switch (s) {
            case "pending", "scheduled", "open" -> l.getStyleClass().add("pill-warn");
            case "accepted", "completed", "resolved", "active" -> l.getStyleClass().add("pill-ok");
            case "rejected", "cancelled", "blocked" -> l.getStyleClass().add("pill-bad");
            default -> { }
        }
        return l;
    }

    /* ---------------- tables (admin) ---------------- */
    public static <T> void addCol(TableView<T> table, String title, double width, Function<T, String> getter) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(cd -> new SimpleStringProperty(getter.apply(cd.getValue())));
        table.getColumns().add(c);
    }

    /** Returns the selected row or shows a hint and returns null. */
    public static <T> T selected(TableView<T> table) {
        T item = table.getSelectionModel().getSelectedItem();
        if (item == null) error("Please select a row first.");
        return item;
    }
}
