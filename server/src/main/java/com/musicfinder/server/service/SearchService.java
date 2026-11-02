package com.musicfinder.server.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.musicfinder.server.api.YtDlpApi;
import com.musicfinder.server.model.Artists;
import com.musicfinder.server.model.Songs;
import com.musicfinder.server.repository.ArtistRepository;
import com.musicfinder.server.repository.SongRepository;
import com.musicfinder.server.util.DtoMapper;
import com.musicfinder.server.util.FuzzyMatcher;
import com.musicfinder.server.util.TextNormalizer;
import com.musicfinder.shared.response.payload.SearchSongData;
import com.musicfinder.shared.response.payload.SongData;

public class SearchService {
    private final YtDlpApi apiService;
    private final CacheService cache;
    private final SongRepository songRepo;
    private final ArtistRepository artistRepo;
    private final HistoryService historyService;

    public SearchService() {
        this(new YtDlpApi());
    }

    public SearchService(YtDlpApi apiService) {
        this(apiService, new CacheService());
    }

    public SearchService(YtDlpApi apiService, CacheService cache) {
        this(apiService, cache, new SongRepository(), new ArtistRepository(), new HistoryService());
    }

    public SearchService(YtDlpApi apiService, CacheService cache, SongRepository songRepo,
            ArtistRepository artistRepo, HistoryService historyService) {
        this.apiService = apiService;
        this.cache = cache;
        this.songRepo = songRepo;
        this.artistRepo = artistRepo;
        this.historyService = historyService;
    }

    public SearchSongData searchSongs(String keyword, Long userId) {
        if (keyword == null || keyword.isBlank() || keyword.trim().length() > 255)
            throw new IllegalArgumentException("Từ khóa không hợp lệ");
        keyword = keyword.trim();
        synchronized (cache) {
            SearchSongData cached = cache.getSongs(keyword);
            if (cached != null) {
                postSearchSong(keyword, userId, cached.songs());
                return cached;
            }
            List<SongData> result = toSongData(songRepo.searchSongs(keyword, 10));
            if (result.isEmpty()) {
                List<YtDlpApi.RemoteSong> songs = apiService.searchSongs(keyword);
                if (songs == null)
                    throw new IllegalStateException("Không lấy được metadata từ yt-dlp");
                for (YtDlpApi.RemoteSong song : songs)
                    result.add(importSong(song));
            }
            List<SongData> suggestions = new ArrayList<>();
            if (result.isEmpty()) {
                FuzzyMatcher fuzzy = new FuzzyMatcher();
                List<Songs> known = songRepo.searchSongs("", 200);
                suggestions = toSongData(fuzzy.suggestSongs(keyword, known));
            }
            SearchSongData response = new SearchSongData(result, suggestions);
            cache.saveSongs(keyword, response, 900);
            postSearchSong(keyword, userId, result);
            return response;
        }
    }

    public synchronized Artists importArtist(YtDlpApi.RemoteArtist remote) {
        Optional<Artists> found = artistRepo.findByExternalId(remote.externalId());
        if (found.isPresent())
            return found.get();
        Artists artist = new Artists();
        artist.setExternalId(remote.externalId());
        artist.setArtistName(remote.name());
        artist.setNormalizedName(TextNormalizer.normalize(remote.name()));
        artist.setSearchCount(0L);
        artist = artistRepo.save(artist);
        if (artist.getArtistId() == null)
            throw new IllegalStateException("DB chưa cấp ID ca sĩ/kênh");
        return artist;
    }

    public synchronized SongData importSong(YtDlpApi.RemoteSong remote) {
        Artists artist = importArtist(remote.artist());
        Optional<Songs> found = songRepo.findByExternalId(remote.externalId());
        Songs song;
        if (found.isPresent()) {
            song = found.get();
        } else {
            song = new Songs();
            song.setExternalId(remote.externalId());
            song.setTitle(remote.title());
            song.setNormalizedTitle(TextNormalizer.normalize(remote.title()));
            song.setDurationSec(remote.durationSeconds());
            song.setVideoUrl(remote.storeUrl());
            song.setThumbnailUrl(remote.thumbnailUrl());
            song.setSearchCount(0L);
            song = songRepo.save(song);
        }
        if (song.getSongId() == null)
            throw new IllegalStateException("DB chưa cấp ID bài hát");
        songRepo.linkArtist(song.getSongId(), artist.getArtistId());
        return toSongData(song);
    }

    public SongData getSong(Long id) {
        if (id == null || id <= 0)
            throw new IllegalArgumentException("ID bài hát không hợp lệ");
        Optional<Songs> found = songRepo.findByIdSong(id);
        if (found.isEmpty())
            throw new IllegalArgumentException("Không tìm thấy bài hát");
        return toSongData(found.get());
    }

    public SongData detail(Long id) {
        SongData song = getSong(id);
        if (song.video_url() == null || !song.video_url().startsWith("https://www.youtube.com/watch?v="))
            throw new IllegalArgumentException("Bài hát chưa có nguồn YouTube");
        String videoId = song.video_url().replace("https://www.youtube.com/watch?v=", "");
        SongData detail = apiService.getSongDetail(videoId);
        if (detail == null || detail.audio_url() == null || detail.audio_url().isBlank())
            throw new IllegalStateException("Không lấy được link audio trực tiếp từ yt-dlp");
        if (!songRepo.updateAudioUrl(id, detail.audio_url()))
            throw new IllegalStateException("Không cập nhật được link audio vào DB");
        return new SongData(song.songID(), detail.title(), detail.composerList(), detail.artistNames(),
                detail.durations_seconds(), detail.audio_url(), detail.video_url(), detail.thumbnail_url(),
                song.searchCount(), null);
    }

    private SongData toSongData(Songs song) {
        List<String> names = new ArrayList<>();
        for (Artists artist : artistRepo.findAllArtistByASongId(song.getSongId()))
            names.add(artist.getArtistName());
        return DtoMapper.toSongData(song, names);
    }

    private List<SongData> toSongData(List<Songs> songs) {
        List<SongData> result = new ArrayList<>();
        for (Songs song : songs)
            result.add(toSongData(song));
        return result;
    }

    private void postSearchSong(String keyword, Long userId, List<SongData> songs) {
        // Tăng search_count cho các bài hát tìm thấy
        if (songs != null) {
            for (SongData song : songs) {
                if (song.songID() != null) {
                    songRepo.updateSearchCount(song.songID());
                }
            }
        }
        // Ghi SearchHistory nếu user đã đăng nhập
        if (userId != null && userId > 0) {
            int count = (songs != null) ? songs.size() : 0;
            historyService.recordSearch(userId, keyword, "SONG", count);
        }
    }

}