package com.musicfinder.controller;

import com.musicfinder.shared.response.payload.SongData;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;

public class SongDetailController {
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
    private SongData song;

    @FXML
    private void initialize() {
        ToggleGroup tabs = new ToggleGroup();
        lyricsTab.setToggleGroup(tabs);
        videoTab.setToggleGroup(tabs);
        Rectangle clip = new Rectangle(210, 210);
        clip.setArcWidth(20);
        clip.setArcHeight(20);
        coverImage.setClip(clip);
        coverImage.setFitWidth(210);
        coverImage.setFitHeight(210);
        coverImage.setPreserveRatio(false);
    }

    public void init(SongData song) {
        this.song = java.util.Objects.requireNonNull(song, "song");
        titleLabel.setText(song.title());

        String artists = song.artistNames() == null
                ? "Unknown artist"
                : String.join(", ", song.artistNames());
        artistLabel.setText(artists);

        composerLabel.setText(song.composerList());

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
    }

}
