package com.example.ftp.server;

import com.example.ftp.db.DatabaseManager;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Chạy FTP Server dạng console ở port 2121.
 * Có thể dùng ServerManagerApp để có giao diện quản lý.
 */
public class FTPServerMain {

    public static final int DEFAULT_PORT = 2121;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        Path root = Paths.get(System.getProperty("ftp.server.root", "server_files"))
                .toAbsolutePath().normalize();
        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ex) {
                System.err.println("Port không hợp lệ: " + args[0]);
                return;
            }
        }
        if (args.length >= 2) {
            root = Paths.get(args[1]).toAbsolutePath().normalize();
        }

        DatabaseManager database = new DatabaseManager();
        database.initDatabase();

        System.out.println("========================================");
        System.out.println("  FTP SERVER - FILEZILLA SIMULATOR");
        System.out.println("  Port: " + port);
        System.out.println("  Root: " + root);
        System.out.println("  DB  : " + database.getUrl());
        System.out.println("  admin / 123456");
        System.out.println("========================================");

        FTPServer server = new FTPServer(port, root, database);
        try {
            server.start();
        } catch (Exception ex) {
            System.err.println("Không khởi động được server: " + ex.getMessage());
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        System.out.println("[SERVER] Nhấn Ctrl+C để dừng.");

        // Giữ tiến trình main sống.
        try {
            Thread.currentThread().join();
        } catch (InterruptedException ignored) {
        }
    }
}
