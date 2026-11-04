package com.learnxchange.controller;

import com.learnxchange.model.User;
import com.learnxchange.service.AuthService;
import com.learnxchange.ui.SceneManager;
import com.learnxchange.ui.UiUtil;
import com.learnxchange.util.AppContext;
import com.learnxchange.util.ServiceException;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

public class LoginController {
    private final AuthService auth = new AuthService();
    private final HBox root = new HBox();

    private final TextField loginEmail = new TextField();
    private final PasswordField loginPassword = new PasswordField();
    private final TextField regName = new TextField();
    private final TextField regEmail = new TextField();
    private final TextField regDept = new TextField();
    private final PasswordField regPassword = new PasswordField();
    private final PasswordField regConfirm = new PasswordField();

    public LoginController() {
        Node hero = buildHero();
        Node panel = buildPanel();
        HBox.setHgrow(panel, Priority.ALWAYS);
        root.getChildren().addAll(hero, panel);
    }

    public Parent getView() { return root; }

    /* ---------- left: pitch + example swap ticket ---------- */
    private Node buildHero() {
        Label brandName = new Label("LearnXchange");
        brandName.getStyleClass().add("hero-brand");
        HBox brand = new HBox(10, UiUtil.brandMark(34), brandName);
        brand.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Trade what you know for what you want to learn.");
        title.setWrapText(true);
        title.getStyleClass().add("hero-title");
        Label sub = new Label("No courses, no fees. Tell us the skills you can teach and the ones you want, "
                + "and we'll find people whose answers are the mirror image of yours.");
        sub.setWrapText(true);
        sub.getStyleClass().add("hero-sub");

        VBox hero = new VBox(24, brand, title, sub, demoTicket());
        hero.getStyleClass().add("hero");
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.setPadding(new Insets(48));
        hero.setMinWidth(540);
        hero.setPrefWidth(540);
        hero.setMaxWidth(540);
        return hero;
    }

    private Node demoTicket() {
        VBox who = new VBox(2, UiUtil.h3("Maya Rao"), UiUtil.muted("Design, 3rd year"));
        HBox top = new HBox(12, UiUtil.avatar(5, "Maya Rao", 44), who, UiUtil.spacer(), UiUtil.ring(92, 64));
        top.setAlignment(Pos.CENTER_LEFT);
        HBox pair = UiUtil.swapPair(
                UiUtil.side("You get", true, UiUtil.chips(List.of("Photoshop"), true, "")),
                UiUtil.side("You give", false, UiUtil.chips(List.of("Java"), true, "")));
        VBox ticket = new VBox(14, top, pair);
        ticket.getStyleClass().add("ticket");
        ticket.setMaxWidth(440);
        return ticket;
    }

    /* ---------- right: sign in / create account ---------- */
    private Node buildPanel() {
        ToggleGroup group = new ToggleGroup();
        ToggleButton tLogin = new ToggleButton("Sign in");
        ToggleButton tReg = new ToggleButton("Create account");
        for (ToggleButton t : List.of(tLogin, tReg)) {
            t.setToggleGroup(group);
            t.getStyleClass().add("seg");
            t.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(t, Priority.ALWAYS);
        }
        tLogin.setSelected(true);
        group.selectedToggleProperty().addListener((o, oldT, newT) -> { if (newT == null) oldT.setSelected(true); });
        HBox seg = new HBox(4, tLogin, tReg);
        seg.getStyleClass().add("seg-bar");

        VBox loginForm = buildLogin();
        VBox registerForm = buildRegister();
        registerForm.setVisible(false);
        registerForm.setManaged(false);
        tLogin.selectedProperty().addListener((o, was, now) -> {
            loginForm.setVisible(now);
            loginForm.setManaged(now);
            registerForm.setVisible(!now);
            registerForm.setManaged(!now);
        });

        VBox card = new VBox(20, seg, loginForm, registerForm);
        card.getStyleClass().add("auth-card");
        card.setMaxWidth(400);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane panel = new StackPane(card, themeCorner());
        panel.setPadding(new Insets(28));
        return panel;
    }

    private Node themeCorner() {
        Button t = UiUtil.themeButton(SceneManager::showLogin);
        StackPane.setAlignment(t, Pos.TOP_RIGHT);
        return t;
    }

    private VBox buildLogin() {
        loginEmail.setPromptText("you@example.com");
        loginPassword.setPromptText("Your password");
        loginPassword.setOnAction(e -> doLogin());
        Button btn = UiUtil.primary("Sign in");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setDefaultButton(false);
        btn.setOnAction(e -> doLogin());
        return new VBox(14, UiUtil.field("Email", loginEmail), UiUtil.field("Password", loginPassword), btn);
    }

    private VBox buildRegister() {
        regName.setPromptText("Full name");
        regEmail.setPromptText("you@example.com");
        regDept.setPromptText("Optional");
        regPassword.setPromptText("8+ characters, a letter and a digit");
        regConfirm.setPromptText("Repeat password");
        Button btn = UiUtil.primary("Create account");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> doRegister());
        return new VBox(11, UiUtil.field("Full name", regName), UiUtil.field("Email", regEmail),
                UiUtil.field("Department", regDept), UiUtil.field("Password", regPassword),
                UiUtil.field("Confirm password", regConfirm), btn);
    }

    private void doLogin() {
        UiUtil.run(() -> {
            User u = auth.login(loginEmail.getText(), loginPassword.getText());
            AppContext.setCurrentUser(u);
            SceneManager.showMain();
        });
    }

    private void doRegister() {
        UiUtil.run(() -> {
            if (!regPassword.getText().equals(regConfirm.getText()))
                throw new ServiceException("Passwords do not match.");
            User u = auth.register(regName.getText(), regEmail.getText(), regPassword.getText(), regDept.getText());
            AppContext.setCurrentUser(u);
            SceneManager.showMain();
            UiUtil.info("Welcome! Add the skills you can teach and the ones you want to learn.");
        });
    }
}
