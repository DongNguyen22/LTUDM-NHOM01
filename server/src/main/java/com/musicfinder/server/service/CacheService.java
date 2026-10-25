package com.musicfinder.server.service;

import java.time.LocalDateTime;
import java.util.Optional;

import com.musicfinder.server.exception.DataException;
import com.musicfinder.server.model.SearchCache;
import com.musicfinder.server.repository.SearchCacheRepository;
import com.musicfinder.server.util.TextNormalizer;
import com.musicfinder.shared.response.payload.SearchArtistData;
import com.musicfinder.shared.response.payload.SearchSongData;
import com.musicfinder.shared.util.Json;

public class CacheService {
    private final SearchCacheRepository cacheRepo;

    public CacheService() {
        this(new SearchCacheRepository());
    }

    CacheService(SearchCacheRepository cacheRepo) {
        this.cacheRepo = cacheRepo;
    }

    public String createKey(String s, String type) {
        return "server1:itunes:VN:" + type + ":" + TextNormalizer.normalize(s);
    }

    private String getJson(String s, String type) {
        try {
            Optional<SearchCache> found = cacheRepo.findByCacheKey(createKey(s, type));
            if (found.isEmpty()) {
                return null;
            }

            SearchCache cache = found.get();
            if (cache.getExpiresAt() == null
                    || !cache.getExpiresAt().isAfter(LocalDateTime.now())) {
                return null;
            }

            return cache.getResponseJson();
        } catch (DataException e) {
            System.out.println("Không đọc được cache: " + e.getMessage());
            return null;
        }
    }

    public SearchSongData getSongs(String s) {
        String json = getJson(s, "SONG");
        if (json == null) {
            return null;
        }

        try {
            SearchSongData data = Json.fromJson(json, SearchSongData.class);
            if (data == null || data.songs() == null) {
                return null;
            }
            for (int i = 0; i < data.songs().size(); i++) {
                if (data.songs().get(i) == null || data.songs().get(i).songID() == null) {
                    return null;
                }
            }
            return data;
        } catch (IllegalArgumentException e) {
            System.out.println("Cache bài hát bị lỗi");
            return null;
        }
    }

    public SearchArtistData getArtists(String s) {
        String json = getJson(s, "ARTIST");
        if (json == null) {
            return null;
        }

        try {
            SearchArtistData data = Json.fromJson(json, SearchArtistData.class);
            if (data == null || data.artists() == null) {
                return null;
            }
            for (int i = 0; i < data.artists().size(); i++) {
                if (data.artists().get(i) == null || data.artists().get(i).artistID() == null) {
                    return null;
                }
            }
            return data;
        } catch (IllegalArgumentException e) {
            System.out.println("Cache ca sĩ bị lỗi");
            return null;
        }
    }

    public void saveSongs(String s, SearchSongData data, int seconds) {
        save(s, "SONG", data, seconds);
    }

    public void saveArtists(String s, SearchArtistData data, int seconds) {
        save(s, "ARTIST", data, seconds);
    }

    private void save(String s, String type, Object data, int seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("Thời gian cache phải lớn hơn 0");
        }
        try {
            SearchCache cache = SearchCache.builder()
                    .cacheKey(createKey(s, type))
                    .keyword(s.trim())
                    .normalizedKeyword(TextNormalizer.normalize(s))
                    .searchType(type)
                    .responseJson(Json.toJson(data))
                    .expiresAt(LocalDateTime.now().plusSeconds(seconds))
                    .build();

            cacheRepo.save(cache);
        } catch (DataException | IllegalArgumentException e) {
            System.out.println("Không lưu được cache: " + e.getMessage());
        }
    }
}
