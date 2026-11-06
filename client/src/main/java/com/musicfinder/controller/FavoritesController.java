package com.musicfinder.controller;

import com.musicfinder.navigation.Navigator;
import com.musicfinder.navigation.Screen;

import com.musicfinder.network.ClientConnection;
import com.musicfinder.player.PlayerManager;
import com.musicfinder.shared.enums.Action;
import com.musicfinder.shared.enums.PlayList_Type;
import com.musicfinder.shared.request.Request;
import com.musicfinder.shared.request.payload.Add_RemoveToPlaylistPayload;
import com.musicfinder.shared.response.payload.GetFavoritesData;
import com.musicfinder.shared.response.payload.SongData;

import javafx.application.Platform;
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
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FavoritesController {

    @FXML
    private TextField searchField;
    @FXML
    private Label songCountLabel;
    @FXML
    private Label emptyLabel;
    @FXML
    private VBox songList;
    @FXML
    private ScrollPane songScroll;
    private final List<SongData> originalList = new ArrayList<>();

    @FXML
    private void initialize() {
        searchField.textProperty().addListener(
                (observable, oldValue, newValue) -> filterSongs(newValue));
        loadData();
        updateSongCount();
    }

    private void loadData() {
        Request request = Request.of(Action.GET_FAVORITES, null);
        try {
            ClientConnection.getInstance().send(request, response -> {
                if (!response.isSuccess()) {
                    Platform.runLater(() -> StatusDialog.error(songList.getScene().getWindow(), response.getMessage()));
                    return;
                }

                GetFavoritesData data = response.dataAs(GetFavoritesData.class);
                Platform.runLater(() -> {
                    originalList.clear();
                    if (data != null && data.songs() != null) {
                        originalList.addAll(data.songs());
                    }
                    filterSongs(searchField.getText());
                    updateSongCount();
                });
            });
        } catch (Exception e) {
            // initialize() occurs before the root is attached to a Scene.
            System.err.println("Cannot load favorite songs: " + e.getMessage());
        }
    }

    private void updateSongCount() {
        int count = originalList.size();
        songCountLabel.setText(count + (count == 1 ? " song" : " songs"));
    }

    private void renderSongs(List<SongData> songs) {
        songList.getChildren().clear();

        for (int i = 0; i < songs.size(); i++) {
            HBox row = createSongRow(songs.get(i), i + 1);
            songList.getChildren().add(row);
        }

        boolean isEmpty = songs.isEmpty();

        emptyLabel.setVisible(isEmpty);
        emptyLabel.setManaged(isEmpty);
        songScroll.setPrefHeight(
                Math.min(320, Math.max(60, songs.size() * 68)));
    }

    private HBox createSongRow(SongData song, int index) {

        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("favorite-row");

        // ===== RANK =====

        Label rankLabel = new Label(String.format("%02d", index));

        rankLabel.getStyleClass().add("favorite-rank");

        // ===== THUMBNAIL =====

        ImageView thumbnail = new ImageView();

        thumbnail.setFitWidth(52);
        thumbnail.setFitHeight(52);
        thumbnail.setPreserveRatio(false);

        thumbnail.getStyleClass().add("favorite-cover");

        if (song.thumbnail_url() != null && !song.thumbnail_url().isBlank()) {

            try {
                Image image = new Image(song.thumbnail_url(), true);
                thumbnail.setImage(image);
            } catch (Exception e) {
                System.out.println(
                        "Cannot load thumbnail: "
                                + song.thumbnail_url());
            }
        }

        // ===== TITLE =====

        Label titleLabel = new Label(song.title());

        titleLabel.getStyleClass().add("favorite-song-title");

        // ===== ARTIST =====

        String artists = song.artistNames() == null ? ""
                : String.join(", ", song.artistNames());

        Label artistLabel = new Label(artists);

        artistLabel.getStyleClass().add("favorite-song-artist");
        artistLabel.setPrefWidth(155);
        artistLabel.setMinWidth(155);
        artistLabel.setMaxWidth(155);

        Integer seconds = song.durations_seconds();
        Label durationLabel = new Label(seconds != null && seconds >= 0
                ? String.format("%d:%02d", seconds / 60, seconds % 60)
                : "--:--");
        durationLabel.getStyleClass().add("favorite-duration");
        durationLabel.setPrefWidth(75);
        durationLabel.setMinWidth(75);
        durationLabel.setMaxWidth(75);

        // ===== SONG INFO =====

        VBox songInfo = new VBox(5);

        songInfo.setAlignment(Pos.CENTER_LEFT);

        songInfo.getChildren().add(titleLabel);

        HBox.setHgrow(songInfo, Priority.ALWAYS);

        songInfo.setMinWidth(0);

        Button playListButton = new Button("♫");
        playListButton.getStyleClass().addAll("favorite-action-button", "favorite-action-playlist");

        // Tạo icon thùng rác từ SVG path
        SVGPath trashIcon = new SVGPath();
        trashIcon.setContent(
                "M3 6h18 M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6 M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2 M10 11v6 M14 11v6");
        trashIcon.getStyleClass().add("favorite-trash-icon");

        // Gán vào Button
        Button deleteButton = new Button("", trashIcon);
        deleteButton.getStyleClass().addAll("favorite-action-button", "favorite-action-delete");
        // ===== PLAY =====

        Button playButton = new Button("▶");

        playButton.getStyleClass().addAll("favorite-action-button", "favorite-action-play");

        HBox actionGroup = new HBox(8, playListButton, deleteButton, playButton);
        actionGroup.setAlignment(Pos.CENTER_RIGHT);
        actionGroup.getStyleClass().add("favorite-actions");
        actionGroup.setPrefWidth(120);
        actionGroup.setMinWidth(120);
        actionGroup.setMaxWidth(120);

        row.getChildren().addAll(rankLabel, thumbnail, songInfo,
                artistLabel, durationLabel, actionGroup);

        // event
        playButton.setOnAction(event -> {
            handlePlaySong(song);
            event.consume();
        });
        deleteButton.setOnAction(event -> {
            handleRemoveFromFavorite(song);
        });
        // playListButton.setOnAction(event -> {
        // handleAddToPlaylist(song);
        // });

        return row;
    }

    private void handlePlaySong(SongData song) {

        PlayerManager.getInstance().playSong(originalList, song);
    }

    private void filterSongs(String keyword) {
        String key = keyword == null
                ? ""
                : keyword.trim().toLowerCase(Locale.ROOT);

        if (key.isEmpty()) {
            renderSongs(originalList);
            return;
        }

        List<SongData> filteredSongs = originalList.stream()
                .filter(song -> {
                    String title = song.title() == null
                            ? ""
                            : song.title().toLowerCase(Locale.ROOT);

                    String artists = song.artistNames() == null
                            ? ""
                            : String.join(", ", song.artistNames())
                                    .toLowerCase(Locale.ROOT);

                    return title.contains(key) || artists.contains(key);
                })
                .toList();

        renderSongs(filteredSongs);
    }

    @FXML
    private void handleRemoveFromFavorite(SongData song) {

        Boolean confirm = ConfirmDialog.show(songList.getScene().getWindow(), "Hello", "Confirm your action");
        if (!confirm) {
            return;
        }
        Add_RemoveToPlaylistPayload payload = new Add_RemoveToPlaylistPayload(null, song, PlayList_Type.FAVORITES);
        Request request = Request.of(Action.REMOVE_FAVORITE, payload);
        try {
            ClientConnection.getInstance().send(request, response -> {
                if (!response.isSuccess()) {
                    Platform.runLater(() -> {
                        StatusDialog.error(emptyLabel.getScene().getWindow(), response.getMessage());
                    });
                    return;
                }
                originalList.remove(song);
                Platform.runLater(() -> {
                    StatusDialog.success(emptyLabel.getScene().getWindow(), response.getMessage());
                    filterSongs(searchField.getText());
                    updateSongCount();
                });
            });
        } catch (Exception e) {
            // TODO: handle exception
            StatusDialog.error(emptyLabel.getScene().getWindow(), e.getMessage());

        }
    }

}
