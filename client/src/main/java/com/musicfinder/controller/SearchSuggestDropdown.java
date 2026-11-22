package com.musicfinder.controller;

import com.musicfinder.network.ClientConnection;
import com.musicfinder.shared.enums.Action;
import com.musicfinder.shared.request.Request;
import com.musicfinder.shared.request.payload.SearchPayload;
import com.musicfinder.shared.response.payload.SuggestData;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.util.Duration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SearchSuggestDropdown {
    private static final Duration DELAY = Duration.millis(350);
    private static final int MAX_SUGGESTIONS = 6;

    private final TextField field;
    private final Consumer<String> onSelect;
    private final ContextMenu menu = new ContextMenu();
    private final PauseTransition debounce = new PauseTransition(DELAY);

    private long version = 0;
    private boolean suppress = false;

    public SearchSuggestDropdown(TextField field, Consumer<String> onSelect) {
        this.field = field;
        this.onSelect = onSelect;

        menu.getStyleClass().add("suggest-menu");
        menu.setAutoHide(true);

        debounce.setOnFinished(e -> request(field.getText()));

        field.textProperty().addListener((obs, oldText, newText) -> {
            version++;
            debounce.stop();
            if (suppress) {
                return;
            }
            if (newText == null || newText.trim().isEmpty()) {
                menu.hide();
                return;
            }
            debounce.playFromStart();
        });

        field.focusedProperty().addListener((obs, wasFocused, focused) -> {
            if (!focused) {
                cancel();
            }
        });
    }

    public void cancel() {
        version++;
        debounce.stop();
        menu.hide();
    }

    public void setTextSilently(String text) {
        suppress = true;
        try {
            field.setText(text);
        } finally {
            suppress = false;
        }
    }

    private void request(String rawKeyword) {
        String keyword = rawKeyword == null ? "" : rawKeyword.trim();
        if (keyword.isEmpty()) {
            menu.hide();
            return;
        }
        final long requestVersion = version;
        Request request = Request.of(Action.SUGGEST, new SearchPayload(keyword));
        try {
            ClientConnection.getInstance().send(request, response -> Platform.runLater(() -> {

                if (requestVersion != version || !field.isFocused()) {
                    return;
                }
                if (!response.isSuccess()) {
                    menu.hide();
                    return;
                }
                SuggestData data;
                try {
                    data = response.dataAs(SuggestData.class);
                } catch (Exception e) {
                    menu.hide();
                    return;
                }
                show(data == null ? null : data.suggestions());
            }));
        } catch (IOException e) {
            menu.hide();
        }
    }

    private void show(List<String> suggestions) {
        List<CustomMenuItem> items = new ArrayList<>();
        if (suggestions != null) {
            for (String suggestion : suggestions) {
                if (suggestion == null || suggestion.isBlank()) {
                    continue;
                }
                Label label = new Label(suggestion);
                label.getStyleClass().add("suggest-item-label");
                label.setTextOverrun(OverrunStyle.ELLIPSIS);
                label.setPrefWidth(Math.max(field.getWidth() - 24, 200));

                CustomMenuItem item = new CustomMenuItem(label, true);
                item.getStyleClass().add("suggest-item");
                item.setOnAction(e -> select(suggestion));
                items.add(item);
                if (items.size() >= MAX_SUGGESTIONS) {
                    break;
                }
            }
        }
        if (items.isEmpty()) {
            menu.hide();
            return;
        }
        menu.getItems().setAll(items);
        if (!menu.isShowing()) {
            menu.show(field, Side.BOTTOM, 0, 4);
        }
    }

    private void select(String suggestion) {
        setTextSilently(suggestion);
        field.positionCaret(suggestion.length());
        if (onSelect != null) {
            onSelect.accept(suggestion);
        }
    }
}
