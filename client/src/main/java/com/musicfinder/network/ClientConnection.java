package com.musicfinder.network;

import com.musicfinder.shared.crypto.CryptoUtil;
import com.musicfinder.shared.enums.Status;
import com.musicfinder.shared.request.Request;
import com.musicfinder.shared.response.Response;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ClientConnection {
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final ClientConnection INSTANCE = new ClientConnection();

    private volatile Socket socket;
    private DataOutputStream out;
    private SecretKey sessionKey; // khóa phiên mới cho MỖI lần kết nối
    private long sendSeq = 0;
    private final Map<String, Consumer<Response>> pendingRequest = new ConcurrentHashMap<>();

    private ClientConnection() {
    }

    public static ClientConnection getInstance() {
        return INSTANCE;
    }

    public synchronized void connect(String host, int port) throws IOException {
        if (isConnected()) {
            return;
        }
        PublicKey serverPub = loadServerPublicKey(); // ghim sẵn trong client, KHÔNG nhận qua mạng
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            DataOutputStream o = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));

            // Bắt tay: sinh khóa AES ngẫu nhiên, bọc bằng RSA-OAEP rồi gửi cho server
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(256);
            SecretKey key = kg.generateKey();
            CryptoUtil.sendFrame(o, CryptoUtil.wrapKey(serverPub, key));

            socket = s;
            out = o;
            sessionKey = key;
            sendSeq = 0;
            startListening(s, in, key);
        } catch (IOException | GeneralSecurityException e) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
            throw new IOException("Không thể kết nối/bắt tay với server: " + e.getMessage(), e);
        }
    }

    // Public key của server nằm trong resources của client (mặc định
    // /server_public.key)
    private PublicKey loadServerPublicKey() throws IOException {
        try {
            String file = System.getProperty("music.server.publickey");
            if (file != null) {
                return CryptoUtil.loadPublic(file);
            }
            try (InputStream is = ClientConnection.class.getResourceAsStream("/server_public.key")) {
                if (is == null) {
                    throw new IOException("Thiếu server_public.key trong resources của client");
                }
                return CryptoUtil.loadPublic(is);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Không đọc được public key của server: " + e.getMessage(), e);
        }
    }

    public synchronized void send(Request request, Consumer<Response> callback) throws IOException {
        if (!isConnected()) {
            throw new IOException("Client chưa kết nối tới server");
        }
        String id = request.getRequestId();
        if (callback != null) {
            pendingRequest.put(id, callback);
        }
        try {
            byte[] pkt = CryptoUtil.encrypt(sessionKey, sendSeq++, request.toJson().getBytes(StandardCharsets.UTF_8));
            CryptoUtil.sendFrame(out, pkt);
        } catch (GeneralSecurityException | IOException e) {
            pendingRequest.remove(id);
            closeSocket(socket);
            throw new IOException("Không thể gửi request: " + e.getMessage(), e);
        }
    }

    public void send(Request request) throws IOException {
        send(request, null);
    }

    // Luồng nền chuyên nhận dữ liệu từ server, không chạy trên UI thread
    private void startListening(Socket s, DataInputStream in, SecretKey key) {
        Thread listenerThread = new Thread(() -> {
            long lastRecvSeq = -1;
            try {
                while (true) {
                    byte[] pkt = CryptoUtil.readFrame(in, CryptoUtil.MAX_FRAME);
                    long seq = CryptoUtil.seqOf(pkt);
                    if (seq <= lastRecvSeq) {
                        throw new IOException("Gói cũ/phát lại");
                    }
                    String json = new String(CryptoUtil.decrypt(key, pkt), StandardCharsets.UTF_8);
                    lastRecvSeq = seq;
                    try {
                        Response response = Response.fromJson(json);
                        Consumer<Response> callback = pendingRequest.remove(response.getRequestId());
                        if (callback != null) {
                            callback.accept(response);
                        }
                    } catch (RuntimeException e) {
                        System.out.println("Không thể xử lý response: " + e.getMessage());
                    }
                }
            } catch (IOException | GeneralSecurityException e) {
                System.out.println("Mất kết nối tới server: " + e.getMessage());
            } finally {
                closeSocket(s);
                failAllPending("Mất kết nối tới server"); // để giao diện thoát trạng thái "đang tải"
            }
        }, "server-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    // Báo lỗi cho mọi request đang chờ phản hồi
    private void failAllPending(String message) {
        for (String id : pendingRequest.keySet()) {
            Consumer<Response> callback = pendingRequest.remove(id);
            if (callback == null) {
                continue;
            }
            Response res = new Response();
            res.requestId = id;
            res.status = Status.SERVER_ERROR;
            res.message = message;
            try {
                callback.accept(res);
            } catch (RuntimeException e) {
                System.out.println("Lỗi khi báo mất kết nối: " + e.getMessage());
            }
        }
    }

    private static void closeSocket(Socket s) {
        try {
            if (s != null) {
                s.close();
            }
        } catch (IOException ignored) {
        }
    }

    public boolean isConnected() {
        Socket s = socket;
        return s != null && s.isConnected() && !s.isClosed();
    }

    public synchronized void close() throws IOException {
        pendingRequest.clear(); // đóng chủ động thì không báo lỗi cho request đang chờ
        Socket s = socket;
        socket = null;
        if (s != null) {
            s.close();
        }
    }
}