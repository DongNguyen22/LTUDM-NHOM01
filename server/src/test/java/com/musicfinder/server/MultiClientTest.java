package com.musicfinder.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.musicfinder.server.handler.RequestDispatcher;
import com.musicfinder.server.network.TcpServer;
import com.musicfinder.shared.crypto.CryptoUtil;
import com.musicfinder.shared.enums.Action;
import com.musicfinder.shared.enums.Status;
import com.musicfinder.shared.request.Request;
import com.musicfinder.shared.response.Response;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MultiClientTest {
    private static final int PORT = 54321;
    private static TcpServer server;
    private static PublicKey publicKey;

    @BeforeAll
    static void startServer() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair pair = g.generateKeyPair();
        publicKey = pair.getPublic();
        server = new TcpServer(PORT, new RequestDispatcher(), pair.getPrivate(), 5, 50);
        new Thread(server::start).start();
        Thread.sleep(500); // chờ server mở cổng
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    // Gửi 1 dòng, nhận 1 dòng
    private Response sendLine(String line) throws Exception {
        try (Socket socket = new Socket("localhost", PORT)) {
            socket.setSoTimeout(10_000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(256);
            SecretKey key = kg.generateKey();
            CryptoUtil.sendFrame(out, CryptoUtil.wrapKey(publicKey, key));
            CryptoUtil.sendFrame(out, CryptoUtil.encrypt(key, 0, line.getBytes(StandardCharsets.UTF_8)));
            byte[] pkt = CryptoUtil.readFrame(in, CryptoUtil.MAX_FRAME);
            return Response.fromJson(new String(CryptoUtil.decrypt(key, pkt), StandardCharsets.UTF_8));
        }
    }

    @Test
    void ping20ClientCungLuc() throws Exception {
        int clients = 20;
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        List<Future<Response>> results = new ArrayList<>();

        for (int i = 0; i < clients; i++) {
            final String requestId = "req-" + i;
            results.add(pool.submit(() -> {
                Request req = Request.of(Action.PING, null);
                req.setRequestId(requestId);
                return sendLine(req.toJson());
            }));
        }

        for (int i = 0; i < clients; i++) {
            Response res = results.get(i).get(10, TimeUnit.SECONDS);
            assertTrue(res.isSuccess());
            assertEquals("req-" + i, res.requestId);
        }
        pool.shutdown();
    }

    @Test
    void jsonHongKhongLamSapServer() throws Exception {
        Response bad = sendLine("abc");
        assertEquals(Status.INVALID_REQUEST, bad.status);

        // server vẫn phục vụ bình thường sau đó
        Response ok = sendLine(Request.of(Action.PING, null).toJson());
        assertTrue(ok.isSuccess());
    }
}