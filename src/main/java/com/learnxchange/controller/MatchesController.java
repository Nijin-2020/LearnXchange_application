package com.learnxchange.controller;

import com.learnxchange.model.*;
import com.learnxchange.service.AdminService;
import com.learnxchange.service.MatchingService;
import com.learnxchange.service.ProfileService;
import com.learnxchange.service.RatingService;
import com.learnxchange.service.SwapService;
import com.learnxchange.ui.UiUtil;
import com.learnxchange.util.AppContext;
import com.learnxchange.util.ServiceException;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class MatchesController implements Refreshable {
    private static final String ALL_CATEGORIES = "All categories";
    private static final String ANY_SLOT = "Any availability";

    private final MatchingService matching = new MatchingService();
    private final ProfileService profiles = new ProfileService();
    private final SwapService swaps = new SwapService();
    private final RatingService ratings = new RatingService();
    private final AdminService admin = new AdminService();

    private final VBox root = new VBox(18);
    private final VBox list = new VBox(14);
    private final TextField skillField = new TextField();
    private final ComboBox<String> categoryBox = new ComboBox<>();
    private final ComboBox<String> slotBox = new ComboBox<>();
    private final ComboBox<String> sortBox = new ComboBox<>();
    private List<MatchResult> current = List.of();
    private Runnable onAddSkills = () -> { };

    public MatchesController() {
        skillField.setPromptText("Skill they teach, e.g. Photoshop");
        skillField.setOnAction(e -> refresh());
        HBox.setHgrow(skillField, Priority.ALWAYS);
        categoryBox.setPrefWidth(190);
        slotBox.setPrefWidth(190);
        slotBox.getItems().add(ANY_SLOT);
        slotBox.getItems().addAll(Availability.SLOTS);
        slotBox.setValue(ANY_SLOT);

        Button search = UiUtil.primary("Search");
        search.setOnAction(e -> refresh());
        Button reset = new Button("Reset");
        reset.setOnAction(e -> {
            skillField.clear();
            categoryBox.setValue(ALL_CATEGORIES);
            slotBox.setValue(ANY_SLOT);
            refresh();
        });
        sortBox.getItems().addAll("Best match", "Highest rated", "Name A to Z");
        sortBox.setValue("Best match");
        sortBox.setPrefWidth(150);
        sortBox.setOnAction(e -> renderList());
        HBox filters = new HBox(10, skillField, categoryBox, slotBox, sortBox, search, reset);
        filters.setAlignment(Pos.CENTER_LEFT);

        VBox head = new VBox(4, UiUtil.h1("People worth swapping with"),
                UiUtil.sub("Ranked by how well your skills fit in both directions, plus proficiency, shared free time and past reviews."));
        root.getChildren().addAll(head, filters, list);
    }

    public void setOnAddSkills(Runnable r) { this.onAddSkills = r; }

    public void focusSearch() { skillField.requestFocus(); }

    /** Opens the profile dialog for a match (also used by the Home dashboard). */
    public void openProfile(MatchResult m) { UiUtil.run(() -> showProfile(m)); }

    @Override public Parent getView() { return root; }

    @Override
    public void refresh() {
        UiUtil.run(() -> {
            User me = AppContext.getCurrentUser();
            if (categoryBox.getItems().isEmpty()) {
                categoryBox.getItems().add(ALL_CATEGORIES);
                categoryBox.getItems().addAll(profiles.allSkills().stream()
                        .map(Skill::category).distinct().sorted().toList());
                categoryBox.setValue(ALL_CATEGORIES);
            }
            String cat = ALL_CATEGORIES.equals(categoryBox.getValue()) ? null : categoryBox.getValue();
            String slot = ANY_SLOT.equals(slotBox.getValue()) ? null : slotBox.getValue();
            List<MatchResult> results = matching.findMatches(me, new MatchFilter(skillField.getText(), cat, slot));

            current = results;
            sortBox.setValue("Best match");
            renderList();
        });
    }

    private void renderList() {
        UiUtil.run(() -> {
            List<MatchResult> results = new java.util.ArrayList<>(current);
            switch (String.valueOf(sortBox.getValue())) {
                case "Highest rated" -> results.sort(java.util.Comparator
                        .comparingDouble((MatchResult m) -> m.user().getAvgRating()).reversed()
                        .thenComparing(java.util.Comparator.comparingDouble(MatchResult::percentage).reversed()));
                case "Name A to Z" -> results.sort(java.util.Comparator.comparing((MatchResult m) -> m.user().getName()));
                default -> { }
            }
            list.getChildren().clear();
            if (results.isEmpty()) {
                Button add = UiUtil.primary("Add your skills");
                add.setOnAction(e -> onAddSkills.run());
                VBox empty = UiUtil.emptyState("No matches yet",
                        "Matches appear when someone teaches what you want to learn, or wants to learn what you teach. "
                                + "Add both kinds of skills to your profile.");
                empty.getChildren().add(add);
                list.getChildren().add(empty);
            } else {
                for (MatchResult m : results) list.getChildren().add(ticket(m));
            }
            UiUtil.reveal(list.getChildren());
        });
    }

    /* ---------- the swap ticket ---------- */
    private Node ticket(MatchResult m) {
        User u = m.user();
        HBox nameRow = new HBox(8, UiUtil.h3(u.getName()));
        nameRow.setAlignment(Pos.CENTER_LEFT);
        if (!m.theyTeachMe().isEmpty() && !m.iTeachThem().isEmpty()) nameRow.getChildren().add(UiUtil.tag("Two-way"));
        VBox who = new VBox(2, nameRow,
                UiUtil.muted(u.getDepartment().isBlank() ? "No department" : u.getDepartment()),
                UiUtil.muted(ratingText(u)));
        HBox whoBox = new HBox(12, UiUtil.avatar(u.getId(), u.getName(), 44), who);
        whoBox.setAlignment(Pos.CENTER_LEFT);
        whoBox.setMinWidth(200);
        whoBox.setPrefWidth(220);
        whoBox.setMaxWidth(220);

        HBox swap = UiUtil.swapPair(
                UiUtil.side("You get", true, UiUtil.chips(m.theyTeachMe(), true, "Nothing you listed")),
                UiUtil.side("You give", false, UiUtil.chips(m.iTeachThem(), true, "Nothing they listed")));
        HBox.setHgrow(swap, Priority.ALWAYS);

        Button request = UiUtil.small(UiUtil.primary("Request swap"));
        request.setMaxWidth(Double.MAX_VALUE);
        request.setOnAction(e -> UiUtil.run(() -> sendRequest(m)));
        Button view = UiUtil.small(new Button("View profile"));
        view.setMaxWidth(Double.MAX_VALUE);
        view.setOnAction(e -> UiUtil.run(() -> showProfile(m)));
        VBox actions = new VBox(8, request, view);
        actions.setMinWidth(120);
        actions.setAlignment(Pos.CENTER);

        HBox ticket = new HBox(20, whoBox, swap, UiUtil.ring(m.percentage(), 76), actions);
        ticket.setAlignment(Pos.CENTER_LEFT);
        ticket.getStyleClass().add("ticket");
        UiUtil.liftOnHover(ticket);
        return ticket;
    }

    private static String ratingText(User u) {
        return u.getRatingCount() == 0 ? "New member"
                : String.format("%.1f / 5 (%d review%s)", u.getAvgRating(), u.getRatingCount(), u.getRatingCount() == 1 ? "" : "s");
    }

    /* ---------- profile dialog ---------- */
    private void showProfile(MatchResult m) {
        User u = m.user();
        List<UserSkill> skills = profiles.skillsOf(u.getId());

        VBox who = new VBox(2, UiUtil.h2(u.getName()),
                UiUtil.muted(u.getDepartment().isBlank() ? "No department" : u.getDepartment()), UiUtil.muted(ratingText(u)));
        HBox header = new HBox(14, UiUtil.avatar(u.getId(), u.getName(), 56), who);
        header.setAlignment(Pos.CENTER_LEFT);

        Label bio = new Label(u.getBio().isBlank() ? "No bio yet." : u.getBio());
        bio.setWrapText(true);

        VBox free = new VBox(8, UiUtil.h3("Free time"),
                UiUtil.chips(List.copyOf(u.getAvailability()), false, "Not set"));

        VBox teach = skillColumn("Can teach", skills, UserSkill.Type.TEACH);
        VBox learn = skillColumn("Wants to learn", skills, UserSkill.Type.LEARN);
        HBox.setHgrow(teach, Priority.ALWAYS);
        HBox.setHgrow(learn, Priority.ALWAYS);
        HBox cols = new HBox(24, teach, learn);

        VBox why = new VBox(8, UiUtil.h3(String.format("Why %.0f%%", m.percentage())),
                bar("Skill fit", m.skillScore()), bar("Reverse fit", m.reverseScore()),
                bar("Proficiency", m.proficiencyScore()), bar("Availability", m.availabilityScore()),
                bar("Rating", m.ratingScore()));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
        VBox reviews = new VBox(6, UiUtil.h3("Reviews"));
        List<Rating> rs = ratings.reviewsFor(u.getId());
        if (rs.isEmpty()) reviews.getChildren().add(UiUtil.muted("No reviews yet."));
        for (Rating r : rs) {
            Label l = new Label(r.score() + "/5 from " + r.raterName() + " on " + r.createdAt().format(fmt)
                    + (r.feedback().isBlank() ? "" : "\n" + r.feedback()));
            l.setWrapText(true);
            l.getStyleClass().add("row-line");
            reviews.getChildren().add(l);
        }

        VBox body = new VBox(16, header, bio, free, cols, why, reviews);
        body.setPadding(new Insets(22));
        ScrollPane sp = new ScrollPane(body);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("app-scroll");
        sp.setPrefViewportWidth(600);
        sp.setPrefViewportHeight(540);

        ButtonType request = new ButtonType("Request swap", ButtonBar.ButtonData.OK_DONE);
        ButtonType report = new ButtonType("Report", ButtonBar.ButtonData.LEFT);
        Optional<ButtonType> choice = UiUtil.dialog("Profile - " + u.getName(), sp, report, ButtonType.CLOSE, request);
        if (choice.isPresent()) {
            if (choice.get() == request) sendRequest(m);
            else if (choice.get() == report) reportUser(m);
        }
    }

    private VBox skillColumn(String title, List<UserSkill> skills, UserSkill.Type type) {
        VBox col = new VBox(4, UiUtil.h3(title));
        boolean any = false;
        for (UserSkill s : skills) {
            if (s.type() != type) continue;
            any = true;
            VBox name = new VBox(0, new Label(s.skillName()), UiUtil.muted(s.category()));
            HBox row = new HBox(10, name, UiUtil.spacer(), UiUtil.dots(s.proficiency()));
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("row-line");
            col.getChildren().add(row);
        }
        if (!any) col.getChildren().add(UiUtil.muted("Nothing listed"));
        return col;
    }

    private HBox bar(String label, double value) {
        Label name = new Label(label);
        name.setMinWidth(100);
        ProgressBar pb = new ProgressBar(value);
        pb.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pb, Priority.ALWAYS);
        Label pct = new Label(Math.round(value * 100) + "%");
        pct.setMinWidth(40);
        pct.setAlignment(Pos.CENTER_RIGHT);
        HBox row = new HBox(10, name, pb, pct);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /* ---------- send request ---------- */
    private void sendRequest(MatchResult m) {
        User me = AppContext.getCurrentUser();
        List<UserSkill> myTeach = profiles.skillsOf(me.getId()).stream()
                .filter(s -> s.type() == UserSkill.Type.TEACH).toList();
        List<UserSkill> theirTeach = profiles.skillsOf(m.user().getId()).stream()
                .filter(s -> s.type() == UserSkill.Type.TEACH).toList();
        if (myTeach.isEmpty()) {
            UiUtil.error("Add at least one skill you can teach on your profile before sending a request.");
            return;
        }
        if (theirTeach.isEmpty()) {
            UiUtil.error(m.user().getName() + " has not listed any skills to teach yet.");
            return;
        }

        ComboBox<UserSkill> offer = new ComboBox<>(FXCollections.observableArrayList(myTeach));
        ComboBox<UserSkill> want = new ComboBox<>(FXCollections.observableArrayList(theirTeach));
        offer.setMaxWidth(Double.MAX_VALUE);
        want.setMaxWidth(Double.MAX_VALUE);
        offer.getSelectionModel().selectFirst();
        want.getSelectionModel().selectFirst();
        // pre-select the skills that actually make this a match
        myTeach.stream().filter(s -> m.iTeachThem().contains(s.skillName())).findFirst().ifPresent(offer::setValue);
        theirTeach.stream().filter(s -> m.theyTeachMe().contains(s.skillName())).findFirst().ifPresent(want::setValue);
        TextArea message = new TextArea();
        message.setPromptText("Say hi and mention what you hope to learn.");
        message.setPrefRowCount(4);
        message.setWrapText(true);

        GridPane g = UiUtil.form();
        g.setPrefWidth(460);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);
        Label title = UiUtil.h2("Request a swap with " + m.user().getName());
        g.add(title, 0, 0, 2, 1);
        g.add(UiUtil.field("You teach", offer), 0, 1);
        g.add(UiUtil.field("You learn", want), 1, 1);
        g.add(UiUtil.field("Message", message), 0, 2, 2, 1);

        if (UiUtil.showDialog("Send swap request", g)) {
            UiUtil.run(() -> {
                if (offer.getValue() == null) throw new ServiceException("Choose a skill you will teach.");
                if (want.getValue() == null) throw new ServiceException("Choose a skill you want to learn.");
                swaps.send(me, m.user().getId(), offer.getValue().skillId(), want.getValue().skillId(), message.getText());
                UiUtil.info("Swap request sent to " + m.user().getName() + ".");
            });
        }
    }

    private void reportUser(MatchResult m) {
        TextArea reason = new TextArea();
        reason.setPromptText("What happened?");
        reason.setPrefRowCount(4);
        reason.setWrapText(true);
        VBox box = new VBox(10, UiUtil.h2("Report " + m.user().getName()), reason,
                UiUtil.hint("An administrator will review this report."));
        box.setPadding(new Insets(20));
        box.setPrefWidth(420);
        if (UiUtil.showDialog("Report user", box)) {
            UiUtil.run(() -> {
                admin.reportUser(AppContext.getCurrentUser(), m.user().getId(), reason.getText());
                UiUtil.info("Report sent. An administrator will review it.");
            });
        }
    }
}
