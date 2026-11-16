package com.example.ftp.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class FTPClientApp extends Application {

    private Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        this.primaryStage = stage;
        stage.setTitle("FTP Client Manager");
        stage.setMinWidth(980);
        stage.setMinHeight(620);
        showLogin();
        stage.show();
    }

    private void showLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
            Scene scene = new Scene(loader.load(), 1040, 680);
            applyCss(scene);

            LoginController ctrl = loader.getController();
            ctrl.init((fullname, role, host, port, user, client) ->
                    showMain(fullname, role, host, port, user, client));

            primaryStage.setScene(scene);
            primaryStage.setTitle("FTP Client Manager · Đăng nhập");
        } catch (Exception ex) {
            throw new RuntimeException("Không tải được LoginView.fxml", ex);
        }
    }

    private void showMain(String fullname, String role, String host, int port, String user,
                          FTPClientSocket client) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
            Scene scene = new Scene(loader.load(), 1280, 800);
            applyCss(scene);

            MainController ctrl = loader.getController();
            ctrl.init(fullname, role, host, port, user, client);
            ctrl.setLogoutCallback(this::showLogin);

            primaryStage.setScene(scene);
            primaryStage.setTitle("FTP Client Manager · " + fullname);
        } catch (Exception ex) {
            throw new RuntimeException("Không tải được MainView.fxml", ex);
        }
    }

    private void applyCss(Scene scene) {
        URL css = getClass().getResource("/css/style.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
    }

    public static void main(String[] args) { launch(args); }
}