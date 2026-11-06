package com.musicfinder.controller;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.Objects;

public final class StatusDialog {

    private StatusDialog() {
    }

    // Thông báo thành công
    public static void success(Window owner, String message) {
        show(owner, true, message, 2.5);
    }

    // Thông báo thất bại
    public static void error(Window owner, String message) {
        show(owner, false, message, 4);
    }

    // Hiển thị thông báo với thời gian tùy chỉnh
    public static void show(
            Window owner,
            boolean success,
            String message,
            double seconds) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(owner, success, message, seconds));
            return;
        }

        Stage stage = new Stage();

        if (owner != null) {
            stage.initOwner(owner);
        }

        stage.initStyle(StageStyle.TRANSPARENT);

        // Tạo nội dung
        VBox content = createContent(success, message);

        Scene scene = new Scene(content);
        scene.setFill(Color.TRANSPARENT);

        scene.getStylesheets().add(
                Objects.requireNonNull(
                        StatusDialog.class.getResource(
                                "/com/musicfinder/css/status-dialog.css"),
                        "Cannot find status-dialog.css").toExternalForm());

        stage.setScene(scene);

        // Đưa thông báo lên phía trên, giữa cửa sổ cha
        stage.setOnShown(event -> {
            if (owner != null) {
                stage.setX(
                        owner.getX()
                                + (owner.getWidth() - stage.getWidth()) / 2);

                stage.setY(owner.getY() + 35);
            }
        });

        // Tự đóng
        PauseTransition timer = new PauseTransition(
                Duration.seconds(Math.max(0.1, seconds)));

        timer.setOnFinished(event -> stage.close());

        stage.setOnHidden(event -> timer.stop());

        stage.show();
        timer.play();
    }

    // Tạo giao diện
    private static VBox createContent(
            boolean success,
            String message) {
        Label icon = new Label(success ? "✓" : "✕");
        icon.getStyleClass().add("status-icon");

        Label title = new Label(
                success ? "Success!" : "Something went wrong!");
        title.getStyleClass().add("status-title");

        Label description = new Label(message);
        description.setWrapText(true);
        description.setMaxWidth(290);
        description.getStyleClass().add("status-message");

        VBox texts = new VBox(5, title, description);

        HBox row = new HBox(14, icon, texts);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(row);
        content.setPadding(new Insets(18));
        content.setPrefWidth(370);
        content.setMaxWidth(370);

        content.getStyleClass().add("status-dialog");

        content.getStyleClass().add(
                success ? "status-success" : "status-error");

        return content;
    }
}