package com.musicfinder.controller;

import com.musicfinder.MainApp;
import com.musicfinder.navigation.Navigator;
import com.musicfinder.network.ClientConnection;
import com.musicfinder.navigation.Screen;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import com.musicfinder.player.PlayerListener;
import com.musicfinder.player.PlayerManager;
import com.musicfinder.shared.response.payload.SongData;

public class MainLayoutController implements PlayerListener {

    @FXML
    private StackPane contentContainer;

    @FXML
    private Label currentPageLabel;

    @FXML
    private Label serverStatusLabel;

    @FXML
    private Label avatarInitialLabel;

    @FXML
    private Label favoriteCountLabel;

    @FXML
    private Button homeButton;

    @FXML
    private Button searchButton;

    @FXML
    private Button artistsButton;

    @FXML
    private Button playlistsButton;

    @FXML
    private Button favoritesButton;

    @FXML
    private Button historyButton;

    @FXML
    private Button trendingButton;

    @FXML
    private Button profileButton;

    @FXML
    private HBox nowPlayingBar;

    @FXML
    private ImageView nowPlayingImage;

    @FXML
    private Label nowPlayingTitle;

    @FXML
    private Label nowPlayingArtist;

    @FXML
    private Button playPauseButton;

    private SearchController searchController;

    private final Map<Screen, Button> navigationButtons = new EnumMap<>(Screen.class);

    @FXML
    private Slider progressSlider;

    @FXML
    private Label currentTimeLabel;

    @FXML
    private Label totalTimeLabel;

    private boolean userSeeking = false;

    // Âm lượng
    private static final String ICON_VOLUME_HIGH = "M11 5L6 9H2v6h4l5 4V5z M15.5 8.5a5 5 0 0 1 0 7 M19 5a10 10 0 0 1 0 14";
    private static final String ICON_VOLUME_LOW = "M11 5L6 9H2v6h4l5 4V5z M15.5 8.5a5 5 0 0 1 0 7";
    private static final String ICON_VOLUME_MUTED = "M11 5L6 9H2v6h4l5 4V5z M23 9l-6 6 M17 9l6 6";

    @FXML
    private Button muteButton;

    @FXML
    private SVGPath volumeIcon;

    @FXML
    private Slider volumeSlider;

    @FXML
    private Label volumeLabel;

    @FXML
    private StackPane loadingOverlay;

    @FXML
    private void initialize() {
        navigationButtons.put(Screen.HOME, homeButton);
        navigationButtons.put(Screen.SEARCH, searchButton);
        navigationButtons.put(Screen.ARTISTS, artistsButton);
        navigationButtons.put(Screen.PLAYLISTS, playlistsButton);
        navigationButtons.put(Screen.FAVORITES, favoritesButton);
        navigationButtons.put(Screen.SEARCH_HISTORY, historyButton);
        navigationButtons.put(Screen.TRENDING, trendingButton);
        navigationButtons.put(Screen.PROFILE, profileButton);
        setServerConnected(ClientConnection.getInstance().isConnected());
        PlayerManager.getInstance().setListener(this);
        progressSlider.setMin(0);
        progressSlider.setMax(100);
        progressSlider.setValue(0);

        progressSlider.setOnMousePressed(event -> {
            userSeeking = true;
        });

        progressSlider.setOnMouseReleased(event -> {
            userSeeking = false;

            PlayerManager.getInstance()
                    .seek(progressSlider.getValue());
        });

        setupVolume();
    }

    // ===================== Âm lượng =====================

    private void setupVolume() {
        PlayerManager player = PlayerManager.getInstance();
        volumeSlider.setValue(player.getVolume() * 100);
        updateVolumeUi();
        // .track chỉ tồn tại sau khi Slider có skin -> tô màu lại lúc đó
        volumeSlider.skinProperty().addListener((obs, oldSkin, newSkin) -> Platform.runLater(this::updateVolumeUi));

        // Kéo thanh -> đổi âm lượng ngay
        volumeSlider.valueProperty().addListener((obs, oldV, newV) -> {
            player.setVolume(newV.doubleValue() / 100.0);
            updateVolumeUi();
        });

        // Lăn chuột trên thanh để tăng/giảm 5%
        volumeSlider.setOnScroll(e -> {
            double step = e.getDeltaY() > 0 ? 5 : -5;
            volumeSlider.setValue(Math.max(0, Math.min(100, volumeSlider.getValue() + step)));
        });
    }

    @FXML
    private void handleToggleMute() {
        PlayerManager.getInstance().toggleMute();
        updateVolumeUi();
    }

    private void updateVolumeUi() {
        PlayerManager player = PlayerManager.getInstance();
        int percent = (int) Math.round(player.getVolume() * 100);
        boolean silent = player.isMuted() || percent == 0;

        volumeLabel.setText(player.isMuted() ? "Muted" : percent + "%");
        if (silent) {
            volumeIcon.setContent(ICON_VOLUME_MUTED);
        } else if (percent < 50) {
            volumeIcon.setContent(ICON_VOLUME_LOW);
        } else {
            volumeIcon.setContent(ICON_VOLUME_HIGH);
        }

        // Tô phần đã kéo của thanh bằng màu tím
        double fill = player.isMuted() ? 0 : percent;
        volumeSlider.lookupAll(".track").forEach(track -> track.setStyle(
                "-fx-background-color: linear-gradient(to right, #c99eff " + fill + "%, #46404d " + fill + "%);"));
    }

    public void show(Screen screen) throws IOException {
        if (screen.isLogin()) {
            throw new IllegalArgumentException("Login is not content of the main layout.");
        }

        FXMLLoader loader = new FXMLLoader(
                MainApp.class.getResource(screen.getFxml()));

        Parent content = loader.load();
        Object controller = loader.getController();
        if (controller instanceof SearchController) {
            searchController = (SearchController) controller;
            searchController.setMainLayoutController(this);
        }
        contentContainer.getChildren().setAll(content);

        currentPageLabel.setText(screen.getHeaderTitle());
        setActiveNavigation(screen);
    }

    public void show(Screen screen, Consumer onReady) throws IOException {
        if (screen.isLogin()) {
            throw new IllegalArgumentException("Login is not content of the main layout.");
        }

        FXMLLoader loader = new FXMLLoader(
                MainApp.class.getResource(screen.getFxml()));

        Parent content = loader.load();
        Object controller = loader.getController();
        if (controller instanceof SearchController) {
            searchController = (SearchController) controller;
            searchController.setMainLayoutController(this);
        } else if (controller instanceof ForgotPasswordController) {
            ForgotPasswordController forgotController = (ForgotPasswordController) controller;
            forgotController.setListener(this);
        }
        contentContainer.getChildren().setAll(content);

        currentPageLabel.setText(screen.getHeaderTitle());
        setActiveNavigation(screen);
        onReady.accept(controller);
    }

    private void setActiveNavigation(Screen activeScreen) {
        navigationButtons.forEach((screen, button) -> {
            button.getStyleClass().remove("nav-active");
            if (screen == activeScreen) {
                button.getStyleClass().add("nav-active");
            }
        });
    }

    @FXML
    private void handleHome() throws IOException {
        Navigator.goTo(Screen.HOME);
    }

    @FXML
    private void handleSearch() throws IOException {
        Navigator.goTo(Screen.SEARCH);
    }

    @FXML
    private void handleArtists() throws IOException {
        Navigator.goTo(Screen.ARTISTS);
    }

    @FXML
    private void handlePlaylists() throws IOException {
        Navigator.goTo(Screen.PLAYLISTS);
    }

    @FXML
    private void handleFavorites() throws IOException {
        Navigator.goTo(Screen.FAVORITES);
    }

    @FXML
    private void handleHistory() throws IOException {
        Navigator.goTo(Screen.SEARCH_HISTORY);
    }

    @FXML
    private void handleTrending() throws IOException {
        Navigator.goTo(Screen.TRENDING);
    }

    @FXML
    private void handleProfile() throws IOException {
        Navigator.goTo(Screen.PROFILE);
    }

    @FXML
    private void handleSettings() {
        // Settings screen is not implemented yet.
    }

    @FXML
    private void handleLogout() throws IOException {
        Navigator.goTo(Screen.LOGIN);
    }

    public void setServerConnected(boolean connected) {
        serverStatusLabel.setText(connected ? "Server Connected" : "Server Disconnected");
    }

    public void setAvatarInitials(String initials) {
        avatarInitialLabel.setText(initials == null ? "" : initials);
    }

    public void setFavoriteCount(Integer count) {
        favoriteCountLabel.setText(count == null ? "" : String.valueOf(count));
    }

    public void showNowPlaying(String title, String artist, String thumbnailUrl) {

        nowPlayingTitle.setText(title);
        nowPlayingArtist.setText(artist);

        if (thumbnailUrl != null && !thumbnailUrl.isBlank()) {
            nowPlayingImage.setImage(
                    new Image(thumbnailUrl, true));
        }

        nowPlayingBar.setVisible(true);
        nowPlayingBar.setManaged(true);
    }

    @FXML
    private void handlePlayPause() {

        PlayerManager.getInstance()
                .togglePlayPause();
    }

    @FXML
    private void handleNextSong() {

        PlayerManager.getInstance()
                .nextSong();
    }

    @FXML
    private void handlePreviousSong() {

        PlayerManager.getInstance()
                .previousSong();
    }

    @Override
    public void onSongChanged(SongData song) {

        String artists = song.artistNames() == null ? "Unknown artist"
                : String.join(", ", song.artistNames());
        showNowPlaying(song.title(), artists, song.thumbnail_url());
    }

    @Override
    public void onPlayingChanged(
            boolean playing) {

        setPlayingState(playing);
    }

    public void setPlayingState(boolean playing) {
        playPauseButton.setText(
                playing ? "⏸" : "▶");
    }

    @Override
    public void onPlayerError(String message) {

        System.out.println(
                "Player error: " + message);
    }

    @Override
    public void onProgressChanged(
            double currentSeconds,
            double totalSeconds) {

        if (totalSeconds <= 0) {
            return;
        }

        if (!userSeeking) {

            double percent = currentSeconds
                    / totalSeconds
                    * 100;

            progressSlider.setValue(percent);
        }

        currentTimeLabel.setText(
                formatTime(currentSeconds));

        totalTimeLabel.setText(
                formatTime(totalSeconds));
    }

    private String formatTime(double seconds) {

        int totalSeconds = (int) seconds;

        int minutes = totalSeconds / 60;

        int remainingSeconds = totalSeconds % 60;

        return String.format(
                "%02d:%02d",
                minutes,
                remainingSeconds);
    }

    @Override
    public void onLoadingChanged(boolean loading) {

        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> onLoadingChanged(loading));
            return;
        }

        loadingOverlay.setVisible(loading);
    }

}
