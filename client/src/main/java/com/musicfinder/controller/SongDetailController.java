package com.musicfinder.controller;

import java.io.IOException;
import java.util.List;

import com.musicfinder.navigation.Navigator;
import com.musicfinder.navigation.Screen;
import com.musicfinder.network.ClientConnection;
import com.musicfinder.player.PlayerManager;
import com.musicfinder.shared.enums.Action;
import com.musicfinder.shared.enums.PlayList_Type;
import com.musicfinder.shared.request.Request;
import com.musicfinder.shared.request.payload.Add_RemoveToPlaylistPayload;
import com.musicfinder.shared.request.payload.SongPayload;
import com.musicfinder.shared.response.payload.LyricData;
import com.musicfinder.shared.response.payload.SongData;
import com.musicfinder.shared.response.payload.VideoData;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class SongDetailController {
    @FXML
    private StackPane loadingOverlay;

    private static final Duration VIDEO_POLL_DELAY = Duration.seconds(2);
    private static final int VIDEO_MAX_POLLS = 90; // ~3 phút chờ server tải video

    @FXML
    private Label titleLabel, composerLabel, durationLabel, artistLabel, statusLabel;
    @FXML
    private Button playButton;
    @FXML
    private Button playlistButton;
    @FXML
    private Button favoriteButton;
    @FXML
    private ImageView coverImage;
    @FXML
    private ToggleButton lyricsTab, videoTab;
    @FXML
    private VBox lyricsPane, videoPane;

    @FXML
    private Button backToSearch;

    // Video
    @FXML
    private StackPane videoContainer;
    @FXML
    private Label videoStatusLabel, videoCurrentTime, videoTotalTime;
    @FXML
    private HBox videoControls;
    @FXML
    private Button videoPlayButton;
    @FXML
    private Slider videoSlider;

    @FXML
    private Label lyricsLabel;
    @FXML
    private Label nextLyricsLabel;
    @FXML
    private VBox nextLyricBox;
    private com.musicfinder.player.LrcLyrics lyrics;
    private boolean lyricLoading;
    private boolean videoClockActive;
    private final javafx.beans.value.ChangeListener<Number> lyricProgressListener = (obs, oldTime,
            newTime) -> updateLyrics();

    private SongData song;

    private final MediaView mediaView = new MediaView();
    private MediaPlayer videoPlayer;
    private PauseTransition pollTimer;
    private int pollCount;
    private boolean videoRequested;
    private boolean userSeeking;
    private boolean disposed;
    private final Runnable pauseVideoOnAudio = () -> {
        videoClockActive = false;
        pauseVideo();
        updateLyrics();
    };

    @FXML
    private void initialize() {
        ToggleGroup tabs = new ToggleGroup();
        lyricsTab.setToggleGroup(tabs);
        videoTab.setToggleGroup(tabs);
        tabs.selectedToggleProperty().addListener((obs, oldTab, newTab) -> {

            if (newTab == null) {
                oldTab.setSelected(true);
                return;
            }
            showTab(newTab == videoTab);
        });

        Rectangle clip = new Rectangle(210, 210);
        clip.setArcWidth(20);
        clip.setArcHeight(20);
        coverImage.setClip(clip);
        coverImage.setFitWidth(210);
        coverImage.setFitHeight(210);
        coverImage.setPreserveRatio(false);

        setupVideoView();
        PlayerManager.getInstance().playbackSecondsProperty().addListener(lyricProgressListener);

        videoPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                disposeVideo();
            }
        });

        lyricsPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null)
                updateLyrics();
        });
        lyricsTab.setSelected(true);
        PlayerManager.getInstance().setOnAudioStart(pauseVideoOnAudio);
        playButton.setOnAction(event -> {
            if (disposed || song == null)
                return;
            pauseVideo();
            videoClockActive = false;
            lyricsTab.setSelected(true);
            getLyric();
            PlayerManager.getInstance().playSong(List.of(song), song);
        });
        favoriteButton.setOnAction(event -> {
            handlleAddToFavorite(song);
        });
        backToSearch.setOnAction(event -> {
            try {
                Navigator.goTo(Screen.SEARCH);
            } catch (IOException e) {
                StatusDialog.error(playButton.getScene().getWindow(), "Lỗi chuyển màn hình!");
            }
        });
    }

    public void init(SongData song) {
        this.song = java.util.Objects.requireNonNull(song, "song");
        titleLabel.setText(song.title());

        String artists = song.artistNames() == null
                ? "Unknown artist"
                : String.join(", ", song.artistNames());
        artistLabel.setText(artists);

        composerLabel.setText(song.artistNames() == null || song.artistNames().isEmpty() ? "—"
                : String.join(", ", song.artistNames()));

        Integer seconds = song.durations_seconds();
        durationLabel.setText(seconds == null ? "—"
                : String.format("%d:%02d", seconds / 60, seconds % 60));

        statusLabel.setText("");
        coverImage.setImage(null);
        if (song.thumbnail_url() != null && !song.thumbnail_url().isBlank()) {
            try {
                coverImage.setImage(new Image(song.thumbnail_url(), true));
            } catch (IllegalArgumentException e) {
                statusLabel.setText("Không thể tải ảnh bìa.");
            }
        }
        showLyricFrame("Bấm Play song để phát nhạc và xem lời đồng bộ.", "");
    }

    // ===================== Tabs =====================

    private void showTab(boolean video) {
        lyricsPane.setVisible(!video);
        lyricsPane.setManaged(!video);
        videoPane.setVisible(video);
        videoPane.setManaged(video);

        if (video) {
            startVideo();
        } else {
            updateLyrics();
        }
    }

    // ===================== Video: lấy link =====================

    private void startVideo() {
        if (disposed || song == null)
            return;
        if (videoPlayer != null) {
            if (videoPlayer.getStatus() == MediaPlayer.Status.PAUSED) {
                videoClockActive = true;
                PlayerManager.getInstance().pause();
                videoPlayer.play();
            }
            return;
        }
        if (videoRequested)
            return;
        videoRequested = true;

        pollCount = 0;
        // URL lưu trước đó có thể trỏ tới cache đã hết hạn; luôn kiểm tra server.
        requestVideoFromServer();
    }

    private void requestVideoFromServer() {
        if (disposed) {
            return;
        }
        if (song.songID() == null && (song.externalId() == null || song.externalId().isBlank())) {
            onVideoFailed("Bài hát này chưa có video.");
            return;
        }
        if (pollCount == 0) {
            showVideoStatus("Đang chuẩn bị video...");
        }

        Request request = Request.of(Action.GET_VIDEO, new SongPayload(song.songID(), song.externalId()));
        try {
            ClientConnection.getInstance().send(request, response -> Platform.runLater(() -> {
                if (disposed) {
                    return;
                }
                if (!response.isSuccess()) {
                    onVideoFailed(textOr(response.getMessage(), "Không lấy được video."));
                    return;
                }
                try {
                    VideoData data = response.dataAs(VideoData.class);
                    if (data != null && "READY".equalsIgnoreCase(data.state())
                            && data.play_url() != null && !data.play_url().isBlank()) {
                        playVideo(data.play_url());
                    } else if (data != null && "PROCESSING".equalsIgnoreCase(data.state())) {
                        scheduleNextPoll();
                    } else {
                        onVideoFailed("Server trả về dữ liệu video không hợp lệ.");
                    }
                } catch (Exception e) {
                    onVideoFailed("Không thể mở video: " + e.getMessage());
                }
            }));
        } catch (Exception e) {
            onVideoFailed("Không thể kết nối server: " + e.getMessage());
        }
    }

    // Server trả PROCESSING -> chờ 2 giây rồi hỏi lại
    private void scheduleNextPoll() {
        pollCount++;
        if (pollCount > VIDEO_MAX_POLLS) {
            onVideoFailed("Video mất quá nhiều thời gian để chuẩn bị. Vui lòng thử lại sau.");
            return;
        }
        showVideoStatus("Server đang tải video, vui lòng chờ... (" + pollCount * 2 + "s)");
        pollTimer = new PauseTransition(VIDEO_POLL_DELAY);
        pollTimer.setOnFinished(e -> requestVideoFromServer());
        pollTimer.play();
    }

    // ===================== Video: phát =====================

    private void playVideo(String url) {
        System.out.println("VIDEO URL: " + url);
        disposePlayerOnly();
        showVideoStatus("Đang tải video...");
        // Dùng biến cục bộ "player" trong mọi listener: MediaPlayer vẫn bắn sự kiện
        // (currentTime, status...)
        // một lúc sau khi stop()/dispose(), lúc đó field videoPlayer đã là null hoặc đã
        // là player khác.
        MediaPlayer player;
        try {
            Media media = new Media(url);
            player = new MediaPlayer(media);
        } catch (MediaException | IllegalArgumentException e) {
            onVideoFailed("Không phát được video: " + e.getMessage());
            return;
        }
        videoPlayer = player;
        mediaView.setMediaPlayer(player);

        player.setOnReady(() -> {
            if (player != videoPlayer) {
                return; // player cũ đã bị thay/huỷ
            }
            videoStatusLabel.setVisible(false);
            mediaView.setVisible(true);
            videoControls.setDisable(false);
            videoSlider.setDisable(false);
            videoTotalTime.setText(formatTime(player.getTotalDuration()));
            videoClockActive = true;
            PlayerManager.getInstance().pause(); // dừng nhạc nền khi xem video
            playWhenBuffered(player);
        });
        player.statusProperty().addListener((obs, oldS, newS) -> {
            if (player == videoPlayer) {
                videoPlayButton.setText(newS == MediaPlayer.Status.PLAYING ? "⏸" : "▶");
            }
        });
        player.currentTimeProperty().addListener((obs, oldT, newT) -> {
            if (player != videoPlayer) {
                return;
            }
            videoCurrentTime.setText(formatTime(newT));
            updateLyrics();
            Duration total = player.getTotalDuration();
            if (!userSeeking && total != null && total.toMillis() > 0) {
                videoSlider.setValue(newT.toMillis() / total.toMillis() * 100);
            }
        });
        player.setOnEndOfMedia(() -> {
            if (player == videoPlayer) {
                player.pause();
                player.seek(Duration.ZERO);
            }
        });
        player.setOnError(() -> {
            if (player == videoPlayer) {
                onVideoFailed("Không phát được video: "
                        + (player.getError() == null ? "" : player.getError().getMessage()));
            }
            if (player.getError() != null) {
                player.getError().printStackTrace();
            }
        });
    }

    // Chờ đệm trước ~2s (hoặc tối đa 1.5s) rồi mới play, tránh giật ở vài giây đầu
    // khi mở lần đầu
    private void playWhenBuffered(MediaPlayer player) {
        Duration total = player.getTotalDuration();
        Duration target = Duration.seconds(2);
        if (total != null && !total.isUnknown() && total.lessThan(target)) {
            target = total;
        }
        Duration need = target;
        if (!player.getBufferProgressTime().lessThan(need)) {
            player.play();
            return;
        }
        boolean[] started = { false };
        Runnable start = () -> {
            if (!started[0] && player == videoPlayer) {
                started[0] = true;
                player.play();
            }
        };
        player.bufferProgressTimeProperty().addListener((obs, oldB, newB) -> {
            if (newB != null && !newB.lessThan(need)) {
                start.run();
            }
        });
        PauseTransition fallback = new PauseTransition(Duration.millis(1500));
        fallback.setOnFinished(e -> start.run());
        fallback.play();
    }

    private void onVideoFailed(String message) {
        if (disposed)
            return;
        if (pollTimer != null) {
            pollTimer.stop();
            pollTimer = null;
        }
        disposePlayerOnly();
        videoRequested = false;
        videoClockActive = false;
        pollCount = 0;
        showVideoStatus(message + " Bấm ▶ để thử lại.");
    }

    @FXML
    private void handleVideoPlayPause() {
        if (videoPlayer == null) {
            startVideo();
            return;
        }
        if (videoPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            videoPlayer.pause();
        } else {
            videoClockActive = true;
            PlayerManager.getInstance().pause();
            videoPlayer.play();
        }
    }

    private void setupVideoView() {
        mediaView.setPreserveRatio(true);
        mediaView.setVisible(false);
        // Video co giãn theo khung, giữ tỉ lệ
        mediaView.fitWidthProperty().bind(videoContainer.widthProperty());
        mediaView.fitHeightProperty().bind(videoContainer.heightProperty());
        videoContainer.getChildren().add(0, mediaView);
        // Không để kích thước video "đẩy" khung to ra (tránh khung chỉ to mà không co
        // lại khi thu nhỏ cửa sổ);
        // VBox cha sẽ kéo khung rộng hết chiều ngang
        videoContainer.setMinWidth(0);
        videoContainer.setPrefWidth(0);
        videoContainer.setMaxWidth(Double.MAX_VALUE);

        Rectangle clip = new Rectangle();
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        clip.widthProperty().bind(videoContainer.widthProperty());
        clip.heightProperty().bind(videoContainer.heightProperty());
        videoContainer.setClip(clip);

        // Khung 16:9 theo chiều rộng
        videoContainer.prefHeightProperty().bind(videoContainer.widthProperty().multiply(9.0 / 16.0));

        // Click vào video để play/pause
        videoContainer.setOnMouseClicked(e -> handleVideoPlayPause());

        videoSlider.setDisable(true);
        videoSlider.setMin(0);
        videoSlider.setMax(100);
        videoSlider.setOnMousePressed(e -> userSeeking = true);
        videoSlider.setOnMouseReleased(e -> {
            userSeeking = false;
            seekVideo();
        });
    }

    private void seekVideo() {
        if (videoPlayer == null) {
            return;
        }
        Duration total = videoPlayer.getTotalDuration();
        if (total != null && total.toMillis() > 0) {
            videoPlayer.seek(total.multiply(videoSlider.getValue() / 100.0));
        }
    }

    // ===================== Dọn dẹp =====================

    private void disposePlayerOnly() {
        if (videoPlayer != null) {
            MediaPlayer old = videoPlayer;
            videoPlayer = null; // gán null trước để mọi sự kiện muộn của player cũ bị bỏ qua
            old.stop();
            old.dispose();
        }
        mediaView.setMediaPlayer(null);
        mediaView.setVisible(false);
        videoSlider.setDisable(true);
        videoSlider.setValue(0);
        videoCurrentTime.setText("0:00");
        videoTotalTime.setText("0:00");
    }

    private void pauseVideo() {
        if (videoPlayer != null && videoPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            videoPlayer.pause();
        }
    }

    private void disposeVideo() {
        disposed = true;
        PlayerManager.getInstance().playbackSecondsProperty().removeListener(lyricProgressListener);
        PlayerManager.getInstance().clearOnAudioStart(pauseVideoOnAudio);
        if (pollTimer != null) {
            pollTimer.stop();
        }
        disposePlayerOnly();
    }

    // ===================== Tiện ích =====================

    private void showVideoStatus(String message) {
        videoStatusLabel.setText(message);
        videoStatusLabel.setVisible(true);
    }

    private String formatTime(Duration d) {
        if (d == null || d.isUnknown() || d.isIndefinite()) {
            return "0:00";
        }
        int total = (int) Math.floor(d.toSeconds());
        return String.format("%d:%02d", total / 60, total % 60);
    }

    private String textOr(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private void updateLyrics() {
        if (disposed || lyricLoading || lyrics == null
                || !lyricsTab.isSelected() || lyricsLabel.getScene() == null || song == null)
            return;
        PlayerManager manager = PlayerManager.getInstance();
        double seconds;
        if (videoClockActive && videoPlayer != null) {
            seconds = videoPlayer.getCurrentTime().toSeconds();
        } else {
            SongData playing = manager.getCurrentSong();
            boolean sameSong = playing != null && ((song.songID() != null && song.songID().equals(playing.songID()))
                    || (song.externalId() != null && !song.externalId().isBlank()
                            && song.externalId().equals(playing.externalId())));
            if (!sameSong || manager.playbackSecondsProperty().get() < 0) {
                showLyricFrame("Phát bài hát này để xem lời đồng bộ.", "");
                return;
            }
            seconds = manager.playbackSecondsProperty().get();
        }
        if (lyrics != null) {
            var frame = lyrics.frameAt(seconds);
            showLyricFrame(frame.current(), frame.next());
        }
    }

    private void showLyricFrame(String current, String next) {
        if (!current.equals(lyricsLabel.getText()))
            lyricsLabel.setText(current);
        if (!next.equals(nextLyricsLabel.getText()))
            nextLyricsLabel.setText(next);
        boolean hasNext = !next.isBlank();
        nextLyricBox.setVisible(hasNext);
        nextLyricBox.setManaged(hasNext);
    }

    private void getLyric() {
        if (disposed || song == null || lyricLoading)
            return;
        if (lyrics != null) {
            updateLyrics();
            return;
        }
        lyricLoading = true;
        showLyricFrame("Đang tải lời bài hát...", "");
        SongPayload payload = new SongPayload(song.songID(), song.externalId());
        Request request = Request.of(Action.GET_LYRIC, payload);
        try {
            ClientConnection.getInstance().send(request, response -> Platform.runLater(() -> {
                lyricLoading = false;
                if (disposed)
                    return;
                try {
                    if (!response.isSuccess()) {
                        showLyricFrame(textOr(response.getMessage(), "Chưa có lời bài hát."), "");
                        return;
                    }
                    LyricData data = response.dataAs(LyricData.class);
                    if (data == null || !data.hasLyric() || data.text() == null || data.text().isBlank()) {
                        showLyricFrame("Chưa có lời bài hát.", "");
                        return;
                    }
                    lyrics = new com.musicfinder.player.LrcLyrics(data.text());
                    updateLyrics();
                } catch (Exception e) {
                    showLyricFrame("Không thể đọc lời bài hát.", "");
                }
            }));
        } catch (Exception e) {
            lyricLoading = false;
            showLyricFrame("Không thể tải lời bài hát: " + e.getMessage(), "");
        }
    }

    private void handlleAddToFavorite(SongData song) {
        Add_RemoveToPlaylistPayload payload = new Add_RemoveToPlaylistPayload(null, song, PlayList_Type.FAVORITES);
        Request request = Request.of(Action.ADD_FAVORITE, payload);
        try {
            ClientConnection.getInstance().send(request, response -> {
                Platform.runLater(() -> {
                    if (!response.isSuccess()) {
                        StatusDialog.error(playButton.getScene().getWindow(), response.getMessage());
                        return;
                    }
                    StatusDialog.success(playButton.getScene().getWindow(), response.getMessage());
                });
            });
        } catch (Exception e) {
            // TODO: handle exception
            Platform.runLater(() -> {
                StatusDialog.error(playButton.getScene().getWindow(), e.getMessage());
                return;
            });
        }
    }
}
