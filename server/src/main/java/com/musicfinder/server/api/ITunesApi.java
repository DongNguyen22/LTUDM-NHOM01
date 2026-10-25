package com.musicfinder.server.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
// import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.Connection;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

public class ITunesApi {
    private static final String API_URL = "https://itunes.apple.com/search";
    private static final int TIMEOUT_MS = 4000;
    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final int RESULT_LIMIT = 10;
    private static final int MAX_API_CALLS = 20;
    private static final long API_WINDOW_MS = 60 * 1000;

    private long startTime = System.currentTimeMillis();
    private int requestCount = 0;

    private synchronized void checkLimit() {
        long now = System.currentTimeMillis();

        if (now - startTime >= API_WINDOW_MS) {
            startTime = now;
            requestCount = 0;
        }

        if (requestCount >= MAX_API_CALLS) {
            throw new UncheckedIOException(new IOException("Đã hết lượt gọi iTunes trong phút này"));
        }

        requestCount++;
    }

    protected String getData(String api) {
        try {
            String body = Jsoup.connect(api)
                    .method(Connection.Method.GET)
                    .ignoreContentType(true)
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_BYTES)
                    .execute()
                    .body();

            return body;
        } catch (IOException e) {
            throw new UncheckedIOException("Không gọi được iTunes", e);
        }
    }

    private JSONArray search(String s, String entity, String attribute) {
        if (s == null || s.isBlank() || s.trim().length() > 255) {
            throw new IllegalArgumentException("Từ khóa không hợp lệ");
        }

        checkLimit();

        String api = API_URL + "?term="
                + URLEncoder.encode(s.trim(), StandardCharsets.UTF_8)
                + "&media=music&entity=" + entity
                + "&attribute=" + attribute
                + "&country=VN&limit=" + RESULT_LIMIT;

        String body = getData(api);

        try {
            JSONObject json = new JSONObject(body);
            return json.getJSONArray("results");
        } catch (JSONException e) {
            throw new UncheckedIOException(new IOException("iTunes trả JSON không hợp lệ", e));
        }
    }

    public List<RemoteSong> searchSongs(String s) {
        List<RemoteSong> result = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        JSONArray data = search(s, "song", "songTerm");

        for (int i = 0; i < data.length() && result.size() < RESULT_LIMIT; i++) {
            JSONObject obj = data.optJSONObject(i);
            if (obj == null) {
                continue;
            }

            String id = obj.optString("trackId", "");
            String artistId = obj.optString("artistId", "");
            String title = obj.optString("trackName", "").trim();
            String name = obj.optString("artistName", "").trim();

            if (!obj.optString("kind", "").equals("song")
                    || id.isEmpty() || artistId.isEmpty()
                    || title.isEmpty() || title.length() > 255
                    || name.isEmpty() || name.length() > 150
                    || ids.contains(id)) {
                continue;
            }

            long millis = obj.optLong("trackTimeMillis", 0);
            long durationSeconds = millis / 1000;
            durationSeconds = Math.max(0, durationSeconds);
            durationSeconds = Math.min(Integer.MAX_VALUE, durationSeconds);
            int seconds = (int) durationSeconds;

            RemoteArtist artist = new RemoteArtist("itunes:artist:" + artistId, name);
            RemoteSong song = new RemoteSong(
                    "itunes:song:" + id, title, seconds,
                    obj.optString("previewUrl", null),
                    obj.optString("artworkUrl100", null),
                    obj.optString("trackViewUrl", null),
                    artist);

            result.add(song);
            ids.add(id);
        }

        return result;
    }

    public List<RemoteArtist> searchArtists(String s) {
        List<RemoteArtist> result = new ArrayList<>();
        // Nhớ những ID đã nhận để bỏ qua kết quả bị lặp.
        List<String> ids = new ArrayList<>();
        JSONArray data = search(s, "musicArtist", "artistTerm");

        for (int i = 0; i < data.length() && result.size() < RESULT_LIMIT; i++) {
            JSONObject obj = data.optJSONObject(i);
            if (obj == null) {
                continue;
            }

            String id = obj.optString("artistId", "");
            String name = obj.optString("artistName", "").trim();

            if (id.isEmpty() || name.isEmpty() || name.length() > 150 || ids.contains(id)) {
                continue;
            }

            result.add(new RemoteArtist("itunes:artist:" + id, name));
            ids.add(id);
        }

        return result;
    }

    public record RemoteArtist(String externalId, String name) {
    }

    public record RemoteSong(String externalId, String title, int durationSeconds,
            String previewUrl, String thumbnailUrl, String storeUrl, RemoteArtist artist) {
    }
}
