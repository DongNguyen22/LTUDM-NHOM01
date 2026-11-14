package com.musicfinder.server.util;

import com.musicfinder.shared.crypto.CryptoUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

/**
 * Chạy MỘT LẦN để tạo cặp khóa RSA-2048 cho server.
 * keys/server_private.key : giữ bí mật, chỉ đặt ở máy server
 * keys/server_public.key : chép vào client/src/main/resources/ (ghim khóa)
 */
public class KeyGenTool {
    static final String OUTPUT_DIR = System.getProperty("music.keys.dir", "keys");

    public static void main(String[] args) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        Path dir = Path.of(OUTPUT_DIR);
        Files.createDirectories(dir);
        Files.write(dir.resolve("server_private.key"), kp.getPrivate().getEncoded()); // PKCS#8
        Files.write(dir.resolve("server_public.key"), kp.getPublic().getEncoded()); // X.509
        System.out.println("Đã tạo khóa trong " + dir.toAbsolutePath().normalize());
        System.out.println("Fingerprint public key: " + CryptoUtil.fingerprint(kp.getPublic()));
    }
}