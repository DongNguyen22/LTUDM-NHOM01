package com.musicfinder.server.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;

import com.musicfinder.server.util.DataException;
import com.musicfinder.server.model.SearchCache;
import com.musicfinder.server.repository.SearchCacheRepository;
import com.musicfinder.server.util.TextNormalizer;
import com.musicfinder.shared.response.payload.SearchArtistData;
import com.musicfinder.shared.response.payload.SearchSongData;
import com.musicfinder.shared.util.Json;

public class CacheService {
    private final SearchCacheRepository cacheRepo;
    private final Map<String, String> values = new HashMap<>();
    private final Map<String, Long> expires = new HashMap<>();

    public CacheService() {
        this(new SearchCacheRepository());
    }

    public CacheService(SearchCacheRepository cacheRepo) {
        this.cacheRepo = cacheRepo;
    }

    public String createKey(String s, String type) {
        return "server1:youtube:db:" + type + ":" + TextNormalizer.normalize(s);
    }

    private synchronized String getJson(String s, String type) {
        if (cacheRepo == null) {
            String key = createKey(s, type);
            Long end = expires.get(key);
            if (end == null)
                return null;
            if (end <= System.currentTimeMillis()) {
                remove(s, type);
                return null;
            }
            return values.get(key);
        }
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

    public <T> T getValue(String s, String type, Class<T> cls) {
        String json = getJson(s, type);
        if (json == null)
            return null;
        try {
            return Json.fromJson(json, cls);
        } catch (IllegalArgumentException e) {
            remove(s, type);
            return null;
        }
    }

    public void putValue(String s, String type, Object data, int seconds) {
        save(s, type, data, seconds);
    }

    public synchronized void remove(String s, String type) {
        values.remove(createKey(s, type));
        expires.remove(createKey(s, type));
    }

    private synchronized void save(String s, String type, Object data, int seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("Thời gian cache phải lớn hơn 0");
        }
        if (cacheRepo == null) {
            String key = createKey(s, type);
            if (values.size() >= 512 && !values.containsKey(key)) {
                values.clear();
                expires.clear();
            }
            values.put(key, Json.toJson(data));
            expires.put(key, System.currentTimeMillis() + seconds * 1000L);
            return;
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