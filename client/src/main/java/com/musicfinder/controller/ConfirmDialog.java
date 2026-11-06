package com.musicfinder.controller;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.geometry.Insets;
import javafx.stage.StageStyle;

public final class ConfirmDialog {

        private ConfirmDialog() {
        }

        public static boolean show(Window owner, String title, String message) {

                Dialog<ButtonType> dialog = new Dialog<>();
                dialog.initStyle(StageStyle.UNDECORATED);
                dialog.setResizable(false);

                if (owner != null) {
                        dialog.initOwner(owner);
                        dialog.initModality(Modality.WINDOW_MODAL);
                } else {
                        dialog.initModality(Modality.APPLICATION_MODAL);
                }

                ButtonType confirmType = new ButtonType(
                                "Confirm",
                                ButtonBar.ButtonData.OK_DONE);

                ButtonType cancelType = new ButtonType(
                                "Cancel",
                                ButtonBar.ButtonData.CANCEL_CLOSE);

                DialogPane pane = dialog.getDialogPane();

                pane.getButtonTypes().addAll(
                                cancelType,
                                confirmType);

                pane.setPrefWidth(380);

                Label titleLabel = new Label(title);
                titleLabel.getStyleClass().add("confirm-title");

                Label messageLabel = new Label(message);
                messageLabel.setWrapText(true);
                messageLabel.getStyleClass().add("confirm-message");

                VBox content = new VBox(
                                12,
                                titleLabel,
                                messageLabel);

                content.setPadding(new Insets(10));

                pane.setContent(content);

                // CSS
                pane.getStylesheets().add(
                                ConfirmDialog.class.getResource("/com/musicfinder/css/confirm-dialog.css")
                                                .toExternalForm());

                pane.getStyleClass().add("confirm-dialog");

                Button confirmButton = (Button) pane.lookupButton(confirmType);

                Button cancelButton = (Button) pane.lookupButton(cancelType);

                confirmButton.getStyleClass().add("confirm-btn");
                cancelButton.getStyleClass().add("cancel-btn");

                // Chờ người dùng lựa chọn
                return dialog.showAndWait()
                                .map(result -> result == confirmType)
                                .orElse(false);
        }
}