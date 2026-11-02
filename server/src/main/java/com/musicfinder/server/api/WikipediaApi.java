package com.musicfinder.server.api;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;


import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
 
@Slf4j 
public class WikipediaApi {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";
    private static final int TIMEOUT_MS = 3000;

    public record WikiArtist(
            String externalId,
            String name,
            String bio,
            String imageUrl,
            LocalDate birthDate,
            String hometown) {
    }

    public WikiArtist searchArtist(String keyword) {
        if (keyword == null || keyword.isBlank())
            return null;

        try {
            // Tìm tiêu đề chuẩn từ keyword
            String searchUrl = "https://vi.wikipedia.org/w/api.php?action=opensearch&search="
                    + URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8)
                    + "&limit=1&namespace=0&format=json";

            String searchJson = Jsoup.connect(searchUrl)
                    .ignoreContentType(true)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .execute()
                    .body();

            JSONArray searchArr = new JSONArray(searchJson);
            JSONArray titles = searchArr.optJSONArray(1);
            if (titles == null || titles.isEmpty())
                return null;

            String exactTitle = titles.getString(0);

            // Lấy summary (Bio, Ảnh, Page ID)
            String summaryUrl = "https://vi.wikipedia.org/api/rest_v1/page/summary/"
                    + URLEncoder.encode(exactTitle.replace(" ", "_"), StandardCharsets.UTF_8);

            String summaryJson = Jsoup.connect(summaryUrl)
                    .ignoreContentType(true)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .execute()
                    .body();

            JSONObject root = new JSONObject(summaryJson);
            String title = root.optString("title", exactTitle);
            String extract = root.optString("extract", "");
            long pageId = root.optLong("pageid", 0);

            String imageUrl = null;
            if (root.has("thumbnail")) {
                imageUrl = root.getJSONObject("thumbnail").optString("source", null);
            }

            LocalDate birthDate = extractBirthDate(extract);

            // Lấy hometown từ bảng Infobox HTML
            String hometown = extractHometownFromInfobox(exactTitle);

            return new WikiArtist("wiki:page:" + pageId, title, extract, imageUrl, birthDate, hometown);
        } catch (Exception e) {
            log.warn("Lỗi gọi Wikipedia API: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Quét bảng Infobox để lấy Quê quán hoặc Nơi sinh
     */
    private String extractHometownFromInfobox(String title) {
        try {
            String pageUrl = "https://vi.wikipedia.org/wiki/"
                    + URLEncoder.encode(title.replace(" ", "_"), StandardCharsets.UTF_8);

            Document doc = Jsoup.connect(pageUrl)
                    .maxBodySize(500 * 1024)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            Element infobox = doc.selectFirst("table.infobox");
            if (infobox == null)
                return null;

            for (Element row : infobox.select("tr")) {
                Element th = row.selectFirst("th");
                Element td = row.selectFirst("td");
                if (th != null && td != null) {
                    String label = th.text().toLowerCase();
                    // Bắt các nhãn phổ biến trong bảng thông tin ca sĩ Việt Nam
                    if (label.contains("quê quán") || label.contains("nơi sinh") || label.contains("nguyên quán")) {
                        // Loại bỏ các thẻ chú thích wiki dạng [1], [2]
                        return td.text().replaceAll("\\[\\d+\\]", "").trim();
                    }
                }
            }
        } catch (Exception ignored) {
           
        }
        return null;
    }

    private LocalDate extractBirthDate(String text) {
        if (text == null || text.isBlank())
            return null;
        Pattern p1 = Pattern.compile("sinh(?: ngày)?\\s+(\\d{1,2})\\s+tháng\\s+(\\d{1,2})\\s+năm\\s+(\\d{4})");
        Matcher m1 = p1.matcher(text);
        if (m1.find()) {
            try {
                return LocalDate.of(Integer.parseInt(m1.group(3)),
                        Integer.parseInt(m1.group(2)),
                        Integer.parseInt(m1.group(1)));
            } catch (Exception ignored) {
            }
        }
        return null;
    }
 

}