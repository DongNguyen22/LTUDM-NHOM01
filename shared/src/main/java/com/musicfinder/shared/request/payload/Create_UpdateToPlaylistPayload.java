package com.musicfinder.shared.request.payload;

public record Create_UpdateToPlaylistPayload(Long playlistId, String name, String description, String thumbnailUrl) {
    // Dùng khi tạo/sửa có thumbnail
    public Create_UpdateToPlaylistPayload(String name, String description, String thumbnailUrl) {
        this(null, name, description, thumbnailUrl);
    }

    // Dùng khi sửa không truyền thumbnail
    public Create_UpdateToPlaylistPayload(Long playlistId, String name, String description) {
        this(playlistId, name, description, null);
    }

    // Dùng khi tạo mới cơ bản
    public Create_UpdateToPlaylistPayload(String name, String description) {
        this(null, name, description, null);
    }
}