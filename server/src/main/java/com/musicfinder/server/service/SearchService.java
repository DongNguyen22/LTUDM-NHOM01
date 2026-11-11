package com.musicfinder.server.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.musicfinder.server.api.YtDlpApi;
import com.musicfinder.server.api.ZingMp3Api;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SearchService {
    private final YtDlpApi apiService;
    private final ZingMp3Api zingApi = new ZingMp3Api();
    private final CacheService cache;
    private final SongRepository songRepo;
    private final ArtistRepository artistRepo;
    private final HistoryService historyService;
    private final StatisticsService statisticsService;

    private static final List<String> BAD_WORDS = List.of("karaoke", "reaction", "mix");
    private static final Pattern BAD_TITLE = Pattern.compile(
            "\\b(" + String.join("|", BAD_WORDS) + ")\\b", Pattern.CASE_INSENSITIVE);
    private static final int MIN_SECONDS = 60;
    private static final int MAX_SECONDS = 600;
    private static final int LIST_CACHE_SECONDS = 3600; // 1 giờ

    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public SearchService() {
        this(new YtDlpApi());
    }

    public SearchService(YtDlpApi apiService) {
        this(apiService, new CacheService());
    }

    public SearchService(YtDlpApi apiService, CacheService cache) {
        this(apiService, cache, new SongRepository(), new ArtistRepository(), new HistoryService(),
                new StatisticsService());
    }

    public SearchService(YtDlpApi apiService, CacheService cache, SongRepository songRepo,
            ArtistRepository artistRepo, HistoryService historyService, StatisticsService statisticsService) {
        this.apiService = apiService;
        this.cache = cache;
        this.songRepo = songRepo;
        this.artistRepo = artistRepo;
        this.historyService = historyService;
        this.statisticsService = statisticsService;
    }

    public SearchSongData searchSongs(String keyword, Long userId) {
        if (keyword == null || keyword.isBlank() || keyword.trim().length() > 255)
            throw new IllegalArgumentException("Từ khóa không hợp lệ");
        keyword = keyword.trim();
        if (TextNormalizer.normalize(keyword).isEmpty())
            throw new IllegalArgumentException("Từ khóa không hợp lệ");

        SearchSongData data = cache.getSongs(keyword);
        if (data == null)
            data = fetchWithLock(keyword);

        if (userId != null && userId > 0)
            historyService.recordSearch(userId, keyword, "SONG", data.songs().size());
        // Ghi nhận thống kê từ khóa loại bài hát
        statisticsService.recordSearch(keyword, "SONG");
        return data;
    }

    private SearchSongData fetchWithLock(String keyword) {
        Object lock = locks.computeIfAbsent(TextNormalizer.normalize(keyword), k -> new Object());
        synchronized (lock) {
            SearchSongData cached = cache.getSongs(keyword);
            if (cached != null)
                return cached;

            try {
                List<YtDlpApi.RemoteSong> found = zingApi.searchSongs(keyword);
                if (found.isEmpty()) {
                    System.out.println("Zing MP3 không có kết quả, chuyển sang yt-dlp: " + keyword);
                    found = apiService.searchSongs(keyword);
                }
                SearchSongData data = buildResult(keyword, found);
                cache.saveSongs(keyword, data, LIST_CACHE_SECONDS);
                return data;
            } catch (RuntimeException e) {
                SearchSongData old = cache.getSongsEvenIfExpired(keyword);
                if (old != null)
                    return old;
                throw e;
            }
        }
    }

    private SearchSongData buildResult(String keyword, List<YtDlpApi.RemoteSong> found) {
        if (found == null)
            throw new IllegalStateException("Không lấy được metadata từ yt-dlp");

        List<SongData> songs = new ArrayList<>();
        for (YtDlpApi.RemoteSong remote : found) {
            if (!isRealSong(remote))
                continue;
            songs.add(toResultSong(remote));
        }

        List<SongData> suggestions = new ArrayList<>();
        if (songs.isEmpty()) {
            FuzzyMatcher fuzzy = new FuzzyMatcher();
            List<Songs> known = songRepo.searchSongs("", 200);
            suggestions = toSongData(fuzzy.suggestSongs(keyword, known));
        }
        return new SearchSongData(songs, suggestions);
    }

    private boolean isRealSong(YtDlpApi.RemoteSong remote) {
        int seconds = remote.durationSeconds();
        if (seconds > 0 && (seconds < MIN_SECONDS || seconds > MAX_SECONDS))
            return false;
        return !BAD_TITLE.matcher(remote.title()).find();
    }

    private SongData remoteToSongData(YtDlpApi.RemoteSong remote) {
        return new SongData(null, remote.title(), null, List.of(remote.artist().name()),
                remote.durationSeconds(), null, remote.storeUrl(), remote.thumbnailUrl(), 0L,
                remote.previewUrl(), remote.externalId());
    }

    public SongData toResultSong(YtDlpApi.RemoteSong remote) {
        cache.putValue(remote.externalId(), "SONG_META", remote, LIST_CACHE_SECONDS);
        return remoteToSongData(remote);
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

    public Long ensureSong(String externalId) {
        if (externalId == null || !externalId.matches("youtube:video:[A-Za-z0-9_-]{11}|zing:song:[A-Za-z0-9]{6,20}"))
            throw new IllegalArgumentException("Mã bài hát không hợp lệ");

        Optional<Songs> found = songRepo.findByExternalId(externalId);
        if (found.isPresent())
            return found.get().getSongId();

        YtDlpApi.RemoteSong meta = cache.getValue(externalId, "SONG_META", YtDlpApi.RemoteSong.class);
        if (meta == null || !externalId.equals(meta.externalId()))
            throw new IllegalArgumentException("Kết quả đã hết hạn, vui lòng tìm lại");

        return importSong(meta).songID(); // importSong đã synchronized và kiểm tra trùng lại
    }

    public SongData getSong(Long id) {
        if (id == null || id <= 0)
            throw new IllegalArgumentException("ID bài hát không hợp lệ");
        Optional<Songs> found = songRepo.findByIdSong(id);
        if (found.isEmpty())
            throw new IllegalArgumentException("Không tìm thấy bài hát");
        return toSongData(found.get());
    }

    public SongData detail(Long id, String externalId) {
        if (externalId != null && !externalId.isBlank())
            id = ensureSong(externalId);
        if (id == null || id <= 0)
            throw new IllegalArgumentException("ID bài hát không hợp lệ");

        songRepo.updateSearchCount(id);

        SongData cached = cache.getValue(String.valueOf(id), "SONG_DETAIL", SongData.class);
        if (cached != null && cached.audio_url() != null && !cached.audio_url().isBlank()) {
            String cachedVideoId = youtubeId(cached);
            File audioDir = new File(System.getProperty("music.audio.directory", "storage/audio"));
            if (cachedVideoId != null && new File(audioDir, cachedVideoId + ".mp3").isFile()) {
                return cached;
            }
        }

        SongData song = getSong(id);
        String videoId = resolveVideoId(song);
        if (videoId == null)
            throw new IllegalArgumentException("Bài hát chưa có nguồn YouTube");

        if (apiService.downloadAudioToCache(videoId) == null)
            throw new IllegalStateException("Không chuẩn bị được MP3. Xem lỗi yt-dlp/FFmpeg tại server.");

        String audioUrl = AudioStreamServer.getAudioUrl(videoId);
        if (!songRepo.updateAudioUrl(id, audioUrl))
            throw new IllegalStateException("Không cập nhật được link audio vào DB");

        String composer = song.composerList();
        if ((composer == null || composer.isBlank()) && song.externalId() != null
                && song.externalId().startsWith("zing:song:")) {
            String found = zingApi.getComposer(song.externalId().substring("zing:song:".length()));
            if (found != null) {
                try {
                    songRepo.updateComposer(id, found);
                    composer = found;
                } catch (RuntimeException e) {
                    System.out.println("Không lưu được nhạc sĩ: " + e.getMessage());
                }
            }
        }

        SongData result = new SongData(song.songID(), song.title(), composer, song.artistNames(),
                song.durations_seconds(), audioUrl, song.video_url(), song.thumbnail_url(),
                song.searchCount(), song.preview_url(), song.externalId());

        // LƯU BÀI HÁT ĐÃ PHÁT VÀO CACHE
        cache.putValue(String.valueOf(id), "SONG_DETAIL", result, 900);

        return result;

    }

    public static String youtubeId(SongData song) {
        if (song == null)
            return null;
        String ext = song.externalId();
        if (ext != null && ext.matches("youtube:video:[A-Za-z0-9_-]{11}"))
            return ext.substring("youtube:video:".length());
        String url = song.video_url();
        String prefix = "https://www.youtube.com/watch?v=";
        if (url != null && url.startsWith(prefix) && url.substring(prefix.length()).matches("[A-Za-z0-9_-]{11}"))
            return url.substring(prefix.length());
        String fromVideo = idFromServerUrl(url);
        return fromVideo != null ? fromVideo : idFromServerUrl(song.audio_url());
    }

    private static final Pattern SERVER_FILE_URL = Pattern
            .compile("/(?:audio|video)/([A-Za-z0-9_-]{11})\\.(?:mp3|mp4)$");

    private static String idFromServerUrl(String url) {
        if (url == null)
            return null;
        java.util.regex.Matcher m = SERVER_FILE_URL.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    public String resolveVideoId(SongData song) {
        String videoId = youtubeId(song);
        if (videoId == null && song.externalId() != null && song.externalId().startsWith("zing:song:")) {
            String artist = song.artistNames() == null || song.artistNames().isEmpty() ? "" : song.artistNames().get(0);
            int seconds = song.durations_seconds() == null ? 0 : song.durations_seconds();
            videoId = apiService.findMatchingVideoId(song.title(), artist, seconds);
            if (videoId == null)
                throw new IllegalStateException("Không tìm được nguồn phát cho bài hát này");
            System.out.println("Audio Bridge: " + song.title() + " -> YouTube " + videoId);
        }
        return videoId;
    }

    public void saveVideoUrl(Long songId, String videoId, String videoUrl) {
        Long id = songId;
        if (id == null || id <= 0) {
            Optional<Songs> found = songRepo.findByExternalId("youtube:video:" + videoId);
            if (found.isEmpty())
                return;
            id = found.get().getSongId();
        }
        if (!songRepo.updateVideoUrl(id, videoUrl))
            return;
        String key = String.valueOf(id);
        SongData cached = cache.getValue(key, "SONG_DETAIL", SongData.class);
        if (cached != null) {
            cache.putValue(key, "SONG_DETAIL", new SongData(cached.songID(), cached.title(), cached.composerList(),
                    cached.artistNames(), cached.durations_seconds(), cached.audio_url(), videoUrl,
                    cached.thumbnail_url(), cached.searchCount(), cached.preview_url(), cached.externalId()), 900);
        }
    }

    // =====================UTIL========================
    // Trong SearchService.java
    public void prepareSongAudio(Long songId) {
        if (songId == null || songId <= 0)
            return;

        // Chạy ngầm trong background thread, không block luồng thêm playlist của người
        // dùng
        CompletableFuture.runAsync(() -> {
            try {
                // Tận dụng luôn hàm detail() đã có sẵn logic kiểm tra file, tải MP3 và lưu
                // cache
                detail(songId, null);
                log.info("Đã chuẩn bị xong MP3 cho bài hát ID: {}", songId);
            } catch (Exception e) {
                // Nếu lỗi mạng, yt-dlp lỗi hay video bản quyền: chỉ log cảnh báo, không làm
                // crash app
                log.warn("Không thể tải trước audio cho bài hát [{}]: {}", songId, e.getMessage());
            }
        });
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
}