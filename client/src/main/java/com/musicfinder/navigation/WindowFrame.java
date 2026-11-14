package com.musicfinder.navigation;

import com.musicfinder.MainApp;
import com.musicfinder.network.ClientConnection;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Khung cửa sổ tự vẽ cho Stage UNDECORATED:
 * thanh tiêu đề (kéo để di chuyển, double-click để phóng to) + nút thu nhỏ / phóng to / đóng.
 * Nội dung từng màn hình được đặt vào giữa bằng setContent().
 */
public class WindowFrame extends BorderPane {

    private static final String ICON_MINIMIZE = "M5 12h14";
    private static final String ICON_MAXIMIZE = "M5 5h14v14H5z";
    private static final String ICON_RESTORE = "M8 8h11v11H8z M5 16V5h11";
    private static final String ICON_CLOSE = "M6 6l12 12 M18 6L6 18";

    private final Stage stage;
    private final StackPane contentPane = new StackPane();
    private final SVGPath maximizeIcon = new SVGPath();

    // Vị trí kéo
    private double dragOffsetX;
    private double dragOffsetY;

    // Lưu kích thước trước khi phóng to để khôi phục
    private boolean maximized;
    private double restoreX, restoreY, restoreW, restoreH;

    public WindowFrame(Stage stage) {
        this.stage = stage;
        getStyleClass().add("window-frame");
        getStylesheets().add(MainApp.class.getResource("/com/musicfinder/css/window-frame.css").toExternalForm());
        // Giữ nội dung (đặc biệt các hình trang trí ở login) trong vùng bên dưới title bar.
        contentPane.setMinSize(0, 0);
        Rectangle contentClip = new Rectangle();
        contentClip.widthProperty().bind(contentPane.widthProperty());
        contentClip.heightProperty().bind(contentPane.heightProperty());
        contentPane.setClip(contentClip);
        setCenter(contentPane);
        HBox titleBar = createTitleBar();
        titleBar.setViewOrder(-1);
        setTop(titleBar);
    }

    public void setContent(Parent content) {
        contentPane.getChildren().setAll(content);
    }

    public Parent getContent() {
        return contentPane.getChildren().isEmpty() ? null : (Parent) contentPane.getChildren().get(0);
    }

    // ===================== Thanh tiêu đề =====================

    private HBox createTitleBar() {
        Label title = new Label("Music Finder");
        title.getStyleClass().add("window-title");
        title.textProperty().bind(stage.titleProperty());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button minimize = createButton(ICON_MINIMIZE, "window-btn");
        minimize.setOnAction(e -> stage.setIconified(true));

        maximizeIcon.setContent(ICON_MAXIMIZE);
        Button maximize = createButton(maximizeIcon, "window-btn");
        maximize.setOnAction(e -> toggleMaximize());

        Button close = createButton(ICON_CLOSE, "window-btn", "window-btn-close");
        close.setOnAction(e -> closeApp());

        HBox bar = new HBox(title, spacer, minimize, maximize, close);
        bar.getStyleClass().add("window-title-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 0, 14));
        bar.setMinHeight(32);
        bar.setPrefHeight(32);
        bar.setMaxHeight(32);

        // Kéo để di chuyển cửa sổ
        bar.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            dragOffsetX = e.getScreenX() - stage.getX();
            dragOffsetY = e.getScreenY() - stage.getY();
        });
        bar.setOnMouseDragged(e -> {
            if (!e.isPrimaryButtonDown()) {
                return;
            }
            if (maximized) {
                // Đang phóng to mà kéo -> khôi phục kích thước cũ, giữ con trỏ ở vị trí tương ứng
                double ratio = dragOffsetX / stage.getWidth();
                toggleMaximize();
                dragOffsetX = stage.getWidth() * ratio;
            }
            stage.setX(e.getScreenX() - dragOffsetX);
            stage.setY(e.getScreenY() - dragOffsetY);
        });
        // Double-click để phóng to / khôi phục
        bar.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                toggleMaximize();
            }
        });
        return bar;
    }

    private Button createButton(String iconPath, String... styleClasses) {
        SVGPath icon = new SVGPath();
        icon.setContent(iconPath);
        return createButton(icon, styleClasses);
    }

    private Button createButton(SVGPath icon, String... styleClasses) {
        icon.getStyleClass().add("window-btn-icon");
        Group graphic = new Group(icon);
        graphic.setScaleX(0.55);
        graphic.setScaleY(0.55);
        Button button = new Button();
        button.setGraphic(graphic);
        button.getStyleClass().addAll(styleClasses);
        button.setFocusTraversable(false);
        // Nút vẫn xử lý action, nhưng không truyền thao tác kéo lên title bar.
        button.setOnMousePressed(e -> e.consume());
        button.setOnMouseDragged(e -> e.consume());
        button.setOnMouseClicked(e -> e.consume());
        return button;
    }

    // ===================== Hành động =====================

    // Phóng to theo vùng làm việc (không che taskbar) — stage.setMaximized() với UNDECORATED sẽ che taskbar
    private void toggleMaximize() {
        if (!maximized) {
            restoreX = stage.getX();
            restoreY = stage.getY();
            restoreW = stage.getWidth();
            restoreH = stage.getHeight();

            Screen screen = Screen.getScreensForRectangle(restoreX, restoreY, restoreW, restoreH)
                    .stream().findFirst().orElse(Screen.getPrimary());
            Rectangle2D bounds = screen.getVisualBounds();
            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());
            maximizeIcon.setContent(ICON_RESTORE);
        } else {
            stage.setX(restoreX);
            stage.setY(restoreY);
            stage.setWidth(restoreW);
            stage.setHeight(restoreH);
            maximizeIcon.setContent(ICON_MAXIMIZE);
        }
        maximized = !maximized;
    }

    // Đóng kết nối server rồi thoát hẳn chương trình
    public static void closeApp() {
        try {
            ClientConnection.getInstance().close();
        } catch (Exception ignored) {
            // đang thoát, bỏ qua lỗi đóng socket
        }
        Platform.exit();
        System.exit(0);
    }
}
