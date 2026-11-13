package com.musicfinder.controller;

import com.musicfinder.network.ClientConnection;
import com.musicfinder.player.PlayerManager;
import com.musicfinder.shared.enums.Action;
import com.musicfinder.shared.request.Request;
import com.musicfinder.shared.request.payload.SongPayload;
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

    private static final Duration VIDEO_POLL_DELAY = Duration.seconds(2);
    private static final int VIDEO_MAX_POLLS = 90; // ~3 phút chờ server tải video

    @FXML
    private Label titleLabel, composerLabel, durationLabel, artistLabel, statusLabel;
    @FXML
    private Button favoriteButton;
    @FXML
    private ImageView coverImage;
    @FXML
    private ToggleButton lyricsTab, videoTab;
    @FXML
    private VBox lyricsPane, videoPane;

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

    private SongData song;

    private final MediaView mediaView = new MediaView();
    private MediaPlayer videoPlayer;
    private PauseTransition pollTimer;
    private int pollCount;
    private boolean videoRequested; // đã bắt đầu tải video chưa (chỉ tải khi mở tab lần đầu)
    private boolean triedServer; // đã hỏi server GET_VIDEO chưa (tránh lặp vô hạn khi lỗi)
    private boolean userSeeking;
    private boolean disposed;
    // Nhạc bắt đầu phát -> tạm dừng video (giữ tham chiếu để gỡ đúng hook khi rời màn hình)
    private final Runnable pauseVideoOnAudio = this::pauseVideo;

    @FXML
    private void initialize() {
        ToggleGroup tabs = new ToggleGroup();
        lyricsTab.setToggleGroup(tabs);
        videoTab.setToggleGroup(tabs);
        tabs.selectedToggleProperty().addListener((obs, oldTab, newTab) -> {
            // Không cho bỏ chọn hết tab
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

        // Rời màn hình (content bị thay) -> dừng và giải phóng video
        videoPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                disposeVideo();
            }
        });

        lyricsTab.setSelected(true);
        PlayerManager.getInstance().setOnAudioStart(pauseVideoOnAudio);
    }

    public void init(SongData song) {
        this.song = java.util.Objects.requireNonNull(song, "song");
        titleLabel.setText(song.title());

        String artists = song.artistNames() == null
                ? "Unknown artist"
                : String.join(", ", song.artistNames());
        artistLabel.setText(artists);

        composerLabel.setText(song.composerList() == null || song.composerList().isBlank() ? "—" : song.composerList());

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

        // Nếu người dùng đã mở tab video trước khi init chạy
        if (videoTab.isSelected()) {
            startVideo();
        }
    }

    // ===================== Tabs =====================

    private void showTab(boolean video) {
        lyricsPane.setVisible(!video);
        lyricsPane.setManaged(!video);
        videoPane.setVisible(video);
        videoPane.setManaged(video);

        if (video) {
            startVideo();
        } else if (videoPlayer != null) {
            videoPlayer.pause(); // rời tab video -> tạm dừng
        }
    }

    // ===================== Video: lấy link =====================

    private void startVideo() {
        if (song == null || videoRequested) {
            return;
        }
        videoRequested = true;

        // 1. Ưu tiên link đã lưu sẵn trong SongData.video_url
        String savedUrl = song.video_url();
        if (savedUrl != null && !savedUrl.isBlank()) {
            playVideo(savedUrl);
            return;
        }
        // 2. Chưa có -> nhờ server chuẩn bị video
        requestVideoFromServer();
    }

    private void requestVideoFromServer() {
        if (disposed) {
            return;
        }
        triedServer = true;
        if (song.songID() == null && (song.externalId() == null || song.externalId().isBlank())) {
            showVideoStatus("Bài hát này chưa có video.");
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
                    showVideoStatus(textOr(response.getMessage(), "Không lấy được video."));
                    return;
                }
                VideoData data = response.dataAs(VideoData.class);
                if (data != null && "READY".equalsIgnoreCase(data.state()) && data.play_url() != null) {
                    playVideo(data.play_url());
                } else {
                    scheduleNextPoll();
                }
            }));
        } catch (Exception e) {
            showVideoStatus("Không thể kết nối server: " + e.getMessage());
        }
    }

    // Server trả PROCESSING -> chờ 2 giây rồi hỏi lại
    private void scheduleNextPoll() {
        pollCount++;
        if (pollCount > VIDEO_MAX_POLLS) {
            showVideoStatus("Video mất quá nhiều thời gian để chuẩn bị. Vui lòng thử lại sau.");
            return;
        }
        showVideoStatus("Server đang tải video, vui lòng chờ... (" + pollCount * 2 + "s)");
        pollTimer = new PauseTransition(VIDEO_POLL_DELAY);
        pollTimer.setOnFinished(e -> requestVideoFromServer());
        pollTimer.play();
    }

    // ===================== Video: phát =====================

    private void playVideo(String url) {
        disposePlayerOnly();
        showVideoStatus("Đang tải video...");
        // Dùng biến cục bộ "player" trong mọi listener: MediaPlayer vẫn bắn sự kiện (currentTime, status...)
        // một lúc sau khi stop()/dispose(), lúc đó field videoPlayer đã là null hoặc đã là player khác.
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
            videoTotalTime.setText(formatTime(player.getTotalDuration()));
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
        });
    }

    // Chờ đệm trước ~2s (hoặc tối đa 1.5s) rồi mới play, tránh giật ở vài giây đầu khi mở lần đầu
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

    // Link lưu trong video_url có thể đã hết hạn / file bị xoá -> hỏi lại server một lần
    private void onVideoFailed(String message) {
        disposePlayerOnly();
        if (!triedServer) {
            pollCount = 0;
            requestVideoFromServer();
        } else {
            showVideoStatus(message);
        }
    }

    @FXML
    private void handleVideoPlayPause() {
        if (videoPlayer == null) {
            return;
        }
        if (videoPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            videoPlayer.pause();
        } else {
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
        // Không để kích thước video "đẩy" khung to ra (tránh khung chỉ to mà không co lại khi thu nhỏ cửa sổ);
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
        videoControls.setDisable(true);
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
}
