package com.musicfinder.player;

import com.musicfinder.network.ClientConnection;
import com.musicfinder.shared.enums.Action;
import com.musicfinder.shared.request.Request;
import com.musicfinder.shared.request.payload.SongPayload;
import com.musicfinder.shared.response.payload.SongData;

import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class PlayerManager {
    private static PlayerManager instance;
    private MediaPlayer mediaPlayer;
    private final javafx.beans.property.ReadOnlyDoubleWrapper playbackSeconds = new javafx.beans.property.ReadOnlyDoubleWrapper(
            -1);

    public javafx.beans.property.ReadOnlyDoubleProperty playbackSecondsProperty() {
        return playbackSeconds.getReadOnlyProperty();
    }

    public SongData getCurrentSong() {
        return currentSong;
    }

    private final List<SongData> currentSongList = new ArrayList<>();
    private int currentSongIndex = -1;
    private SongData currentSong;
    private Boolean isPlaying = false;
    private PlayerListener listener;
    // Gọi ngay trước khi nhạc bắt đầu phát (vd: để dừng video đang xem ở Song
    // detail)
    private Runnable onAudioStart;

    // Âm lượng 0.0 - 1.0, giữ nguyên khi đổi bài và được nhớ giữa các lần mở app
    private static final String PREF_VOLUME = "player.volume";
    private static final String PREF_MUTED = "player.muted";
    private final Preferences prefs = Preferences.userNodeForPackage(PlayerManager.class);
    private double volume = clampVolume(prefs.getDouble(PREF_VOLUME, 0.72));
    private boolean muted = prefs.getBoolean(PREF_MUTED, false);

    private PlayerManager() {

    }

    public static PlayerManager getInstance() {
        if (instance == null) {
            instance = new PlayerManager();
        }
        return instance;
    }

    public void setListener(PlayerListener listener) {
        this.listener = listener;
    }

    public void setOnAudioStart(Runnable onAudioStart) {
        this.onAudioStart = onAudioStart;
    }

    // Chỉ gỡ nếu đúng hook của người gọi (tránh gỡ nhầm hook của màn hình khác)
    public void clearOnAudioStart(Runnable onAudioStart) {
        if (this.onAudioStart == onAudioStart) {
            this.onAudioStart = null;
        }
    }

    // ===================== Âm lượng =====================

    public double getVolume() {
        return volume;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setVolume(double value) {
        volume = clampVolume(value);
        if (volume > 0 && muted) {
            muted = false; // kéo thanh âm lượng lên -> tự bỏ tắt tiếng
        }
        applyVolume();
        savePrefs();
    }

    public void toggleMute() {
        muted = !muted;
        applyVolume();
        savePrefs();
    }

    private void applyVolume() {
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(muted ? 0 : volume);
        }
    }

    private void savePrefs() {
        prefs.putDouble(PREF_VOLUME, volume);
        prefs.putBoolean(PREF_MUTED, muted);
    }

    private static double clampVolume(double value) {
        return Double.isNaN(value) ? 0.72 : Math.max(0, Math.min(1, value));
    }

    private void notifyAudioStart() {
        if (onAudioStart != null) {
            onAudioStart.run();
        }
    }

    public void playSong(List<SongData> songs, SongData selectedSong) {
        currentSongList.clear();
        if (songs != null) {
            currentSongList.addAll(songs);
        }
        currentSongIndex = currentSongList.indexOf(selectedSong);
        if (currentSongIndex < 0)
            currentSongIndex = 0;
        loadSongDetail(selectedSong);
    }

    private void loadSongDetail(SongData song) {

        // Hiện vòng tròn loading
        if (listener != null) {
            listener.onLoadingChanged(true);
        }

        SongPayload payload = new SongPayload(song.songID(), song.externalId());
        Request request = Request.of(Action.VIEW_SONG_DETAIL, payload);
        try {
            ClientConnection.getInstance().send(request, response -> {
                if (!response.isSuccess()) {
                    Platform.runLater(() -> {
                        if (listener != null) {
                            listener.onLoadingChanged(false);
                            listener.onPlayerError(response.getMessage());
                        }
                        return;
                    });
                }
                SongData detail = response.dataAs(SongData.class);
                Platform.runLater(() -> {
                    startMedia(detail);
                });
            });
        } catch (Exception e) {
            // TODO: handle exception
            stopLoadingWithError(e.getMessage());
        }
    }

    private void startMedia(SongData song) {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }
        currentSong = song;
        playbackSeconds.set(-1);
        String audioUrl = song.audio_url();
        if (audioUrl == null || audioUrl.isBlank()) {
            audioUrl = song.preview_url();
        }
        if (audioUrl == null || audioUrl.isBlank()) {
            if (listener != null) {
                listener.onPlayerError("Song does not have audio URL");
            }
            return;
        }
        try {
            Media media = new Media(audioUrl);
            mediaPlayer = new MediaPlayer(media);
            MediaPlayer activePlayer = mediaPlayer;
            applyVolume(); // bài mới dùng lại âm lượng hiện tại
            mediaPlayer.currentTimeProperty().addListener((observable, oldTime, newTime) -> {

                if (activePlayer != mediaPlayer)
                    return;
                playbackSeconds.set(newTime.toSeconds());
                if (listener == null) {
                    return;
                }
                double currentSeconds = newTime.toSeconds();
                double totalSeconds = mediaPlayer.getTotalDuration().toSeconds();
                if (!Double.isNaN(totalSeconds) && !Double.isInfinite(totalSeconds)
                        && totalSeconds > 0) {
                    listener.onProgressChanged(
                            currentSeconds,
                            totalSeconds);
                }
            });
            mediaPlayer.setOnReady(() -> {
                notifyAudioStart();
                mediaPlayer.play();
                isPlaying = true;
                if (listener != null) {
                    listener.onSongChanged(currentSong);
                    listener.onPlayingChanged(true);
                    // Ẩn loading
                    listener.onLoadingChanged(false);
                }
            });
            mediaPlayer.setOnEndOfMedia(this::nextSong);
            mediaPlayer.setOnError(() -> {
                if (listener != null) {
                    listener.onPlayerError("Cannot play this song.");
                }

            });
        } catch (Exception e) {
            // TODO: handle exception
            stopLoadingWithError(e.getMessage());
        }
    }

    // Tạm dừng nhạc (vd: khi bắt đầu xem video) — không làm gì nếu đang không phát
    public void pause() {
        if (mediaPlayer == null || !isPlaying) {
            return;
        }
        mediaPlayer.pause();
        isPlaying = false;
        if (listener != null) {
            listener.onPlayingChanged(false);
        }
    }

    public void togglePlayPause() {

        if (mediaPlayer == null) {
            return;
        }

        if (isPlaying) {

            mediaPlayer.pause();

            isPlaying = false;

        } else {

            notifyAudioStart();
            mediaPlayer.play();

            isPlaying = true;
        }

        if (listener != null) {
            listener.onPlayingChanged(isPlaying);
        }
    }

    public void nextSong() {

        if (currentSongList.isEmpty()) {
            return;
        }

        currentSongIndex++;

        if (currentSongIndex >= currentSongList.size()) {
            currentSongIndex = 0;
        }

        SongData nextSong = currentSongList.get(currentSongIndex);

        loadSongDetail(nextSong);
    }

    public void previousSong() {

        if (currentSongList.isEmpty()) {
            return;
        }

        currentSongIndex--;

        if (currentSongIndex < 0) {
            currentSongIndex = currentSongList.size() - 1;
        }

        SongData previousSong = currentSongList.get(currentSongIndex);

        loadSongDetail(previousSong);
    }

    public void seek(double percent) {

        if (mediaPlayer == null) {
            return;
        }

        double totalSeconds = mediaPlayer
                .getTotalDuration()
                .toSeconds();

        if (Double.isNaN(totalSeconds)
                || Double.isInfinite(totalSeconds)
                || totalSeconds <= 0) {
            return;
        }

        double seekSeconds = totalSeconds
                * percent
                / 100.0;

        playbackSeconds.set(seekSeconds);
        mediaPlayer.seek(
                javafx.util.Duration.seconds(
                        seekSeconds));
    }

    private void stopLoadingWithError(String message) {

        if (listener != null) {

            listener.onLoadingChanged(false);

            listener.onPlayerError(message);
        }
    }
}
