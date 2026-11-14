package com.musicfinder.server.service;

import com.musicfinder.server.config.MailConfig;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OtpService {
    private record OtpEntry(String code, Instant expiresAt, int attempts) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 5;
    private static final long RESEND_COOLDOWN_SECONDS = 60;

    private final Map<String, OtpEntry> store = new ConcurrentHashMap<>();
    private final EmailService emailService;
    private final int expireMinutes = Integer.parseInt(MailConfig.get("otp.expire.minutes", "5"));

    public OtpService() {
        this(new EmailService());
    }

    public OtpService(EmailService emailService) {
        this.emailService = emailService;
    }

    public void sendOtp(String email) {
        if (email == null || !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new IllegalArgumentException("Email không hợp lệ");
        String key = email.trim().toLowerCase();

        OtpEntry old = store.get(key);
        if (old != null && Instant.now().isBefore(
                old.expiresAt().minusSeconds(expireMinutes * 60L - RESEND_COOLDOWN_SECONDS)))
            throw new IllegalArgumentException("Vui lòng đợi 60 giây trước khi gửi lại mã");

        String otp = String.format("%06d", RANDOM.nextInt(1_000_000)); // 000000 - 999999
        store.put(key, new OtpEntry(otp, Instant.now().plusSeconds(expireMinutes * 60L), 0));
        emailService.sendOtpEmail(key, otp, expireMinutes);
    }

    public boolean verifyOtp(String email, String otp) {
        if (email == null || otp == null)
            return false;
        String key = email.trim().toLowerCase();
        OtpEntry entry = store.get(key);
        if (entry == null)
            throw new IllegalArgumentException("Bạn chưa yêu cầu mã OTP");
        if (Instant.now().isAfter(entry.expiresAt())) {
            store.remove(key);
            throw new IllegalArgumentException("Mã OTP đã hết hạn");
        }
        if (entry.attempts() >= MAX_ATTEMPTS) {
            store.remove(key);
            throw new IllegalArgumentException("Nhập sai quá nhiều lần, vui lòng gửi lại mã");
        }
        if (!entry.code().equals(otp.trim())) {
            store.put(key, new OtpEntry(entry.code(), entry.expiresAt(), entry.attempts() + 1));
            throw new IllegalArgumentException("Mã OTP không chính xác");
        }
        store.remove(key);
        return true;
    }
}
