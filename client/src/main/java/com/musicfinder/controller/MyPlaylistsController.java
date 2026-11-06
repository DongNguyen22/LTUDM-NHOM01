package com.musicfinder.controller;

import com.musicfinder.navigation.Navigator;
import com.musicfinder.navigation.Screen;

import com.musicfinder.network.ClientConnection;
import com.musicfinder.shared.enums.Action;
import com.musicfinder.shared.request.Request;
import com.musicfinder.shared.request.payload.Add_RemoveToPlaylistPayload;
import com.musicfinder.shared.request.payload.Create_UpdateToPlaylistPayload;
import com.musicfinder.shared.request.payload.Remove_OpenPlaylistPayload;
import com.musicfinder.shared.response.payload.PlayListData;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Priority;

import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.beans.binding.Bindings;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
// import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.StageStyle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class MyPlaylistsController {

    @FXML
    private HBox playlistCards;

    @FXML
    private ScrollPane playlistScroller;

    @FXML
    private Label playlistCountLabel;

    @FXML
    private Label playlistMessageLabel;

    @FXML
    private TextField searchField;
    private ArrayList<PlayListData> originalPlaylists;

    private static final double CARD_WIDTH = 245;
    private static final double CARD_HEIGHT = 270;
    private static final double COVER_HEIGHT = 128;
    private static final double CARD_GAP = 18;

    @FXML
    private void initialize() {

        playlistMessageLabel.setText("");
        searchField.textProperty().addListener(
                (observable, oldValue, newValue) -> filterPlaylists());
        loadPlaylists();
    }

    private VBox createPlayListCard(PlayListData playlist, int index) {
        VBox card = new VBox(0);
        card.getStyleClass().add("playlist-card");

        card.setMinSize(CARD_WIDTH, CARD_HEIGHT);
        card.setPrefSize(CARD_WIDTH, CARD_HEIGHT);
        card.setMaxSize(CARD_WIDTH, CARD_HEIGHT);

        // 2. Khu vực ảnh cover
        StackPane cover = new StackPane();
        cover.setMinSize(CARD_WIDTH, COVER_HEIGHT);
        cover.setPrefSize(CARD_WIDTH, COVER_HEIGHT);
        cover.setMaxSize(CARD_WIDTH, COVER_HEIGHT);
        cover.getStyleClass().addAll("playlist-artwork", "playlist-artwork" + (index % 3));

        Label artworkIcon = new Label("♫");
        artworkIcon.getStyleClass().addAll("playlist-artwork-symbol");
        Button playButton = new Button("▶");
        playButton.getStyleClass().addAll("playlist-card-play");
        StackPane.setAlignment(playButton, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(playButton, new Insets(0, 12, 12, 0));
        cover.getChildren().addAll(artworkIcon, playButton);

        // 3. Phần nội dung
        VBox body = new VBox(6);
        body.getStyleClass().add("playlist-card-body");

        body.setPadding(new Insets(12));
        body.setMinHeight(CARD_HEIGHT - COVER_HEIGHT);
        body.setPrefHeight(CARD_HEIGHT - COVER_HEIGHT);
        body.setMaxHeight(CARD_HEIGHT - COVER_HEIGHT);

        Label category = new Label("PLAYLIST");
        category.getStyleClass().add("card-eyebrow");

        Label name = new Label(playlist.name() == null ? "Untitled playlist" : playlist.name());
        name.getStyleClass().add("playlist-name");
        name.setMaxWidth(CARD_WIDTH - 24);
        name.setTextOverrun(OverrunStyle.ELLIPSIS);

        Label description = new Label(playlist.description() == null ? "" : playlist.description());

        description.getStyleClass().add("playlist-description");
        description.setWrapText(true);
        description.setMaxHeight(32);

        // 4. Đẩy các nút xuống cuối card
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // 5. Nút Open playlist
        Button openButton = new Button("Open playlist →");

        openButton.getStyleClass().add("open-link");

        openButton.setOnAction(event -> showMessage("Opening: " + playlist.name()));
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        HBox actions = new HBox(openButton, gap);

        actions.setAlignment(Pos.CENTER_LEFT);

        body.getChildren().addAll(category, name, description, spacer, actions);
        // 7. Ghép cover và body thành card
        card.getChildren().addAll(cover, body);

        return card;
    }

    @FXML
    private void handleCreatePlaylist() {
        // PlaylistPopup.show(playlistMessageLabel, null, () -> {
        // showMessage("Playlist created successfully");
        // loadPlaylists();
        // });
        showPlaylistDialog(null);
    }

    private void loadPlaylists() {
        Request request = Request.of(Action.GET_PLAYLISTS, null);
        try {
            ClientConnection.getInstance().send(request, response -> {
                Platform.runLater(() -> {
                    if (!response.isSuccess()) {

                        showMessage(
                                response.getMessage());

                        return;
                    }
                    originalPlaylists = new ArrayList<>(
                            Arrays.asList(response.dataAs(PlayListData[].class)));
                    filterPlaylists();
                });
            });
        } catch (Exception e) {
            // TODO: handle exception
            showMessage("Cannot load playlists: " + e.getMessage());
        }
    }

    private void renderPlaylists(List<PlayListData> playlists) {
        playlistCards.getChildren().clear();
        int count = playlists == null ? 0 : playlists.size();
        playlistCountLabel.setText(count + " playlists in your library");
        if (count == 0) {
            Label empty = new Label("No playlists yet. Create your first playlist!");
            playlistCards.getChildren().add(empty);
        }
        for (int i = 0; i < playlists.size(); i++) {
            PlayListData playlist = playlists.get(i);
            VBox card = createPlayListCard(playlist, i);
            playlistCards.getChildren().add(card);
        }
        playlistScroller.setHvalue(0);
    }

    private void filterPlaylists() {
        String keyword = searchField.getText() == null
                ? ""
                : searchField.getText().trim().toLowerCase(Locale.ROOT);

        List<PlayListData> filtered = originalPlaylists.stream()
                .filter(playlist -> {
                    String name = playlist.name() == null
                            ? ""
                            : playlist.name().toLowerCase(Locale.ROOT);
                    return name.contains(keyword);
                }).toList();

        renderPlaylists(filtered);
    }

    @FXML
    private void handleScrollLeft() {
        scrollByPixels(-(CARD_WIDTH + CARD_GAP));
    }

    @FXML
    private void handleScrollRight() {
        scrollByPixels(CARD_WIDTH + CARD_GAP);
    }

    private void scrollByPixels(double pixels) {

        double contentWidth = playlistCards.getBoundsInLocal().getWidth();

        double viewportWidth = playlistScroller.getViewportBounds().getWidth();

        double scrollableWidth = contentWidth - viewportWidth;

        if (scrollableWidth <= 0) {
            return;
        }

        double current = playlistScroller.getHvalue();

        double newValue = current + pixels / scrollableWidth;

        newValue = Math.max(0, Math.min(1, newValue));

        playlistScroller.setHvalue(newValue);
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
                        showMessage(isEdit ? "Playlist updated successfully"
                                : "Playlist created successfully");
                        loadPlaylists();
                    } else {
                        showMessage("Cannot save playlist: " + response.getMessage());
                    }
                });
            });
        } catch (Exception e) {
            // TODO: handle exception
            showMessage("Cannot send request: " + e.getMessage());
        }
    }

    @FXML
    private void handleNavPlaceholder() {
        showMessage("This screen will be connected when we build it in the next steps.");
    }

    private void showMessage(String message) {
        playlistMessageLabel.setText(message);
    }
}
