package gui;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MainDashboardFX extends Application {

    private static MainDashboardFX instance;
    private ContainerController container;
    private ListView<String> logList;
    private VisualAnalyticsFX analyticsPanel;

    // The Dedicated ID Counters
    private AtomicInteger dealerCounter = new AtomicInteger(1);
    private AtomicInteger buyerCounter = new AtomicInteger(1);
    private boolean isBrokerSpawned = false;
    private List<AgentController> activeAgents = new ArrayList<>();

    public static MainDashboardFX getInstance() {
        return instance;
    }

    @Override
    public void start(Stage primaryStage) {
        instance = this;
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #16161a;");

        // --- Top Control Bar ---
        HBox controlBox = new HBox(10);
        controlBox.setPadding(new Insets(10));
        controlBox.setStyle("-fx-background-color: #28282d;");

        Button btnStartJade = new Button("1. Start JADE Platform");
        Button btnLaunchSniffer = new Button("2. Start Sniffer");
        Button btnSpawnScenario = new Button("3. Auto Demo");
        Button btnDealer = new Button("4. Spawn Dealer");
        Button btnBuyerAuto = new Button("5. Spawn Buyer");
        Button btnBuyerManual = new Button("6. Manual Nego Platform");
        Button btnResetMarket = new Button("7. Reset Market");
        Button btnReport = new Button("8. Market Report");

        styleButton(btnStartJade, "#2e7d32", "white");
        styleButton(btnLaunchSniffer, "#6a1b9a", "white");
        styleButton(btnSpawnScenario, "#00838f", "white");
        styleButton(btnDealer, "#1565c0", "white");
        styleButton(btnBuyerAuto, "#e65100", "white");
        styleButton(btnBuyerManual, "#e65100", "white");
        styleButton(btnResetMarket, "#c62828", "white");
        styleButton(btnReport, "#fbc02d", "black");

        controlBox.getChildren().addAll(btnStartJade, btnLaunchSniffer, btnSpawnScenario, btnDealer, btnBuyerAuto, btnBuyerManual, btnResetMarket, btnReport);
        ScrollPane controlScroll = new ScrollPane(controlBox);
        controlScroll.setFitToHeight(true);
        controlScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        controlScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        controlScroll.setStyle("-fx-background-color: #28282d; -fx-background: #28282d;");
        root.setTop(controlScroll);

        // --- Center Area (SplitPane) ---
        logList = new ListView<>();
        logList.setStyle("-fx-control-inner-background: #1e1e22; -fx-text-fill: #96fa96; -fx-font-family: 'Consolas';");
        logList.setCellFactory(list -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item);
                setTextFill(empty ? Color.TRANSPARENT : Color.web("#96fa96"));
                setStyle("-fx-background-color: #1e1e22; -fx-font-family: 'Consolas'; -fx-font-size: 12px;");
            }
        });
        
        analyticsPanel = new VisualAnalyticsFX();
        ScrollPane analyticsScroll = new ScrollPane(analyticsPanel);
        analyticsScroll.setFitToWidth(true);
        analyticsScroll.setFitToHeight(true);
        analyticsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        analyticsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        analyticsScroll.setStyle("-fx-background-color: #16161a; -fx-background: #16161a;");

        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        splitPane.getItems().addAll(logList, analyticsScroll);
        splitPane.setDividerPositions(0.25); 

        root.setCenter(splitPane);

        // --- Button Actions ---
        btnStartJade.setOnAction(e -> startJADE());
        btnLaunchSniffer.setOnAction(e -> spawnAgent("Sniffer", "jade.tools.sniffer.Sniffer", new Object[]{"*"}));
        
        btnDealer.setOnAction(e -> spawnAgent("Dealer_" + dealerCounter.getAndIncrement(), "agents.DealerAgent", new Object[]{}));
        btnBuyerAuto.setOnAction(e -> spawnAgent("AutoBuyer_" + buyerCounter.getAndIncrement(), "agents.BuyerAgent", new Object[]{"false"}));
        btnBuyerManual.setOnAction(e -> spawnAgent("ManualBuyer_" + buyerCounter.getAndIncrement(), "agents.BuyerAgent", new Object[]{"true"}));
        btnResetMarket.setOnAction(e -> resetMarket());
        btnReport.setOnAction(e -> {
            if (agents.BrokerAgent.instance != null) {
                agents.BrokerAgent.instance.generateReport();
            } else {
                log("SYSTEM", "Broker is offline. Spawn the Broker first!");
            }
        });

        // The Auto-Spawn Macro Thread
        btnSpawnScenario.setOnAction(e -> {
            new Thread(() -> {
                if (!isBrokerSpawned) {
                    Platform.runLater(() -> spawnAgent("Broker", "agents.BrokerAgent", new Object[]{}));
                    isBrokerSpawned = true;
                    try { Thread.sleep(1500); } catch (Exception ex) {} 
                } else {
                    log("SYSTEM", "Broker already running. Skipping spawn.");
                }
                
                for (int i = 1; i <= 3; i++) {
                    Platform.runLater(() -> spawnAgent("Dealer_" + dealerCounter.getAndIncrement(), "agents.DealerAgent", new Object[]{}));
                }
                try { Thread.sleep(1500); } catch (Exception ex) {} 
                
                for (int i = 1; i <= 5; i++) {
                    Platform.runLater(() -> spawnAgent("AutoBuyer_" + buyerCounter.getAndIncrement(), "agents.BuyerAgent", new Object[]{"false"}));
                    try { Thread.sleep(300); } catch (Exception ex) {} 
                }
                log("SYSTEM", "Auto demo loaded: 1 broker, 3 dealers, and 5 buyers. Dashboard view remains active.");
            }).start();
        });

        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setTitle("COS30018 Broker Exchange - JavaFX");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public void resetMarket() {
        try {
            for (AgentController ac : activeAgents) {
                try {
                    ac.kill();
                } catch (Exception ignored) {}
            }
            activeAgents.clear();
            dealerCounter.set(1);
            buyerCounter.set(1);
            isBrokerSpawned = false;
            Platform.runLater(() -> analyticsPanel.clearCharts());
            log("SYSTEM", "Market Reset: All agents terminated and counters cleared.");
        } catch (Exception ex) {
            log("ERROR", "Reset failed: " + ex.getMessage());
        }
    }

    private void startJADE() {
        new Thread(() -> {
            try {
                Runtime rt = Runtime.instance();
                Profile p = new ProfileImpl();
                p.setParameter(Profile.MAIN_HOST, "localhost");
                p.setParameter(Profile.MAIN_PORT, "1099");
                p.setParameter(Profile.GUI, "false");
                container = rt.createMainContainer(p);
                log("SYSTEM", "JADE Platform Started.");
            } catch (Exception ex) {
                log("ERROR", "Failed to start JADE: " + ex.getMessage());
            }
        }).start();
    }

    private void spawnAgent(String nickname, String className, Object[] args) {
        if (container == null) {
            log("ERROR", "Start JADE first!");
            return;
        }
        try {
            AgentController ac = container.createNewAgent(nickname, className, args);
            ac.start();
            activeAgents.add(ac); // Track the active agent
            if ("Broker".equals(nickname)) {
                isBrokerSpawned = true;
            }
            log("SYSTEM", "Spawned: " + nickname);
        } catch (Exception ex) {
            log("ERROR", "Failed to spawn " + nickname + ": " + ex.getMessage());
        }
    }

    public void log(String agent, String message) {
        Platform.runLater(() -> {
            String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            logList.getItems().add(String.format("[%s] [%s] %s", time, agent, message));
            logList.scrollTo(logList.getItems().size() - 1);
        });
    }

    public void updateAnalytics(String buyerName, String dealerName, String carModel, int round, double buyerOffer, double dealerAsk, int buyerWarranty, int dealerWarranty) {
        analyticsPanel.updateChart(buyerName, dealerName, carModel, round, buyerOffer, dealerAsk, buyerWarranty, dealerWarranty);
    }
    
    public void updateMarketChart(java.util.Map<String, java.util.List<Double>> marketData) {
        if (analyticsPanel != null) {
            analyticsPanel.updateMarketChart(marketData);
        }
    }

    public void recordSuccessfulDeal(String buyer, String dealer, String carModel, double price, int warranty) {
        analyticsPanel.addDealToLedger(buyer, dealer, carModel, price, warranty);
    }

    private void styleButton(Button button, String backgroundColor, String textColor) {
        button.setFocusTraversable(false);
        button.setMinHeight(34);
        button.setStyle("-fx-background-color: " + backgroundColor + "; -fx-text-fill: " + textColor + "; "
                + "-fx-font-weight: bold; -fx-background-radius: 5px; -fx-border-color: transparent; "
                + "-fx-padding: 7px 10px;");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
