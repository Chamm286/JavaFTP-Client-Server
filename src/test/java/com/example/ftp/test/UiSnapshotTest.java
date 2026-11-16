package com.example.ftp.test;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CountDownLatch;

/** Chụp ảnh 2 màn hình để kiểm tra giao diện. */
public class UiSnapshotTest {

    public static void main(String[] args) throws Exception {
        File outDir = new File("F:/Temp/opencode");
        outDir.mkdirs();
        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {});
        Platform.setImplicitExit(false);
        Platform.runLater(() -> snapshot("/fxml/LoginView.fxml", 1040, 680,
                new File(outDir, "login.png"), done, true));
        done.await();
        CountDownLatch done2 = new CountDownLatch(1);
        Platform.runLater(() -> snapshot("/fxml/MainView.fxml", 1280, 800,
                new File(outDir, "main.png"), done2, false));
        done2.await();
        Platform.exit();
        System.exit(0);
    }

    private static void snapshot(String fxml, int w, int h, File out, CountDownLatch latch, boolean exitAfter) {
        try {
            FXMLLoader loader = new FXMLLoader(UiSnapshotTest.class.getResource(fxml));
            Scene scene = new Scene(loader.load(), w, h);
            scene.getStylesheets().add(UiSnapshotTest.class.getResource("/css/style.css").toExternalForm());
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();

            PauseTransition pause = new PauseTransition(Duration.millis(900));
            pause.setOnFinished(e -> {
                try {
                    WritableImage img = scene.snapshot(null);
                    BufferedImage bimg = new BufferedImage((int) img.getWidth(), (int) img.getHeight(),
                            BufferedImage.TYPE_INT_ARGB);
                    PixelReader pr = img.getPixelReader();
                    for (int y = 0; y < bimg.getHeight(); y++)
                        for (int x = 0; x < bimg.getWidth(); x++)
                            bimg.setRGB(x, y, pr.getArgb(x, y));
                    ImageIO.write(bimg, "png", out);
                    System.out.println("[SNAPSHOT] " + out.getAbsolutePath());
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    stage.close();
                    latch.countDown();
                }
            });
            pause.play();
        } catch (Exception ex) {
            ex.printStackTrace();
            latch.countDown();
        }
    }
}