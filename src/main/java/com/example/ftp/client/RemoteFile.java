package com.example.ftp.client;

/**
 * Mô tả một file/thư mục trên FTP Server.
 */
public final class RemoteFile {

    private final String name;
    private final long size;
    private final boolean directory;
    private final String modified;
    private final String permissions;

    public RemoteFile(String name, long size, boolean directory, String modified, String permissions) {
        this.name = name;
        this.size = size;
        this.directory = directory;
        this.modified = modified == null ? "" : modified;
        this.permissions = permissions == null ? "" : permissions;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public boolean isDirectory() {
        return directory;
    }

    public String getModified() {
        return modified;
    }

    public String getPermissions() {
        return permissions;
    }

    public String getDisplayType() {
        return directory ? "Thư mục" : "Tệp tin";
    }

    public String getDisplaySize() {
        if (directory) {
            return "-";
        }
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f KB", size / 1024.0);
        }
        if (size < 1024L * 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.2f MB", size / (1024.0 * 1024));
        }
        return String.format(java.util.Locale.US, "%.2f GB", size / (1024.0 * 1024 * 1024));
    }
}
