package com.musicfinder.server.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.musicfinder.server.model.Artists;
import com.musicfinder.server.api.YtDlpApi;
import com.musicfinder.server.api.ZingMp3Api;
import com.musicfinder.server.util.FuzzyMatcher;
import com.musicfinder.server.util.TextNormalizer;
import com.musicfinder.shared.response.payload.ArtistData;
import com.musicfinder.shared.response.payload.SearchArtistData;
import com.musicfinder.server.model.Songs;
import com.musicfinder.server.repository.ArtistRepository;
import com.musicfinder.server.repository.SongRepository;
import com.musicfinder.server.util.DtoMapper;
import com.musicfinder.shared.response.payload.SongData;
import com.musicfinder.shared.response.payload.ViewArtistDetailData;
import com.musicfinder.shared.response.payload.ViewArtistSongsData;

// Xem thông tin ca sĩ và các bài hát của ca sĩ (đọc từ DB)
// Optional.empty() = không có ca sĩ này -> Dispatcher trả NOT_FOUND
public class ArtistService {
    private final ArtistRepository artistRepo = new ArtistRepository();
    private final SongRepository songRepo = new SongRepository();

    // Metadata mới dùng ID và dữ liệu từ repository DB.
    private CacheService cache;
    private YtDlpApi api;
    private SearchService searchService;
    private final ZingMp3Api zingApi = new ZingMp3Api();
    private final StatisticsService statisticsService = new StatisticsService();
    private final HistoryService historyService = new HistoryService();
    private final Map<Long, Object> artistSongLocks = new ConcurrentHashMap<>();

    public ArtistService() {
        cache = new CacheService();
        api = new YtDlpApi();
        searchService = new SearchService(api, cache);
    }

    public ArtistService(CacheService cache, YtDlpApi api, SearchService searchService) {
        this.cache = cache;
        this.api = api;
        this.searchService = searchService;
    }

    public SearchArtistData searchArtist(String keyword, Long userId) {
        if (keyword == null || keyword.isBlank() || keyword.trim().length() > 255)
            throw new IllegalArgumentException("Từ khóa không hợp lệ");
        keyword = keyword.trim();

        statisticsService.recordSearch(keyword, "ARTIST");
        SearchArtistData cached = cache.getArtists(keyword);
        if (cached != null) {
            postSearchArtist(keyword, userId, cached.artists());
            return cached;
        }

        List<ArtistData> result = new ArrayList<>();

        // 1. Tìm trong DB
        for (Artists artist : artistRepo.searchByName(keyword, 10)) {
            if ((artist.getBio() == null || artist.getBio().isBlank()) && artist.getExternalId() != null) {
                if (artist.getExternalId().startsWith("zing:artist:")) {
                    String alias = artist.getExternalId().substring("zing:artist:".length());
                    ZingMp3Api.ZingArtistDetail detail = zingApi.getArtistDetail(alias);
                    if (detail != null) {
                        artist.setBio(detail.bio());
                        artist.setImageUrl(detail.imageUrl());
                        artist.setBirthDate(detail.birthDate());
                        artist.setHomeTown(detail.hometown());
                        artistRepo.update(artist);
                    }
                }
            }
            result.add(DtoMapper.toArtistData(artist));
        }

        // 2. Nếu DB chưa có, gọi Zing MP3
        if (result.isEmpty()) {
            List<ZingMp3Api.ZingArtist> remoteArtists = zingApi.searchArtists(keyword);
            for (ZingMp3Api.ZingArtist remote : remoteArtists) {
                String externalId = "zing:artist:" + remote.alias();
                Artists artist = artistRepo.findByExternalId(externalId).orElseGet(() -> {
                    // Lấy thêm chi tiết tiểu sử và ngày sinh
                    ZingMp3Api.ZingArtistDetail detail = zingApi.getArtistDetail(remote.alias());
                    Artists newArtist = Artists.builder()
                            .externalId(externalId)
                            .artistName(detail != null ? detail.name() : remote.name())
                            .normalizedName(TextNormalizer.normalize(remote.name()))
                            .birthDate(detail != null ? detail.birthDate() : null)
                            .homeTown(detail != null ? detail.hometown() : null)
                            .bio(detail != null ? detail.bio() : null)
                            .imageUrl(detail != null && detail.imageUrl() != null ? detail.imageUrl()
                                    : remote.thumbnail())
                            .searchCount(1L)
                            .build();
                    return artistRepo.save(newArtist);
                });
                result.add(DtoMapper.toArtistData(artist));
                if (result.size() >= 5)
                    break; // Lấy tối đa 5 ca sĩ từ API
            }
        }

        // 3. Gợi ý mờ nếu vẫn không thấy
        List<ArtistData> suggestions = new ArrayList<>();
        if (result.isEmpty()) {
            FuzzyMatcher fuzzy = new FuzzyMatcher();
            List<Artists> known = artistRepo.searchByName("", 200);
            for (Artists artist : fuzzy.suggestArtists(keyword, known))
                suggestions.add(DtoMapper.toArtistData(artist));
        }

        SearchArtistData response = new SearchArtistData(result, suggestions);
        cache.saveArtists(keyword, response, 900);
        postSearchArtist(keyword, userId, result);
        return response;
    }

    // VIEW_ARTIST_DETAIL
    public Optional<ViewArtistDetailData> viewArtistDetail(Long artistId) {
        checkId(artistId);
        Optional<Artists> artist = artistRepo.findById(artistId);
        if (artist.isEmpty()) {
            return Optional.empty(); // không có ca sĩ này
        }

        ViewArtistDetailData data = new ViewArtistDetailData(DtoMapper.toArtistData(artist.get()));
        return Optional.of(data);
    }

    // VIEW_ARTIST_SONGS
    public Optional<ViewArtistSongsData> viewArtistSongs(Long artistId) {
        checkId(artistId);
        Optional<Artists> artist = artistRepo.findById(artistId);
        if (artist.isEmpty()) {
            return Optional.empty();
        }

        String source = artist.get().getExternalId();
        if (source == null) {
            return Optional.of(new ViewArtistSongsData(songsInDb(artistId)));
        }

        // 1. Trường hợp ca sĩ từ Zing MP3 -> Lấy 40 bài hát nổi bật từ Zing API
        if (source.startsWith("zing:artist:")) {
            ViewArtistSongsData cached = cache.getValue(String.valueOf(artistId), "ARTIST_SONGS",
                    ViewArtistSongsData.class);
            if (cached != null && cached.songs() != null && !cached.songs().isEmpty())
                return Optional.of(cached);

            synchronized (artistSongLocks.computeIfAbsent(artistId, k -> new Object())) {
                cached = cache.getValue(String.valueOf(artistId), "ARTIST_SONGS", ViewArtistSongsData.class);
                if (cached != null && cached.songs() != null && !cached.songs().isEmpty())
                    return Optional.of(cached);

                String alias = source.substring("zing:artist:".length());
                ZingMp3Api.ZingArtistDetail detail = zingApi.getArtistDetail(alias);
                List<YtDlpApi.RemoteSong> songs = (detail != null && detail.songs() != null)
                        ? detail.songs()
                        : java.util.Collections.emptyList();

                ViewArtistSongsData response = new ViewArtistSongsData(mergeSongs(artistId, songs));
                cache.putValue(String.valueOf(artistId), "ARTIST_SONGS", response, 900);
                return Optional.of(response);
            }
        }

        // 2. Trường hợp kênh YouTube
        if (source.startsWith("youtube:channel:")) {
            ViewArtistSongsData cached = cache.getValue(String.valueOf(artistId), "ARTIST_SONGS",
                    ViewArtistSongsData.class);
            if (cached != null)
                return Optional.of(cached);

            synchronized (artistSongLocks.computeIfAbsent(artistId, k -> new Object())) {
                cached = cache.getValue(String.valueOf(artistId), "ARTIST_SONGS", ViewArtistSongsData.class);
                if (cached != null)
                    return Optional.of(cached);

                try {
                    List<YtDlpApi.RemoteSong> videos = api.songsForArtist(source);
                    if (videos == null)
                        throw new IllegalStateException("Không lấy được video của kênh");
                    ViewArtistSongsData response = new ViewArtistSongsData(mergeSongs(artistId, videos));
                    cache.putValue(String.valueOf(artistId), "ARTIST_SONGS", response, 900);
                    return Optional.of(response);
                } catch (RuntimeException e) {
                    ViewArtistSongsData old = cache.getValueEvenIfExpired(String.valueOf(artistId),
                            "ARTIST_SONGS", ViewArtistSongsData.class);
                    if (old != null)
                        return Optional.of(old);
                    throw e;
                }
            }
        }

        // Mặc định trả về các bài đã có trong DB
        return Optional.of(new ViewArtistSongsData(songsInDb(artistId)));
    }

    private List<SongData> songsInDb(Long artistId) {
        List<SongData> result = new ArrayList<>();
        for (Songs song : songRepo.findByArtistId(artistId)) {
            result.add(DtoMapper.toSongData(song, getArtistNames(song.getSongId())));
        }
        return result;
    }

    private static final int MAX_ARTIST_SONGS = 5;

    private List<SongData> mergeSongs(Long artistId, List<YtDlpApi.RemoteSong> videos) {
        List<SongData> result = songsInDb(artistId);
        if (result.size() > MAX_ARTIST_SONGS)
            result = new ArrayList<>(result.subList(0, MAX_ARTIST_SONGS));

        Set<String> seen = new HashSet<>();
        for (SongData song : result) {
            if (song.externalId() != null)
                seen.add(song.externalId());
        }

        for (YtDlpApi.RemoteSong video : videos) {
            if (result.size() >= MAX_ARTIST_SONGS)
                break;
            if (seen.add(video.externalId()))
                result.add(searchService.toResultSong(video));
        }
        return result;
    }

    // id null hoặc <= 0 là không hợp lệ -> Dispatcher đổi thành INVALID_REQUEST
    private static void checkId(Long artistId) {
        if (artistId == null || artistId <= 0) {
            throw new IllegalArgumentException("ID ca sĩ không hợp lệ");
        }
    }

    // Lấy tên các ca sĩ của 1 bài hát (JOIN SONG_ARTISTS)
    private List<String> getArtistNames(Long songId) {
        List<String> names = new ArrayList<>();
        for (Artists artist : artistRepo.findAllArtistByASongId(songId)) {
            names.add(artist.getArtistName());
        }
        return names;
    }

    private void postSearchArtist(String keyword, Long userId, List<ArtistData> artists) {
        // Tăng search_count cho các ca sĩ tìm thấy
        if (artists != null) {
            for (ArtistData artist : artists) {
                if (artist.artistID() != null) {
                    artistRepo.updateSearchCount(artist.artistID());
                }
            }
        }
        // Ghi SearchHistory nếu user đã đăng nhập
        if (userId != null && userId > 0) {
            int count = (artists != null) ? artists.size() : 0;
            historyService.recordSearch(userId, keyword, "ARTIST", count);
        }
    }
}
