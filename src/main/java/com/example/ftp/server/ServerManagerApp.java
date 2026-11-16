package com.example.ftp.server;

import com.example.ftp.client.FTPClientApp;
import com.example.ftp.db.DatabaseManager;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * FTP Server Manager - giao diện quản trị Dark Mode hiện đại.
 */
public class ServerManagerApp extends Application {

    private final DatabaseManager database = new DatabaseManager();
    private FTPServer server;

    private Label lblServerState, lblMySql, lblPort, lblUptime;
    private Circle statusDot;
    private Button btnStart, btnStop, btnLaunchClient;
    private TextField searchField;
    private String searchQuery = "";

    private Label cardClients, cardBytes, cardUsers, cardLogs;
    private TableView<ServerStats.LiveConnection> tableLive;
    private TableView<DatabaseManager.UserRow> tableUsers;
    private TableView<DatabaseManager.LogRow> tableLogs;
    private ComboBox<String> logFilter;
    private List<DatabaseManager.LogRow> logCache = new ArrayList<>();

    private StackPane contentPane;
    private MenuItemView currentMenu = MenuItemView.DASHBOARD;
    private int tickCounter = 0;

    @Override
    public void start(Stage stage) {
        database.initDatabase();
        server = new FTPServer(2121, Paths.get("server_files"), database);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("server-root");

        root.setTop(buildTopBar());
        root.setLeft(buildSidebar());
        root.setCenter(buildContent());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1360, 860);
        URL css = getClass().getResource("/css/server-dark.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage.setTitle("FTP Server Manager");
        stage.setMinWidth(1180);
        stage.setMinHeight(720);
        stage.setOnCloseRequest(e -> {
            if (server.isRunning()) server.stop();
        });
        stage.setScene(scene);
        stage.show();

        updateServerState();
        updateMySqlState();

        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> refreshRuntime()));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
    }

    // ================= TOP BAR =================
    private HBox buildTopBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(14, 24, 14, 24));
        bar.getStyleClass().add("top-bar");

        HBox searchBox = new HBox(8);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.getStyleClass().add("search-box");

        FontIcon searchIcon = new FontIcon(FontAwesomeSolid.SEARCH);
        searchIcon.setIconSize(14);
        searchIcon.getStyleClass().add("search-icon");

        searchField = new TextField();
        searchField.setPromptText("Search users, logs, connections...");
        searchField.getStyleClass().add("search-field");
        searchField.setPrefWidth(320);
        searchField.textProperty().addListener((o, a, b) -> {
            searchQuery = b == null ? "" : b.trim().toLowerCase();
            refreshCurrentView();
        });
        searchBox.getChildren().addAll(searchIcon, searchField);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusDot = new Circle(5);
        statusDot.getStyleClass().add("status-dot");
        lblServerState = new Label("Offline");
        lblServerState.getStyleClass().add("status-offline");

        HBox stateBox = new HBox(7, statusDot, lblServerState);
        stateBox.setAlignment(Pos.CENTER);
        stateBox.getStyleClass().add("server-state");

        btnStart = new Button("Start Server");
        btnStart.getStyleClass().addAll("action-button", "start-button");
        btnStart.setGraphic(new FontIcon(FontAwesomeSolid.PLAY));
        btnStart.setOnAction(e -> startServer());

        btnStop = new Button("Stop");
        btnStop.getStyleClass().addAll("action-button", "stop-button");
        btnStop.setGraphic(new FontIcon(FontAwesomeSolid.STOP));
        btnStop.setOnAction(e -> stopServer());

        btnLaunchClient = new Button("Launch Client");
        btnLaunchClient.getStyleClass().addAll("action-button", "launch-client-button");
        btnLaunchClient.setGraphic(new FontIcon(FontAwesomeSolid.EXTERNAL_LINK_ALT));
        btnLaunchClient.setTooltip(new Tooltip("Mở cửa sổ FTP Client"));
        btnLaunchClient.setOnAction(e -> openClientWindow());

        StackPane avatar = new StackPane();
        Circle avatarCircle = new Circle(17);
        avatarCircle.getStyleClass().add("avatar-circle");
        Label initials = new Label("AD");
        initials.getStyleClass().add("avatar-initials");
        avatar.getChildren().addAll(avatarCircle, initials);

        VBox userText = new VBox(1, label("Admin", "user-name"), label("Administrator", "user-role"));
        HBox userChip = new HBox(10, avatar, userText);
        userChip.setAlignment(Pos.CENTER_LEFT);
        userChip.getStyleClass().add("user-chip");

        bar.getChildren().addAll(searchBox, spacer, stateBox, btnStart, btnStop, btnLaunchClient, userChip);
        return bar;
    }

    // ================= SIDEBAR =================
    private VBox buildSidebar() {
        VBox side = new VBox(18);
        side.getStyleClass().add("sidebar");
        side.setPrefWidth(250);
        side.setMinWidth(232);
        side.setPadding(new Insets(24, 16, 20, 16));

        HBox brand = new HBox(12);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.getStyleClass().add("brand");

        StackPane logoBox = new StackPane();
        logoBox.getStyleClass().add("logo-box");
        FontIcon logo = new FontIcon(FontAwesomeSolid.SERVER);
        logo.setIconSize(20);
        logo.getStyleClass().add("logo-icon");
        logoBox.getChildren().add(logo);

        VBox brandText = new VBox(1,
                label("FTP Manager", "logo-title"),
                label("Server Console", "logo-subtitle"));
        brand.getChildren().addAll(logoBox, brandText);

        Label navLabel = new Label("MAIN MENU");
        navLabel.getStyleClass().add("nav-label");

        ListView<MenuItemView> menu = new ListView<>();
        menu.getItems().addAll(MenuItemView.values());
        menu.getStyleClass().add("menu-list");
        menu.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(MenuItemView item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                FontIcon icon = new FontIcon(item.icon);
                icon.setIconSize(15);
                icon.getStyleClass().add("menu-icon");

                Label text = new Label(item.label);
                text.getStyleClass().add("menu-text");

                HBox box = new HBox(12, icon, text);
                box.setAlignment(Pos.CENTER_LEFT);
                box.setPadding(new Insets(10, 14, 10, 14));
                box.setMaxWidth(Double.MAX_VALUE);
                setGraphic(box);
                setText(null);
            }
        });
        menu.getSelectionModel().select(0);
        menu.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b != null) {
                currentMenu = b;
                showView(b);
            }
        });
        VBox.setVgrow(menu, Priority.ALWAYS);

        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);

        VBox footer = new VBox(4,
                label("FTP Server v1.0", "sidebar-footer"),
                label("MySQL 3307", "sidebar-footer"));
        footer.getStyleClass().add("sidebar-footer-box");

        side.getChildren().addAll(brand, navLabel, menu, grow, footer);
        return side;
    }

    // ================= CONTENT =================
    private StackPane buildContent() {
        contentPane = new StackPane();
        contentPane.getStyleClass().add("content");
        contentPane.setPadding(new Insets(24));
        showView(currentMenu);
        return contentPane;
    }

    private void showView(MenuItemView view) {
        if (contentPane == null) return;
        VBox pane = switch (view) {
            case DASHBOARD -> buildDashboard();
            case USERS -> buildUsers();
            case LOGS -> buildLogs();
            case SETTINGS -> buildSettings();
        };
        contentPane.getChildren().setAll(pane);
        FadeTransition fade = new FadeTransition(Duration.millis(170), pane);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.play();
    }

    private VBox buildDashboard() {
        VBox box = new VBox(20);

        HBox pageHeader = new HBox(12);
        pageHeader.setAlignment(Pos.CENTER_LEFT);
        VBox titles = pageTitle("Dashboard", "Monitor your FTP server at a glance.");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button refresh = iconActionButton(FontAwesomeSolid.SYNC, "Refresh dashboard", this::refreshRuntime);
        pageHeader.getChildren().addAll(titles, spacer, refresh);

        HBox cards = new HBox(16);
        cardClients = new Label("0");
        cardBytes = new Label("0 B");
        cardUsers = new Label("0");
        cardLogs = new Label("0");
        VBox c1 = statCard("Connections", "Live clients", cardClients, FontAwesomeSolid.SERVER, "#3B82F6");
        VBox c2 = statCard("Active Users", "Authenticated sessions", cardUsers, FontAwesomeSolid.USERS, "#22C55E");
        VBox c3 = statCard("Data Transferred", "Since server start", cardBytes, FontAwesomeSolid.DATABASE, "#A78BFA");
        VBox c4 = statCard("Logs Today", "Audit events", cardLogs, FontAwesomeSolid.CLIPBOARD_LIST, "#F59E0B");
        HBox.setHgrow(c1, Priority.ALWAYS);
        HBox.setHgrow(c2, Priority.ALWAYS);
        HBox.setHgrow(c3, Priority.ALWAYS);
        HBox.setHgrow(c4, Priority.ALWAYS);
        cards.getChildren().addAll(c1, c2, c3, c4);

        VBox panel = new VBox(14);
        panel.getStyleClass().add("panel");
        HBox panelHead = new HBox(12);
        panelHead.setAlignment(Pos.CENTER_LEFT);
        VBox panelTitles = new VBox(2,
                label("Live Connections", "panel-title"),
                label("Currently connected FTP clients.", "panel-subtitle"));
        Region pSpacer = new Region();
        HBox.setHgrow(pSpacer, Priority.ALWAYS);
        Label live = new Label("● LIVE");
        live.getStyleClass().add("live-pill");
        panelHead.getChildren().addAll(panelTitles, pSpacer, live);

        tableLive = new TableView<>();
        tableLive.getStyleClass().add("data-table");
        TableColumn<ServerStats.LiveConnection, String> cUser = new TableColumn<>("Username");
        TableColumn<ServerStats.LiveConnection, String> cIp = new TableColumn<>("IP Address");
        TableColumn<ServerStats.LiveConnection, String> cTime = new TableColumn<>("Connected At");
        TableColumn<ServerStats.LiveConnection, String> cStatus = new TableColumn<>("Status");
        cUser.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().username()));
        cIp.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().ip()));
        cTime.setCellValueFactory(d -> new ReadOnlyStringWrapper(String.valueOf(d.getValue().connectedAt())));
        cStatus.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().status()));
        cUser.setPrefWidth(220);
        cIp.setPrefWidth(220);
        cTime.setPrefWidth(260);
        cStatus.setPrefWidth(160);
        cStatus.setCellFactory(col -> statusBadgeCell());
        tableLive.getColumns().addAll(cUser, cIp, cTime, cStatus);
        tableLive.setPlaceholder(new Label("No active connections"));
        VBox.setVgrow(tableLive, Priority.ALWAYS);

        panel.getChildren().addAll(panelHead, tableLive);
        VBox.setVgrow(panel, Priority.ALWAYS);
        box.getChildren().addAll(pageHeader, cards, panel);
        return box;
    }

    private VBox statCard(String title, String description, Label value,
                          FontAwesomeSolid icon, String accent) {
        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = label(title, "card-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        StackPane iconBox = new StackPane();
        iconBox.getStyleClass().add("card-icon-box");
        FontIcon iconView = new FontIcon(icon);
        iconView.setIconSize(18);
        iconView.setIconColor(Color.web(accent));
        iconBox.getChildren().add(iconView);
        top.getChildren().addAll(titleLabel, spacer, iconBox);

        value.getStyleClass().add("card-value");
        Label desc = label(description, "card-desc");

        VBox card = new VBox(8, top, value, desc);
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private VBox buildUsers() {
        VBox box = new VBox(20);

        HBox pageHeader = new HBox(12);
        pageHeader.setAlignment(Pos.CENTER_LEFT);
        VBox titles = pageTitle("User Management", "Manage FTP accounts and permissions.");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refresh = iconActionButton(FontAwesomeSolid.SYNC, "Refresh users", this::loadUsers);
        Button add = new Button("Add User");
        add.getStyleClass().addAll("action-button", "blue-button");
        add.setGraphic(new FontIcon(FontAwesomeSolid.USER_PLUS));
        add.setOnAction(e -> addUser());
        pageHeader.getChildren().addAll(titles, spacer, refresh, add);

        VBox panel = new VBox(14);
        panel.getStyleClass().add("panel");
        HBox panelHead = new HBox(12);
        panelHead.setAlignment(Pos.CENTER_LEFT);
        VBox panelTitles = new VBox(2,
                label("All Users", "panel-title"),
                label("Edit a permission or remove an account with the row actions.", "panel-subtitle"));
        panelHead.getChildren().add(panelTitles);

        tableUsers = new TableView<>();
        tableUsers.getStyleClass().add("data-table");
        tableUsers.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<DatabaseManager.UserRow, String> cName = new TableColumn<>("Username");
        TableColumn<DatabaseManager.UserRow, String> cPerm = new TableColumn<>("Permission");
        TableColumn<DatabaseManager.UserRow, String> cCreated = new TableColumn<>("Created At");
        TableColumn<DatabaseManager.UserRow, Void> cActions = new TableColumn<>("Actions");

        cName.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().username()));
        cPerm.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().permission()));
        cCreated.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().createdAt()));
        cName.setPrefWidth(280);
        cPerm.setPrefWidth(180);
        cCreated.setPrefWidth(260);
        cActions.setPrefWidth(120);
        cActions.setSortable(false);
        cPerm.setCellFactory(col -> permissionBadgeCell());
        cActions.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                int index = getIndex();
                if (empty || index < 0 || index >= getTableView().getItems().size()) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                DatabaseManager.UserRow row = getTableView().getItems().get(index);
                Button edit = rowIconButton(FontAwesomeSolid.EDIT, "Edit user", "edit-icon", () -> editUser(row));
                Button delete = rowIconButton(FontAwesomeSolid.TRASH_ALT, "Delete user", "danger-icon", () -> deleteUser(row));
                HBox actions = new HBox(6, edit, delete);
                actions.setAlignment(Pos.CENTER_LEFT);
                setGraphic(actions);
                setText(null);
            }
        });

        tableUsers.getColumns().addAll(cName, cPerm, cCreated, cActions);
        tableUsers.setPlaceholder(new Label("No users found"));
        VBox.setVgrow(tableUsers, Priority.ALWAYS);

        panel.getChildren().addAll(panelHead, tableUsers);
        VBox.setVgrow(panel, Priority.ALWAYS);
        box.getChildren().addAll(pageHeader, panel);
        loadUsers();
        return box;
    }

    private VBox buildLogs() {
        VBox box = new VBox(20);

        HBox pageHeader = new HBox(12);
        pageHeader.setAlignment(Pos.CENTER_LEFT);
        VBox titles = pageTitle("Connection Logs", "Track logins, logouts and file transfers.");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label filterLabel = new Label("Action");
        filterLabel.getStyleClass().add("filter-label");
        logFilter = new ComboBox<>();
        logFilter.getItems().addAll("ALL", "LOGIN", "LOGOUT", "UPLOAD", "DOWNLOAD", "DELETE");
        logFilter.setValue("ALL");
        logFilter.getStyleClass().add("filter-combo");
        logFilter.setOnAction(e -> applyLogFilter());

        Button refresh = iconActionButton(FontAwesomeSolid.SYNC, "Refresh logs", this::loadLogs);
        pageHeader.getChildren().addAll(titles, spacer, filterLabel, logFilter, refresh);

        VBox panel = new VBox(14);
        panel.getStyleClass().add("panel");
        HBox panelHead = new HBox(12);
        panelHead.setAlignment(Pos.CENTER_LEFT);
        VBox panelTitles = new VBox(2,
                label("Audit Trail", "panel-title"),
                label("Newest events from the MySQL logs table.", "panel-subtitle"));
        panelHead.getChildren().add(panelTitles);

        tableLogs = new TableView<>();
        tableLogs.getStyleClass().add("data-table");
        tableLogs.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<DatabaseManager.LogRow, String> cUser = new TableColumn<>("Username");
        TableColumn<DatabaseManager.LogRow, String> cAction = new TableColumn<>("Action");
        TableColumn<DatabaseManager.LogRow, String> cFile = new TableColumn<>("Filename");
        TableColumn<DatabaseManager.LogRow, String> cIp = new TableColumn<>("IP");
        TableColumn<DatabaseManager.LogRow, String> cStatus = new TableColumn<>("Status");
        TableColumn<DatabaseManager.LogRow, String> cTime = new TableColumn<>("Time");

        cUser.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().username()));
        cAction.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().action()));
        cFile.setCellValueFactory(d -> new ReadOnlyStringWrapper(
                d.getValue().filename() == null ? "-" : d.getValue().filename()));
        cIp.setCellValueFactory(d -> new ReadOnlyStringWrapper(
                d.getValue().ip() == null ? "-" : d.getValue().ip()));
        cStatus.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().status()));
        cTime.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().createdAt()));
        cStatus.setCellFactory(col -> statusBadgeCell());
        cUser.setPrefWidth(150);
        cAction.setPrefWidth(120);
        cFile.setPrefWidth(240);
        cIp.setPrefWidth(160);
        cStatus.setPrefWidth(110);
        cTime.setPrefWidth(200);
        tableLogs.getColumns().addAll(cUser, cAction, cFile, cIp, cStatus, cTime);
        tableLogs.setPlaceholder(new Label("No logs found"));
        VBox.setVgrow(tableLogs, Priority.ALWAYS);

        panel.getChildren().addAll(panelHead, tableLogs);
        VBox.setVgrow(panel, Priority.ALWAYS);
        box.getChildren().addAll(pageHeader, panel);
        loadLogs();
        return box;
    }

    private VBox buildSettings() {
        VBox box = new VBox(20);

        HBox pageHeader = new HBox(12);
        pageHeader.setAlignment(Pos.CENTER_LEFT);
        VBox titles = pageTitle("Settings", "Server and database configuration.");
        pageHeader.getChildren().add(titles);

        VBox panel = new VBox(16);
        panel.getStyleClass().add("panel");

        HBox panelHead = new HBox(12);
        panelHead.setAlignment(Pos.CENTER_LEFT);
        VBox panelTitles = new VBox(2,
                label("Runtime Configuration", "panel-title"),
                label("Values are loaded from the current application configuration.", "panel-subtitle"));
        panelHead.getChildren().add(panelTitles);

        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(14);
        grid.add(settingsLabel("FTP Port"), 0, 0);
        grid.add(settingsValue("2121"), 1, 0);
        grid.add(settingsLabel("Server Root"), 0, 1);
        grid.add(settingsValue(server.getRootDir().toString()), 1, 1);
        grid.add(settingsLabel("MySQL URL"), 0, 2);
        grid.add(settingsValue(database.getUrl()), 1, 2);
        grid.add(settingsLabel("Database"), 0, 3);
        grid.add(settingsValue(database.databaseName()), 1, 3);
        grid.add(settingsLabel("Shared Folder"), 0, 4);
        grid.add(settingsValue(server.getRootDir().toAbsolutePath().toString()), 1, 4);

        panel.getChildren().addAll(panelHead, grid);
        box.getChildren().addAll(pageHeader, panel);
        return box;
    }

    // ================= STATUS BAR =================
    private HBox buildStatusBar() {
        HBox bar = new HBox(18);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 24, 8, 24));
        bar.getStyleClass().add("status-bar");

        lblMySql = new Label();
        lblMySql.getStyleClass().add("status-text");
        lblPort = new Label("Port: 2121");
        lblPort.getStyleClass().add("status-text");
        lblUptime = new Label("Uptime: 00:00:00");
        lblUptime.getStyleClass().add("status-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label version = new Label("FTP Server Manager v1.0");
        version.getStyleClass().add("status-text");
        bar.getChildren().addAll(lblMySql, lblPort, lblUptime, spacer, version);
        return bar;
    }

    private void updateMySqlState() {
        new Thread(() -> {
            boolean ok = database.testConnection();
            Platform.runLater(() -> setMySqlState(ok));
        }, "db-state").start();
    }

    private void setMySqlState(boolean ok) {
        lblMySql.setText(ok
                ? "● MySQL Connected (Lab2_ftp_db:3307)"
                : "● MySQL Disconnected (Lab2_ftp_db:3307)");
        lblMySql.getStyleClass().removeAll("mysql-connected", "mysql-disconnected");
        lblMySql.getStyleClass().add(ok ? "mysql-connected" : "mysql-disconnected");
        lblMySql.setTooltip(new Tooltip(ok ? database.getUrl()
                : "Không kết nối được MySQL port 3307.\n"
                        + "Kiểm tra XAMPP và db.properties.\n"
                        + "Chi tiết: " + database.getLastError()));
    }

    private void updateServerState() {
        boolean running = server.isRunning();
        lblServerState.setText(running ? "Online" : "Offline");
        lblServerState.getStyleClass().removeAll("status-online", "status-offline");
        lblServerState.getStyleClass().add(running ? "status-online" : "status-offline");
        statusDot.setFill(running ? Color.web("#22C55E") : Color.web("#EF4444"));
        btnStart.setDisable(running);
        btnStop.setDisable(!running);
    }

    private void startServer() {
        if (server.isRunning()) return;
        try {
            server.start();
        } catch (Exception ex) {
            showError("Không thể khởi động server", ex.getMessage());
        }
        updateServerState();
    }

    private void openClientWindow() {
        Platform.runLater(() -> {
            try {
                FTPClientApp clientApp = new FTPClientApp();
                Stage clientStage = new Stage();
                clientApp.start(clientStage);
            } catch (Exception ex) {
                showError("Không thể mở Client", ex.getMessage());
            }
        });
    }

    private void stopServer() {
        if (!server.isRunning()) return;
        server.stop();
        updateServerState();
    }

    private void refreshRuntime() {
        if (cardClients != null) cardClients.setText(String.valueOf(ServerStats.getActiveClients()));
        if (cardBytes != null) cardBytes.setText(formatBytes(ServerStats.getBytesTransferred()));
        if (cardUsers != null) cardUsers.setText(String.valueOf(ServerStats.getActiveUsers()));
        if (++tickCounter % 5 == 0 && cardLogs != null) {
            cardLogs.setText(String.valueOf(database.countLogsToday()));
        }
        if (tableLive != null) tableLive.getItems().setAll(ServerStats.snapshot());
        if (lblUptime != null) lblUptime.setText("Uptime: " + formatUptime(server.getUptimeSeconds()));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String formatUptime(long seconds) {
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    // ================= CRUD =================
    private void refreshCurrentView() {
        switch (currentMenu) {
            case USERS -> loadUsers();
            case LOGS -> applyLogFilter();
            default -> { }
        }
    }

    private void loadUsers() {
        if (tableUsers == null) return;
        List<DatabaseManager.UserRow> rows = database.getAllUsers().stream()
                .filter(u -> searchQuery.isEmpty()
                        || containsIgnoreCase(u.username(), searchQuery)
                        || containsIgnoreCase(u.permission(), searchQuery))
                .toList();
        tableUsers.getItems().setAll(rows);
    }

    private void loadLogs() {
        if (tableLogs == null) return;
        logCache = database.getLogRows();
        applyLogFilter();
    }

    private void applyLogFilter() {
        if (tableLogs == null) return;
        String filter = logFilter == null ? "ALL" : logFilter.getValue();
        List<DatabaseManager.LogRow> rows = logCache.stream()
                .filter(r -> filter == null || "ALL".equalsIgnoreCase(filter)
                        || filter.equalsIgnoreCase(r.action()))
                .filter(r -> searchQuery.isEmpty()
                        || containsIgnoreCase(r.username(), searchQuery)
                        || containsIgnoreCase(r.action(), searchQuery)
                        || containsIgnoreCase(r.filename(), searchQuery)
                        || containsIgnoreCase(r.ip(), searchQuery)
                        || containsIgnoreCase(r.status(), searchQuery))
                .toList();
        tableLogs.getItems().setAll(rows);
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }

    private void addUser() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Thêm user");
        ButtonType ok = new ButtonType("Thêm", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(18));
        TextField user = new TextField();
        PasswordField pass = new PasswordField();
        ComboBox<String> perm = new ComboBox<>();
        perm.getItems().addAll("read", "write", "admin");
        perm.setValue("read");
        grid.add(new Label("Username"), 0, 0);
        grid.add(user, 1, 0);
        grid.add(new Label("Password"), 0, 1);
        grid.add(pass, 1, 1);
        grid.add(new Label("Permission"), 0, 2);
        grid.add(perm, 1, 2);
        dialog.getDialogPane().setContent(grid);
        addDialogCss(dialog);

        dialog.showAndWait().ifPresent(r -> {
            if (r != ok) return;
            try {
                database.addUser(user.getText().trim(), pass.getText(), perm.getValue());
                loadUsers();
            } catch (SQLException ex) {
                showError("Không thêm được user", ex.getMessage());
            }
        });
    }

    private void editUser(DatabaseManager.UserRow selected) {
        if (selected == null) {
            showError("Chưa chọn user", "Hãy chọn một user trong bảng.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Sửa user: " + selected.username());
        ButtonType ok = new ButtonType("Lưu", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(18));
        ComboBox<String> perm = new ComboBox<>();
        perm.getItems().addAll("read", "write", "admin");
        perm.setValue(selected.permission());
        PasswordField pass = new PasswordField();
        pass.setPromptText("Để trống nếu không đổi");
        grid.add(new Label("Permission"), 0, 0);
        grid.add(perm, 1, 0);
        grid.add(new Label("New password"), 0, 1);
        grid.add(pass, 1, 1);
        dialog.getDialogPane().setContent(grid);
        addDialogCss(dialog);

        dialog.showAndWait().ifPresent(r -> {
            if (r != ok) return;
            try {
                database.updateUser(selected.id(), perm.getValue(), pass.getText());
                loadUsers();
            } catch (SQLException ex) {
                showError("Không cập nhật được user", ex.getMessage());
            }
        });
    }

    private void deleteUser(DatabaseManager.UserRow selected) {
        if (selected == null) {
            showError("Chưa chọn user", "Hãy chọn một user trong bảng.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Xóa user '" + selected.username() + "'?", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("Xác nhận xóa");
        addDialogCss(confirm);
        confirm.showAndWait().ifPresent(r -> {
            if (r != ButtonType.OK) return;
            try {
                database.deleteUser(selected.id());
                loadUsers();
            } catch (SQLException ex) {
                showError("Không xóa được user", ex.getMessage());
            }
        });
    }

    private void addDialogCss(Dialog<?> dialog) {
        URL css = getClass().getResource("/css/server-dark.css");
        if (css != null) dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, message);
            alert.setHeaderText(title);
            addDialogCss(alert);
            alert.showAndWait();
        });
    }

    // ================= SMALL UI HELPERS =================
    private Label label(String text, String styleClass) {
        Label l = new Label(text);
        l.getStyleClass().add(styleClass);
        return l;
    }

    private VBox pageTitle(String title, String subtitle) {
        return new VBox(2,
                label(title, "page-title"),
                label(subtitle, "page-subtitle"));
    }

    private Label settingsLabel(String text) {
        return label(text, "settings-label");
    }

    private Label settingsValue(String text) {
        Label l = label(text, "settings-value");
        l.setWrapText(true);
        return l;
    }

    private Button iconActionButton(FontAwesomeSolid icon, String tooltip, Runnable action) {
        Button b = new Button();
        b.getStyleClass().addAll("action-button", "icon-action-button");
        b.setGraphic(new FontIcon(icon));
        b.setTooltip(new Tooltip(tooltip));
        b.setOnAction(e -> action.run());
        return b;
    }

    private Button rowIconButton(FontAwesomeSolid icon, String tooltip, String styleClass, Runnable action) {
        Button b = new Button();
        b.getStyleClass().addAll("row-icon-button", styleClass);
        b.setGraphic(new FontIcon(icon));
        b.setTooltip(new Tooltip(tooltip));
        b.setOnAction(e -> action.run());
        return b;
    }

    private TableCell<DatabaseManager.UserRow, String> permissionBadgeCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(item.toUpperCase());
                    badge.getStyleClass().addAll("badge", "badge-" + item.toLowerCase());
                    setGraphic(badge);
                    setText(null);
                }
            }
        };
    }

    private <T> TableCell<T, String> statusBadgeCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    boolean good = "SUCCESS".equalsIgnoreCase(item)
                            || "ONLINE".equalsIgnoreCase(item)
                            || "ACTIVE".equalsIgnoreCase(item);
                    Label badge = new Label(item.toUpperCase());
                    badge.getStyleClass().addAll("badge", good ? "badge-success" : "badge-fail");
                    setGraphic(badge);
                    setText(null);
                }
            }
        };
    }

    public static void main(String[] args) {
        launch(args);
    }

    private enum MenuItemView {
        DASHBOARD("Dashboard", FontAwesomeSolid.CHART_BAR),
        USERS("Users", FontAwesomeSolid.USERS),
        LOGS("Logs", FontAwesomeSolid.CLIPBOARD_LIST),
        SETTINGS("Settings", FontAwesomeSolid.COG);

        final String label;
        final FontAwesomeSolid icon;

        MenuItemView(String label, FontAwesomeSolid icon) {
            this.label = label;
            this.icon = icon;
        }
    }
}
