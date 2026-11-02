package com.musicfinder.server.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.musicfinder.server.api.YtDlpApi;
import com.musicfinder.server.model.Artists;
import com.musicfinder.server.model.Songs;
import com.musicfinder.server.network.AudioStreamServer;
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

        // Tìm trong CSDL trước, không ghi vào SEARCH_CACHE
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
        postSearchSong(keyword, userId, result);
        return response;
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
        if (id == null || id <= 0)
            throw new IllegalArgumentException("ID bài hát không hợp lệ");

      
            //  KIỂM TRA TRONG CACHE TRƯỚC 
            SongData cached = cache.getValue(String.valueOf(id), "SONG_DETAIL", SongData.class);
            if (cached != null && cached.audio_url() != null && !cached.audio_url().isBlank()) {
                // Kiểm tra nhanh xem file .mp3 trên ổ cứng còn không
                String videoId = cached.video_url() != null
                        && cached.video_url().startsWith("https://www.youtube.com/watch?v=")
                                ? cached.video_url().replace("https://www.youtube.com/watch?v=", "")
                                : null;
                File audioDir = new File(System.getProperty("music.audio.directory", "storage/audio"));
                if (videoId != null && new File(audioDir, videoId + ".mp3").isFile()) {
                    return cached;
                }
            }

            //  NẾU CACHE MISS: Lấy thông tin bài hát từ DB
            SongData song = getSong(id);
            if (song.video_url() == null || !song.video_url().startsWith("https://www.youtube.com/watch?v="))
                throw new IllegalArgumentException("Bài hát chưa có nguồn YouTube");
            String videoId = song.video_url().replace("https://www.youtube.com/watch?v=", "");

            //  Tải/chuyển đổi file MP3 vào storage/audio/
            if (apiService.downloadAudioToCache(videoId) == null)
                throw new IllegalStateException("Không chuẩn bị được MP3. Xem lỗi yt-dlp/FFmpeg tại server.");

            //  Sinh audio_url và cập nhật vào Database
            String audioUrl = AudioStreamServer.getAudioUrl(videoId);
            if (!songRepo.updateAudioUrl(id, audioUrl))
                throw new IllegalStateException("Không cập nhật được link audio vào DB");

            SongData result = new SongData(song.songID(), song.title(), song.composerList(), song.artistNames(),
                    song.durations_seconds(), audioUrl, song.video_url(), song.thumbnail_url(),
                    song.searchCount(), song.preview_url());

            //  LƯU BÀI HÁT ĐÃ PHÁT VÀO CACHE 
            cache.putValue(String.valueOf(id), "SONG_DETAIL", result, 900);

            return result;
        
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
