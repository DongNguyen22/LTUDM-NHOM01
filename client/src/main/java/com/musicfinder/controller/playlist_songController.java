package com.musicfinder.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.musicfinder.navigation.Navigator;
import com.musicfinder.navigation.Screen;
import com.musicfinder.network.ClientConnection;
import com.musicfinder.player.PlayerManager;
import com.musicfinder.shared.enums.Action;
import com.musicfinder.shared.enums.PlayList_Type;
import com.musicfinder.shared.request.Request;
import com.musicfinder.shared.request.payload.Add_RemoveToPlaylistPayload;
import com.musicfinder.shared.request.payload.Create_UpdateToPlaylistPayload;
import com.musicfinder.shared.request.payload.Remove_OpenPlaylistPayload;
import com.musicfinder.shared.response.payload.GetPlaylistSongsData;
import com.musicfinder.shared.response.payload.PlayListData;
import com.musicfinder.shared.response.payload.SongData;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.StageStyle;

public class playlist_songController {

    @FXML
    private Button backButton;

    @FXML
    private ImageView playlistCover;

    @FXML
    private Label playlistNameLabel;
    @FXML
    private Label playlistDescriptionLabel;
    @FXML
    private Label songCountLabel;

    @FXML
    private Button playAllButton;
    @FXML
    private Button editPlaylistButton;
    @FXML
    private Button deletePlaylistButton;

    @FXML
    private Label playlistMessageLabel;

    @FXML
    private VBox songTable;
    @FXML
    private VBox songList;

    @FXML
    private TextField searchField;
    @FXML
    private ScrollPane playlistScroll;

    private PlayListData playlist;
    private List<SongData> originalSongList = new ArrayList<>();

    public void init(PlayListData playlist) {
        this.playlist = playlist;
        playlistNameLabel.setText(
                playlist.name() == null ? "Untitled playlist" : playlist.name());

        playlistDescriptionLabel.setText(
                playlist.description() == null ? "" : playlist.description());

        songCountLabel.setText(originalSongList.size() + (originalSongList.size() > 1 ? "Songs" : "Song"));
        updatePlaylistCover(playlist.thumbnailUrl());
        searchField.textProperty().addListener(
                (observable, oldValue, newValue) -> filterSongs(newValue));
        editPlaylistButton.setOnAction(event -> handleEditPlaylist());
        deletePlaylistButton.setOnAction(event -> {
            handleDeletePlaylist();
        });
        backButton.setOnAction(event -> handleBack());

        playAllButton.setOnAction(event -> {
            if (originalSongList.isEmpty()) {
                StatusDialog.error(
                        songList.getScene().getWindow(),
                        "Playlist chưa có bài hát.");
                return;
            }

            handlePlaySong(originalSongList.get(0));
        });
        loadData();
    }

    private void loadData() {
        Remove_OpenPlaylistPayload payload = new Remove_OpenPlaylistPayload(playlist.playlistID());
        Request request = Request.of(Action.GET_PLAYLIST_SONGS, payload);
        try {
            ClientConnection.getInstance().send(request, response -> {
                Platform.runLater(() -> {
                    if (!response.isSuccess()) {
                        StatusDialog.error(songList.getScene().getWindow(), "Lỗi kết nối server!");
                        return;
                    }
                    GetPlaylistSongsData data = response.dataAs(GetPlaylistSongsData.class);

                    originalSongList.clear();
                    if (data != null && data.songs() != null) {
                        originalSongList.addAll(data.songs());
                    }

                    songCountLabel.setText(originalSongList.size() + " songs");
                    filterSongs(searchField.getText());
                });
            });
        } catch (Exception e) {
            // TODO: handle exception
            Platform.runLater(() -> {
                StatusDialog.error(songList.getScene().getWindow(), "Lỗi kết nối server!");

            });
        }
    }

    private void renderSongs(List<SongData> songs) {
        songList.getChildren().clear();

        for (int i = 0; i < songs.size(); i++) {
            HBox row = createSongRow(songs.get(i), i + 1);
            songList.getChildren().add(row);
        }

        playlistScroll.setPrefHeight(
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

        titleLabel.getStyleClass().add("playlist-song-title");

        // ===== ARTIST =====

        String artists = song.artistNames() == null ? ""
                : String.join(", ", song.artistNames());

        Label artistLabel = new Label(artists);

        artistLabel.getStyleClass().add("playlist-song-artist");
        artistLabel.setPrefWidth(155);
        artistLabel.setMinWidth(155);
        artistLabel.setMaxWidth(155);

        Integer seconds = song.durations_seconds();
        Label durationLabel = new Label(seconds != null && seconds >= 0
                ? String.format("%d:%02d", seconds / 60, seconds % 60)
                : "--:--");
        durationLabel.getStyleClass().add("playlist-duration");
        durationLabel.setPrefWidth(75);
        durationLabel.setMinWidth(75);
        durationLabel.setMaxWidth(75);

        // ===== SONG INFO =====

        VBox songInfo = new VBox(5);

        songInfo.setAlignment(Pos.CENTER_LEFT);

        songInfo.getChildren().add(titleLabel);

        HBox.setHgrow(songInfo, Priority.ALWAYS);

        songInfo.setMinWidth(0);

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

        HBox actionGroup = new HBox(8, favoriteButton, deleteButton, playButton);
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
            handleRemoveFromPlaylist(song);
        });
        favoriteButton.setOnAction(event -> {
            handlleAddToFavorite(song);
        });

        return row;
    }

    private void handlePlaySong(SongData song) {

        PlayerManager.getInstance().playSong(originalSongList, song);
    }

    private void filterSongs(String keyword) {
        String key = keyword == null
                ? ""
                : keyword.trim().toLowerCase(Locale.ROOT);

        if (key.isEmpty()) {
            renderSongs(originalSongList);
            return;
        }

        List<SongData> filteredSongs = originalSongList.stream()
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
    private void handleRemoveFromPlaylist(SongData song) {

        Boolean confirm = ConfirmDialog.show(songList.getScene().getWindow(), "Hello", "Confirm your action");
        if (!confirm) {
            return;
        }
        Add_RemoveToPlaylistPayload payload = new Add_RemoveToPlaylistPayload(playlist.playlistID(), song,
                PlayList_Type.NORMAL);
        Request request = Request.of(Action.REMOVE_FROM_PLAYLIST, payload);
        try {
            ClientConnection.getInstance().send(request, response -> {
                if (!response.isSuccess()) {
                    Platform.runLater(() -> {
                        StatusDialog.error(songList.getScene().getWindow(), response.getMessage());
                    });
                    return;
                }
                Platform.runLater(() -> {
                    originalSongList.remove(song);
                    if (originalSongList.isEmpty()) {
                        updatePlaylistCover(null);
                    }
                    StatusDialog.success(songList.getScene().getWindow(), response.getMessage());
                    filterSongs(searchField.getText());
                    songCountLabel.setText(originalSongList.size() + (originalSongList.size() > 1 ? "Songs" : "Song"));
                });
            });
        } catch (Exception e) {
            // TODO: handle exception
            StatusDialog.error(songList.getScene().getWindow(), e.getMessage());

        }
    }

    private void handleEditPlaylist() {
        showPlaylistDialog(this.playlist);
    }

    private void handleDeletePlaylist() {
        Remove_OpenPlaylistPayload payload = new Remove_OpenPlaylistPayload(playlist.playlistID());
        Request request = Request.of(Action.DELETE_PLAYLIST, payload);
        try {
            ClientConnection.getInstance().send(request, response -> {
                Platform.runLater(() -> {
                    if (!response.isSuccess()) {
                        StatusDialog.error(songList.getScene().getWindow(), response.getMessage());
                        return;
                    }

                    StatusDialog.success(songList.getScene().getWindow(), response.getMessage());
                    originalSongList.clear();
                    // Chức năng giống như back button trở về giao diện playlist
                    handleBack();
                });
            });
        } catch (Exception e) {
            // TODO: handle exception
            Platform.runLater(() -> {
                StatusDialog.error(songList.getScene().getWindow(), e.getMessage());
                return;
            });
        }

    }

    private void showPlaylistDialog(PlayListData playlist) {
        boolean isEdit = playlist != null;
        // 1. Tạo Dialog không có thanh tiêu đề Windows
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Edit playlist" : "Create playlist");
        dialog.initOwner(playlistMessageLabel.getScene().getWindow());
        dialog.initStyle(StageStyle.TRANSPARENT);

        DialogPane pane = dialog.getDialogPane();
        // 2. Gắn CSS
        pane.getStylesheets().add(
                getClass().getResource("/com/musicfinder/css/playlist-popup.css").toExternalForm());
        pane.getStyleClass().add("playlist-dialog-pane");
        pane.setPrefWidth(400);

        // 3. Khai báo các nút Dialog
        ButtonType saveType = new ButtonType(isEdit ? "Save changes" : "Create playlist",
                ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(ButtonType.CANCEL, saveType);

        // 4. Phần đầu popup
        Label icon = new Label("♫");
        icon.getStyleClass().add("playlist-dialog-icon");

        Button closeButton = new Button("✕");
        closeButton.getStyleClass().add("playlist-modal-close");

        closeButton.setOnAction(e -> {
            dialog.setResult(ButtonType.CANCEL);
            dialog.close();
        });

        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);

        HBox header = new HBox(12, icon, space, closeButton);
        header.setAlignment(Pos.CENTER_LEFT);

        // 5. Tiêu đề và mô tả
        Label eyebrow = new Label("YOUR LIBRARY");
        eyebrow.getStyleClass().add("playlist-modal-eyebrow");

        Label title = new Label(
                isEdit ? "Edit playlist" : "Create new playlist");
        title.getStyleClass().add("playlist-modal-title");

        Label subtitle = new Label(
                "Give your collection a name and make it your own.");
        subtitle.getStyleClass().add("playlist-modal-subtitle");

        // 6. Các ô nhập liệu
        Label nameLabel = new Label("Playlist name");
        nameLabel.getStyleClass().add("playlist-modal-field-label");

        TextField nameField = new TextField();
        nameField.setPromptText("e.g. Late night drives");
        nameField.setPrefHeight(38);
        nameField.getStyleClass().add("playlist-modal-input");

        Label descriptionLabel = new Label("Description");
        descriptionLabel.getStyleClass()
                .add("playlist-modal-field-label");

        TextArea descriptionArea = new TextArea();
        descriptionArea.setPromptText("What's the vibe?");
        descriptionArea.setPrefHeight(76);
        descriptionArea.setPrefRowCount(2);
        descriptionArea.setWrapText(true);
        descriptionArea.getStyleClass()
                .add("playlist-modal-textarea");

        // 7. Điền dữ liệu khi chỉnh sửa
        if (isEdit) {
            nameField.setText(playlist.name() == null ? "" : playlist.name());
            descriptionArea.setText(playlist.description() == null ? "" : playlist.description());
        }

        // 8. Sắp xếp toàn bộ nội dung
        VBox content = new VBox(
                9,
                header,
                eyebrow,
                title,
                subtitle,
                nameLabel,
                nameField,
                descriptionLabel,
                descriptionArea);

        VBox.setMargin(eyebrow, new Insets(9, 0, 0, 0));

        VBox.setMargin(nameLabel, new Insets(13, 0, 0, 0));

        VBox.setMargin(descriptionLabel, new Insets(6, 0, 0, 0));

        pane.setContent(content);

        // 9. Style các nút
        Button saveButton = (Button) pane.lookupButton(saveType);

        Button cancelButton = (Button) pane.lookupButton(ButtonType.CANCEL);

        saveButton.getStyleClass().add("playlist-modal-save");
        cancelButton.getStyleClass().add("playlist-modal-cancel");

        // Không cho lưu tên rỗng hoặc quá dài
        saveButton.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            String name = nameField.getText().trim();
            return name.isEmpty() || name.length() > 100;
        }, nameField.textProperty()));

        // 10. Đảm bảo nền cửa sổ trong suốt ở góc bo
        dialog.setOnShowing(e -> {
            Scene scene = pane.getScene();
            if (scene != null) {
                scene.setFill(Color.TRANSPARENT);
            }
        });

        // 11. Hiển thị và xử lý kết quả
        dialog.showAndWait().ifPresent(result -> {
            if (result == saveType) {
                savePlaylist(playlist, nameField.getText().trim(),
                        descriptionArea.getText().trim());
            }
        });
    }

    private void savePlaylist(PlayListData playlist, String name, String description) {
        boolean isEdit = playlist != null;
        Long playlistId = isEdit ? playlist.playlistID() : null;
        Create_UpdateToPlaylistPayload payload = new Create_UpdateToPlaylistPayload(playlistId, name, description);

        Action action = isEdit ? Action.EDIT_FROM_PLAYLIST : Action.CREATE_PLAYLIST;
        Request request = Request.of(action, payload);
        try {
            ClientConnection.getInstance().send(request, response -> {
                Platform.runLater(() -> {
                    if (response.isSuccess()) {
                        this.playlist = new PlayListData(playlist.playlistID(), name, description,
                                playlist.thumbnailUrl(), playlist.type());

                        playlistNameLabel.setText(this.playlist.name());
                        playlistDescriptionLabel.setText(this.playlist.description());

                        StatusDialog.success(songList.getScene().getWindow(), response.getMessage());
                    } else {
                        StatusDialog.error(songList.getScene().getWindow(), response.getMessage());
                    }
                });
            });
        } catch (Exception e) {
            // TODO: handle exception
            StatusDialog.error(songList.getScene().getWindow(), e.getMessage());
        }
    }

    private void updatePlaylistCover(String thumbnailUrl) {
        Image defaultCover = new Image(
                getClass().getResource(
                        "/com/musicfinder/assets/cover-blue-flower.png").toExternalForm());

        playlistCover.setImage(defaultCover);

        if (thumbnailUrl == null || thumbnailUrl.isBlank()) {
            return;
        }

        try {
            Image image = new Image(thumbnailUrl.trim(), true);
            playlistCover.setImage(image);

            image.errorProperty().addListener((obs, oldValue, hasError) -> {
                if (hasError && playlistCover.getImage() == image) {
                    playlistCover.setImage(defaultCover);
                }
            });

            if (image.isError()) {
                playlistCover.setImage(defaultCover);
            }
        } catch (IllegalArgumentException e) {
            playlistCover.setImage(defaultCover);
        }
    }

    private void handlleAddToFavorite(SongData song) {
        Add_RemoveToPlaylistPayload payload = new Add_RemoveToPlaylistPayload(null, song, PlayList_Type.FAVORITES);
        Request request = Request.of(Action.ADD_FAVORITE, payload);
        try {
            ClientConnection.getInstance().send(request, response -> {
                if (!response.isSuccess()) {
                    Platform.runLater(() -> {
                        StatusDialog.error(playlistScroll.getScene().getWindow(), response.getMessage());
                    });
                    return;
                }
                Platform.runLater(() -> {
                    StatusDialog.success(playlistScroll.getScene().getWindow(), response.getMessage());
                });
            });
        } catch (Exception e) {
            // TODO: handle exception
            Platform.runLater(() -> {
                StatusDialog.error(playlistScroll.getScene().getWindow(), e.getMessage());
                return;
            });
        }
    }

    private void handleBack() {
        try {
            Navigator.goTo(Screen.PLAYLISTS);
        } catch (Exception e) {
            StatusDialog.error(
                    songList.getScene().getWindow(),
                    "Không thể quay lại My playlists: " + e.getMessage());
        }
    }

}
