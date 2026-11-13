package com.musicfinder.server.service;

import com.musicfinder.server.api.ZingMp3Api;
import com.musicfinder.shared.response.payload.LyricData;
import com.musicfinder.shared.response.payload.SongData;

public class LyricService {
    private static final String CACHE_TYPE = "LYRIC";
    private static final String ZING_PREFIX = "zing:song:";
    private static final int HAS_LYRIC_SECONDS = 7 * 24 * 3600;
    private static final int NO_LYRIC_SECONDS = 24 * 3600;
    private final SearchService searchService;
    private final CacheService cache;
    private final ZingMp3Api zingApi;

    public LyricService(SearchService searchService, CacheService cache) {
        this(searchService, cache, new ZingMp3Api());
    }

    public LyricService(SearchService searchService, CacheService cache, ZingMp3Api zingApi) {
        this.searchService = searchService;
        this.cache = cache;
        this.zingApi = zingApi;
    }

    public LyricData getLyric(Long songId, String externalId) {
        String ext = externalId;
        if ((ext == null || ext.isBlank()) && songId != null) {
            SongData song = searchService.getSong(songId);
        }
        if (ext == null || ext.isBlank()) {
            throw new IllegalArgumentException("Thiếu mã bài hát");
        }

        if (!ext.startsWith(ZING_PREFIX)) {
            return new LyricData(false, null, null);
        }
        String encodeId = ext.substring(ZING_PREFIX.length());
        if (!encodeId.matches("[A-Za-z0-9]{6,20}")) {
            throw new IllegalArgumentException("Mã bài hát không hợp lệ");
        }

        LyricData cached = cache.getValue(encodeId, CACHE_TYPE, LyricData.class);
        if (cached != null) {
            return cached;
        }

        ZingMp3Api.ZingLyric zing = zingApi.getLyric(encodeId);
        if (zing == null) {
            LyricData old = cache.getValueEvenIfExpired(encodeId, CACHE_TYPE, LyricData.class);
            if (old != null) {
                return old;
            }
            throw new IllegalStateException("Không tải được lời bài hát, thử lại sau");
        }

        LyricData result = zing.text() == null
                ? new LyricData(false, null, null)
                : new LyricData(true, zing.text(), "zing");
        if (zing.reliable()) {
            cache.putValue(encodeId, CACHE_TYPE, result, result.hasLyric() ? HAS_LYRIC_SECONDS : NO_LYRIC_SECONDS);
        }
        return result;
    }
}
