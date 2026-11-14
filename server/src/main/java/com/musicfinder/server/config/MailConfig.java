package com.musicfinder.server.config;

import java.io.InputStream;
import java.util.Properties;

import org.springframework.mail.javamail.JavaMailSenderImpl;

public class MailConfig {
    private static final Properties props = new Properties();
    private static final JavaMailSenderImpl mailSender;

    static {
        try (InputStream is = MailConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (is != null)
                props.load(is);
        } catch (Exception e) {
            // TODO: handle exception
            System.err.println("Không load được cấu hình mail");
        }

        mailSender = new JavaMailSenderImpl();
        mailSender.setHost(props.getProperty("mail.host", "smtp.gmail.com"));
        mailSender.setPort(Integer.parseInt(props.getProperty("mail.port", "587")));
        mailSender.setUsername(props.getProperty("mail.username"));
        mailSender.setPassword(props.getProperty("mail.password"));
        mailSender.setDefaultEncoding("UTF-8");

        Properties javaMail = mailSender.getJavaMailProperties();
        javaMail.put("mail.transport.protocol", "smtp");
        javaMail.put("mail.smtp.auth", "true");
        javaMail.put("mail.smtp.starttls.enable", "true");
        javaMail.put("mail.smtp.connectiontimeout", "10000");
        javaMail.put("mail.smtp.timeout", "10000");
    }

    public static JavaMailSenderImpl getMailSender() {
        return mailSender;
    }

    public static String get(String key, String def) {
        return props.getProperty(key, def);
    }
}
