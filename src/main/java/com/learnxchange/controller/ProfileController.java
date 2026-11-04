package com.learnxchange.controller;

import com.learnxchange.model.Availability;
import com.learnxchange.model.Skill;
import com.learnxchange.model.User;
import com.learnxchange.model.UserSkill;
import com.learnxchange.service.ProfileService;
import com.learnxchange.ui.UiUtil;
import com.learnxchange.util.AppContext;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProfileController implements Refreshable {
    private final ProfileService profiles = new ProfileService();

    private final VBox root = new VBox(18);
    private final Label whoLabel = new Label();
    private final TextField nameField = new TextField();
    private final TextField deptField = new TextField();
    private final TextArea bioArea = new TextArea();
    private final Map<String, ToggleButton> slotButtons = new LinkedHashMap<>();

    private final ComboBox<Skill> skillCombo = new ComboBox<>();
    private final ComboBox<UserSkill.Type> typeCombo = new ComboBox<>();
    private final Slider levelSlider = new Slider(1, 5, 3);
    private final Label levelLabel = new Label(UiUtil.LEVELS[2]);
    private final HBox skillLists = new HBox(18);

    public ProfileController() {
        // ----- about you -----
        bioArea.setPromptText("A short bio (max 500 characters)");
        bioArea.setPrefRowCount(3);
        bioArea.setWrapText(true);
        FlowPane slots = new FlowPane(8, 8);
        for (String s : Availability.SLOTS) {
            ToggleButton t = new ToggleButton(s);
            t.getStyleClass().add("slot-toggle");
            slotButtons.put(s, t);
            slots.getChildren().add(t);
        }
        HBox nameDept = new HBox(14, UiUtil.field("Name", nameField), UiUtil.field("Department", deptField));
        HBox.setHgrow(nameDept.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(nameDept.getChildren().get(1), Priority.ALWAYS);

        Button save = UiUtil.primary("Save profile");
        save.setOnAction(e -> saveProfile());
        VBox about = UiUtil.card(UiUtil.h2("About you"), whoLabel, nameDept,
                UiUtil.field("Bio", bioArea), UiUtil.field("When are you free?", slots), save);
        whoLabel.getStyleClass().add("muted");
        save.setMaxWidth(160);

        // ----- add a skill -----
        typeCombo.getItems().addAll(UserSkill.Type.values());
        typeCombo.setValue(UserSkill.Type.TEACH);
        typeCombo.setConverter(new StringConverter<>() {
            @Override public String toString(UserSkill.Type t) {
                return t == null ? "" : (t == UserSkill.Type.TEACH ? "can teach this" : "want to learn this");
            }
            @Override public UserSkill.Type fromString(String s) { return null; }
        });
        skillCombo.setPromptText("Choose a skill");
        skillCombo.setPrefWidth(260);
        typeCombo.setPrefWidth(190);
        levelSlider.setMajorTickUnit(1);
        levelSlider.setMinorTickCount(0);
        levelSlider.setSnapToTicks(true);
        levelSlider.setShowTickMarks(true);
        levelSlider.setPrefWidth(190);
        levelSlider.valueProperty().addListener((o, a, b) ->
                levelLabel.setText(UiUtil.LEVELS[Math.max(1, Math.min(5, (int) Math.round(b.doubleValue()))) - 1]));

        VBox levelBox = new VBox(5, new HBox(6, styledFieldLabel("Level:"), levelLabel), levelSlider);
        Button add = UiUtil.primary("Add skill");
        add.setOnAction(e -> addSkill());
        HBox addRow = new HBox(14, UiUtil.field("Skill", skillCombo), UiUtil.field("I...", typeCombo), levelBox, add);
        addRow.setAlignment(Pos.BOTTOM_LEFT);
        VBox addCard = UiUtil.card(UiUtil.h2("Add a skill"), addRow,
                UiUtil.hint("Adding a skill you already have updates its level."));

        root.getChildren().addAll(
                new VBox(4, UiUtil.h1("My profile"), UiUtil.sub("Matches are only as good as what you tell us here.")),
                about, addCard, skillLists);
    }

    private Label styledFieldLabel(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("field-label");
        return l;
    }

    @Override public Parent getView() { return root; }

    @Override
    public void refresh() {
        UiUtil.run(() -> {
            User me = profiles.getUser(AppContext.getCurrentUser().getId());
            AppContext.setCurrentUser(me);
            whoLabel.setText("Signed in as " + me.getEmail() + (me.getRatingCount() == 0 ? ""
                    : String.format("  -  rated %.1f / 5 from %d review(s)", me.getAvgRating(), me.getRatingCount())));
            nameField.setText(me.getName());
            deptField.setText(me.getDepartment());
            bioArea.setText(me.getBio());
            slotButtons.forEach((slot, t) -> t.setSelected(me.getAvailability().contains(slot)));
            skillCombo.setItems(FXCollections.observableArrayList(profiles.allSkills()));
            paintSkills();
        });
    }

    private void paintSkills() {
        List<UserSkill> skills = profiles.skillsOf(AppContext.getCurrentUser().getId());
        VBox teach = skillCard("I can teach", skills, UserSkill.Type.TEACH, "Nothing yet. What are you good at?");
        VBox learn = skillCard("I want to learn", skills, UserSkill.Type.LEARN, "Nothing yet. What would you like to pick up?");
        HBox.setHgrow(teach, Priority.ALWAYS);
        HBox.setHgrow(learn, Priority.ALWAYS);
        teach.setPrefWidth(300);
        learn.setPrefWidth(300);
        skillLists.getChildren().setAll(teach, learn);
    }

    private VBox skillCard(String title, List<UserSkill> skills, UserSkill.Type type, String empty) {
        VBox card = UiUtil.card(UiUtil.h2(title));
        card.setSpacing(0);
        boolean any = false;
        for (UserSkill s : skills) {
            if (s.type() != type) continue;
            any = true;
            VBox name = new VBox(0, new Label(s.skillName()), UiUtil.muted(s.category()));
            name.getChildren().get(0).setStyle("-fx-font-weight: bold;");
            Button remove = UiUtil.danger(UiUtil.small(new Button("Remove")));
            remove.setOnAction(e -> removeSkill(s));
            HBox row = new HBox(12, name, UiUtil.spacer(), UiUtil.dots(s.proficiency()), remove);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("row-line");
            card.getChildren().add(row);
        }
        if (!any) card.getChildren().add(UiUtil.muted(empty));
        return card;
    }

    private void saveProfile() {
        UiUtil.run(() -> {
            Set<String> chosen = new LinkedHashSet<>();
            slotButtons.forEach((slot, t) -> { if (t.isSelected()) chosen.add(slot); });
            profiles.updateProfile(AppContext.getCurrentUser(), nameField.getText(), deptField.getText(),
                    bioArea.getText(), chosen);
            UiUtil.info("Profile saved.");
            UiUtil.progressChanged();
        });
    }

    private void addSkill() {
        UiUtil.run(() -> {
            profiles.saveSkill(AppContext.getCurrentUser().getId(), skillCombo.getValue(),
                    typeCombo.getValue(), (int) Math.round(levelSlider.getValue()));
            paintSkills();
            UiUtil.info("Skill saved.");
            UiUtil.progressChanged();
        });
    }

    private void removeSkill(UserSkill s) {
        UiUtil.run(() -> {
            profiles.removeSkill(s.id(), AppContext.getCurrentUser().getId());
            paintSkills();
        });
    }
}
