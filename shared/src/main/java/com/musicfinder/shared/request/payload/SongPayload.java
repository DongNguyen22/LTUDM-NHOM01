package com.musicfinder.shared.request.payload;

public record SongPayload(Long songID, String externalId) {
    public SongPayload(Long songID) {
        this(songID, null);
    }
}
