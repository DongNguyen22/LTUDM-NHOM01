package com.musicfinder.controller;

import com.musicfinder.navigation.Navigator;
import com.musicfinder.navigation.Screen;
import com.musicfinder.network.ClientConnection;
import com.musicfinder.shared.enums.Action;
import com.musicfinder.shared.request.Request;
import com.musicfinder.shared.request.payload.RegisterPayload;

import java.io.IOException;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class RegisterController {

    @FXML
    private TextField fullNameField;
    @FXML
    private TextField usernameField;
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Button registerButton;
    @FXML
    private Label messageLabel;
    @FXML
    private Hyperlink loginLink;

    @FXML
    private void initialize() {
        registerButton.setOnAction(event -> {
            handleRegister();
        });
    }

    @FXML
    private void handleBackToLogin() {
        try {
            Navigator.goTo(Screen.LOGIN);
        } catch (IOException e) {
            messageLabel.setText("Không thể mở trang đăng nhập.");
        }
    }

    private void handleRegister() {
        messageLabel.setText("");
        String fullName = fullNameField.getText() == null ? "" : fullNameField.getText().trim();

        String userName = usernameField.getText() == null ? "" : usernameField.getText().trim();

        String passWord = passwordField.getText() == null ? "" : passwordField.getText();

        String confirmPassword = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();

        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        if (fullName.isEmpty()) {
            fullNameField.requestFocus();
            messageLabel.setText("Vui lòng điền thông tin!");
            StatusDialog.error(messageLabel.getScene().getWindow(), "Vui lòng điền thông tin!");
            return;
        }
        if (userName.isEmpty()) {
            usernameField.requestFocus();
            messageLabel.setText("Vui lòng điền thông tin!");
            StatusDialog.error(messageLabel.getScene().getWindow(), "Vui lòng điền thông tin!");
            return;
        }
        if (passWord.isEmpty()) {
            passwordField.requestFocus();
            messageLabel.setText("Vui lòng điền thông tin!");
            StatusDialog.error(messageLabel.getScene().getWindow(), "Vui lòng điền thông tin!");
            return;
        }
        if (confirmPassword.isEmpty()) {
            confirmPasswordField.requestFocus();
            messageLabel.setText("Vui lòng điền thông tin!");
            StatusDialog.error(messageLabel.getScene().getWindow(), "Vui lòng điền thông tin!");
            return;
        }
        if (email.isEmpty()) {
            emailField.requestFocus();
            messageLabel.setText("Vui lòng điền thông tin!");
            StatusDialog.error(messageLabel.getScene().getWindow(), "Vui lòng điền thông tin!");
            return;
        }
        if (!validateEmail(email)) {
            emailField.requestFocus();
            messageLabel.setText("Định dạng email chưa đúng, biểu mẫu: xxx@gmail.com");
            return;
        }
        if (!passWord.equals(confirmPassword)) {
            StatusDialog.error(messageLabel.getScene().getWindow(), "Xác thực pasword chưa khớp!");
            return;
        }
        RegisterPayload payload = new RegisterPayload(userName, passWord, fullName, email);
        Request request = Request.of(Action.REGISTER, payload);
        registerButton.setDisable(true);
        try {
            ClientConnection.getInstance().send(request, response -> {
                Platform.runLater(() -> {
                    if (!response.isSuccess()) {
                        registerButton.setDisable(false);
                        StatusDialog.error(messageLabel.getScene().getWindow(), response.getMessage());
                    } else {
                        StatusDialog.success(messageLabel.getScene().getWindow(), response.getMessage());
                        handleBackToLogin();
                    }
                });
            });
        } catch (Exception e) {
            // TODO: handle exception
            registerButton.setDisable(false);
            StatusDialog.error(messageLabel.getScene().getWindow(), e.getMessage());
        }
    }

    private boolean validateEmail(String email) {
        String regex = "^[a-zA-Z0-9._%+-]+@gmail\\.com$";

        return email.matches(regex);
    }
}
