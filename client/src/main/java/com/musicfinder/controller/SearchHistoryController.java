package com.musicfinder.controller;

import com.musicfinder.navigation.Navigator;
import com.musicfinder.navigation.Screen;

import com.musicfinder.network.ClientConnection;
import com.musicfinder.shared.enums.Action;
import com.musicfinder.shared.request.Request;
import com.musicfinder.shared.request.payload.RemoveRowSearchHistory;
import com.musicfinder.shared.response.payload.GetHistorysData;
import com.musicfinder.shared.response.payload.HistoryData;
import com.musicfinder.shared.response.payload.SongData;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.scene.Group;

public class SearchHistoryController {

    @FXML
    private HBox historyRoot;
    @FXML
    private VBox historyTable;
    @FXML
    private Label historyMessageLabel;

    @FXML
    private Button allTabButton;

    @FXML
    private Button songsTabButton;

    @FXML
    private Button artistsTabButton;

    @FXML
    private VBox historyList;

    @FXML
    private TextField searchField;

    @FXML
    private ScrollPane historyScroll;

    private List<HistoryData> originalList = new ArrayList<>();

    private String selectedType = "";

    @FXML
    private void initialize() {

        historyMessageLabel.setText("");

        searchField.textProperty().addListener(
                (observable, oldValue, newValue) -> filterHistory(newValue));

        allTabButton.setOnAction(event -> selectType(""));
        songsTabButton.setOnAction(event -> selectType("song"));
        artistsTabButton.setOnAction(event -> selectType("artist"));

        getAll();
    }

    @FXML
    private void getAll() {
        try {
            Request request = Request.of(Action.GET_HISTORY, null);
            ClientConnection.getInstance().send(request, response -> {
                if (!response.isSuccess()) {
                    Platform.runLater(() -> historyMessageLabel.setText("Không thể tải lịch sử tìm kiếm."));
                    return;
                }
                GetHistorysData data = response.dataAs(GetHistorysData.class);
                Platform.runLater(() -> {
                    originalList.clear();
                    if (data != null && data.lists() != null) {
                        originalList.addAll(data.lists());
                    }
                    filterHistory(searchField.getText());
                });
            });
        } catch (Exception e) {
            // TODO: handle exception
            StatusDialog.error(historyTable.getScene().getWindow(), "Kết nối server thất bại!");
        }
    }

    @FXML
    private void handleClearHistory() {
        Boolean confirm = ConfirmDialog.show(historyList.getScene().getWindow(), "History", "Xác nhận thao tác xóa");
        if (!confirm) {
            StatusDialog.error(historyList.getScene().getWindow(), "Hủy thao tác xóa lịch sử.");
            return;
        }
        try {
            Request request = Request.of(Action.CLEAR_HISTORY, null);
            ClientConnection.getInstance().send(request, response -> {
                Platform.runLater(() -> {
                    if (!response.isSuccess()) {
                        StatusDialog.error(historyList.getScene().getWindow(),
                                "Không thể kết nối server để xóa lịch sử.");
                        return;
                    }

                    StatusDialog.success(historyList.getScene().getWindow(), "Xóa lịch sử thành công!");
                    originalList.clear();
                    filterHistory(searchField.getText());
                });
            });
        } catch (Exception e) {
            // TODO: handle exception
            StatusDialog.error(historyList.getScene().getWindow(), "Không thể kết nối server để xóa lịch sử.");
        }
    }

    private void handleRemoveRow(HistoryData history, Button removeButton) {
        removeButton.setDisable(true);
        Boolean confirm = ConfirmDialog.show(historyList.getScene().getWindow(), "History", "Xác nhận thao tác xóa");
        if (!confirm) {
            removeButton.setDisable(false);
            StatusDialog.error(historyList.getScene().getWindow(), "Hủy thao tác xóa lịch sử.");
            return;
        }
        Request request = Request.of(Action.REMOVE_ROW_HISTORY, new RemoveRowSearchHistory(history.historyId()));
        try {
            ClientConnection.getInstance().send(request, response -> Platform.runLater(() -> {
                removeButton.setDisable(false);
                if (!response.isSuccess()) {
                    StatusDialog.error(historyList.getScene().getWindow(), "Không thể kết nối server để xóa lịch sử.");
                    return;
                }
                if (originalList != null) {
                    originalList.remove(history);
                    filterHistory(searchField.getText());
                }
                StatusDialog.success(historyList.getScene().getWindow(), "Xóa lịch sử thành công!");
            }));
        } catch (IOException e) {
            removeButton.setDisable(false);
            StatusDialog.error(historyList.getScene().getWindow(), "Không thể kết nối server để xóa lịch sử.");
        }
    }

    private void renderHistory(List<HistoryData> lists) {
        historyList.getChildren().clear();
        for (HistoryData history : lists) {
            HBox row = createHistoryRow(history);
            historyList.getChildren().add(row);
        }
        historyScroll.setPrefHeight(
                Math.min(320, Math.max(60, lists.size() * 70)));
    }

    private void filterHistory(String keyword) {
        String key = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);

        List<HistoryData> filtered = originalList.stream()
                .filter(history -> selectedType.isEmpty()
                        || selectedType.equalsIgnoreCase(history.searchType()))
                .filter(history -> key.isEmpty()
                        || (history.keyword() != null
                                && history.keyword().toLowerCase(Locale.ROOT).contains(key)))
                .toList();

        renderHistory(filtered);
    }

    private void selectType(String type) {
        selectedType = type;

        allTabButton.getStyleClass().remove("filter-tab-active");
        songsTabButton.getStyleClass().remove("filter-tab-active");
        artistsTabButton.getStyleClass().remove("filter-tab-active");

        Button active = switch (type) {
            case "song" -> songsTabButton;
            case "artist" -> artistsTabButton;
            default -> allTabButton;
        };
        active.getStyleClass().add("filter-tab-active");

        filterHistory(searchField.getText());
    }

    private HBox createHistoryRow(HistoryData history) {
        HBox row = new HBox();
        row.getStyleClass().add("history-row");
        row.setAlignment(Pos.CENTER_LEFT);

        boolean artist = "artist".equalsIgnoreCase(history.searchType());
        SVGPath glyph = new SVGPath();
        glyph.setContent(artist
                ? "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M22 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75"
                : "M9 18V5l12-2v13 M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0z M21 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z");
        glyph.getStyleClass().add("history-type-glyph");
        Group iconGraphic = new Group(glyph);
        iconGraphic.setScaleX(0.65);
        iconGraphic.setScaleY(0.65);
        StackPane icon = new StackPane(iconGraphic);
        icon.getStyleClass().add("history-type-icon");

        Label title = new Label(history.keyword() == null ? "—" : history.keyword());
        title.getStyleClass().add("history-keyword");
        title.setMinWidth(0);
        HBox keywordCell = new HBox(10, icon, title);
        keywordCell.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);

        Label type = new Label(history.searchType() == null ? "—" : history.searchType());
        type.getStyleClass().add("history-cell");
        Label totalRecords = new Label(history.resultCount() == null ? "—" : history.resultCount().toString());
        totalRecords.getStyleClass().addAll("history-cell", "history-record-count");
        Label searchedAt = new Label(history.searchedAt() == null ? "—"
                : history.searchedAt()
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)));
        searchedAt.getStyleClass().add("history-date");

        Button searchAgain = new Button("Search again");
        searchAgain.getStyleClass().add("history-search-again");
        SVGPath arrow = new SVGPath();
        arrow.setContent("M1 5h10 M7 1l4 4-4 4");
        arrow.getStyleClass().add("arrow-svg");
        searchAgain.setGraphic(arrow);
        searchAgain.setGraphicTextGap(8);
        searchAgain.setMinWidth(0);
        Button removeButton = new Button();
        removeButton.getStyleClass().add("history-remove-row");
        removeButton.setAccessibleText("Xóa lịch sử tìm kiếm");
        removeButton.setTooltip(new javafx.scene.control.Tooltip("Xóa lịch sử tìm kiếm"));
        SVGPath trash = new SVGPath();
        trash.setContent("M2 4h12 M5 4V2h6v2 M3 4l1 11h8l1-11 M6 7v5 M10 7v5");
        trash.getStyleClass().add("history-remove-icon");
        removeButton.setGraphic(trash);
        removeButton.setDisable(history.historyId() == null);
        removeButton.setOnAction(event -> handleRemoveRow(history, removeButton));
        HBox actionCell = new HBox(8, searchAgain, removeButton);
        HBox.setHgrow(searchAgain, Priority.ALWAYS);
        actionCell.setAlignment(Pos.CENTER_LEFT);

        Region[] cells = { keywordCell, type, totalRecords, searchedAt, actionCell };
        double[] proportions = { 0.34, 0.12, 0.14, 0.20, 0.20 };
        for (int i = 0; i < cells.length; i++) {
            Region cell = cells[i];
            cell.setMinWidth(0);
            final double proportion = proportions[i];
            cell.prefWidthProperty().bind(Bindings.createDoubleBinding(
                    () -> Math.max(0, row.getWidth() - row.getInsets().getLeft() - row.getInsets().getRight())
                            * proportion,
                    row.widthProperty(), row.insetsProperty()));
            cell.maxWidthProperty().bind(cell.prefWidthProperty());
        }
        row.getChildren().addAll(cells);
        searchAgain.setOnAction(event -> Navigator.seachFromAnotherScreen(
                history.keyword(),
                history.searchType()));
        return row;

    }
}
