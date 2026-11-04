package com.learnxchange.controller;

import com.learnxchange.model.Report;
import com.learnxchange.model.Skill;
import com.learnxchange.model.User;
import com.learnxchange.service.AdminService;
import com.learnxchange.ui.UiUtil;
import com.learnxchange.util.AppContext;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Lightweight admin panel: users, skill catalog, reports and platform statistics. */
public class AdminController implements Refreshable {
    private final AdminService admin = new AdminService();

    private final VBox root = new VBox(16);
    private final StackPane body = new StackPane();
    private final TableView<User> userTable = new TableView<>();
    private final TableView<Skill> skillTable = new TableView<>();
    private final TableView<Report> reportTable = new TableView<>();
    private final FlowPane statsPane = new FlowPane(14, 14);

    public AdminController() {
        ToggleGroup group = new ToggleGroup();
        HBox tabs = new HBox(8);
        Node users = buildUsers();
        Node skills = buildSkills();
        Node reports = buildReports();
        Node stats = statsPane;
        addTab(tabs, group, "Users", users);
        addTab(tabs, group, "Skills", skills);
        addTab(tabs, group, "Reports", reports);
        addTab(tabs, group, "Statistics", stats);
        group.selectedToggleProperty().addListener((o, oldT, newT) -> {
            if (newT == null) { oldT.setSelected(true); return; }
            body.getChildren().setAll((Node) newT.getUserData());
        });
        group.getToggles().get(0).setSelected(true);

        root.getChildren().addAll(UiUtil.h1("Admin panel"), tabs, body);
    }

    private void addTab(HBox tabs, ToggleGroup group, String title, Node content) {
        ToggleButton b = new ToggleButton(title);
        b.getStyleClass().add("subtab");
        b.setToggleGroup(group);
        b.setUserData(content);
        tabs.getChildren().add(b);
    }

    @Override public Parent getView() { return root; }

    @Override
    public void refresh() {
        UiUtil.run(() -> {
            User me = AppContext.getCurrentUser();
            userTable.setItems(FXCollections.observableArrayList(admin.users(me)));
            skillTable.setItems(FXCollections.observableArrayList(admin.skills()));
            reportTable.setItems(FXCollections.observableArrayList(admin.reports(me)));
            statsPane.getChildren().clear();
            for (Map.Entry<String, Long> e : admin.stats(me).entrySet()) {
                Label value = new Label(String.valueOf(e.getValue()));
                value.getStyleClass().add("stat-value");
                VBox tile = UiUtil.card(value, UiUtil.muted(e.getKey()));
                tile.setSpacing(2);
                tile.setPrefWidth(190);
                statsPane.getChildren().add(tile);
            }
        });
    }

    private Node buildUsers() {
        UiUtil.addCol(userTable, "Name", 170, User::getName);
        UiUtil.addCol(userTable, "Email", 240, User::getEmail);
        UiUtil.addCol(userTable, "Department", 160, User::getDepartment);
        UiUtil.addCol(userTable, "Role", 80, u -> u.getRole().name().toLowerCase());
        UiUtil.addCol(userTable, "Rating", 100, u -> u.getRatingCount() == 0 ? "-"
                : String.format("%.1f (%d)", u.getAvgRating(), u.getRatingCount()));
        UiUtil.addCol(userTable, "Status", 90, u -> u.isBlocked() ? "Blocked" : "Active");
        userTable.setMinHeight(380);

        Button block = UiUtil.danger(new Button("Block"));
        block.setOnAction(e -> toggleBlock(true));
        Button unblock = new Button("Unblock");
        unblock.setOnAction(e -> toggleBlock(false));
        return new VBox(12, userTable, new HBox(10, block, unblock));
    }

    private void toggleBlock(boolean blocked) {
        User u = UiUtil.selected(userTable);
        if (u == null) return;
        UiUtil.run(() -> {
            admin.setBlocked(AppContext.getCurrentUser(), u.getId(), blocked);
            refresh();
        });
    }

    private Node buildSkills() {
        UiUtil.addCol(skillTable, "Skill", 280, Skill::name);
        UiUtil.addCol(skillTable, "Category", 220, Skill::category);
        skillTable.setMinHeight(340);

        TextField name = new TextField();
        name.setPromptText("New skill name");
        TextField category = new TextField();
        category.setPromptText("Category");
        Button add = UiUtil.primary("Add skill");
        add.setOnAction(e -> UiUtil.run(() -> {
            admin.addSkill(AppContext.getCurrentUser(), name.getText(), category.getText());
            name.clear();
            category.clear();
            refresh();
            UiUtil.info("Skill added.");
        }));
        HBox.setHgrow(name, Priority.ALWAYS);
        HBox.setHgrow(category, Priority.ALWAYS);
        HBox row = new HBox(10, name, category, add);
        row.setAlignment(Pos.CENTER_LEFT);

        Button delete = UiUtil.danger(new Button("Delete selected"));
        delete.setOnAction(e -> {
            Skill s = UiUtil.selected(skillTable);
            if (s == null || !UiUtil.confirm("Delete \"" + s.name() + "\"? It will be removed from all user profiles.")) return;
            UiUtil.run(() -> {
                admin.deleteSkill(AppContext.getCurrentUser(), s.id());
                refresh();
            });
        });
        VBox card = UiUtil.card(UiUtil.h2("Add a skill to the catalog"), row);
        return new VBox(14, card, skillTable, delete);
    }

    private Node buildReports() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
        UiUtil.addCol(reportTable, "Reported user", 160, Report::reportedName);
        UiUtil.addCol(reportTable, "Reported by", 160, Report::reporterName);
        UiUtil.addCol(reportTable, "Reason", 340, Report::reason);
        UiUtil.addCol(reportTable, "Status", 90, r -> r.status().name().toLowerCase());
        UiUtil.addCol(reportTable, "Filed", 140, r -> r.createdAt().format(fmt));
        reportTable.setMinHeight(360);
        reportTable.setPlaceholder(new Label("No reports. The community is behaving."));

        Button resolve = new Button("Mark resolved");
        resolve.setOnAction(e -> {
            Report r = UiUtil.selected(reportTable);
            if (r == null) return;
            UiUtil.run(() -> {
                admin.resolveReport(AppContext.getCurrentUser(), r.id());
                refresh();
            });
        });
        Button resolveBlock = UiUtil.danger(new Button("Resolve and block reported user"));
        resolveBlock.setOnAction(e -> {
            Report r = UiUtil.selected(reportTable);
            if (r == null || !UiUtil.confirm("Block " + r.reportedName() + " and resolve this report?")) return;
            UiUtil.run(() -> {
                admin.setBlocked(AppContext.getCurrentUser(), r.reportedId(), true);
                admin.resolveReport(AppContext.getCurrentUser(), r.id());
                refresh();
            });
        });
        return new VBox(12, reportTable, new HBox(10, resolve, resolveBlock));
    }
}
