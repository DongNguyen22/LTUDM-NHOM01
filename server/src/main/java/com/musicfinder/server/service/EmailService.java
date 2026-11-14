package com.musicfinder.server.service;

import com.musicfinder.server.config.MailConfig;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

@Slf4j
public class EmailService {
    private final JavaMailSender mailSender = MailConfig.getMailSender();
    private final String from = MailConfig.get("mail.from", MailConfig.get("mail.username", ""));

    public void sendOtpEmail(String to, String otp, int expireMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("Mã xác thực OTP - Music Finder");
            helper.setText("""
                    <div style="font-family:Arial,sans-serif">
                      <p>Mã OTP của bạn là:</p>
                      <h2 style="letter-spacing:6px;color:#1db954">%s</h2>
                      <p>Mã có hiệu lực trong <b>%d phút</b>. Không chia sẻ mã này cho bất kỳ ai.</p>
                    </div>
                    """.formatted(otp, expireMinutes), true); // true = HTML
            mailSender.send(message);
            log.info("Đã gửi OTP tới {}", to);
        } catch (Exception e) {
            log.error("Gửi email thất bại tới {}: {}", to, e.getMessage());
            throw new IllegalStateException("Không gửi được email, vui lòng thử lại sau");
        }
    }
}
