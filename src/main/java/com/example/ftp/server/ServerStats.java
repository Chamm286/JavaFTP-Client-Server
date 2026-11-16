package com.example.ftp.server;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thống kê runtime của FTP Server (số client, dung lượng, kết nối đang hoạt động).
 * Dùng chung giữa FTPServer, ClientHandler và ServerManagerApp.
 */
public final class ServerStats {

    private static final AtomicInteger ACTIVE_CLIENTS = new AtomicInteger(0);
    private static final AtomicLong BYTES_TRANSFERRED = new AtomicLong(0);
    private static final AtomicInteger CONNECTION_IDS = new AtomicInteger(0);
    private static final ConcurrentHashMap<Integer, LiveConnection> CONNECTIONS = new ConcurrentHashMap<>();

    private ServerStats() {}

    public static int register(String ip) {
        int id = CONNECTION_IDS.incrementAndGet();
        ACTIVE_CLIENTS.incrementAndGet();
        CONNECTIONS.put(id, new LiveConnection(id, ip, "-", LocalDateTime.now(), "Connected"));
        return id;
    }

    public static void setUsername(int id, String username) {
        LiveConnection c = CONNECTIONS.get(id);
        if (c != null) {
            CONNECTIONS.put(id, new LiveConnection(c.id(), c.ip(), username, c.connectedAt(), "Logged in"));
        }
    }

    public static void unregister(int id) {
        if (CONNECTIONS.remove(id) != null) {
            ACTIVE_CLIENTS.decrementAndGet();
        }
    }

    public static void addBytes(long bytes) {
        if (bytes > 0) BYTES_TRANSFERRED.addAndGet(bytes);
    }

    public static int getActiveClients() {
        return ACTIVE_CLIENTS.get();
    }

    public static long getBytesTransferred() {
        return BYTES_TRANSFERRED.get();
    }

    public static int getActiveUsers() {
        return (int) CONNECTIONS.values().stream()
                .map(LiveConnection::username)
                .filter(u -> u != null && !"-".equals(u))
                .distinct()
                .count();
    }

    public static List<LiveConnection> snapshot() {
        return new ArrayList<>(CONNECTIONS.values());
    }

    public static void reset() {
        CONNECTIONS.clear();
        ACTIVE_CLIENTS.set(0);
    }

    public record LiveConnection(int id, String ip, String username,
                                 LocalDateTime connectedAt, String status) {}
}
