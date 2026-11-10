package com.musicfinder.server.service;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.musicfinder.server.api.YtDlpApi;
import com.musicfinder.server.network.AudioStreamServer;
import com.musicfinder.shared.response.payload.VideoData;

public class VideoService {
    private static final Pattern EXTERNAL_ID = Pattern.compile("youtube:video:([A-Za-z0-9_-]{11})");
    private static final long FAILED_COOLDOWN_MS = 30_000;

    private static class Job {
        volatile boolean failed;
        volatile String message;
        volatile long failedAt;

        void fail(String message) {
            this.message = message;
            this.failedAt = System.currentTimeMillis();
            this.failed = true;
        }
    }

    private final SearchService searchService;
    private final YtDlpApi api;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(10), runnable -> {
                Thread thread = new Thread(runnable, "video-download");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public VideoService(SearchService searchService, YtDlpApi api) {
        this.searchService = searchService;
        this.api = api;
    }

    public VideoData getVideo(Long songId, String externalId) {
        String videoId = resolveVideoId(songId, externalId);
        long start = System.nanoTime();

        File file = new File(System.getProperty("music.video.directory", "storage/video"), videoId + ".mp4");
        if (file.isFile() && file.length() > 0 && !Files.isSymbolicLink(file.toPath())) {
            file.setLastModified(System.currentTimeMillis());
            jobs.remove(videoId);
            saveVideoUrlSafely(videoId);
            System.out.println("VIDEO READY " + videoId + " (kiểm tra " + millisSince(start) + " ms)");
            return new VideoData("READY", AudioStreamServer.getVideoUrl(videoId), null);
        }

        Job existing = jobs.get(videoId);
        if (existing != null) {
            if (!existing.failed)
                return processing();
            if (System.currentTimeMillis() - existing.failedAt < FAILED_COOLDOWN_MS)
                throw new IllegalStateException(existing.message);
            Job fresh = new Job();
            if (!jobs.replace(videoId, existing, fresh))
                return processing();
            return startDownload(videoId, fresh);
        }

        Job job = new Job();
        if (jobs.putIfAbsent(videoId, job) != null)
            return processing();
        return startDownload(videoId, job);
    }

    private VideoData startDownload(String videoId, Job job) {
        try {
            executor.execute(() -> download(videoId, job));
        } catch (RejectedExecutionException e) {
            jobs.remove(videoId, job);
            throw new IllegalStateException("Server đang bận tải video, vui lòng thử lại sau");
        }
        System.out.println("VIDEO đã xếp hàng tải " + videoId);
        return processing();
    }

    private void download(String videoId, Job job) {
        long start = System.nanoTime();
        try {
            api.downloadVideoToCache(videoId);
            saveVideoUrlSafely(videoId);
            jobs.remove(videoId, job);
            System.out.println("VIDEO tải xong " + videoId + " sau " + millisSince(start) + " ms");
        } catch (IllegalStateException e) {
            job.fail(e.getMessage());
            System.out.println("VIDEO tải lỗi " + videoId + ": " + e.getMessage());
        } catch (RuntimeException e) {
            e.printStackTrace();
            job.fail("Không tải được video");
        }
    }

    private void saveVideoUrlSafely(String videoId) {
        try {
            searchService.saveVideoUrl(videoId, AudioStreamServer.getVideoUrl(videoId));
        } catch (RuntimeException e) {
            System.out.println("Không lưu được video_url cho " + videoId + ": " + e.getMessage());
        }
    }

    private VideoData processing() {
        return new VideoData("PROCESSING", null, null);
    }

    private String resolveVideoId(Long songId, String externalId) {
        if (externalId != null && !externalId.isBlank()) {
            Matcher matcher = EXTERNAL_ID.matcher(externalId.trim());
            if (!matcher.matches())
                throw new IllegalArgumentException("Mã bài hát không hợp lệ");
            return matcher.group(1);
        }
        if (songId == null || songId <= 0)
            throw new IllegalArgumentException("ID bài hát không hợp lệ");
        String videoId = SearchService.youtubeId(searchService.getSong(songId));
        if (videoId == null)
            throw new IllegalArgumentException("Bài hát chưa có nguồn YouTube");
        return videoId;
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}