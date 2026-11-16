package com.example.ftp.client;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.util.List;

public class MainController {

    @FXML private Label lblGreeting, lblRole, lblEndpoint, lblAvatar, lblStatus, lblProgress,
            lblLocalPath, lblRemotePath;
    @FXML private ProgressBar progressBar;
    @FXML private TreeView<File> treeLocal;
    @FXML private TableView<RemoteFile> tableRemote;
    @FXML private Button btnUpload, btnDownload, btnRefresh, btnLogout, btnUp, btnDelete;

    private FTPClientSocket client;
    private Runnable logoutCallback;
    private boolean busy = false;

    @FXML
    public void initialize() {
        btnUpload.setGraphic(new FontIcon(FontAwesomeSolid.ARROW_UP));
        btnDownload.setGraphic(new FontIcon(FontAwesomeSolid.ARROW_DOWN));
        btnRefresh.setGraphic(new FontIcon(FontAwesomeSolid.SYNC));
        btnDelete.setGraphic(new FontIcon(FontAwesomeSolid.TRASH_ALT));
        btnUp.setGraphic(new FontIcon(FontAwesomeSolid.LEVEL_UP_ALT));
        btnLogout.setGraphic(new FontIcon(FontAwesomeSolid.SIGN_OUT_ALT));
        progressBar.setVisible(false);
        progressBar.setManaged(false);

        buildRemoteTable();
        buildLocalTree();
    }

    public void init(String fullname, String role, String host, int port, String userName,
                     FTPClientSocket client) {
        this.client = client;
        lblGreeting.setText("Xin chào, " + fullname);
        lblRole.setText(role == null ? "user" : role.toUpperCase());
        lblEndpoint.setText(userName + "@" + host + ":" + port);
        lblAvatar.setText(initials(fullname));
        lblStatus.setText("Đã kết nối " + host + ":" + port);
        refreshAsync();
    }

    public void setLogoutCallback(Runnable r) { this.logoutCallback = r; }

    private String initials(String fullname) {
        if (fullname == null || fullname.isBlank()) return "?";
        String[] parts = fullname.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        String first = parts[0].substring(0, 1);
        String last = parts[parts.length - 1].substring(0, 1);
        return (first + last).toUpperCase();
    }

    // ================= TABLES / TREE =================
    private void buildRemoteTable() {
        TableColumn<RemoteFile, String> colName = new TableColumn<>("Name");
        colName.setCellValueFactory(d -> new javafx.beans.property.ReadOnlyStringWrapper(d.getValue().getName()));
        colName.setCellFactory(c -> new TableCell<RemoteFile, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                RemoteFile rf = getTableRow() == null ? null : getTableRow().getItem();
                boolean dir = rf != null && rf.isDirectory();
                FontIcon icon = new FontIcon(dir ? FontAwesomeSolid.FOLDER : FontAwesomeSolid.FILE);
                icon.setIconColor(dir
                        ? javafx.scene.paint.Color.web("#f59e0b")
                        : javafx.scene.paint.Color.web("#94a3b8"));
                setGraphic(icon);
                setText(item);
            }
        });

        TableColumn<RemoteFile, String> colSize = new TableColumn<>("Size");
        colSize.setCellValueFactory(d -> new javafx.beans.property.ReadOnlyStringWrapper(d.getValue().getDisplaySize()));
        TableColumn<RemoteFile, String> colModified = new TableColumn<>("Modified");
        colModified.setCellValueFactory(d -> new javafx.beans.property.ReadOnlyStringWrapper(d.getValue().getModified()));

        tableRemote.getColumns().addAll(colName, colSize, colModified);
        tableRemote.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableRemote.setPlaceholder(new Label("Chưa có dữ liệu — bấm Refresh để tải danh sách."));

        tableRemote.setRowFactory(tv -> {
            TableRow<RemoteFile> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !row.isEmpty()) {
                    RemoteFile rf = row.getItem();
                    if (rf.isDirectory()) enterDirectory(rf.getName());
                    else handleDownload();
                }
            });
            return row;
        });
    }

    private void buildLocalTree() {
        File home = new File(System.getProperty("user.home"));
        treeLocal.setRoot(new FileTreeItem(home));
        treeLocal.getRoot().setExpanded(true);
        treeLocal.setCellFactory(tv -> new TreeCell<File>() {
            @Override
            protected void updateItem(File f, boolean empty) {
                super.updateItem(f, empty);
                if (empty || f == null) { setText(null); setGraphic(null); return; }
                setText(f.getName().isEmpty() ? f.getAbsolutePath() : f.getName());
                FontIcon icon = new FontIcon(f.isDirectory() ? FontAwesomeSolid.FOLDER : FontAwesomeSolid.FILE);
                icon.setIconColor(f.isDirectory()
                        ? javafx.scene.paint.Color.web("#f59e0b")
                        : javafx.scene.paint.Color.web("#94a3b8"));
                setGraphic(icon);
            }
        });
        treeLocal.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b != null && b.getValue() != null && lblLocalPath != null) {
                lblLocalPath.setText(b.getValue().getAbsolutePath());
            }
        });
        treeLocal.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) handleUpload();
        });
    }

    // ================= ACTIONS =================
    @FXML private void handleRefresh() {
        refreshAsync();
    }

    private void refreshAsync() {
        if (client == null || busy) return;
        setBusy(true);
        lblStatus.setText("Đang tải danh sách file...");
        Task<List<RemoteFile>> task = new Task<>() {
            @Override protected List<RemoteFile> call() throws Exception { return client.listFiles(); }
        };
        task.setOnSucceeded(e -> {
            tableRemote.getItems().setAll(task.getValue());
            try { lblRemotePath.setText(client.pwd()); } catch (Exception ignored) {}
            lblStatus.setText("Đã tải " + task.getValue().size() + " mục.");
            setBusy(false);
        });
        task.setOnFailed(e -> {
            lblStatus.setText("Lỗi tải danh sách: " + rootMessage(task.getException()));
            setBusy(false);
        });
        new Thread(task, "ftp-list").start();
    }

    private void enterDirectory(String name) {
        if (client == null || busy) return;
        setBusy(true);
        lblStatus.setText("Đang mở thư mục " + name + "...");
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                client.changeDirectory(name);
                return null;
            }
        };
        task.setOnSucceeded(e -> { setBusy(false); refreshAsync(); });
        task.setOnFailed(e -> {
            lblStatus.setText("Không mở được thư mục: " + rootMessage(task.getException()));
            setBusy(false);
        });
        new Thread(task, "ftp-cwd").start();
    }

    @FXML private void handleUp() {
        if (client == null || busy) return;
        setBusy(true);
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                client.changeToParent();
                return null;
            }
        };
        task.setOnSucceeded(e -> { setBusy(false); refreshAsync(); });
        task.setOnFailed(e -> {
            lblStatus.setText("Không lên được thư mục cha: " + rootMessage(task.getException()));
            setBusy(false);
        });
        new Thread(task, "ftp-cdup").start();
    }

    @FXML private void handleUpload() {
        if (client == null || busy) {
            lblStatus.setText("Chưa kết nối tới server.");
            return;
        }
        TreeItem<File> sel = treeLocal.getSelectionModel().getSelectedItem();
        File localFile = (sel != null && sel.getValue() != null && sel.getValue().isFile())
                ? sel.getValue() : null;

        if (localFile == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Chọn file để upload");
            localFile = chooser.showOpenDialog(treeLocal.getScene().getWindow());
        }
        if (localFile == null) return;

        final File file = localFile;
        setBusy(true);
        lblStatus.setText("Đang upload: " + file.getName());
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                client.uploadFile(file, p -> updateProgress(p, 1.0));
                return null;
            }
        };
        bindProgress(task);
        task.setOnSucceeded(e -> {
            lblStatus.setText("Upload thành công: " + file.getName());
            setBusy(false);
            refreshAsync();
        });
        task.setOnFailed(e -> {
            lblStatus.setText("Upload lỗi: " + rootMessage(task.getException()));
            setBusy(false);
        });
        new Thread(task, "ftp-upload").start();
    }

    @FXML private void handleDownload() {
        RemoteFile rf = tableRemote.getSelectionModel().getSelectedItem();
        if (rf == null) {
            lblStatus.setText("Hãy chọn một file ở REMOTE FILES.");
            return;
        }
        if (rf.isDirectory()) {
            enterDirectory(rf.getName());
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Lưu file về máy");
        chooser.setInitialFileName(rf.getName());
        File downloads = new File(System.getProperty("user.home"), "Downloads");
        chooser.setInitialDirectory(downloads.isDirectory() ? downloads : new File(System.getProperty("user.home")));
        File target = chooser.showSaveDialog(tableRemote.getScene().getWindow());
        if (target == null) return;

        setBusy(true);
        lblStatus.setText("Đang download: " + rf.getName());
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                client.downloadFile(rf.getName(), target, p -> {
                    if (p >= 0) updateProgress(p, 1.0);
                });
                return null;
            }
        };
        bindProgress(task);
        task.setOnSucceeded(e -> {
            lblStatus.setText("Download thành công: " + target.getName());
            setBusy(false);
        });
        task.setOnFailed(e -> {
            lblStatus.setText("Download lỗi: " + rootMessage(task.getException()));
            setBusy(false);
        });
        new Thread(task, "ftp-download").start();
    }

    @FXML private void handleDelete() {
        RemoteFile rf = tableRemote.getSelectionModel().getSelectedItem();
        if (rf == null) {
            lblStatus.setText("Hãy chọn một file để xóa.");
            return;
        }
        if (rf.isDirectory()) {
            lblStatus.setText("Chưa hỗ trợ xóa thư mục.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận xóa");
        confirm.setHeaderText("Xóa file trên server?");
        confirm.setContentText("Bạn có chắc muốn xóa '" + rf.getName() + "'?");
        styleDialog(confirm);
        var result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        setBusy(true);
        lblStatus.setText("Đang xóa: " + rf.getName());
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                client.deleteFile(rf.getName());
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            lblStatus.setText("Đã xóa: " + rf.getName());
            setBusy(false);
            refreshAsync();
        });
        task.setOnFailed(e -> {
            lblStatus.setText("Xóa lỗi: " + rootMessage(task.getException()));
            setBusy(false);
        });
        new Thread(task, "ftp-delete").start();
    }

    @FXML private void handleLogout() {
        try { if (client != null) client.disconnect(); } catch (Exception ignored) {}
        if (logoutCallback != null) logoutCallback.run();
    }

    // ================= HELPERS =================
    private void bindProgress(Task<?> task) {
        progressBar.setVisible(true);
        progressBar.setManaged(true);
        progressBar.progressProperty().bind(task.progressProperty());
        lblProgress.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> String.format("%.0f%%", Math.max(0, task.getProgress()) * 100),
                task.progressProperty()));
        task.runningProperty().addListener((o, was, running) -> {
            if (!running) {
                progressBar.progressProperty().unbind();
                lblProgress.textProperty().unbind();
                progressBar.setProgress(0);
                progressBar.setVisible(false);
                progressBar.setManaged(false);
                lblProgress.setText("");
            }
        });
    }

    /** Áp dụng CSS tối cho Alert/Dialog (dialog có Scene riêng). */
    private void styleDialog(Dialog<?> dialog) {
        var css = getClass().getResource("/css/style.css");
        if (css != null) dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
    }

    private void setBusy(boolean value) {
        busy = value;
        btnUpload.setDisable(value);
        btnDownload.setDisable(value);
        btnRefresh.setDisable(value);
        btnDelete.setDisable(value);
        btnUp.setDisable(value);
    }

    private String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause != null && cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause == null ? "Lỗi không xác định." : cause.getMessage();
    }

    private static class FileTreeItem extends TreeItem<File> {
        private boolean loaded = false;
        FileTreeItem(File f) { super(f); }
        @Override public boolean isLeaf() {
            File f = getValue();
            if (f == null || !f.isDirectory()) return true;
            String[] c = f.list();
            return c == null || c.length == 0;
        }
        @Override public javafx.collections.ObservableList<TreeItem<File>> getChildren() {
            if (!loaded) { loaded = true; super.getChildren().setAll(loadChildren()); }
            return super.getChildren();
        }
        private List<TreeItem<File>> loadChildren() {
            File dir = getValue();
            List<TreeItem<File>> result = new java.util.ArrayList<>();
            if (dir == null || !dir.isDirectory()) return result;
            File[] files = dir.listFiles();
            if (files == null) return result;
            java.util.Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            for (File f : files) if (!f.isHidden()) result.add(new FileTreeItem(f));
            return result;
        }
    }
}