package com.learnxchange.controller;

import com.learnxchange.model.User;
import com.learnxchange.service.AuthService;
import com.learnxchange.service.ProgressService;
import com.learnxchange.service.SwapService;
import com.learnxchange.ui.SceneManager;
import com.learnxchange.ui.UiUtil;
import com.learnxchange.util.AppContext;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.Map;

public class MainController {
    private static final double CONTENT_WIDTH = 1120;

    private final BorderPane root = new BorderPane();
    private final StackPane content = new StackPane();
    private final SwapService swapService = new SwapService();
    private final ProgressService progressService = new ProgressService();
    private final ToggleGroup navGroup = new ToggleGroup();
    private final Map<ToggleButton, Refreshable> screens = new LinkedHashMap<>();
    private final Map<ToggleButton, Node> scrollers = new LinkedHashMap<>();
    private final Map<String, ToggleButton> byKey = new LinkedHashMap<>();
    private final Label levelChip = new Label();
    private ToggleButton requestsButton;
    private MatchesController matches;
    private Timeline poller;
    private long lastPending = -1;

    public MainController() { this(0); }

    /** startIndex lets the window be rebuilt (e.g. after a theme change) on the same tab. */
    public MainController(int startIndex) {
        User me = AppContext.getCurrentUser();

        Label brand = new Label("LearnXchange");
        brand.getStyleClass().add("brand-name");
        HBox nav = new HBox(4);
        nav.setAlignment(Pos.CENTER_LEFT);

        Button logout = UiUtil.small(new Button("Sign out"));
        logout.setOnAction(e -> {
            new AuthService().logout();
            SceneManager.showLogin();
        });
        Button theme = UiUtil.themeButton(() -> SceneManager.reloadMain(selectedIndex()));
        Label who = new Label(me.getName() + (me.isAdmin() ? " (admin)" : ""));
        levelChip.getStyleClass().add("lvl-chip");
        levelChip.setVisible(false);
        levelChip.setManaged(false);

        HBox top = new HBox(16, UiUtil.brandMark(32), brand, nav, UiUtil.spacer(),
                UiUtil.avatar(me.getId(), me.getName(), 32), who, levelChip, theme, logout);
        top.setAlignment(Pos.CENTER_LEFT);
        top.getStyleClass().add("topbar");

        if (me.isAdmin()) {
            addScreen(nav, "admin", "Admin panel", new AdminController());
        } else {
            HomeController home = new HomeController();
            matches = new MatchesController();
            addScreen(nav, "home", "Home", home);
            addScreen(nav, "matches", "Matches", matches);
            addScreen(nav, "profile", "My profile", new ProfileController());
            requestsButton = addScreen(nav, "requests", "Requests", new RequestsController());
            addScreen(nav, "sessions", "Sessions", new SessionsController());
            home.setNavigate(key -> { ToggleButton b = byKey.get(key); if (b != null) b.setSelected(true); });
            home.setViewMatch(m -> matches.openProfile(m));
            matches.setOnAddSkills(() -> byKey.get("profile").setSelected(true));
        }

        navGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (newT == null) { // never allow "nothing selected"
                if (oldT != null) oldT.setSelected(true);
                return;
            }
            show((ToggleButton) newT);
        });

        // "/" jumps to the match search, like on the website
        root.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.SLASH || matches == null || root.getScene() == null) return;
            if (root.getScene().getFocusOwner() instanceof TextInputControl) return;
            if (navGroup.getSelectedToggle() == byKey.get("matches")) {
                matches.focusSearch();
                e.consume();
            }
        });

        root.setTop(top);
        root.setCenter(content);
        if (!me.isAdmin()) {
            UiUtil.setProgressHook(this::refreshProgress);
            poller = new Timeline(new KeyFrame(Duration.seconds(30), e -> pollPending(me)));
            poller.setCycleCount(Animation.INDEFINITE);
            poller.play();
        } else {
            UiUtil.setProgressHook(null);
        }
        int idx = Math.max(0, Math.min(startIndex, navGroup.getToggles().size() - 1));
        navGroup.getToggles().get(idx).setSelected(true);
    }

    public Parent getView() { return root; }

    /** Stops background polling when this window is replaced. */
    public void stop() {
        if (poller != null) poller.stop();
    }

    private int selectedIndex() {
        return Math.max(0, navGroup.getToggles().indexOf(navGroup.getSelectedToggle()));
    }

    private ToggleButton addScreen(HBox nav, String key, String title, Refreshable screen) {
        ToggleButton b = new ToggleButton(title);
        b.setToggleGroup(navGroup);
        b.getStyleClass().add("nav-button");
        nav.getChildren().add(b);

        Parent view = screen.getView();
        if (view instanceof Region r) r.setMaxWidth(CONTENT_WIDTH);
        StackPane holder = new StackPane(view);
        holder.setAlignment(Pos.TOP_CENTER);
        holder.setPadding(new Insets(26, 24, 40, 24));
        ScrollPane sp = new ScrollPane(holder);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("app-scroll");
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        screens.put(b, screen);
        scrollers.put(b, sp);
        byKey.put(key, b);
        return b;
    }

    private void show(ToggleButton b) {
        Node scroller = scrollers.get(b);
        content.getChildren().setAll(scroller);
        scroller.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(220), scroller);
        fade.setToValue(1);
        fade.play();
        screens.get(b).refresh();
        updateBadge();
        refreshProgress();
    }

    /** Shows the number of pending incoming swap requests on the Requests tab (a lightweight notification). */
    private void updateBadge() {
        if (requestsButton == null) return;
        try {
            long n = swapService.pendingIncomingCount(AppContext.getCurrentUser());
            setBadge(n);
            lastPending = n;
        } catch (Exception ignored) {
            // badge is cosmetic; never interrupt the user for it
        }
    }

    private void setBadge(long n) {
        requestsButton.setText(n > 0 ? "Requests (" + n + ")" : "Requests");
    }

    /** Checks every 30 seconds for new swap requests and tells the user. */
    private void pollPending(User me) {
        try {
            long n = swapService.pendingIncomingCount(me);
            if (lastPending >= 0 && n > lastPending) UiUtil.info("You have a new swap request!");
            lastPending = n;
            setBadge(n);
        } catch (Exception ignored) {
            // try again next time
        }
    }

    /** Recalculates the level chip in the top bar and celebrates if the user levelled up. */
    private void refreshProgress() {
        if (matches == null) return; // admin has no progress
        try {
            User me = AppContext.getCurrentUser();
            ProgressService.Progress p = progressService.forUser(me);
            levelChip.setText("Lv " + p.level() + " " + p.levelName());
            levelChip.setVisible(true);
            levelChip.setManaged(true);
            LevelUp.check(me.getId(), p);
        } catch (Exception ignored) {
            // cosmetic
        }
    }
}
