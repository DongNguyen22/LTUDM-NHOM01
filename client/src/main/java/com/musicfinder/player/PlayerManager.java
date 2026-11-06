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

public class PlayerManager {
    private static PlayerManager instance;
    private MediaPlayer mediaPlayer;
    private final List<SongData> currentSongList = new ArrayList<>();
    private int currentSongIndex = -1;
    private SongData currentSong;
    private Boolean isPlaying = false;
    private PlayerListener listener;

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

        SongPayload payload = new SongPayload(song.songID());
        Request request = Request.of(Action.VIEW_SONG_DETAIL, payload);
        try {
            ClientConnection.getInstance().send(request, response -> {
                if (!response.isSuccess()) {
                    Platform.runLater(() -> {
                        if (listener != null) {
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
            mediaPlayer.currentTimeProperty().addListener((observable, oldTime, newTime) -> {

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

    public void togglePlayPause() {

        if (mediaPlayer == null) {
            return;
        }

        if (isPlaying) {

            mediaPlayer.pause();

            isPlaying = false;

        } else {

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
