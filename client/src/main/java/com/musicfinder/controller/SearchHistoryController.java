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
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

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
    private VBox songList;

    @FXML
    private TextField searchField;

    private List<HistoryData> originalList;

    @FXML
    private void initialize() {

        historyMessageLabel.setText("");
    }

    @FXML
    private void getAll() {
        try {
            Request request = Request.of(Action.GET_HISTORY, null);
            ClientConnection.getInstance().send(request, response -> {
                if (!response.isSuccess()) {
                    Platform.runLater(() -> {
                        StatusDialog.error(historyTable.getScene().getWindow(), "Kết nối server thất bại!");
                        return;
                    });
                }
                originalList = response.dataAs(GetHistorysData.class).lists();
            });
        } catch (Exception e) {
            // TODO: handle exception
            StatusDialog.error(historyTable.getScene().getWindow(), "Kết nối server thất bại!");
        }
    }
    @FXML
    private void handleClearHistory() throws IOException {
        Request request = Request.of(Action.CLEAR_HISTORY, null);
        ClientConnection.getInstance().send(request, response -> {
            // UI UPDATE LATER
        });
    }

    @FXML
    private void handleRemoveRow() throws IOException {
        // RemoveRowSearchHistory payload = new RemoveRowSearchHistory();
        Request request = Request.of(Action.REMOVE_ROW_HISTORY, null);
        ClientConnection.getInstance().send(request, response -> {
            // UI UPDATE LATER
        });
    }

    private void filterSong(String keyword) {
        String key = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        List<HistoryData> filteredHistory = originalList.stream().filter(history -> {
            String key_v1 = history.keyword() == null ? "" : history.keyword().toLowerCase(Locale.ROOT);
            return key_v1.contains(key);
        }).toList();
    }
}
