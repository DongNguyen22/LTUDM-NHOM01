package com.musicfinder.server;

import com.musicfinder.server.handler.RequestDispatcher;
import com.musicfinder.server.network.TcpServer;
import com.musicfinder.server.repository.SearchCacheRepository;
import com.musicfinder.shared.crypto.CryptoUtil;
import com.musicfinder.server.network.AudioStreamServer;

import java.security.PrivateKey;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.musicfinder.server.api.YtDlpApi;
import com.musicfinder.server.api.ZingMp3Api;

public class ServerApplication {
    private static final int PORT = 12345;
    private static final int CORE_POOL_SIZE = 10;
    private static final int MAX_POOL_SIZE = 50;

    public static void main(String[] args) {
        String keyFile = System.getProperty("music.private.key", "keys/server_private.key");
        PrivateKey privateKey;
        try {
            privateKey = CryptoUtil.loadPrivate(keyFile);
            System.out.println("Đã nạp private key: " + keyFile);
        } catch (Exception e) {
            System.err.println("Không nạp được private key: " + e.getMessage());
            return;
        }

        // Lần đầu tự tải công cụ trên Windows; lỗi tải vẫn cho phép tra cứu.
        YtDlpApi.prepareAudioTools();
        AudioStreamServer audioServer = new AudioStreamServer();
        if (!audioServer.start())
            return;
        // Đánh thức Zing MP3 ở luồng nền để lần tìm kiếm đầu tiên không bị timeout
        Thread warmUp = new Thread(() -> new ZingMp3Api().warmUp(), "zing-warmup");
        warmUp.setDaemon(true);
        warmUp.start();

        RequestDispatcher dispatcher = new RequestDispatcher();
        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cache-cleaner");
            t.setDaemon(true);
            return t;
        });
        SearchCacheRepository cacheRepo = new SearchCacheRepository();
        cleaner.scheduleAtFixedRate(() -> {
            try {
                System.out.println("Dọn cache: xóa " + cacheRepo.deleteExpiredOlderThanDays(7) + " dòng");
            } catch (RuntimeException e) {
                System.out.println("Dọn cache lỗi: " + e.getMessage());
            }
        }, 6, 6, TimeUnit.HOURS);
        TcpServer server = new TcpServer(PORT, dispatcher, privateKey, CORE_POOL_SIZE, MAX_POOL_SIZE);

        // Nhấn Ctrl+C thì dừng server gọn gàng
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            cleaner.shutdownNow();
            audioServer.stop();
            server.stop();
        }));

        try {
            server.start();
        } finally {
            audioServer.stop();
        }
    }
}