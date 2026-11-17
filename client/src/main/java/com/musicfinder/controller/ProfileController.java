package com.musicfinder.controller;

import com.musicfinder.network.ClientConnection;
import com.musicfinder.shared.enums.Action;
import com.musicfinder.shared.request.Request;
import com.musicfinder.shared.request.payload.EditProfilePayload;
import com.musicfinder.shared.request.payload.ChangePasswordProfilePayload;
import com.musicfinder.shared.response.Response;
import com.musicfinder.shared.response.payload.UserData;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.stage.FileChooser;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Base64;
import java.util.function.Consumer;

public class ProfileController {
    @FXML
    private Label profileMessageLabel, fullNameLabel, fullNameValueLabel, emailLabel, avatarInitialsLabel;
    @FXML
    private ImageView avatarImageView;
    @FXML
    private TextField fullNameField, emailField;
    @FXML
    private PasswordField currentPasswordField, newPasswordField;
    @FXML
    private Button editProfileButton, saveChangesButton;
    private UserData userData;
    private Consumer<UserData> onProfileUpdated;

    public void setOnProfileUpdated(Consumer<UserData> onProfileUpdated) {
        this.onProfileUpdated = onProfileUpdated;
    }
    private boolean savingProfile;
    private boolean editingProfile = false;
    private boolean changingPassword;
    private String selectedAvatarBase64;
    @FXML
    private Button chooseAvatar;

    @FXML
    private void initialize() {
        setEditingProfile(false);
        getInfo();
        saveChangesButton.setOnAction(event -> {
            handleSaveProfile();
        });
    }

    private void getInfo() {
        showMessage("Đang tải hồ sơ...");
        try {
            Request request = Request.of(Action.GET_PROFILE, null);
            ClientConnection.getInstance().send(request, response -> {
                Platform.runLater(() -> {
                    if (!response.isSuccess()) {
                        showMessage(null);
                        return;
                    } else {
                        userData = response.dataAs(UserData.class);
                        if (userData == null) {
                            showMessage("");
                            return;
                        } else {
                            showInfo();
                            showMessage("");
                        }
                    }
                });
            });
        } catch (Exception e) {
            // TODO: handle exception
            showMessage(e.getMessage());
        }
    }

    private String displayValue(String value) {
        return value == null || value.isBlank() ? "Chưa cập nhật" : value.trim();
    }

    private void showMessage(String message) {
        String text = message == null ? "Yêu cầu thất bại." : message;
        profileMessageLabel.setText(text);
        profileMessageLabel.setVisible(!text.isBlank());
        profileMessageLabel.setManaged(!text.isBlank());
    }

    private void loadImage(String avatarUrl) {
        loadDefaultAvatar();
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return;
        }
        try {
            String value = avatarUrl.trim();
            Image image = value.startsWith("data:image/")
                    ? new Image(new ByteArrayInputStream(Base64.getDecoder().decode(
                            value.substring(value.indexOf(',') + 1))))
                    : new Image(value, true);
            avatarImageView.setImage(image);
            avatarInitialsLabel.setVisible(false);
            image.errorProperty().addListener((observable, oldValue, error) -> {
                if (error && avatarImageView.getImage() == image) {
                    loadDefaultAvatar();
                }
            });
            if (image.isError()) {
                loadDefaultAvatar();
            }
        } catch (IllegalArgumentException e) {
            loadDefaultAvatar();
        }
    }

    private void loadDefaultAvatar() {
        URL resource = getClass().getResource("/com/musicfinder/assets/minion.jpg");
        Image image = resource == null ? null : new Image(resource.toExternalForm());
        avatarImageView.setImage(image);
        avatarInitialsLabel.setVisible(image == null || image.isError());
    }

    private void showInfo() {
        loadImage(userData.avatarUrl());
        fullNameLabel.setText(displayValue(userData.fullName()));
        fullNameValueLabel.setText(displayValue(userData.fullName()));
        emailLabel.setText(displayValue(userData.email()));
        saveChangesButton.setVisible(false);
        saveChangesButton.setManaged(false);
    }

    @FXML
    private void handleEditProfile() {
        if (userData == null) {
            showMessage("Chưa tải được thông tin hồ sơ.");
            return;
        }

        if (!editingProfile) {
            fullNameField.setText(userData.fullName());
            emailField.setText(userData.email());
        }

        editingProfile = !editingProfile;
        selectedAvatarBase64 = null;
        setEditingProfile(editingProfile);

        if (!editingProfile) {
            loadImage(userData.avatarUrl());
        }
    }

    @FXML
    private void handleChooseAvatar() {
        String avatarBase64 = chooseAvatar();
        if (avatarBase64 != null) {
            selectedAvatarBase64 = avatarBase64;
            loadImage(selectedAvatarBase64);
            showMessage("");
        }
    }

    private String chooseAvatar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh đại diện");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Ảnh", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File selectedFile = chooser.showOpenDialog(avatarImageView.getScene().getWindow());
        if (selectedFile == null) {
            return null;
        }

        try {
            Image selectedImage = new Image(selectedFile.toURI().toString());
            if (selectedImage.isError()) {
                showMessage("Không thể đọc ảnh đã chọn.");
                return null;
            }

            if (Files.size(selectedFile.toPath()) > 5 * 1024 * 1024) {
                showMessage("Dung lượng ảnh vượt quá 5MB.");
                return null;
            }
            byte[] imageBytes = Files.readAllBytes(selectedFile.toPath());
            String fileName = selectedFile.getName().toLowerCase(Locale.ROOT);
            String mimeType;
            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                mimeType = "image/jpeg";
            } else if (fileName.endsWith(".gif")) {
                mimeType = "image/gif";
            } else if (fileName.endsWith(".bmp")) {
                mimeType = "image/bmp";
            } else {
                mimeType = "image/png";
            }
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            showMessage("Không thể đọc ảnh đại diện: " + e.getMessage());
            return null;
        }
    }

    @FXML
    private void handleChangePassword() {
        String currentPassword = currentPasswordField.getText() == null ? "" : currentPasswordField.getText();
        String newPassword = newPasswordField.getText() == null ? "" : newPasswordField.getText();
        ChangePasswordProfilePayload payload = new ChangePasswordProfilePayload(currentPassword, newPassword);
        Request request = Request.of(Action.CHANGE_PASSWORD, payload);
        try {
            ClientConnection.getInstance().send(request, response -> {
                Platform.runLater(() -> {
                    if (!response.isSuccess()) {
                        StatusDialog.error(fullNameField.getScene().getWindow(), response.getMessage());
                        return;
                    } else {
                        StatusDialog.success(fullNameField.getScene().getWindow(), response.getMessage());
                    }
                });
            });
        } catch (Exception e) {
            // TODO: handle exception
            StatusDialog.error(fullNameField.getScene().getWindow(), e.getMessage());
            return;
        }
    }

    private void handleSaveProfile() {
        String fullName = fullNameField.getText() == null ? "" : fullNameField.getText();
        String email = emailField.getText() == null ? "" : emailField.getText();
        if (fullName.isBlank()) {
            fullNameField.requestFocus();
            StatusDialog.error(fullNameField.getScene().getWindow(), "Vui lòng nhập fullname");
            return;
        }
        if (email.isBlank()) {
            emailField.requestFocus();
            StatusDialog.error(emailField.getScene().getWindow(), "Vui lòng nhập email");
            return;
        }
        if (!validateEmail(email)) {
            StatusDialog.error(fullNameField.getScene().getWindow(), "Định dạng email chưa đúng! xxx@gmail.com");
        }
        EditProfilePayload payload = new EditProfilePayload(fullName, email,
                selectedAvatarBase64 == null ? userData.avatarUrl() : selectedAvatarBase64);
        Request request = Request.of(Action.UPDATE_PROFILE, payload);
        saveChangesButton.setDisable(false);
        try {
            ClientConnection.getInstance().send(request, response -> {
                Platform.runLater(() -> {
                    if (!response.isSuccess()) {
                        StatusDialog.error(fullNameField.getScene().getWindow(), response.getMessage());
                    } else {
                        UserData updatedUser = response.dataAs(UserData.class);
                        if (updatedUser == null) {
                            StatusDialog.error(
                                    fullNameField.getScene().getWindow(),
                                    "Không nhận được thông tin hồ sơ sau khi lưu.");
                            return;
                        }
                        userData = updatedUser;
                        if (onProfileUpdated != null) {
                            onProfileUpdated.accept(updatedUser);
                        }
                        showInfo();
                        editingProfile = false;
                        setEditingProfile(editingProfile);
                        StatusDialog.success(fullNameField.getScene().getWindow(), response.getMessage());
                    }
                });
            });
        } catch (Exception e) {
            // TODO: handle exception
            StatusDialog.error(fullNameField.getScene().getWindow(), e.getMessage());
        }
    }

    private void setEditingProfile(boolean edit) {
        chooseAvatar.setDisable(!edit);
        chooseAvatar.setVisible(edit);
        chooseAvatar.setManaged(edit);

        fullNameField.setDisable(!edit);
        fullNameField.setVisible(edit);
        fullNameField.setManaged(edit);

        fullNameValueLabel.setVisible(!edit);
        fullNameValueLabel.setManaged(!edit);

        emailField.setDisable(!edit);
        emailField.setVisible(edit);
        emailField.setManaged(edit);

        emailLabel.setVisible(!edit);
        emailLabel.setManaged(!edit);

        editProfileButton.setText(edit ? "Cancel Edit" : "Edit Profile");
        saveChangesButton.setVisible(edit);
        saveChangesButton.setManaged(edit);
        saveChangesButton.setDisable(!edit);
    }

    private boolean validateEmail(String email) {
        String regex = "^[a-zA-Z0-9._%+-]+@gmail\\.com$";

        return email.matches(regex);
    }
}
