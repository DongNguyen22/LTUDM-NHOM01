package com.musicfinder.controller;

import com.musicfinder.navigation.Navigator;
import com.musicfinder.navigation.Screen;
import com.musicfinder.network.ClientConnection;
import com.musicfinder.player.PlayerManager;
import com.musicfinder.shared.enums.Action;
import com.musicfinder.shared.request.Request;
import com.musicfinder.shared.request.payload.ArtistPayload;
import com.musicfinder.shared.response.payload.ArtistData;
import com.musicfinder.shared.response.payload.SongData;
import com.musicfinder.shared.response.payload.ViewArtistDetailData;
import com.musicfinder.shared.response.payload.ViewArtistSongsData;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ArtistDetailController {

    private static final double PHOTO_SIZE = 160;
    private static final double SONG_THUMB_SIZE = 36;
    private static final int ZING_IMAGE_SIZE = 600;
    private static final String DEFAULT_PHOTO = "/com/musicfinder/assets/art-son-tung.png";
    private static final DateTimeFormatter BIRTH_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    @FXML
    private StackPane heroPane;

    @FXML
    private ImageView heroBackground;

    @FXML
    private ImageView artistPhoto;

    @FXML
    private Label artistNameLabel;

    @FXML
    private Label heroMetaLabel;

    @FXML
    private Button playPopularButton;

    @FXML
    private Label detailMessageLabel;

    @FXML
    private Label aboutTitleLabel;

    @FXML
    private Label bioLabel;

    @FXML
    private Label fullNameLabel;

    @FXML
    private Label birthDateLabel;

    @FXML
    private Label hometownLabel;

    @FXML
    private Label searchCountLabel;

    @FXML
    private VBox popularSongsBox;

    private final List<SongData> popularSongs = new ArrayList<>();

    @FXML
    private void initialize() {
        detailMessageLabel.setText("");
        playPopularButton.setDisable(true);
        setupHeroLayout();
        setupPhotoClip();
    }

    public void init(ArtistData artist) {
        if (artist == null) {
            detailMessageLabel.setText("Không có thông tin ca sĩ.");
            return;
        }
        showArtist(artist);
        loadArtistDetail(artist.artistID());
        loadPopularSongs(artist.artistID());
    }

    private void loadArtistDetail(Long artistId) {
        Request request = Request.of(Action.VIEW_ARTIST_DETAIL, new ArtistPayload(artistId));
        try {
            ClientConnection.getInstance().send(request, response -> {
                if (!response.isSuccess()) {
                    Platform.runLater(() -> detailMessageLabel.setText(response.getMessage()));
                    return;
                }
                ViewArtistDetailData data = response.dataAs(ViewArtistDetailData.class);
                if (data != null && data.artist() != null) {
                    Platform.runLater(() -> {
                        detailMessageLabel.setText("");
                        showArtist(data.artist());
                    });
                }
            });
        } catch (IOException e) {
            detailMessageLabel.setText("Không thể kết nối server: " + e.getMessage());
        }
    }

    private void loadPopularSongs(Long artistId) {
        Request request = Request.of(Action.VIEW_ARTIST_SONGS, new ArtistPayload(artistId));
        try {
            ClientConnection.getInstance().send(request, response -> {
                if (!response.isSuccess()) {
                    Platform.runLater(() -> renderSongs(List.of()));
                    return;
                }
                ViewArtistSongsData data = response.dataAs(ViewArtistSongsData.class);
                List<SongData> songs = data.songs();
                popularSongs.clear();
                popularSongs.addAll(songs);
                Platform.runLater(() -> renderSongs(songs));
            });
        } catch (IOException e) {
            detailMessageLabel.setText("Không thể kết nối server: " + e.getMessage());
        }
    }

    private void showArtist(ArtistData artist) {
        String name = textOr(artist.name(), "Unknown artist");
        artistNameLabel.setText(name);
        aboutTitleLabel.setText("About " + name);

        String hometown = textOr(artist.homeTown(), "Đang cập nhật");
        long searches = artist.searchCount() == null ? 0 : artist.searchCount();
        heroMetaLabel.setText(formatCount(searches) + " searches · " + hometown);

        bioLabel.setText(textOr(artist.biagraphy(),
                "Known for a distinctive sound and captivating presence, " + name
                        + " is a standout voice in modern Vietnamese music. Explore their songs and discover what makes their music resonate."));
        fullNameLabel.setText(name);
        birthDateLabel
                .setText(artist.birth_date() == null ? "Đang cập nhật" : artist.birth_date().format(BIRTH_FORMAT));
        hometownLabel.setText(hometown);
        searchCountLabel.setText(formatCount(searches));

        Image image = loadImage(artist.image_url());
        artistPhoto.setImage(image);
        cropToCover(artistPhoto, image, 1.0);
        heroBackground.setImage(image);
    }

    private void renderSongs(List<SongData> songs) {
        popularSongsBox.getChildren().clear();

        playPopularButton.setDisable(popularSongs.isEmpty());
        if (popularSongs.isEmpty()) {
            Label empty = new Label("Chưa có bài hát nào.");
            empty.getStyleClass().add("song-empty");
            popularSongsBox.getChildren().add(empty);
            return;
        }
        for (int i = 0; i < popularSongs.size(); i++) {
            HBox row = createSongRow(i + 1, popularSongs.get(i));
            if (i == popularSongs.size() - 1) {
                row.getStyleClass().add("song-row-last");
            }
            popularSongsBox.getChildren().add(row);
        }
    }

    private HBox createSongRow(int rank, SongData song) {
        Label rankLabel = new Label(String.format("%02d", rank));
        rankLabel.getStyleClass().add("song-rank");

        ImageView thumb = new ImageView(loadImage(song.thumbnail_url()));
        thumb.setFitWidth(SONG_THUMB_SIZE);
        thumb.setFitHeight(SONG_THUMB_SIZE);
        thumb.setSmooth(true);
        Rectangle clip = new Rectangle(SONG_THUMB_SIZE, SONG_THUMB_SIZE);
        clip.setArcWidth(8);
        clip.setArcHeight(8);
        thumb.setClip(clip);

        Label title = new Label(textOr(song.title(), "Unknown song"));
        title.getStyleClass().add("song-title");
        String artists = song.artistNames() == null || song.artistNames().isEmpty()
                ? artistNameLabel.getText()
                : String.join(", ", song.artistNames());
        Label artist = new Label(artists);
        artist.getStyleClass().add("song-artist");
        VBox info = new VBox(3, title, artist);
        info.setMinWidth(0);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label countLabel = new Label(formatCount(count(song)) + " searches");
        countLabel.getStyleClass().add("song-count");

        SVGPath playIcon = new SVGPath();
        playIcon.setContent("M6 4l14 8-14 8z");
        playIcon.getStyleClass().add("song-play-svg");
        Group iconGroup = new Group(playIcon);
        iconGroup.setScaleX(0.55);
        iconGroup.setScaleY(0.55);
        Button playButton = new Button();
        playButton.setGraphic(iconGroup);
        playButton.getStyleClass().add("song-play");
        playButton.setOnAction(e -> {
            playSong(song);
            e.consume();
        });

        HBox row = new HBox(rankLabel, thumb, info, countLabel, playButton);
        row.getStyleClass().add("song-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setOnMouseClicked(e -> Navigator.openSongDetail(song));
        return row;
    }

    @FXML
    private void handleBack() {
        try {
            Navigator.goTo(Screen.ARTISTS);
        } catch (IOException e) {
            detailMessageLabel.setText("Không thể quay lại: " + e.getMessage());
        }
    }

    @FXML
    private void handlePlayPopular() {
        if (!popularSongs.isEmpty()) {
            playSong(popularSongs.get(0));
        }
    }

    private void playSong(SongData song) {
        PlayerManager.getInstance().playSong(new ArrayList<>(popularSongs), song);
    }

    private void setupHeroLayout() {
        heroBackground.setManaged(false);
        heroBackground.setEffect(new GaussianBlur(30));
        heroBackground.fitWidthProperty().bind(heroPane.widthProperty().multiply(0.6));
        heroBackground.fitHeightProperty().bind(heroPane.heightProperty());
        heroBackground.layoutXProperty().bind(heroPane.widthProperty().subtract(heroBackground.fitWidthProperty()));
        heroBackground.setLayoutY(0);

        Rectangle heroClip = new Rectangle();
        heroClip.setArcWidth(28);
        heroClip.setArcHeight(28);
        heroClip.widthProperty().bind(heroPane.widthProperty());
        heroClip.heightProperty().bind(heroPane.heightProperty());
        heroPane.setClip(heroClip);
    }

    private void setupPhotoClip() {
        Rectangle clip = new Rectangle(PHOTO_SIZE, PHOTO_SIZE);
        clip.setArcWidth(20);
        clip.setArcHeight(20);
        artistPhoto.setClip(clip);
    }

    private Image loadImage(String url) {
        if (url != null && !url.isBlank()) {
            if (url.contains("zmdcdn.me")) {
                url = url.replaceFirst("/w\\d+_", "/w" + ZING_IMAGE_SIZE + "_");
            }
            return new Image(url, true);
        }
        return new Image(getClass().getResource(DEFAULT_PHOTO).toExternalForm());
    }

    private void cropToCover(ImageView view, Image image, double frameRatio) {
        Runnable apply = () -> {
            double w = image.getWidth();
            double h = image.getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            double cropW = w;
            double cropH = h;
            if (w / h > frameRatio) {
                cropW = h * frameRatio;
            } else {
                cropH = w / frameRatio;
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

    private long count(SongData song) {
        return song.searchCount() == null ? 0 : song.searchCount();
    }

    private String textOr(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
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
}
