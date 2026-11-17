package com.musicfinder.server.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.UUID;

import com.musicfinder.server.model.Users;
import com.musicfinder.server.repository.UserRepository;
import com.musicfinder.server.util.PasswordHash;
import com.musicfinder.server.util.DtoMapper;
import com.musicfinder.shared.request.payload.ChangePasswordProfilePayload;
import com.musicfinder.shared.request.payload.EditProfilePayload;
import com.musicfinder.shared.request.payload.LoginPayload;
import com.musicfinder.shared.request.payload.RegisterPayload;
import com.musicfinder.shared.request.payload.UpdatePasswordPayload;
import com.musicfinder.shared.response.payload.UserData;
import com.musicfinder.server.network.AudioStreamServer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AuthService {
    private final UserRepository userRepository;

    public AuthService() {
        this.userRepository = new UserRepository();
    }

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Đăng ký
    public UserData register(RegisterPayload payload) {
        if (payload == null)
            throw new IllegalArgumentException("Dữ liệu đăng kí không được để trống");
        if (payload.username() == null || payload.username().trim().length() < 3)
            throw new IllegalArgumentException("Tên đăng nhập phải ít nhất 3 kí tự!");
        if (payload.password() == null || payload.password().trim().length() < 6)
            throw new IllegalArgumentException("Mật khẩu phải có ít nhất 6 kí tự!");
        if (payload.email() != null && !payload.email().isBlank()
                && !payload.email().matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
            throw new IllegalArgumentException("Định dạng email không hợp lệ");
        }

        String username = payload.username().trim();
        String email = (payload.email() != null && !payload.email().isBlank() ? payload.email().trim() : null);

        // Kiểm tra user
        if (userRepository.existsByUsername(username))
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tài, vui lòng chọn tên khác!");
        if (email != null && userRepository.existByEmail(email))
            throw new IllegalArgumentException("Email này đã được sử dụng!");

        String hashedPassword = PasswordHash.hashPassword(payload.password());
        Users newUser = Users.builder()
                .username(username)
                .email(email)
                .passwordHash(hashedPassword)
                .fullName(payload.fullName() != null ? payload.fullName().trim() : username)
                .avatarUrl(null)
                .build();
        Users saveUser = userRepository.save(newUser);
        log.info("Người dùng đã đăng kí thành công: {}", saveUser.getUsername());
        return DtoMapper.toUserData(saveUser);
    }

    // Đăng nhập
    public UserData login(LoginPayload payload) {
        if (payload == null || payload.usernameOrEmail() == null || payload.usernameOrEmail().isBlank())
            throw new IllegalArgumentException("Vui lòng nhập tên đăng nhập hoặc email");
        if (payload.password() == null || payload.password().isEmpty())
            throw new IllegalArgumentException("Vui lòng nhập mật khẩu!");
        String str = payload.usernameOrEmail().trim();

        // Tìm tài khoản
        Users user = userRepository.findByUsernameOrEmail(str)
                // Kiểm tra opt.empty()
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản hoặc mật khẩu không chính xác"));
        ;
        if (!PasswordHash.verifyPassword(payload.password(), user.getPasswordHash()))
            throw new IllegalArgumentException("Tài khoản hoặc mật khẩu không chính xác");
        return DtoMapper.toUserData(user);
    }

    // Lấy thông tin profile
    public UserData getProfile(String username) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại" + username));
        return DtoMapper.toUserData(user);
    }

    // Lấy thông tin người dùng theo email
    public UserData getUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập email");
        }
        Users user = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng với email này"));
        return DtoMapper.toUserData(user);
    }

    public UserData getById(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại" + userId));
        return DtoMapper.toUserData(user);
    }

    // Cập nhật hồ sơ
    public UserData updateProfile(String username, EditProfilePayload payload) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại" + username));
        if (payload.fullName() != null && !payload.fullName().isBlank()) {
            user.setFullName(payload.fullName().trim());
        }
        if (payload.email() != null && !payload.email().isBlank()) {
            String newEmail = payload.email();
            if (userRepository.existByEmail(newEmail) && !newEmail.equals(user.getEmail()))
                throw new IllegalArgumentException("Email đã tồn tại!");
            user.setEmail(newEmail);
        }
        if (payload.avatarUrl() != null && !payload.avatarUrl().isBlank()) {
            user.setAvatarUrl(saveAvatar(user.getUserId(), payload.avatarUrl()));
        }
        userRepository.update(user);
        return DtoMapper.toUserData(user);
    }

    // Cập nhật mật khẩu theo email sau khi đã xác thực quyền đặt lại mật khẩu.
    public boolean updatePassword(UpdatePasswordPayload payload) {
        if (payload == null || payload.email() == null || payload.email().isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập email");
        }
        if (payload.password() == null || payload.password().trim().length() < 6) {
            throw new IllegalArgumentException("Mật khẩu mới phải có ít nhất 6 ký tự!");
        }
        Users user = userRepository.findByEmail(payload.email().trim())
                .orElseThrow(() -> new IllegalArgumentException("Email không tồn tại trong hệ thống!"));
        String newHash = PasswordHash.hashPassword(payload.password().trim());
        return userRepository.updatePassword(user.getUserId(), newHash);
    }

    // Đổi mật khẩu
    public boolean changePassword(String username, ChangePasswordProfilePayload payload) {
        String newPassword = payload.newPassword();
        if (newPassword == null || newPassword.trim().length() < 6) {
            throw new IllegalArgumentException("Mật khẩu mới phải có ít nhất 6 ký tự!");
        }
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng " + username));
        if (!PasswordHash.verifyPassword(payload.currentPassword(), user.getPasswordHash()))
            throw new IllegalArgumentException("mật khẩu cũ không chính xác");
        String newHash = PasswordHash.hashPassword(newPassword.trim());

        // cập nhật vào DB
        boolean updated = userRepository.updatePassword(user.getUserId(), newHash);
        if (updated)
            log.info("Người dùng [{}] đã đổi mật khẩu thành công ", username);
        return updated;
    }

    // Save avatar
     
    private String saveAvatar(Long userId, String avatarData) {
        String trimmed = avatarData.trim();
        // Nếu client gửi URL thường (không phải Base64) -> giữ nguyên
        if (!trimmed.startsWith("data:image/")) {
            return trimmed;
        }

        try {
            int commaIndex = trimmed.indexOf(',');
            if (commaIndex == -1) {
                throw new IllegalArgumentException("Dữ liệu ảnh Base64 không đúng định dạng.");
            }
            String header = trimmed.substring(0, commaIndex).toLowerCase();
            String base64Payload = trimmed.substring(commaIndex + 1);

            // Xác định phần mở rộng file (.png, .jpg, .webp)
            String extension = ".png";
            if (header.contains("image/jpeg") || header.contains("image/jpg")) {
                extension = ".jpg";
            } else if (header.contains("image/webp")) {
                extension = ".webp";
            }

            // Decode Base64 -> byte[]
            byte[] imageBytes = Base64.getDecoder().decode(base64Payload);
            if (imageBytes.length == 0) {
                throw new IllegalArgumentException("Ảnh rỗng.");
            }
            if (imageBytes.length > 5 * 1024 * 1024) { // Chặn file > 5MB
                throw new IllegalArgumentException("Dung lượng ảnh vượt quá 5MB.");
            }

            // Tạo thư mục storage/avatar nếu chưa có
            File avatarDir = new File(System.getProperty("music.avatar.directory", "storage/avatar"));
            if (!avatarDir.exists()) {
                avatarDir.mkdirs();
            }

            // 1. Tên file CỐ ĐỊNH theo User ID: avatar_user_1.png
            String fileName = "avatar_user_" + userId + extension;

            // 2. Dọn file avatar cũ cùng user nếu khác định dạng (vd: trước là .jpg, nay up .png)
            File[] oldFiles = avatarDir.listFiles((dir, name) ->
                    name.startsWith("avatar_user_" + userId + ".") && !name.equals(fileName));
            if (oldFiles != null) {
                for (File old : oldFiles) {
                    if (old.delete()) {
                        log.info("Đã xóa file avatar định dạng cũ: {}", old.getName());
                    }
                }
            }

            // 3. Ghi đè file vật lý (nếu cùng tên sẽ tự động ghi đè, không tạo file mới)
            File targetFile = new File(avatarDir, fileName);
            Files.write(targetFile.toPath(), imageBytes);
            log.info("Đã lưu avatar cho user [{}]: {}", userId, targetFile.getAbsolutePath());

            // 4. Trả về URL có tham số phiên bản ?v=<timestamp> để chống kẹt cache ở Client/Trình duyệt
            return AudioStreamServer.getAvatarUrl(fileName) + "?v=" + System.currentTimeMillis();

        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ảnh đại diện không hợp lệ: " + e.getMessage());
        } catch (IOException e) {
            log.error("Lỗi ghi file avatar: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi hệ thống khi lưu ảnh đại diện.", e);
        }
    }

}
