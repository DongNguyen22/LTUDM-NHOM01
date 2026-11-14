package com.musicfinder.server.network;

import com.musicfinder.server.handler.RequestDispatcher;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.PrivateKey;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TcpServer {
    private final PrivateKey privateKey;
    private final int port;
    private final RequestDispatcher dispatcher;
    private final ThreadPoolExecutor executor;
    private ServerSocket serverSocket;
    private volatile boolean running = true;

    public TcpServer(int port, RequestDispatcher dispatcher, PrivateKey privateKey, int corePoolSize, int maxPoolSize) {
        this.port = port;
        this.dispatcher = dispatcher;
        this.privateKey = privateKey;
        this.executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public void start() {
        try (ServerSocket server = new ServerSocket(port)) {
            serverSocket = server;
            System.out.println("Server đang lắng nghe tại cổng " + port);

            while (running) {
                Socket client = server.accept();
                System.out.println("Client " + client.getRemoteSocketAddress() + " đã kết nối!");
                try {
                    executor.execute(new ClientHandler(client, dispatcher, privateKey));
                } catch (RejectedExecutionException e) {
                    refuse(client); // đã hết thread
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Lỗi ServerSocket: " + e.getMessage());
            }
        }
    }

    private void refuse(Socket client) {
        System.out.println("Server quá tải, từ chối " + client.getRemoteSocketAddress());
        try {
            client.close();
        } catch (IOException ignored) {
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close(); // làm accept() thoát ra
            }
        } catch (IOException ignored) {
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("Server đã dừng.");
    }
}