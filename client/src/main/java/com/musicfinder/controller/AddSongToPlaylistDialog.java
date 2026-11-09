package com.musicfinder.controller;

import com.musicfinder.network.ClientConnection;
import com.musicfinder.shared.enums.Action;
import com.musicfinder.shared.enums.PlayList_Type;
import com.musicfinder.shared.request.Request;
import com.musicfinder.shared.request.payload.Add_RemoveToPlaylistPayload;
import com.musicfinder.shared.request.payload.Add_To_PlaylistPayload;
import com.musicfinder.shared.response.Response;
import com.musicfinder.shared.response.payload.PlayListData;
import com.musicfinder.shared.response.payload.SongData;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class AddSongToPlaylistDialog {

    private final Window owner;
    private final SongData song;
    private final Runnable onSuccess;
    private final Dialog<ButtonType> dialog = new Dialog<>();
    private final ButtonType addType = new ButtonType(
            "Add to playlist", ButtonBar.ButtonData.OK_DONE);
    private final TextField searchField = new TextField();
    private final Label statusLabel = new Label("Loading playlists...");
    private Button addButton;
    private VBox playlistList = new VBox(5);
    private List<PlayListData> originalList = new ArrayList<>();
    private List<PlayListData> filterList = new ArrayList<>();
    private final Set<PlayListData> selectedPlaylists = new LinkedHashSet<>();

    private AddSongToPlaylistDialog(Window owner, SongData song, Runnable onSuccess) {
        this.owner = Objects.requireNonNull(owner, "owner is required");
        this.song = Objects.requireNonNull(song, "song is required");
        this.onSuccess = onSuccess;
    }

    public static void show(Window owner, SongData song, Runnable onSuccess) {
        new AddSongToPlaylistDialog(owner, song, onSuccess).open();
    }

    private void open() {
        initDialog();
        loadPlaylistsFromServer();
        createContent();

        dialog.setOnShowing(event -> {
            Scene scene = dialog.getDialogPane().getScene();
            if (scene != null) {
                scene.setFill(Color.TRANSPARENT);
            }
        });
        dialog.show();
        Platform.runLater(searchField::requestFocus);
    }

    // 3. Khởi tạo cửa sổ và CSS.
    private void initDialog() {
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        DialogPane pane = dialog.getDialogPane();
        pane.setPrefWidth(420);
        pane.getStyleClass().add("add-song-dialog");
        pane.getButtonTypes().addAll(ButtonType.CANCEL, addType);
        pane.getStylesheets().add(Objects.requireNonNull(
                AddSongToPlaylistDialog.class.getResource(
                        "/com/musicfinder/css/add-song-playlist-dialog.css"),
                "Missing add-song-playlist-dialog.css").toExternalForm());
        Button cancelButton = (Button) pane.lookupButton(ButtonType.CANCEL);
        addButton = (Button) pane.lookupButton(addType);
        addButton.setDisable(true);
        addButton.addEventFilter(ActionEvent.ACTION, event -> {
            handleAddSongToPlaylist(song);
        });

        cancelButton.getStyleClass().add("picker-cancel-btn");
        addButton.getStyleClass().add("picker-add-btn");
    }

    // 4. Tạo phần tiêu đề, ô tìm kiếm, danh sách và thông báo.
    private void createContent() {
        Label icon = new Label("♫");
        icon.getStyleClass().add("picker-icon");

        Button closeButton = new Button("✕");
        closeButton.getStyleClass().add("picker-close");
        closeButton.setOnAction(event -> {
            dialog.setResult(ButtonType.CANCEL);
            dialog.close();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(12, icon, spacer, closeButton);
        header.setAlignment(Pos.CENTER_LEFT);

        Label eyebrow = new Label("YOUR LIBRARY");
        eyebrow.getStyleClass().add("picker-eyebrow");
        Label title = new Label("Add to playlist");
        title.getStyleClass().add("picker-title");
        Label subtitle = new Label("Choose where to save: " + song.title());
        subtitle.getStyleClass().add("picker-subtitle");
        subtitle.setWrapText(true);

        searchField.setPromptText("Search your playlists...");
        searchField.getStyleClass().add("picker-search");
        statusLabel.getStyleClass().add("picker-status");
        statusLabel.setWrapText(true);
        handleSearch();
        ScrollPane scrollPane = new ScrollPane(playlistList);

        scrollPane.setFitToWidth(true);

        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        scrollPane.setPrefHeight(230);

        scrollPane.getStyleClass().add("picker-scroll");

        VBox content = new VBox(11,
                header, eyebrow, title, subtitle,
                searchField, scrollPane, statusLabel);
        content.setPadding(new Insets(4, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
    }

    private HBox createPlaylistRow(PlayListData playlist) {

        Label name = new Label(
                playlist.name() == null
                        ? "Untitled playlist"
                        : playlist.name());
        name.getStyleClass().add("picker-row-title");

        String text = playlist.description();

        Label description = new Label(
                text == null || text.isBlank()
                        ? "Your music collection"
                        : text);
        description.getStyleClass().add("picker-row-description");

        VBox labels = new VBox(3, name, description);

        // Chiếm toàn bộ khoảng trống còn lại
        HBox.setHgrow(labels, Priority.ALWAYS);

        // Checkbox chọn playlist
        CheckBox checkBox = new CheckBox();
        checkBox.getStyleClass().add("picker-checkbox");

        checkBox.setSelected(selectedPlaylists.contains(playlist));

        checkBox.setOnAction(event -> {
            if (checkBox.isSelected()) {
                selectedPlaylists.add(playlist);
            } else {
                selectedPlaylists.remove(playlist);
            }
            addButton.setDisable(selectedPlaylists.isEmpty());
        });

        HBox row = new HBox(12, createThumbnail(playlist), labels, checkBox);

        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.getStyleClass().add("picker-playlist-row");

        return row;
    }

    private void loadPlaylistsFromServer() {
        statusLabel.setText("Loading playlists...");
        Request request = Request.of(Action.GET_PLAYLISTS, null);
        try {
            ClientConnection.getInstance().send(request,
                    response -> {
                        Platform.runLater(() -> {
                            if (!dialog.isShowing())
                                return;
                            if (!response.isSuccess()) {
                                statusLabel.setText("Cannot load playlists: " + response.getMessage());
                                return;
                            }
                            try {
                                originalList = new ArrayList<>(Arrays.asList(response.dataAs(PlayListData[].class)));
                                filterPlaylists();
                            } catch (Exception ex) {
                                statusLabel.setText("Invalid playlist data: " + ex.getMessage());
                            }
                        });
                    });
        } catch (IOException ex) {
            statusLabel.setText("Connection error: " + ex.getMessage());
        }
    }

    private void renderPlaylists(List<PlayListData> playlists) {
        playlistList.getChildren().clear();
        if (playlists == null || playlists.isEmpty()) {
            statusLabel.setText("You don't have any playlists yet.");
            return;
        }
        for (PlayListData playlist : playlists) {
            HBox row = createPlaylistRow(playlist);
            playlistList.getChildren().add(row);
        }
        statusLabel.setText(playlists.isEmpty()
                ? "No playlists match."
                : "Select a playlist, then click Add to playlist.");
    }

    private void handleSearch() {
        searchField.textProperty().addListener(
                (observable, oldValue, newValue) -> filterPlaylists());
    }

    private void filterPlaylists() {
        String keyword = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        filterList = originalList.stream().filter(playlist -> keyword.isEmpty() || (playlist.name() != null &&
                playlist.name().toLowerCase(Locale.ROOT).contains(keyword))).toList();
        renderPlaylists(filterList);
    }

    private void handleAddSongToPlaylist(SongData song) {
        List<Long> addPlaylists = new ArrayList<>();
        for (PlayListData playlist : selectedPlaylists) {
            addPlaylists.add(playlist.playlistID());
        }
        Add_To_PlaylistPayload payload = new Add_To_PlaylistPayload(addPlaylists, song);
        Request request = Request.of(Action.ADD_TO_PLAYLIST, payload);
        try {
            ClientConnection.getInstance().send(request, response -> {
                Platform.runLater(() -> {
                    if (!response.isSuccess()) {
                        StatusDialog.error(owner, response.getMessage());
                        return;
                    }
                    StatusDialog.success(owner, response.getMessage());
                    return;
                });
            });
        } catch (Exception e) {
            // TODO: handle exception
            StatusDialog.error(owner, e.getMessage());
        }
    }

    private Node createThumbnail(PlayListData playlist) {
        Label icon = new Label("♫");
        icon.getStyleClass().add("picker-row-icon");

        String url = playlist.thumbnailUrl();
        if (url == null || url.isBlank()) {
            return icon;
        }

        try {
            Image image = new Image(url, 44, 44, false, true, true);
            ImageView view = new ImageView(image);
            view.setFitWidth(44);
            view.setFitHeight(44);

            Rectangle clip = new Rectangle(44, 44);
            clip.setArcWidth(12);
            clip.setArcHeight(12);
            view.setClip(clip);

            view.setVisible(false);
            image.progressProperty().addListener((obs, oldV, newV) -> {
                if (newV.doubleValue() >= 1.0 && !image.isError()) {
                    view.setVisible(true);
                }
            });

            StackPane thumb = new StackPane(icon, view);
            thumb.setMinSize(44, 44);
            thumb.setPrefSize(44, 44);
            thumb.setMaxSize(44, 44);
            return thumb;
        } catch (IllegalArgumentException ex) { // URL sai định dạng
            return icon;
        }
    }
}