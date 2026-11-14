package com.musicfinder.controller;

import com.musicfinder.navigation.Navigator;
import com.musicfinder.navigation.Screen;
import com.musicfinder.network.ClientConnection;
import com.musicfinder.player.PlayerListener;
import com.musicfinder.shared.enums.Action;
import com.musicfinder.shared.request.Request;
import com.musicfinder.shared.request.payload.SendOtpPayload;
import com.musicfinder.shared.request.payload.UpdatePasswordPayload;
import com.musicfinder.shared.request.payload.VerifyOtpPayload;
import com.musicfinder.shared.response.Response;

import java.io.IOException;
import java.util.function.Consumer;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class ForgotPasswordController {
    private enum Step {
        EMAIL, OTP, NEW_PASSWORD
    }

    @FXML
    private StackPane loadingOverlay;

    private PlayerListener listener;

    public void setListener(PlayerListener controller) {
        listener = controller;
    }

    @FXML
    private TextField emailField;
    @FXML
    private TextField otpField;
    @FXML
    private PasswordField newPasswordField;

    @FXML
    private VBox emailSection;
    @FXML
    private VBox otpSection;
    @FXML
    private VBox newPasswordSection;

    @FXML
    private Button sendButton;
    @FXML
    private Label messageLabel;
    private Step step = Step.EMAIL;

    private void setLoading(boolean loading) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> setLoading(loading));
            return;
        }
        loadingOverlay.setVisible(loading);
        loadingOverlay.setManaged(loading);
        if (listener != null) {
            listener.onLoadingChanged(loading);
        }
    }

    @FXML
    private void initialize() {
        toggle(Step.EMAIL);
    }

    @FXML
    private void handleSend() {
        switch (step) {
            case EMAIL:
                String email = emailField.getText().trim();
                if (!validateEmail(email)) {

                    sendButton.setDisable(false);
                    StatusDialog.error(emailField.getScene().getWindow(),
                            "Định dạng email không hợp lệ! xxx@gmail.com");
                    break;
                }
                SendOtpPayload payload = new SendOtpPayload(email);
                Request request = Request.of(Action.SEND_OTP, payload);
                sendButton.setDisable(true);
                setLoading(true);
                try {
                    ClientConnection.getInstance().send(request, response -> {
                        Platform.runLater(() -> {
                            setLoading(false);
                            sendButton.setDisable(false);
                            if (!response.isSuccess()) {
                                StatusDialog.error(emailField.getScene().getWindow(), response.getMessage());
                                return;
                            }
                            emailField.setText(email);
                            this.step = Step.OTP;
                            toggle(Step.OTP);
                        });
                    });
                } catch (Exception e) {

                    sendButton.setDisable(false);
                    setLoading(false);
                    // TODO: handle exception
                    StatusDialog.error(emailField.getScene().getWindow(), e.getMessage());
                }
                break;
            case OTP:
                String otp = otpField.getText();
                if (otp.isBlank() || otp == null) {
                    showMessage("Vui lòng điền OTP", false);
                    sendButton.setDisable(false);
                    break;
                }
                otp = otp.trim();
                VerifyOtpPayload otpPayload = new VerifyOtpPayload(emailField.getText(), otp);
                Request otpRequest = Request.of(Action.VERIFY_OTP, otpPayload);
                sendButton.setDisable(true);
                setLoading(true);
                try {
                    ClientConnection.getInstance().send(otpRequest, response -> {
                        Platform.runLater(() -> {

                            sendButton.setDisable(false);
                            setLoading(false);
                            if (!response.isSuccess()) {
                                StatusDialog.error(emailField.getScene().getWindow(), response.getMessage());
                                return;
                            }
                            this.step = Step.NEW_PASSWORD;
                            toggle(Step.NEW_PASSWORD);
                            StatusDialog.success(emailField.getScene().getWindow(), response.getMessage());
                        });
                    });
                } catch (Exception e) {
                    // TODO: handle exception
                    sendButton.setDisable(false);
                    setLoading(false);
                    StatusDialog.error(emailField.getScene().getWindow(), e.getMessage());
                }
                break;
            case NEW_PASSWORD:
                String password = newPasswordField.getText();
                if (password.isBlank() || password == null) {
                    showMessage("Vui lòng điền Password", false);
                    sendButton.setDisable(false);
                    break;
                }
                password = password.trim();
                UpdatePasswordPayload passwordPayload = new UpdatePasswordPayload(emailField.getText(), password);
                Request passwordRequest = Request.of(Action.UPDATE_PASSWORD, passwordPayload);
                sendButton.setDisable(true);
                setLoading(true);
                try {
                    ClientConnection.getInstance().send(passwordRequest, response -> {
                        Platform.runLater(() -> {
                            setLoading(false);
                            if (!response.isSuccess()) {
                                StatusDialog.error(emailField.getScene().getWindow(), response.getMessage());
                                return;
                            }
                            this.step = Step.NEW_PASSWORD;
                            toggle(Step.NEW_PASSWORD);
                            StatusDialog.success(emailField.getScene().getWindow(), response.getMessage());
                        });
                    });
                } catch (Exception e) {
                    // TODO: handle exception
                    sendButton.setDisable(false);
                    setLoading(false);
                    StatusDialog.error(emailField.getScene().getWindow(), e.getMessage());
                }
                break;

            default:
                break;
        }
    }

    @FXML
    private void handleBackToLogin() {
        try {
            Navigator.goTo(Screen.LOGIN);
            newPasswordField.clear();
        } catch (IOException e) {
            showMessage("Không thể mở trang đăng nhập.", false);
            e.printStackTrace();
        }
    }

    private void showMessage(String message, boolean success) {
        messageLabel.setText(message);
        messageLabel.getStyleClass().removeAll("message-success", "message-error");
        messageLabel.getStyleClass().add(success ? "message-success" : "message-error");
        messageLabel.setVisible(true);
        messageLabel.setManaged(true);
    }

    private boolean validateEmail(String email) {
        String regex = "^[a-zA-Z0-9._%+-]+@gmail\\.com$";

        return email.matches(regex);
    }

    private void toggle(Step step) {
        emailSection.setVisible(this.step == step.EMAIL ? true : false);
        emailSection.setManaged(this.step == step.EMAIL ? true : false);
        otpSection.setVisible(this.step == step.OTP ? true : false);
        otpSection.setManaged(this.step == step.OTP ? true : false);
        newPasswordSection.setVisible(this.step == step.NEW_PASSWORD ? true : false);
        newPasswordSection.setManaged(this.step == step.NEW_PASSWORD ? true : false);
        sendButton.setText(switch (step) {
            case EMAIL -> "Gửi OTP";
            case OTP -> "Xác thực OTP";
            case NEW_PASSWORD -> "Đặt lại mật khẩu";
        });
    }
}
