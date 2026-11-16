package com.example.ftp.server;

import com.example.ftp.db.DatabaseManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FTP Server có thể start/stop từ giao diện Server Manager.
 */
public class FTPServer {

    private final int port;
    private final Path rootDir;
    private final DatabaseManager database;

    private volatile ServerSocket serverSocket;
    private volatile boolean running;
    private Instant startedAt;
    private Thread acceptThread;
    private final AtomicInteger clientCounter = new AtomicInteger(0);

    public FTPServer(int port, Path rootDir, DatabaseManager database) {
        this.port = port;
        this.rootDir = rootDir.toAbsolutePath().normalize();
        this.database = database;
    }

    public synchronized void start() throws IOException {
        if (running) return;
        Files.createDirectories(rootDir);
        serverSocket = new ServerSocket(port, 50);
        serverSocket.setReuseAddress(true);
        running = true;
        startedAt = Instant.now();
        ServerStats.reset();

        acceptThread = new Thread(() -> {
            System.out.println("[SERVER] Listening on port " + port);
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    int id = clientCounter.incrementAndGet();
                    Thread t = new Thread(new ClientHandler(socket, rootDir, database), "ftp-client-" + id);
                    t.setDaemon(true);
                    t.start();
                } catch (IOException ex) {
                    if (running) System.err.println("[SERVER] Accept error: " + ex.getMessage());
                }
            }
        }, "ftp-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public synchronized void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        serverSocket = null;
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public Path getRootDir() {
        return rootDir;
    }

    public long getUptimeSeconds() {
        if (startedAt == null) return 0;
        return Duration.between(startedAt, Instant.now()).getSeconds();
    }
}
