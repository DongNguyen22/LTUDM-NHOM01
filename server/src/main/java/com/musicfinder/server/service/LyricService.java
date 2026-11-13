package com.musicfinder.server.service;

import java.util.regex.Pattern;

import com.musicfinder.server.api.ZingMp3Api;
import com.musicfinder.server.repository.SongRepository;
import com.musicfinder.shared.response.payload.LyricData;
import com.musicfinder.shared.response.payload.SongData;

public class LyricService {
    private static final String ZING_PREFIX = "zing:song:";
    private static final int MAX_LYRIC_CHARS = 64 * 1024;

    private static final Pattern TIMESTAMP = Pattern.compile("\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?]");

    private final SearchService searchService;
    private final CacheService cache;
    private final ZingMp3Api zingApi;
    private final SongRepository songRepo = new SongRepository();

    public LyricService(SearchService searchService, CacheService cache) {
        this(searchService, cache, new ZingMp3Api());
    }

    public LyricService(SearchService searchService, CacheService cache, ZingMp3Api zingApi) {
        this.searchService = searchService;
        this.cache = cache;
        this.zingApi = zingApi;
    }

    static boolean hasTimestamps(String text) {
        return text != null && TIMESTAMP.matcher(text).find();
    }

    public LyricData getLyric(Long songId, String externalId) {
        Long id = songId;
        if (id == null || id <= 0) {
            if (externalId == null || externalId.isBlank())
                throw new IllegalArgumentException("Thiếu mã bài hát");
            id = searchService.ensureSong(externalId.trim());
        }

        String lyric = songRepo.findLyric(id);

        if (lyric == null) {
            SongData song = searchService.getSong(id);
            String ext = song.externalId();

            if (ext == null || !ext.startsWith(ZING_PREFIX))
                return new LyricData(false, null, false);

            String encodeId = ext.substring(ZING_PREFIX.length());
            if (!encodeId.matches("[A-Za-z0-9]{6,20}"))
                throw new IllegalArgumentException("Mã bài hát không hợp lệ");

            ZingMp3Api.ZingLyric zing = zingApi.getLyric(encodeId);
            if (zing == null)
                throw new IllegalStateException("Không tải được lời bài hát, thử lại sau");

            lyric = zing.text();
            if (zing.reliable()) {
                String toStore = lyric == null ? "" : lyric;
                if (toStore.length() <= MAX_LYRIC_CHARS)
                    songRepo.updateLyric(id, toStore);
            }
        }

        if (lyric == null || lyric.isEmpty())
            return new LyricData(false, null, false);
        return new LyricData(true, lyric, hasTimestamps(lyric));
    }
}