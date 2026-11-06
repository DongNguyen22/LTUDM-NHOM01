package com.musicfinder.player;

import com.musicfinder.shared.response.payload.SongData;

public interface PlayerListener {
    void onSongChanged(SongData song);

    void onPlayingChanged(boolean playing);

    void onPlayerError(String message);

    void onProgressChanged(double currentSeconds, double totalSeconds);

    void onLoadingChanged(boolean loading);
}
