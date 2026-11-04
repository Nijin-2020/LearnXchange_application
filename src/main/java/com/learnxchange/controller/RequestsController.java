package com.learnxchange.controller;

import com.learnxchange.model.LearningSession;
import com.learnxchange.model.SwapRequest;
import com.learnxchange.model.User;
import com.learnxchange.service.SessionService;
import com.learnxchange.service.SwapService;
import com.learnxchange.ui.UiUtil;
import com.learnxchange.util.AppContext;
import com.learnxchange.util.ServiceException;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class RequestsController implements Refreshable {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final SwapService swaps = new SwapService();
    private final SessionService sessions = new SessionService();

    private final VBox root = new VBox(14);
    private final VBox incomingBox = new VBox(12);
    private final VBox outgoingBox = new VBox(12);

    public RequestsController() {
        root.getChildren().addAll(
                new VBox(4, UiUtil.h1("Requests"), UiUtil.sub("Accept a swap to schedule your first session.")),
                UiUtil.h2("Received"), incomingBox,
                UiUtil.h2("Sent"), outgoingBox);
        VBox.setMargin(root.getChildren().get(1), new javafx.geometry.Insets(10, 0, 0, 0));
        VBox.setMargin(root.getChildren().get(3), new javafx.geometry.Insets(18, 0, 0, 0));
    }

    @Override public Parent getView() { return root; }

    @Override
    public void refresh() {
        UiUtil.run(() -> {
            User me = AppContext.getCurrentUser();
            fill(incomingBox, swaps.incoming(me), true, "No requests received yet.");
            fill(outgoingBox, swaps.outgoing(me), false, "You haven't sent any requests. Find someone on the Matches tab.");
        });
    }

    private void fill(VBox box, List<SwapRequest> items, boolean incoming, String emptyText) {
        box.getChildren().clear();
        if (items.isEmpty()) {
            box.getChildren().add(UiUtil.emptyState("Nothing here", emptyText));
            return;
        }
        for (SwapRequest r : items) box.getChildren().add(card(r, incoming));
        UiUtil.reveal(box.getChildren());
    }

    private Node card(SwapRequest r, boolean incoming) {
        int otherId = incoming ? r.senderId() : r.receiverId();
        String other = incoming ? r.senderName() : r.receiverName();
        String get = incoming ? r.offeredSkillName() : r.requestedSkillName();
        String give = incoming ? r.requestedSkillName() : r.offeredSkillName();

        HBox top = new HBox(10, UiUtil.avatar(otherId, other, 34), UiUtil.h3(other),
                UiUtil.muted(r.createdAt().format(FMT)), UiUtil.spacer(), UiUtil.pill(r.status().name()));
        top.setAlignment(Pos.CENTER_LEFT);

        HBox pair = UiUtil.swapPair(
                UiUtil.side("You get", true, UiUtil.chips(List.of(get), true, "")),
                UiUtil.side("You give", false, UiUtil.chips(List.of(give), true, "")));
        Label msg = new Label(r.message());
        msg.setWrapText(true);
        msg.getStyleClass().add("quote");

        VBox card = new VBox(12, top, pair, msg);
        card.getStyleClass().add("card");

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        if (incoming && r.status() == SwapRequest.Status.PENDING) {
            Button accept = UiUtil.small(UiUtil.primary("Accept"));
            accept.setOnAction(e -> respond(r, true));
            Button reject = UiUtil.small(new Button("Decline"));
            reject.setOnAction(e -> respond(r, false));
            actions.getChildren().addAll(reject, accept);
        } else if (!incoming && r.status() == SwapRequest.Status.PENDING) {
            Button cancel = UiUtil.small(new Button("Cancel request"));
            cancel.setOnAction(e -> cancelRequest(r));
            actions.getChildren().add(cancel);
        } else if (r.status() == SwapRequest.Status.ACCEPTED) {
            Button schedule = UiUtil.small(UiUtil.primary("Schedule a session"));
            schedule.setOnAction(e -> schedule(r));
            actions.getChildren().add(schedule);
        }
        if (!actions.getChildren().isEmpty()) card.getChildren().add(actions);
        return card;
    }

    private void respond(SwapRequest r, boolean accept) {
        UiUtil.run(() -> {
            User me = AppContext.getCurrentUser();
            if (accept) swaps.accept(me, r.id()); else swaps.reject(me, r.id());
            refresh();
            if (accept) UiUtil.info("Accepted. Now schedule a session.");
        });
    }

    private void cancelRequest(SwapRequest r) {
        if (!UiUtil.confirm("Cancel this request?")) return;
        UiUtil.run(() -> {
            swaps.cancel(AppContext.getCurrentUser(), r.id());
            refresh();
        });
    }

    private void schedule(SwapRequest r) {
        DatePicker date = new DatePicker(LocalDate.now().plusDays(1));
        date.setMaxWidth(Double.MAX_VALUE);
        TextField time = new TextField("18:00");
        time.setPromptText("HH:mm (24-hour)");
        ComboBox<LearningSession.Mode> mode = new ComboBox<>();
        mode.getItems().addAll(LearningSession.Mode.values());
        mode.setValue(LearningSession.Mode.ONLINE);
        mode.setMaxWidth(Double.MAX_VALUE);
        TextField where = new TextField();
        where.setPromptText("Meeting link (Meet / Zoom) or place");

        GridPane g = UiUtil.form();
        g.setPrefWidth(440);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);
        g.add(UiUtil.h2("Schedule a session"), 0, 0, 2, 1);
        g.add(UiUtil.muted(r.offeredSkillName() + " for " + r.requestedSkillName()), 0, 1, 2, 1);
        g.add(UiUtil.field("Date", date), 0, 2);
        g.add(UiUtil.field("Time", time), 1, 2);
        g.add(UiUtil.field("Where", mode), 0, 3, 2, 1);
        g.add(UiUtil.field("Meeting link or place", where), 0, 4, 2, 1);

        if (UiUtil.showDialog("Schedule session", g)) {
            UiUtil.run(() -> {
                LocalTime t;
                try {
                    t = LocalTime.parse(time.getText().trim());
                } catch (DateTimeParseException ex) {
                    throw new ServiceException("Time must be in HH:mm format, e.g. 18:30.");
                }
                sessions.schedule(AppContext.getCurrentUser(), r.id(), date.getValue(), t, mode.getValue(), where.getText());
                UiUtil.info("Session scheduled. You'll find it under Sessions.");
            });
        }
    }
}
