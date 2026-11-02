package com.musicfinder.server.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.text.similarity.LevenshteinDistance;

import com.musicfinder.server.model.Artists;
import com.musicfinder.server.model.Songs;

public class FuzzyMatcher {
    private static final double MIN_SCORE = 0.65;
    private static final int MAX_SUGGESTIONS = 5;
    private static final int MIN_KEYWORD_LENGTH = 3;

    private final LevenshteinDistance distance = new LevenshteinDistance();

    public double score(String s1, String s2) {
        String a = TextNormalizer.normalize(s1);
        String b = TextNormalizer.normalize(s2);

        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }

        int n = distance.apply(a, b);
        int maxLength = Math.max(a.length(), b.length());

        double difference = (double) n / maxLength;
        double result = 1.0 - difference;
        return result;
    }

    public List<Songs> suggestSongs(String s, List<Songs> songs) {
        List<Songs> result = new ArrayList<>();
        if (TextNormalizer.normalize(s).length() < MIN_KEYWORD_LENGTH) {
            return result;
        }

        boolean[] used = new boolean[songs.size()];

        for (int count = 0; count < MAX_SUGGESTIONS; count++) {
            int best = -1;
            double bestScore = MIN_SCORE;

            for (int i = 0; i < songs.size(); i++) {
                if (used[i]) {
                    continue;
                }
                double value = score(s, songs.get(i).getTitle());
                if (value >= MIN_SCORE && (best == -1 || value > bestScore)) {
                    best = i;
                    bestScore = value;
                }
            }

            if (best == -1) {
                break;
            }

            used[best] = true;
            result.add(songs.get(best));
        }

        return result;
    }

    public List<Artists> suggestArtists(String s, List<Artists> artists) {
        List<Artists> result = new ArrayList<>();
        if (TextNormalizer.normalize(s).length() < MIN_KEYWORD_LENGTH) {
            return result;
        }

        boolean[] used = new boolean[artists.size()];

        for (int count = 0; count < MAX_SUGGESTIONS; count++) {
            int best = -1;
            double bestScore = MIN_SCORE;

            for (int i = 0; i < artists.size(); i++) {
                if (used[i]) {
                    continue;
                }
                double value = score(s, artists.get(i).getArtistName());
                if (value >= MIN_SCORE && (best == -1 || value > bestScore)) {
                    best = i;
                    bestScore = value;
                }
            }

            if (best == -1) {
                break;
            }

            used[best] = true;
            result.add(artists.get(best));
        }

        return result;
    }
}
