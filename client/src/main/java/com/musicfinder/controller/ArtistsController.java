package com.musicfinder.controller;

import com.musicfinder.navigation.Navigator;
import com.musicfinder.navigation.Screen;

import com.musicfinder.network.ClientConnection;
import com.musicfinder.shared.enums.Action;
import com.musicfinder.shared.request.Request;
import com.musicfinder.shared.request.payload.ArtistPayload;
import com.musicfinder.shared.request.payload.SearchPayload;
import com.musicfinder.shared.response.payload.ArtistData;
import com.musicfinder.shared.response.payload.SearchArtistData;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

import java.io.IOException;
import java.util.List;

public class ArtistsController {
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


    private static final double PHOTO_WIDTH = 140;
    private static final double PHOTO_HEIGHT = 150;
    private static final int ZING_IMAGE_SIZE = 600;
    private static final String DEFAULT_PHOTO = "/com/musicfinder/assets/art-son-tung.png";

    @FXML
    private TextField artistSearchField;

    @FXML
    private Label artistMessageLabel;

    @FXML
    private VBox artistListBox;

    @FXML
    private void initialize() {
        artistMessageLabel.setText("");
    }

    @FXML
    private void handleArtistSearch() {

        String keyword = artistSearchField.getText() == null
                ? ""
                : artistSearchField.getText().trim();

        if (keyword.isEmpty()) {
            showMessage("Enter an artist name to search.");
            return;
        }

        SearchPayload payload = new SearchPayload(keyword);

        Request request = Request.of(
                Action.SEARCH_ARTIST,
                payload);
        if (loadingOverlay.isVisible()) {
            return;
        }
        setLoading(true);
        try {
            ClientConnection.getInstance().send(request, response -> Platform.runLater(() -> {
                try {
                    if (!response.isSuccess()) {
                        showMessage("Lỗi server khi tìm kiếm dữ liệu!");
                        return;
                    }
                    SearchArtistData data = response.dataAs(SearchArtistData.class);
                    List<ArtistData> artists = (data == null || data.artists() == null) ? List.of() : data.artists();
                    renderArtists(artists);
                } catch (Exception e) {
                    showMessage("Không thể tải kết quả: " + e.getMessage());
                } finally {
                    setLoading(false);
                }
            }));
        } catch (Exception e) {
            setLoading(false);
            showMessage("Không thể kết nối server: " + e.getMessage());
        }
    }

    private void handleViewDetail(Long artistID) {
        ArtistPayload payload = new ArtistPayload(artistID);
        Request request = Request.of(Action.VIEW_ARTIST_DETAIL, payload);
        try {
            ClientConnection.getInstance().send(request, response -> {
                // UI UPDATE LATER
                System.out.println(response.getMessage());
            });
        } catch (IOException e) {
            showMessage("Không thể kết nối server: " + e.getMessage());
        }
    }


    @FXML
    private void handleNavPlaceholder() {
        showMessage("This screen will be connected when we build it in the next steps.");
    }

    private void showMessage(String message) {
        artistMessageLabel.setText(message);
    }

    public void seachFromAnotherScreen(String keyword) {
        artistSearchField.setText(keyword);
        handleArtistSearch();
    }

    private void renderArtists(List<ArtistData> artists) {
        artistListBox.getChildren().clear();
        if (artists.isEmpty()) {
            showMessage("Không tìm thấy ca sĩ phù hợp.");
            return;
        }
        showMessage("");
        for (ArtistData artist : artists) {
            artistListBox.getChildren().add(createRow(artist));
        }
    }

    private HBox createRow(ArtistData artist) {
       
        Image image = loadArtistImage(artist.image_url());
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(PHOTO_WIDTH);
        imageView.setFitHeight(PHOTO_HEIGHT);
        imageView.setPreserveRatio(false); 
        imageView.setSmooth(true);
        cropToCover(imageView, image);
        imageView.getStyleClass().add("artist-photo");
        Rectangle clip = new Rectangle(PHOTO_WIDTH, PHOTO_HEIGHT);
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        imageView.setClip(clip);

        Label profile = new Label("ARTIST PROFILE");
        profile.getStyleClass().add("card-eyebrow");

        Label name = new Label(textOr(artist.name(), "Unknown artist"));
        name.getStyleClass().add("artist-name");

        Label hometown = new Label(textOr(artist.homeTown(), "Đang cập nhật"));
        hometown.getStyleClass().add("artist-location");

        Label description = new Label(textOr(artist.biagraphy(),
                "Đang cập nhật"));
        description.getStyleClass().add("artist-description");
        description.setWrapText(true);
        description.setMaxHeight(48);
        VBox.setMargin(description, new Insets(6, 0, 0, 0));

        Button viewProfile = new Button("View profile");
        viewProfile.getStyleClass().addAll("primary-button", "small-button");
        viewProfile.setOnAction(e -> Navigator.openArtistDetail(artist));
        VBox.setMargin(viewProfile, new Insets(6, 0, 0, 0));

        VBox content = new VBox(6, profile, name, hometown, description, viewProfile);
        content.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(content, Priority.ALWAYS);

        HBox row = new HBox(18, imageView, content);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("artist-card");
        return row;
    }

    // Tải ảnh ở độ phân giải gốc (không ép về kích thước khung) để hiển thị nét trên màn hình scale 125-150%
    private Image loadArtistImage(String url) {
        if (url != null && !url.isBlank()) {
            return new Image(upgradeZingImageSize(url), true); // true = tải nền, không chặn UI
        }
        return new Image(getClass().getResource(DEFAULT_PHOTO).toExternalForm());
    }

    // Ảnh Zing có kích thước nằm trong URL (vd: .../w240_r1x1_jpeg/...) -> xin bản lớn hơn
    private String upgradeZingImageSize(String url) {
        if (url.contains("zmdcdn.me")) {
            return url.replaceFirst("/w\\d+_", "/w" + ZING_IMAGE_SIZE + "_");
        }
        return url;
    }

    // Cắt phần giữa ảnh cho đúng tỉ lệ khung (giống object-fit: cover) thay vì kéo méo
    private void cropToCover(ImageView view, Image image) {
        Runnable apply = () -> {
            double w = image.getWidth();
            double h = image.getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            double frameRatio = PHOTO_WIDTH / PHOTO_HEIGHT;
            double cropW = w;
            double cropH = h;
            if (w / h > frameRatio) {
                cropW = h * frameRatio; // ảnh rộng hơn khung -> cắt bớt 2 bên
            } else {
                cropH = w / frameRatio; // ảnh cao hơn khung -> cắt bớt trên/dưới
            }
            view.setViewport(new Rectangle2D((w - cropW) / 2, (h - cropH) / 2, cropW, cropH));
        };
        if (image.getProgress() >= 1.0) {
            apply.run();
        } else {
            image.progressProperty().addListener((obs, oldV, newV) -> {
                if (newV.doubleValue() >= 1.0) {
                    apply.run();
                }
            });
        }
    }

    private String textOr(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
