package com.musicfinder.server.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.musicfinder.server.model.Artists;
import com.musicfinder.server.api.YtDlpApi;
import com.musicfinder.server.util.FuzzyMatcher;
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

    public SearchArtistData search(String keyword, Long userId) {
        if (keyword == null || keyword.isBlank() || keyword.trim().length() > 255)
            throw new IllegalArgumentException("Từ khóa không hợp lệ");
        keyword = keyword.trim();
        synchronized (cache) {
            SearchArtistData cached = cache.getArtists(keyword);
            if (cached != null)
                return cached;
            List<ArtistData> result = new ArrayList<>();
            for (Artists artist : artistRepo.searchByName(keyword, 10))
                result.add(DtoMapper.toArtistData(artist));
            if (result.isEmpty()) {
                List<YtDlpApi.RemoteArtist> found = api.searchArtists(keyword);
                if (found == null)
                    throw new IllegalStateException("Không lấy được metadata kênh từ yt-dlp");
                for (YtDlpApi.RemoteArtist remote : found) {
                    Artists artist = searchService.importArtist(remote);
                    result.add(DtoMapper.toArtistData(artist));
                }
            }
            List<ArtistData> suggestions = new ArrayList<>();
            if (result.isEmpty()) {
                FuzzyMatcher fuzzy = new FuzzyMatcher();
                List<Artists> known = artistRepo.searchByName("", 200);
                for (Artists artist : fuzzy.suggestArtists(keyword, known))
                    suggestions.add(DtoMapper.toArtistData(artist));
            }
            SearchArtistData response = new SearchArtistData(result, suggestions);
            cache.saveArtists(keyword, response, 900);
            return response;
        }
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
        // Phải kiểm tra ca sĩ có tồn tại không, vì "không có ca sĩ" khác với "ca sĩ
        // chưa có bài nào"
        Optional<Artists> artist = artistRepo.findById(artistId);
        if (artist.isEmpty()) {
            return Optional.empty();
        }

        String source = artist.get().getExternalId();
        if (source != null && source.startsWith("youtube:channel:")) {
            synchronized (cache) {
                ViewArtistSongsData cached = cache.getValue(String.valueOf(artistId), "ARTIST_SONGS",
                        ViewArtistSongsData.class);
                if (cached != null)
                    return Optional.of(cached);
                List<YtDlpApi.RemoteSong> videos = api.songsForArtist(source);
                if (videos == null)
                    throw new IllegalStateException("Không lấy được video của kênh");
                for (YtDlpApi.RemoteSong video : videos) {
                    SongData saved = searchService.importSong(video);
                    songRepo.linkArtist(saved.songID(), artistId);
                }
            }
        }

        List<Songs> songs = songRepo.findByArtistId(artistId);

        List<SongData> result = new ArrayList<>();
        for (Songs song : songs) {
            List<String> artistNames = getArtistNames(song.getSongId());
            result.add(DtoMapper.toSongData(song, artistNames));
        }
        ViewArtistSongsData response = new ViewArtistSongsData(result);
        if (source != null && source.startsWith("youtube:channel:"))
            cache.putValue(String.valueOf(artistId), "ARTIST_SONGS", response, 900);
        return Optional.of(response);
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
}
