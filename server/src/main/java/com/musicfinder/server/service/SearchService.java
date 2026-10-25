package com.musicfinder.server.service;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import com.musicfinder.server.api.ITunesApi;
import com.musicfinder.shared.response.payload.SearchSongData;
import com.musicfinder.shared.response.payload.SongData;

public class SearchService {
    private final ITunesApi apiService;

    public SearchService() {
        this(new ITunesApi());
    }

    public SearchService(ITunesApi apiService) {
        this.apiService = apiService;
    }

    public SearchSongData searchSongs(String keyword, Long userId) {
        if (keyword == null || keyword.isBlank() || keyword.trim().length() > 255) {
            throw new IllegalArgumentException("Từ khóa không hợp lệ");
        }

        keyword = keyword.trim();

        List<ITunesApi.RemoteSong> songs = apiService.searchSongs(keyword);
        List<SongData> result = new ArrayList<>();

        for (int i = 0; i < songs.size(); i++) {
            ITunesApi.RemoteSong song = songs.get(i);

            List<String> names = new ArrayList<>();
            names.add(song.artist().name());

            SongData data = new SongData(
                    null,
                    song.title(),
                    null,
                    names,
                    song.durationSeconds(),
                    null,
                    null,
                    song.thumbnailUrl(),
                    0L,
                    song.previewUrl());
            result.add(data);
        }

        return new SearchSongData(result);
    }

    // Chạy main này để thử API trước khi nối giao diện.
    public static void main(String[] args) {
        SearchService service = new SearchService();

        try {
            SearchSongData data = service.searchSongs("Lạc Trôi", null);

            if (data.songs().isEmpty()) {
                System.out.println("Không tìm thấy bài hát");
                return;
            }

            for (int i = 0; i < data.songs().size(); i++) {
                SongData song = data.songs().get(i);
                System.out.println("Bài hát: " + song.title());
                System.out.println("Ca sĩ: " + song.artistNames());
                System.out.println("Thời lượng: " + song.durations_seconds() + " giây");
                System.out.println("Ảnh: " + song.thumbnail_url());
                System.out.println("Nghe thử: " + song.preview_url());
                System.out.println();
            }
        } catch (UncheckedIOException | IllegalArgumentException e) {
            System.out.println("Không tìm được bài hát: " + e.getMessage());
        }
    }
}
