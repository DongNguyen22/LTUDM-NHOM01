package com.musicfinder.server.network;

import com.musicfinder.server.handler.RequestDispatcher;
import com.musicfinder.shared.crypto.CryptoUtil;
import com.musicfinder.shared.enums.Action;
import com.musicfinder.shared.response.Response;
import com.musicfinder.shared.response.payload.UserData;

import javax.crypto.SecretKey;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;

public class ClientHandler implements Runnable {
    private static final int HANDSHAKE_TIMEOUT_MS = 5_000; // bắt tay quá 5 giây thì bỏ
    private static final int READ_TIMEOUT_MS = 5 * 60 * 1000; // 5 phút không gửi gì thì ngắt

    private final Socket socket;
    private final RequestDispatcher dispatcher;
    private final PrivateKey serverKey; // private key của server, chỉ dùng để mở khóa phiên
    private Long currentUserId = null; // userId của phiên (mỗi socket một ClientHandler riêng)

    private SecretKey sessionKey; // khóa phiên AES RIÊNG của client này
    private long sendSeq = 0; // số thứ tự gói gửi đi
    private long lastRecvSeq = -1; // số thứ tự gói nhận gần nhất

    public ClientHandler(Socket socket, RequestDispatcher dispatcher, PrivateKey serverKey) {
        this.socket = socket;
        this.dispatcher = dispatcher;
        this.serverKey = serverKey;
    }

    @Override
    public void run() {
        String client = String.valueOf(socket.getRemoteSocketAddress());
        try {
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            // 1) Bắt tay: nhận khóa AES đã bọc bằng RSA public key của server
            socket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
            try {
                sessionKey = CryptoUtil.unwrapKey(serverKey, CryptoUtil.readFrame(in, CryptoUtil.HANDSHAKE_MAX));
            } catch (GeneralSecurityException e) {
                System.out.println("Client " + client + ": bắt tay thất bại (sai public key?) -> đóng kết nối");
                return;
            }
            socket.setSoTimeout(READ_TIMEOUT_MS);
            System.out.println("Client " + client + " bắt tay thành công, khóa phiên: "
                    + CryptoUtil.fingerprintKey(sessionKey));

            // 2) Nhận gói mã hóa -> kiểm tra seq -> giải mã -> xử lý -> mã hóa -> gửi lại
            while (true) {
                byte[] pkt = CryptoUtil.readFrame(in, CryptoUtil.MAX_FRAME);
                long seq = CryptoUtil.seqOf(pkt);
                if (seq <= lastRecvSeq) {
                    System.out.println("Client " + client + ": gói cũ/phát lại (seq=" + seq + ") -> đóng kết nối");
                    return;
                }
                String line = new String(CryptoUtil.decrypt(sessionKey, pkt), StandardCharsets.UTF_8);
                lastRecvSeq = seq;
                if (line.isBlank()) {
                    continue;
                }

                Response response = dispatcher.handle(line, currentUserId);

                // Đăng nhập / đăng ký thành công thì ghi nhớ userId của phiên
                if (response.isSuccess() && response.getData() != null) {
                    if (response.getAction() == Action.LOGIN || response.getAction() == Action.REGISTER) {
                        UserData user = response.dataAs(UserData.class);
                        if (user != null && user.userId() != null) {
                            currentUserId = user.userId();
                        }
                    }
                }
                if (response.isSuccess() && response.getAction() == Action.LOGOUT) {
                    currentUserId = null;
                }

                byte[] reply = CryptoUtil.encrypt(sessionKey, sendSeq++,
                        response.toJson().getBytes(StandardCharsets.UTF_8));
                CryptoUtil.sendFrame(out, reply);
            }
        } catch (EOFException e) {
            System.out.println("Client " + client + " đã đóng kết nối.");
        } catch (SocketTimeoutException e) {
            System.out.println("Client " + client + " quá thời gian chờ, đóng kết nối.");
        } catch (GeneralSecurityException e) {
            // Sai tag: gói bị sửa hoặc hỏng. KHÔNG in nội dung gói tin.
            System.out.println("Client " + client + ": gói tin không hợp lệ (" + e.getMessage() + ") -> đóng kết nối");
        } catch (IOException e) {
            System.out.println("Client " + client + " mất kết nối: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            System.out.println("Đã đóng kết nối với " + client);
        }
    }
}