package com.musicfinder.navigation;

import com.musicfinder.MainApp;
import com.musicfinder.controller.ArtistDetailController;
import com.musicfinder.controller.ArtistsController;
import com.musicfinder.controller.MainLayoutController;
import com.musicfinder.controller.SearchController;
import com.musicfinder.controller.SongDetailController;
import com.musicfinder.controller.StatusDialog;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.IOException;
import com.musicfinder.controller.playlist_songController;
import com.musicfinder.shared.response.payload.ArtistData;
import com.musicfinder.shared.response.payload.PlayListData;
import com.musicfinder.shared.response.payload.SongData;

public final class Navigator {

    private static final double DEFAULT_WIDTH = 1200;
    private static final double DEFAULT_HEIGHT = 760;

    private static Stage stage;
    private static Parent mainLayoutRoot;
    private static MainLayoutController mainLayoutController;
    // Khung cửa sổ (thanh tiêu đề + nút đóng) bọc ngoài mọi màn hình
    private static WindowFrame windowFrame;

    private Navigator() {
    }

    public static void initialize(Stage primaryStage) {
        stage = primaryStage;
    }

    public static void goTo(Screen screen) throws IOException {
        ensureInitialized();

        if (screen.isLogin() || screen == Screen.REGISTER || screen == Screen.FORGOT_PASSWORD) {
            showLogin(screen);
            return;
        }

        ensureMainLayout();
        mainLayoutController.show(screen);
        applyWindowSettings(screen);
    }

    private static void showLogin(Screen screen) throws IOException {
        Parent root = FXMLLoader.load(
                MainApp.class.getResource(screen.getFxml()));

        setSceneRoot(root);
        mainLayoutRoot = null;
        mainLayoutController = null;
        applyWindowSettings(screen);
    }

    private static void ensureMainLayout() throws IOException {
        if (mainLayoutRoot != null && mainLayoutController != null) {
            if (stage.getScene() == null || windowFrame == null || windowFrame.getContent() != mainLayoutRoot) {
                setSceneRoot(mainLayoutRoot);
            }
            return;
        }

        FXMLLoader loader = new FXMLLoader(
                MainApp.class.getResource("/com/musicfinder/view/main-layout.fxml"));

        mainLayoutRoot = loader.load();
        mainLayoutController = loader.getController();
        setSceneRoot(mainLayoutRoot);
    }

    private static void setSceneRoot(Parent root) {
        if (windowFrame == null) {
            windowFrame = new WindowFrame(stage);
        }
        windowFrame.setContent(root);

        Scene scene = stage.getScene();
        if (scene == null) {
            stage.setScene(new Scene(windowFrame, DEFAULT_WIDTH, DEFAULT_HEIGHT));
        } else if (scene.getRoot() != windowFrame) {
            scene.setRoot(windowFrame);
        }
    }

    private static void applyWindowSettings(Screen screen) {
        stage.setTitle(screen.getTitle());
        stage.setMinWidth(screen.getMinWidth());
        stage.setMinHeight(screen.getMinHeight());
    }

    private static void ensureInitialized() {
        if (stage == null) {
            throw new IllegalStateException("Navigator has not been initialized.");
        }
    }

    public static void seachFromAnotherScreen(
            String keyword, String searchType) {
        try {
            ensureInitialized();

            boolean artist = "artist".equalsIgnoreCase(searchType);
            boolean song = "song".equalsIgnoreCase(searchType);

            if (!artist && !song) {
                throw new IllegalArgumentException(
                        "Loại tìm kiếm không hợp lệ: " + searchType);
            }

            ensureMainLayout();
            Screen screen = artist ? Screen.ARTISTS : Screen.SEARCH;

            mainLayoutController.show(screen, controller -> {
                if (controller instanceof SearchController search) {
                    search.seachFromAnotherScreen(keyword);
                } else if (controller instanceof ArtistsController artists) {
                    artists.seachFromAnotherScreen(keyword);
                }
            });

            applyWindowSettings(screen);
        } catch (IOException | RuntimeException e) {
            StatusDialog.error(stage.getScene().getWindow(), "Lỗi tìm kiếm");
        }
    }

    public static void openPlaylist(PlayListData playlist) {
        try {
            ensureInitialized();
            ensureMainLayout();

            mainLayoutController.show(Screen.PLAYLIST_SONG, controller -> {
                if (controller instanceof playlist_songController playlistController) {
                    playlistController.init(playlist);
                }
            });

            applyWindowSettings(Screen.PLAYLIST_SONG);
        } catch (IOException | RuntimeException e) {
            Alert alert = new Alert(
                    Alert.AlertType.ERROR);

            alert.setTitle("Lỗi");
            alert.setHeaderText("Không thể mở playlist");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }
    public static void openSongDetail(SongData song) {
        try {
            ensureInitialized();
            ensureMainLayout();

            mainLayoutController.show(Screen.SONG_DETAIL, controller -> {
                if (controller instanceof SongDetailController songDetail) {
                    songDetail.init(song);
                }
            });

            applyWindowSettings(Screen.SONG_DETAIL);
        } catch (IOException | RuntimeException e) {
            Alert alert = new Alert(
                    Alert.AlertType.ERROR);

            alert.setTitle("Lỗi");
            alert.setHeaderText("Không thể mở Detail Songs");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    public static void openArtistDetail(ArtistData artist) {
        try {
            ensureInitialized();
            ensureMainLayout();

            mainLayoutController.show(Screen.ARTIST_DETAIL, controller -> {
                if (controller instanceof ArtistDetailController artistDetail) {
                    artistDetail.init(artist);
                }
            });

            applyWindowSettings(Screen.ARTIST_DETAIL);
        } catch (IOException | RuntimeException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setHeaderText("Không thể mở trang ca sĩ");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }
}
