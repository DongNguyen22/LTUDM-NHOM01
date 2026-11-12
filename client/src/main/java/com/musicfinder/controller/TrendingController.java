package com.musicfinder.controller;

import com.musicfinder.network.ClientConnection;
import com.musicfinder.shared.enums.Action;
import com.musicfinder.shared.request.Request;
import com.musicfinder.shared.request.payload.StatisticsPayload;
import com.musicfinder.shared.response.payload.GetTopSearchData;
import com.musicfinder.shared.response.payload.KeywordStatisticData;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

public class TrendingController {

    private static final double MIN_BAR_RATIO = 0.06;

    private static final String TYPE_SONG = "SONG";
    private static final String TYPE_ARTIST = "ARTIST";
    private static final int TOP_LIMIT = 10;

    @FXML
    private Label trendingMessageLabel;

    @FXML
    private Label topSongTitleLabel;

    @FXML
    private Label topSongSubtitleLabel;

    @FXML
    private Label topArtistTitleLabel;

    @FXML
    private Label topArtistSubtitleLabel;

    @FXML
    private VBox topSongsBox;

    @FXML
    private VBox topArtistsBox;

    @FXML
    private void initialize() {
        trendingMessageLabel.setText("");
        getTopSongs();
        getTopArtists();
    }

    private void getTopSongs() {
        loadTopKeywords(TYPE_SONG, topSongTitleLabel, topSongSubtitleLabel, topSongsBox);
    }

    private void getTopArtists() {
        loadTopKeywords(TYPE_ARTIST, topArtistTitleLabel, topArtistSubtitleLabel, topArtistsBox);
    }

    private void loadTopKeywords(String type, Label titleLabel, Label subtitleLabel, VBox listBox) {
        StatisticsPayload payload = new StatisticsPayload(type, TOP_LIMIT);
        Request request = Request.of(Action.GET_TOP_KEYWORDS, payload);
        try {
            ClientConnection.getInstance().send(request, response -> {
                if (!response.isSuccess()) {
                    Platform.runLater(() -> trendingMessageLabel.setText("Loading error: " + response.getMessage()));
                    return;
                }
                GetTopSearchData data = response.dataAs(GetTopSearchData.class);
                List<KeywordStatisticData> list = (data == null || data.lists() == null) ? List.of() : data.lists();
                Platform.runLater(() -> {
                    showTop(titleLabel, subtitleLabel, list);
                    renderBars(listBox, list);
                });
            });
        } catch (Exception e) {
            trendingMessageLabel.setText("Không thể kết nối server: " + e.getMessage());
        }
    }

    private void showTop(Label title, Label subtitle, List<KeywordStatisticData> list) {
        if (list.isEmpty()) {
            title.setText("—");
            subtitle.setText("No data yet");
            return;
        }
        KeywordStatisticData top = list.get(0);
        title.setText(top.keyword());
        subtitle.setText(formatCount(top.searchCount()) + " searches");
    }

    private void renderBars(VBox box, List<KeywordStatisticData> list) {
        box.getChildren().clear();
        if (list.isEmpty()) {
            Label empty = new Label("Chưa có dữ liệu");
            empty.getStyleClass().add("bar-value");
            box.getChildren().add(empty);
            return;
        }
        long max = list.get(0).searchCount(); // danh sách đã sắp xếp giảm dần
        for (int i = 0; i < list.size(); i++) {
            box.getChildren().add(createBarRow(i + 1, list.get(i), max));
        }
    }

    private HBox createBarRow(int rank, KeywordStatisticData item, long max) {
        Label rankLabel = new Label(String.format("%02d", rank));
        rankLabel.getStyleClass().add("rank-number");

        Label nameLabel = new Label(item.keyword());
        nameLabel.getStyleClass().add("bar-name");

        Region fill = new Region();
        fill.getStyleClass().add("bar-fill");

        StackPane track = new StackPane(fill);
        track.getStyleClass().add("bar-track");
        track.setAlignment(Pos.CENTER_LEFT);
        track.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(track, Priority.ALWAYS);

        double ratio = max > 0 ? Math.sqrt((double) item.searchCount() / max) : 0;
        ratio = Math.max(MIN_BAR_RATIO, Math.min(1, ratio));
        fill.maxWidthProperty().bind(track.widthProperty().multiply(ratio));

        Label valueLabel = new Label(formatCount(item.searchCount()));
        valueLabel.getStyleClass().add("bar-value");

        HBox row = new HBox(rankLabel, nameLabel, track, valueLabel);
        row.getStyleClass().add("bar-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private String formatCount(long count) {
        if (count >= 1_000_000) {
            return String.format("%.1fM", count / 1_000_000.0);
        }
        if (count >= 1_000) {
            return String.format("%.1fk", count / 1_000.0);
        }
        return String.valueOf(count);
    }

    @FXML
    private void handleNavPlaceholder() {
        trendingMessageLabel.setText("This screen will be connected when we build it in the next step.");
    }
}
