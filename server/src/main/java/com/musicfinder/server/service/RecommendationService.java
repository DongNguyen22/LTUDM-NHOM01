package com.musicfinder.server.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.musicfinder.server.model.Artists;
import com.musicfinder.server.model.SearchHistory;
import com.musicfinder.server.model.Songs;
import com.musicfinder.server.repository.ArtistRepository;
import com.musicfinder.server.repository.SearchHistoryRepository;
import com.musicfinder.server.repository.SongRepository;
import com.musicfinder.server.util.DtoMapper;
import com.musicfinder.server.util.TextNormalizer;
import com.musicfinder.shared.response.payload.SearchSongData;
import com.musicfinder.shared.response.payload.SongData;

public class RecommendationService {
    private static final int LIMIT = 4;
    private static final int PER_ARTIST = 2;

    private final SearchHistoryRepository historyRepo = new SearchHistoryRepository();
    private final ArtistRepository artistRepo = new ArtistRepository();
    private final SongRepository songRepo = new SongRepository();
    private final StatisticsService statisticsService = new StatisticsService();

    public SearchSongData recommend(Long userId) {
        List<Songs> picked = new ArrayList<>();
        Set<Long> seenSongs = new HashSet<>();
        Set<Long> seenArtists = new HashSet<>();

        if (userId != null && userId > 0) {
            for (SearchHistory h : historyRepo.findByUserId(userId, 10)) {

                if (TextNormalizer.normalize(h.getKeyword()).isEmpty())
                    continue;

                for (Artists a : relatedArtists(h.getKeyword())) {
                    if (picked.size() >= LIMIT)
                        break;
                    if (!seenArtists.add(a.getArtistId()))
                        continue;

                    int taken = 0;
                    for (Songs s : songRepo.findByArtistId(a.getArtistId())) {
                        if (taken >= PER_ARTIST || picked.size() >= LIMIT)
                            break;
                        if (seenSongs.add(s.getSongId())) {
                            picked.add(s);
                            taken++;
                        }
                    }
                }
            }
        }

        List<SongData> result = new ArrayList<>(DtoMapper.toSongDataList(picked, artistRepo));

        if (result.size() < LIMIT) {
            for (SongData s : statisticsService.getTopSongs(LIMIT * 2).songs()) {
                if (result.size() >= LIMIT)
                    break;
                if (s.songID() != null && seenSongs.add(s.songID())) {
                    result.add(s);
                }
            }
        }
        return new SearchSongData(result);
    }

    private List<Artists> relatedArtists(String keyword) {
        List<Artists> artists = new ArrayList<>(artistRepo.searchByName(keyword, 3));
        for (Songs s : songRepo.searchSongs(keyword, 3)) {
            artists.addAll(artistRepo.findAllArtistByASongId(s.getSongId()));
        }
        return artists;
    }
}