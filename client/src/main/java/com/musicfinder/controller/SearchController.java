package com.musicfinder.controller;

import com.musicfinder.navigation.Navigator;
import com.musicfinder.navigation.Screen;

import com.musicfinder.network.ClientConnection;
import com.musicfinder.player.PlayerManager;
import com.musicfinder.shared.enums.Action;
import com.musicfinder.shared.enums.PlayList_Type;
import com.musicfinder.shared.request.Request;
import com.musicfinder.shared.request.payload.Add_RemoveToPlaylistPayload;
import com.musicfinder.shared.request.payload.SearchPayload;
import com.musicfinder.shared.response.payload.SearchSongData;
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
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.shape.SVGPath;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.Region;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SearchController {
    @FXML
    private StackPane loadingOverlay;

    private void setLoading(boolean loading) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> setLoading(loading));
            return;
        }
        loadingOverlay.setVisible(loading);
        loadingOverlay.setManaged(loading);
    }

    private MainLayoutController mainLayoutController;

    public void setMainLayoutController(MainLayoutController controller) {
        this.mainLayoutController = controller;
    }

    @FXML
    private TextField searchField;

    @FXML
    private Label searchMessageLabel;

    @FXML
    private VBox searchResultsList;

    @FXML
    private Label resultTitleLabel;

    @FXML
    private Label resultSubtitleLabel;

    private final List<SongData> currentSongList = new ArrayList<>();

    @FXML
    private void initialize() {
        searchMessageLabel.setText("");
    }

    @FXML
    private void handleSearch() {

        String keyword = searchField.getText() == null
                ? ""
                : searchField.getText().trim();

        if (keyword.isEmpty()) {
            searchField.requestFocus();
            showMessage("Enter a song title to search.");
            return;
        }

        SearchPayload payload = new SearchPayload(keyword);

        Request request = Request.of(
                Action.SEARCH_SONG,
                payload);
        if (loadingOverlay.isVisible()) {
            return;
        }
        setLoading(true);
        showMessage("Searching...");
        try {
            ClientConnection.getInstance()
                    .send(request, response -> {
                        Platform.runLater(() -> {
                            try {
                                if (!response.isSuccess()) {
                                    showMessage(response.getMessage());
                                    return;
                                }
                                SearchSongData data = response.dataAs(SearchSongData.class);
                                currentSongList.clear();
                                if (data != null && data.songs() != null) {
                                    currentSongList.addAll(data.songs());
                                }
                                showMessage("Found " + currentSongList.size() + " songs");
                                renderSongs(currentSongList);
                            } catch (Exception e) {
                                setLoading(false);
                                showMessage("Cannot load search results: " + e.getMessage());
                            } finally {
                                setLoading(false);
                            }
                        });
                    });

        } catch (IOException e) {
            setLoading(false);
            showMessage(
                    "Cannot connect to server: "
                            + e.getMessage());
        }
    }

    @FXML
    private void handleNavPlaceholder() {
        showMessage("This screen will be connected when we build it in the next steps.");
    }

    private void showMessage(String message) {
        searchMessageLabel.setText(message);
    }

    private void renderSongs(List<SongData> songs) {
        searchResultsList.getChildren().clear();
        if (songs == null || songs.isEmpty()) {
            resultSubtitleLabel.setText("No Songs Found");
            return;
        }
        resultSubtitleLabel.setText(songs.size() + " songs found");
        for (int i = 0; i < songs.size(); i++) {
            SongData song = songs.get(i);
            HBox row = createSongRow(song, i);
            searchResultsList.getChildren().add(row);
        }
    }

    private HBox createSongRow(SongData song, int index) {

        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("popular-button");

        // ===== RANK =====

        Label rankLabel = new Label(String.format("%02d", index + 1));

        rankLabel.getStyleClass().add("rank");

        // ===== THUMBNAIL =====

        ImageView thumbnail = new ImageView();

        thumbnail.setFitWidth(52);
        thumbnail.setFitHeight(52);
        thumbnail.setPreserveRatio(false);

        thumbnail.getStyleClass().add("cover-thumb");

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

        titleLabel.getStyleClass().add("song-title");

        titleLabel.setOnMouseClicked(evenr -> {
            Navigator.openSongDetail(song);
            // PlayerManager.getInstance().playSong(List.of(song), song);
        });

        // ===== ARTIST =====

        String artists = song.artistNames() == null ? ""
                : String.join(", ", song.artistNames());

        Label artistLabel = new Label(artists);

        artistLabel.getStyleClass().add("song-artist");

        // ===== SONG INFO =====

        VBox songInfo = new VBox(5);

        songInfo.setAlignment(Pos.CENTER_LEFT);

        songInfo.getChildren().addAll(titleLabel, artistLabel);

        HBox.setHgrow(songInfo, Priority.ALWAYS);

        songInfo.setMinWidth(0);

        // ===== SEARCH COUNT =====

        long searchCount = song.searchCount() == null ? 0 : song.searchCount();

        Label countLabel = new Label(searchCount + " searches");

        countLabel.getStyleClass().add("search-count");

        SVGPath heartIcon = new SVGPath();
        heartIcon.setContent(
                "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 " +
                        "2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09 " +
                        "C13.09 3.81 14.76 3 16.5 3 " +
                        "19.58 3 22 5.42 22 8.5 " +
                        "c0 3.78-3.4 6.86-8.55 11.54L12 21.35z");

        heartIcon.getStyleClass().add("favorite-icon");

        Button favoriteButton = new Button();
        favoriteButton.setGraphic(heartIcon);

        favoriteButton.getStyleClass().addAll("song-action-btn", "favorite-btn");

        Button playListButton = new Button("🎶");
        playListButton.getStyleClass().addAll("song-action-btn", "playList-btn");

        // ===== PLAY =====

        Button playButton = new Button("▶");

        playButton.getStyleClass().addAll("song-action-btn", "row-play");

        HBox actionGroup = new HBox(8, countLabel, favoriteButton, playListButton, playButton);
        actionGroup.setAlignment(Pos.CENTER_RIGHT);
        actionGroup.getStyleClass().add("song-actions");
        actionGroup.setMinWidth(Region.USE_PREF_SIZE);

        row.getChildren().addAll(rankLabel, thumbnail, songInfo, actionGroup);

        // event
        playButton.setOnAction(event -> {
            handlePlaySong(song);
            event.consume();
        });
        playListButton.setOnAction(event -> {
            handleAddToPlaylist(song);
        });
        favoriteButton.setOnAction(event -> {
            handlleAddToFavorite(song);
        });

        return row;
    }

    private void handlePlaySong(SongData song) {

        PlayerManager.getInstance().playSong(currentSongList, song);
    }

    private void handleAddToPlaylist(SongData song) {

        AddSongToPlaylistDialog.show(searchResultsList.getScene().getWindow(), song, () -> {
            System.out.println("Thêm bài hát vào playlist thành công!");
        });
    }

    private void handlleAddToFavorite(SongData song) {
        Add_RemoveToPlaylistPayload payload = new Add_RemoveToPlaylistPayload(null, song, PlayList_Type.FAVORITES);
        Request request = Request.of(Action.ADD_FAVORITE, payload);
        try {
            ClientConnection.getInstance().send(request, response -> {
                Platform.runLater(() -> {
                    if (!response.isSuccess()) {
                        StatusDialog.error(resultTitleLabel.getScene().getWindow(), response.getMessage());
                        return;
                    }
                    StatusDialog.success(resultTitleLabel.getScene().getWindow(), response.getMessage());
                });
            });
        } catch (Exception e) {
            // TODO: handle exception
            Platform.runLater(() -> {
                StatusDialog.error(resultTitleLabel.getScene().getWindow(), e.getMessage());
                return;
            });
        }
    }

    public void seachFromAnotherScreen(String keyword) {
        searchField.setText(keyword);
        handleSearch();
    }
}
