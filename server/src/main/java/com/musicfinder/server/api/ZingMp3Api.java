package com.musicfinder.server.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;

public class ZingMp3Api {

    public record ZingArtist(
            String id,
            String name,
            String alias,
            String thumbnail,
            long totalFollow) {
    }

    public record ZingArtistDetail(
            String id,
            String name,
            String alias,
            String bio,
            String imageUrl,
            java.time.LocalDate birthDate,
            String hometown,
            List<YtDlpApi.RemoteSong> songs) {
    }

    private static final String BASE_URL = System.getProperty("music.zing.baseUrl",
            "https://mp3-api-f60g.onrender.com");
    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_RESULTS = 15;

    public void warmUp() {
        try {
            Jsoup.connect(BASE_URL + "/api/search/song")
                    .data("q", "a")
                    .ignoreContentType(true)
                    .timeout(60_000)
                    .execute();
            System.out.println("Zing MP3 đã sẵn sàng");
        } catch (IOException e) {
            System.out.println("Chưa làm nóng được Zing MP3: " + e.getMessage());
        }
    }

    public String getComposer(String encodeId) {
        if (encodeId == null || !encodeId.matches("[A-Za-z0-9]{6,20}"))
            return null;
        try {
            String body = Jsoup.connect(BASE_URL + "/api/info-song")
                    .data("id", encodeId)
                    .ignoreContentType(true)
                    .timeout(TIMEOUT_MS)
                    .execute()
                    .body();
            JSONObject root = new JSONObject(body);
            if (root.optInt("err", -1) != 0)
                return null;
            JSONArray composers = root.getJSONObject("data").optJSONArray("composers");
            if (composers == null)
                return null;
            List<String> names = new ArrayList<>();
            for (int i = 0; i < composers.length(); i++) {
                JSONObject c = composers.optJSONObject(i);
                String name = c == null ? "" : c.optString("name", "").trim();
                if (!name.isEmpty())
                    names.add(name);
            }
            return names.isEmpty() ? null : String.join(", ", names);
        } catch (IOException | JSONException e) {
            System.out.println("Không lấy được nhạc sĩ từ Zing MP3: " + e.getMessage());
            return null;
        }
    }

    public List<YtDlpApi.RemoteSong> searchSongs(String keyword) {
        List<YtDlpApi.RemoteSong> result = new ArrayList<>();
        if (keyword == null || keyword.isBlank())
            return result;

        try {
            String body = Jsoup.connect(BASE_URL + "/api/search/song")
                    .data("q", keyword.trim())
                    .ignoreContentType(true)
                    .timeout(TIMEOUT_MS)
                    .execute()
                    .body();

            JSONObject root = new JSONObject(body);
            if (root.optInt("err", -1) != 0) {
                System.out.println("Zing MP3 báo lỗi: " + root.optString("msg", ""));
                return result;
            }
            JSONArray songs = root.getJSONObject("data").optJSONArray("songs");
            if (songs == null)
                return result;

            for (int i = 0; i < songs.length() && result.size() < MAX_RESULTS; i++) {
                YtDlpApi.RemoteSong song = toRemoteSong(songs.optJSONObject(i));
                if (song != null)
                    result.add(song);
            }
        } catch (IOException e) {
            // Gồm timeout, mất mạng, HTTP lỗi (404, 500...)
            System.out.println("Không gọi được Zing MP3: " + e.getMessage());
        } catch (JSONException e) {
            System.out.println("Zing MP3 trả JSON không hợp lệ: " + e.getMessage());
        }
        return result;
    }

    private YtDlpApi.RemoteSong toRemoteSong(JSONObject item) {
        if (item == null)
            return null;

        String encodeId = item.optString("encodeId", "");
        String title = item.optString("title", "").trim();
        if (!encodeId.matches("[A-Za-z0-9]{6,20}") || title.isEmpty())
            return null;

        int duration = Math.max(0, item.optInt("duration", 0));

        String thumbnail = item.optString("thumbnailM", item.optString("thumbnail", ""));
        if (thumbnail.isEmpty())
            thumbnail = null;

        String artistName = "";
        String artistId = "";
        String artistAlias = "";
        JSONArray artists = item.optJSONArray("artists");
        if (artists != null && artists.length() > 0) {
            JSONObject first = artists.optJSONObject(0);
            if (first != null) {
                artistName = first.optString("name", "").trim();
                artistId = first.optString("id", "");
                artistAlias = first.optString("alias", "").trim();
            }
        }
        if (artistName.isEmpty())
            artistName = item.optString("artistsNames", "").trim();
        if (artistName.isEmpty())
            artistName = "Nghệ sĩ chưa rõ";

        String artistExternalId;
        if (!artistAlias.isEmpty()) {
            artistExternalId = "zing:artist:" + artistAlias;
        } else if (artistId.matches("[A-Za-z0-9]{6,20}")) {
            artistExternalId = "zing:artist:" + artistId;
        } else {
            artistExternalId = "zing:unknown:" + encodeId;
        }

        return new YtDlpApi.RemoteSong("zing:song:" + encodeId, title, duration, null, thumbnail, null,
                new YtDlpApi.RemoteArtist(artistExternalId, artistName));
    }

    // Tìm kiếm ca sĩ
    public List<ZingArtist> searchArtists(String keyword) {
        List<ZingArtist> result = new ArrayList<>();
        if (keyword == null || keyword.isBlank())
            return result;

        try {
            String body = Jsoup.connect(BASE_URL + "/api/search/artist")
                    .data("q", keyword.trim())
                    .ignoreContentType(true)
                    .timeout(TIMEOUT_MS)
                    .execute()
                    .body();

            JSONObject root = new JSONObject(body);
            if (root.optInt("err", -1) != 0)
                return result;

            JSONArray artists = root.optJSONObject("data") != null
                    ? root.getJSONObject("data").optJSONArray("artists")
                    : null;
            if (artists == null)
                return result;

            for (int i = 0; i < artists.length() && result.size() < MAX_RESULTS; i++) {
                JSONObject a = artists.optJSONObject(i);
                if (a == null)
                    continue;
                String alias = a.optString("alias", "").trim();
                String name = a.optString("name", "").trim();
                if (alias.isEmpty() || name.isEmpty())
                    continue;

                String thumbnail = a.optString("thumbnailM", a.optString("thumbnail", ""));
                result.add(new ZingArtist(
                        a.optString("id", ""),
                        name,
                        alias,
                        thumbnail.isEmpty() ? null : thumbnail,
                        a.optLong("totalFollow", 0)));
            }
        } catch (IOException | JSONException e) {
            System.out.println("Không gọi được Zing search artist: " + e.getMessage());
        }
        return result;
    }

    // Chi tiết nghệ sĩ
    public ZingArtistDetail getArtistDetail(String alias) {
        if (alias == null || alias.isBlank())
            return null;

        try {
            String body = Jsoup.connect(BASE_URL + "/api/artist")
                    .data("name", alias.trim())
                    .ignoreContentType(true)
                    .timeout(TIMEOUT_MS)
                    .execute()
                    .body();

            JSONObject root = new JSONObject(body);
            if (root.optInt("err", -1) != 0)
                return null;

            JSONObject data = root.optJSONObject("data");
            if (data == null)
                return null;

            String name = data.optString("name", "").trim();
            String bio = data.optString("biography", "")
                    .replaceAll("(?i)<br\\s*/?>", "\n")
                    .trim();
            String imageUrl = data.optString("thumbnailM", data.optString("thumbnail", null));
            String hometown = data.optString("national", null);

            java.time.LocalDate birthDate = null;
            String bdayStr = data.optString("birthday", "");
            if (!bdayStr.isBlank()) {
                try {
                    birthDate = java.time.LocalDate.parse(bdayStr.trim(),
                            java.time.format.DateTimeFormatter.ofPattern("d/M/yyyy"));
                } catch (Exception ignored) {
                }
            }

            List<YtDlpApi.RemoteSong> songs = new ArrayList<>();
            JSONArray sections = data.optJSONArray("sections");
            if (sections != null) {
                for (int i = 0; i < sections.length(); i++) {
                    JSONObject sec = sections.optJSONObject(i);
                    if (sec != null && "song".equalsIgnoreCase(sec.optString("sectionType"))) {
                        JSONArray items = sec.optJSONArray("items");
                        if (items != null) {
                            for (int j = 0; j < items.length() && songs.size() < 5; j++) {
                                YtDlpApi.RemoteSong song = toRemoteSong(items.optJSONObject(j));
                                if (song != null)
                                    songs.add(song);
                            }
                        }
                        break;
                    }
                }
            }

            return new ZingArtistDetail(
                    data.optString("id", ""),
                    name,
                    alias,
                    bio.isEmpty() ? null : bio,
                    imageUrl,
                    birthDate,
                    hometown,
                    songs);
        } catch (IOException | JSONException e) {
            System.out.println("Không lấy được chi tiết ca sĩ từ Zing: " + e.getMessage());
            return null;
        }
    }
}