package com.musicfinder.controller;

import com.musicfinder.navigation.Navigator;
import com.musicfinder.navigation.Screen;
import com.musicfinder.network.ClientConnection;
import com.musicfinder.shared.enums.Action;
import com.musicfinder.shared.request.Request;
import com.musicfinder.shared.request.payload.StatisticsPayload;
import com.musicfinder.shared.response.payload.GetHistorysData;
import com.musicfinder.shared.response.payload.SearchSongData;
import com.musicfinder.shared.response.payload.SearchArtistData;
import com.musicfinder.shared.response.payload.SongData;
import com.musicfinder.shared.response.payload.ArtistData;
import com.musicfinder.shared.response.payload.HistoryData;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.io.IOException;
import java.util.List;

public class HomeController {
    private static final String[] RECENT_COVERS = {
            "cover-blue-flower.png", "cover-concert.png", "cover-lanterns.png", "cover-red-flower.png"
    };
    @FXML private TextField searchField;
    @FXML private Label homeMessageLabel;
    @FXML private HBox recentBox;
    @FXML private HBox artistsBox;
    @FXML private HBox recommendedBox;
    @FXML private VBox popularBox;

    @FXML private void initialize() {
        homeMessageLabel.setText("");
        showEmpty(recentBox, "Loading recent searches...");
        showEmpty(recommendedBox, "Loading recommendations...");
        showEmpty(popularBox, "Loading popular searches...");
        showEmpty(artistsBox, "Loading trending artists...");
        loadHistory();
        loadTopSongs();
        loadTopArtists();
        loadRecommendations();
    }

    private void loadHistory() {
        try {
            ClientConnection.getInstance().send(Request.of(Action.GET_RECENT_SEARCHES, new StatisticsPayload(null, 4)), response -> {
                if (!response.isSuccess()) {
                    Platform.runLater(() -> {
                        showEmpty(recentBox, "Could not load search history.");
                    });
                    return;
                }
                GetHistorysData data = response.dataAs(GetHistorysData.class);
                List<HistoryData> items = data == null || data.lists() == null ? List.of() : data.lists();
                Platform.runLater(() -> renderHistory(items));
            });
        } catch (Exception e) {
            showEmpty(recentBox, "Could not connect to the server.");
        }
    }

    private void renderHistory(List<HistoryData> items) {
        recentBox.getChildren().clear();
        for (HistoryData item : items) {
            if (item.keyword() == null || item.keyword().isBlank()
                    || !("song".equalsIgnoreCase(item.searchType()) || "artist".equalsIgnoreCase(item.searchType()))) continue;
            String cover = RECENT_COVERS[recentBox.getChildren().size() % RECENT_COVERS.length];
            String coverUrl = getClass().getResource("/com/musicfinder/assets/" + cover).toExternalForm();
            VBox card = card(item.keyword(), "artist".equalsIgnoreCase(item.searchType()) ? "Artist search" : "Song search", coverUrl);
            card.setOnMouseClicked(e -> search(item.keyword(), item.searchType()));
            recentBox.getChildren().add(card);
        }
        if (recentBox.getChildren().isEmpty()) showEmpty(recentBox, "Your recent searches will appear here.");
    }

    private void loadTopSongs() {
        try {
            ClientConnection.getInstance().send(Request.of(Action.GET_TOP_SONGS, new StatisticsPayload(null, 3)), response -> {
                if (!response.isSuccess()) {
                    Platform.runLater(() -> showEmpty(popularBox, "Could not load popular songs."));
                    return;
                }
                SearchSongData data = response.dataAs(SearchSongData.class);
                List<SongData> songs = data == null || data.songs() == null ? List.of() : data.songs();
                Platform.runLater(() -> renderSongs(songs));
            });
        } catch (Exception e) { showEmpty(popularBox, "Could not connect to the server."); }
    }

    private void renderSongs(List<SongData> songs) {
        popularBox.getChildren().clear();
        for (int i = 0; i < Math.min(3, songs.size()); i++) {
            SongData song = songs.get(i);
            VBox text = new VBox(3, label(song.title(), "list-title"), label(artists(song), "list-subtitle"));
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox row = new HBox(12, label(String.format("%02d", i + 1), "rank-number"),
                    image(song.thumbnail_url(), 38, 38), text,
                    label(countText(song.searchCount()), "list-meta"), label("›", "list-play"));
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("popular-row");
            row.setOnMouseClicked(e -> Navigator.openSongDetail(song));
            popularBox.getChildren().add(row);
        }
        if (songs.isEmpty()) showEmpty(popularBox, "No popular songs yet.");
    }

    private void loadTopArtists() {
        try {
            ClientConnection.getInstance().send(Request.of(Action.GET_TOP_ARTISTS, new StatisticsPayload(null, 3)), response -> {
                if (!response.isSuccess()) {
                    Platform.runLater(() -> showEmpty(artistsBox, "Could not load trending artists."));
                    return;
                }
                SearchArtistData data = response.dataAs(SearchArtistData.class);
                List<ArtistData> artists = data == null || data.artists() == null ? List.of() : data.artists();
                Platform.runLater(() -> renderArtists(artists));
            });
        } catch (Exception e) { showEmpty(artistsBox, "Could not connect to the server."); }
    }

    private void renderArtists(List<ArtistData> artists) {
        artistsBox.getChildren().clear();
        for (ArtistData artist : artists.stream().limit(3).toList()) {
            ImageView photo = image(artist.image_url(), 54, 54);
            photo.setClip(new Circle(27, 27, 27));
            VBox card = new VBox(8, photo, label(artist.name(), "artist-name"), label(countText(artist.searchCount()), "artist-meta"));
            card.setAlignment(Pos.CENTER);
            card.setMaxWidth(Double.MAX_VALUE);
            card.setMinWidth(0);
            HBox.setHgrow(card, Priority.ALWAYS);
            card.getStyleClass().add("artist-card");
            card.setOnMouseClicked(e -> Navigator.openArtistDetail(artist));
            artistsBox.getChildren().add(card);
        }
        if (artists.isEmpty()) showEmpty(artistsBox, "No trending artists yet.");
    }

    private void loadRecommendations() {
        try {
            ClientConnection.getInstance().send(Request.of(Action.GET_RECOMMENDATIONS, null), response -> {
                if (!response.isSuccess()) {
                    Platform.runLater(() -> showEmpty(recommendedBox, "Could not load recommendations."));
                    return;
                }
                SearchSongData data = response.dataAs(SearchSongData.class);
                List<SongData> songs = data == null || data.songs() == null ? List.of() : data.songs();
                Platform.runLater(() -> {
                    recommendedBox.getChildren().clear();
                    for (SongData song : songs.stream().limit(4).toList()) {
                        VBox card = card(song.title(), artists(song), song.thumbnail_url());
                        card.setOnMouseClicked(e -> Navigator.openSongDetail(song));
                        recommendedBox.getChildren().add(card);
                    }
                    if (songs.isEmpty()) showEmpty(recommendedBox, "No recommendations yet.");
                });
            });
        } catch (Exception e) { showEmpty(recommendedBox, "Could not connect to the server."); }
    }

    private VBox card(String title, String subtitle, String imageUrl) {
        ImageView cover = image(imageUrl, 160, 145);
        StackPane frame = new StackPane(cover);
        frame.getStyleClass().add("home-image-placeholder");
        frame.setPrefHeight(145);
        frame.setMinHeight(145);
        VBox card = new VBox(7, frame, label(title, "card-title"), label(subtitle, "card-subtitle"));
        card.getStyleClass().add("song-card");
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        cover.fitWidthProperty().bind(card.widthProperty().subtract(16));
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(cover.fitWidthProperty());
        clip.setHeight(145); clip.setArcWidth(10); clip.setArcHeight(10);
        cover.setClip(clip);
        return card;
    }

    private ImageView image(String url, double width, double height) {
        ImageView view = new ImageView();
        if (url != null && !url.isBlank()) {
            try { view.setImage(new Image(url, true)); } catch (IllegalArgumentException ignored) { }
        }
        view.setFitWidth(width); view.setFitHeight(height); view.setSmooth(true);
        return view;
    }

    private String artists(SongData song) {
        return song.artistNames() == null || song.artistNames().isEmpty() ? "Unknown artist" : String.join(", ", song.artistNames());
    }

    private String countText(Long count) {
        return count == null ? "" : formatCount(count) + " searches";
    }
    private Label label(String value, String style) {
        Label label = new Label(value == null ? "—" : value);
        label.getStyleClass().add(style);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }
    private void showEmpty(javafx.scene.layout.Pane pane, String message) {
        pane.getChildren().setAll(label(message, "home-empty"));
    }
    private String formatCount(long count) {
        if (count >= 1_000_000) return String.format("%.1fM", count / 1_000_000.0);
        if (count >= 1_000) return String.format("%.1fk", count / 1_000.0);
        return Long.toString(count);
    }
    private void search(String keyword, String type) { Navigator.seachFromAnotherScreen(keyword, type); }
    @FXML private void handleSearch() throws IOException {
        String keyword = searchField.getText();
        if (keyword == null || keyword.isBlank()) Navigator.goTo(Screen.SEARCH);
        else search(keyword.trim(), "song");
    }
    @FXML private void handleExplore() throws IOException { Navigator.goTo(Screen.SEARCH); }
    @FXML private void handleGoToSearch() throws IOException { Navigator.goTo(Screen.SEARCH); }
    @FXML private void handleGoToArtists() throws IOException { Navigator.goTo(Screen.ARTISTS); }
    @FXML private void handleGoSearchHistory() throws IOException { Navigator.goTo(Screen.SEARCH_HISTORY); }
    @FXML private void handleGoToTrending() throws IOException { Navigator.goTo(Screen.TRENDING); }
}
