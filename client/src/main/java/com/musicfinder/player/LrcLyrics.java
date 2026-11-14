package com.musicfinder.player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses LRC timestamps without depending on JavaFX. */
public final class LrcLyrics {
    public record Line(double seconds, String text) {}
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{2}(?:[.:]\\d{1,3})?)\\]");
    private static final Pattern META = Pattern.compile("^\\s*\\[[A-Za-z]+:[^\\]]*\\]\\s*$");
    private static final Pattern OFFSET = Pattern.compile("(?im)^\\s*\\[offset:([+-]?\\d+)\\]\\s*$");
    private final List<Line> lines = new ArrayList<>();
    private final String plainText;

    public LrcLyrics(String raw) {
        raw = raw == null ? "" : raw;
        Matcher offsetMatch = OFFSET.matcher(raw);
        double offset = offsetMatch.find() ? Double.parseDouble(offsetMatch.group(1)) / 1000.0 : 0;
        List<String> plain = new ArrayList<>();
        for (String row : raw.split("\\R")) {
            if (META.matcher(row).matches()) continue;
            String text = TIME.matcher(row).replaceAll("").strip();
            Matcher time = TIME.matcher(row);
            while (time.find()) {
                double seconds = Integer.parseInt(time.group(1)) * 60
                        + Double.parseDouble(time.group(2).replace(':', '.')) - offset;
                lines.add(new Line(seconds, text));
            }
            plain.add(text);
        }
        lines.sort(Comparator.comparingDouble(Line::seconds));
        plainText = String.join("\n", plain).strip();
    }

    public record Frame(String current, String next) {}

    public String at(double seconds) {
        return frameAt(seconds).current();
    }

    public Frame frameAt(double seconds) {
        if (lines.isEmpty()) return new Frame(plainText, "");
        int low = 0, high = lines.size() - 1, found = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).seconds() <= seconds) {
                found = mid;
                low = mid + 1;
            } else high = mid - 1;
        }
        String current = found < 0 || lines.get(found).text().isBlank()
                ? "♪" : lines.get(found).text();
        String next = "";
        for (int i = found + 1; i < lines.size(); i++) {
            if (!lines.get(i).text().isBlank()
                    && (found < 0 || lines.get(i).seconds() > lines.get(found).seconds())) {
                next = lines.get(i).text();
                break;
            }
        }
        return new Frame(current, next);
    }
}
