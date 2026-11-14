package com.musicfinder.shared.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;

public class CryptoUtil {
    static final SecureRandom RNG = new SecureRandom();

    public static final int MAX_FRAME = 8 * 1024 * 1024;
    public static final int HANDSHAKE_MAX = 512;

    // ---- Khung tin: [độ dài 4 byte][dữ liệu] trên luồng TCP ----
    public static void sendFrame(DataOutputStream out, byte[] data) throws IOException {
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }

    public static byte[] readFrame(DataInputStream in, int maxLen) throws IOException {
        int len = in.readInt();
        if (len <= 0 || len > maxLen)
            throw new IOException("Khung tin không hợp lệ");
        byte[] data = new byte[len];
        in.readFully(data);
        return data;
    }

    // ---- AES-GCM: gói = [seq 8][IV 12][ciphertext + tag 16]; seq nằm trong AAD
    // ----
    public static byte[] encrypt(SecretKey key, long seq, byte[] plain) throws GeneralSecurityException {
        byte[] iv = new byte[12];
        RNG.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        c.updateAAD(ByteBuffer.allocate(8).putLong(seq).array());
        byte[] ct = c.doFinal(plain);
        return ByteBuffer.allocate(8 + 12 + ct.length).putLong(seq).put(iv).put(ct).array();
    }

    public static long seqOf(byte[] pkt) {
        return pkt.length < 8 ? -1 : ByteBuffer.wrap(pkt).getLong();
    }

    public static byte[] decrypt(SecretKey key, byte[] pkt) throws GeneralSecurityException {
        if (pkt.length < 8 + 12 + 16)
            throw new GeneralSecurityException("Gói tin quá ngắn");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, pkt, 8, 12));
        c.updateAAD(pkt, 0, 8);
        return c.doFinal(pkt, 20, pkt.length - 20);
    }

    // ---- RSA-OAEP: bọc / mở khóa phiên ----
    static final String RSA = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    public static byte[] wrapKey(PublicKey pub, SecretKey k) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance(RSA);
        c.init(Cipher.ENCRYPT_MODE, pub);
        return c.doFinal(k.getEncoded());
    }

    public static SecretKey unwrapKey(PrivateKey priv, byte[] wrapped) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance(RSA);
        c.init(Cipher.DECRYPT_MODE, priv);
        byte[] raw = c.doFinal(wrapped);
        if (raw.length != 32)
            throw new GeneralSecurityException("Khóa AES phải dài 32 byte");
        return new SecretKeySpec(raw, "AES");
    }

    // ---- Đọc khóa RSA ----
    public static PublicKey loadPublic(InputStream is) throws Exception {
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(is.readAllBytes()));
    }

    public static PublicKey loadPublic(String file) throws Exception {
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(readKeyFile(file)));
    }

    public static PrivateKey loadPrivate(String file) throws Exception {
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(readKeyFile(file)));
    }

    private static byte[] readKeyFile(String file) throws IOException {
        Path p = Path.of(file);
        if (!Files.exists(p))
            throw new FileNotFoundException("Không thấy file khóa: " + p.toAbsolutePath().normalize()
                    + " (hãy chạy KeyGenTool trước)");
        return Files.readAllBytes(p);
    }

    // ---- Fingerprint = SHA-256 của public key, để so khớp bằng mắt giữa 2 máy
    // ----
    public static String fingerprint(PublicKey pub) throws GeneralSecurityException {
        byte[] fp = MessageDigest.getInstance("SHA-256").digest(pub.getEncoded());
        return HexFormat.ofDelimiter(":").formatHex(fp, 0, 8) + "...";
    }

    // In 4 byte đầu SHA-256 của khóa phiên: chỉ để minh họa "mỗi client một khóa
    // khác nhau"
    public static String fingerprintKey(SecretKey k) throws GeneralSecurityException {
        byte[] fp = MessageDigest.getInstance("SHA-256").digest(k.getEncoded());
        return HexFormat.of().formatHex(fp, 0, 4);
    }
}