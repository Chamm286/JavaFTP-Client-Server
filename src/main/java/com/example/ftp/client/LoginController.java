package com.example.ftp.client;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class LoginController {

    public interface LoginSuccessListener {
        void onSuccess(String fullname, String role, String host, int port, String username,
                       FTPClientSocket client);
    }

    @FXML private ComboBox<String> cbServer;
    @FXML private TextField txtUser;
    @FXML private PasswordField txtPass;
    @FXML private TextField txtPassVisible;
    @FXML private ToggleButton btnTogglePass;
    @FXML private Button btnLogin;
    @FXML private Label lblError;
    @FXML private ProgressIndicator loginSpinner;
    @FXML private VBox loginCard;

    private LoginSuccessListener onSuccess;
    private boolean passwordVisible = false;

    @FXML
    public void initialize() {
        cbServer.getItems().setAll("127.0.0.1:2121");
        cbServer.getSelectionModel().selectFirst();
        txtUser.setText("admin");
        txtPass.setText("123456");

        // Hiệu ứng xuất hiện mượt mà cho form login.
        loginCard.setOpacity(0);
        loginCard.setTranslateY(18);
        FadeTransition fade = new FadeTransition(Duration.millis(420), loginCard);
        fade.setFromValue(0);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(420), loginCard);
        slide.setFromY(18);
        slide.setToY(0);
        new ParallelTransition(fade, slide).play();
    }

    public void init(LoginSuccessListener onSuccess) {
        this.onSuccess = onSuccess;
    }

    @FXML
    private void togglePassword() {
        passwordVisible = !passwordVisible;
        if (passwordVisible) {
            txtPassVisible.setText(txtPass.getText());
            txtPass.setVisible(false);
            txtPass.setManaged(false);
            txtPassVisible.setVisible(true);
            txtPassVisible.setManaged(true);
            txtPassVisible.requestFocus();
            btnTogglePass.setGraphic(new FontIcon(FontAwesomeSolid.EYE_SLASH));
        } else {
            txtPass.setText(txtPassVisible.getText());
            txtPassVisible.setVisible(false);
            txtPassVisible.setManaged(false);
            txtPass.setVisible(true);
            txtPass.setManaged(true);
            txtPass.requestFocus();
            btnTogglePass.setGraphic(new FontIcon(FontAwesomeSolid.EYE));
        }
    }

    @FXML
    private void handleLogin() {
        String server = cbServer.getEditor() != null && !cbServer.getEditor().getText().isBlank()
                ? cbServer.getEditor().getText().trim()
                : (cbServer.getValue() == null ? "" : cbServer.getValue().trim());
        String user = txtUser.getText() == null ? "" : txtUser.getText().trim();
        String pass = (passwordVisible ? txtPassVisible.getText() : txtPass.getText());
        if (pass == null) pass = "";

        int port;
        String host;
        try {
            int colon = server.lastIndexOf(':');
            host = colon < 0 ? server : server.substring(0, colon);
            port = Integer.parseInt(colon < 0 ? "2121" : server.substring(colon + 1));
        } catch (Exception ex) {
            showError("Máy chủ không hợp lệ. Định dạng đúng: host:port");
            return;
        }
        if (user.isBlank()) {
            showError("Vui lòng nhập tên đăng nhập.");
            return;
        }

        setLoading(true);
        hideError();

        FTPClientSocket client = new FTPClientSocket();
        final String fHost = host;
        final int fPort = port;
        final String fUser = user;
        final String fPass = pass;

        new Thread(() -> {
            try {
                client.connect(fHost, fPort);
                client.login(fUser, fPass);

                // Server trả fullname + role trong lời chào 230 -> client không cần MySQL.
                String rawFullname = client.getLoggedInFullname();
                String rawRole = client.getLoggedInRole();
                final String fullname = (rawFullname == null || rawFullname.isBlank()) ? fUser : rawFullname;
                final String role = (rawRole == null || rawRole.isBlank()) ? "user" : rawRole;

                Platform.runLater(() -> {
                    if (onSuccess != null) {
                        onSuccess.onSuccess(fullname, role, fHost, fPort, fUser, client);
                    }
                });
            } catch (Exception ex) {
                try { client.close(); } catch (Exception ignored) {}
                Platform.runLater(() -> {
                    showError("Đăng nhập thất bại. Sai tên đăng nhập hoặc mật khẩu.");
                    setLoading(false);
                });
            }
        }, "login-thread").start();
    }

    private void setLoading(boolean loading) {
        btnLogin.setDisable(loading);
        btnLogin.setText(loading ? "ĐANG ĐĂNG NHẬP..." : "ĐĂNG NHẬP");
        loginSpinner.setVisible(loading);
        loginSpinner.setManaged(loading);
    }

    private void showError(String message) {
        lblError.setText(message);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void hideError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
    }
}