package com.example.ftp.test;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Smoke test: nạp 2 file FXML để phát hiện lỗi runtime (controller, icon, style).
 * Chạy: mvn test-compile exec:java -Dexec.mainClass=com.example.ftp.test.FxmlSmokeTest -Dexec.classpathScope=test
 */
public class FxmlSmokeTest {

    public static void main(String[] args) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        final boolean[] ok = {false};

        Platform.startup(() -> {});
        Platform.runLater(() -> {
            try {
                System.out.println("[SMOKE] Loading LoginView.fxml ...");
                FXMLLoader login = new FXMLLoader(FxmlSmokeTest.class.getResource("/fxml/LoginView.fxml"));
                Parent loginRoot = login.load();
                System.out.println("[SMOKE] LoginView OK, controller=" + login.getController());

                System.out.println("[SMOKE] Loading MainView.fxml ...");
                FXMLLoader main = new FXMLLoader(FxmlSmokeTest.class.getResource("/fxml/MainView.fxml"));
                Parent mainRoot = main.load();
                System.out.println("[SMOKE] MainView OK, controller=" + main.getController());

                // Áp CSS để phát hiện lỗi cú pháp stylesheet.
                for (Parent root : new Parent[]{loginRoot, mainRoot}) {
                    root.getStylesheets().add(
                            FxmlSmokeTest.class.getResource("/css/style.css").toExternalForm());
                }
                loginRoot.applyCss();
                mainRoot.applyCss();
                System.out.println("[SMOKE] CSS applied OK.");
                ok[0] = true;
            } catch (Throwable t) {
                System.err.println("[SMOKE] FAILED: " + t);
                t.printStackTrace();
            } finally {
                done.countDown();
            }
        });

        if (!done.await(20, TimeUnit.SECONDS)) {
            System.err.println("[SMOKE] Timeout.");
        }
        Platform.exit();
        System.exit(ok[0] ? 0 : 1);
    }
}