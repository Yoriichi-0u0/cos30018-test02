package gui;

import analytics.NegotiationPredictor;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class VisualAnalyticsFX extends VBox {

    private TabPane chartsContainer;
    private Map<String, LineChart<Number, Number>> activeCharts = new HashMap<>();
    private Map<String, XYChart.Series<Number, Number>[]> activeSeries = new HashMap<>();
    private Map<String, NumberAxis> priceAxes = new HashMap<>();
    private Map<String, Double> chartMinPrices = new HashMap<>();
    private Map<String, Double> chartMaxPrices = new HashMap<>();
    private Map<String, NegotiationSnapshot> negotiationSnapshots = new HashMap<>();

    private BarChart<String, Number> marketChart;
    private TableView<String[]> ledgerTable;
    private ComboBox<String> predictionSelector;
    private Label predictionHintLabel;
    private Map<String, Label> predictionValueLabels = new HashMap<>();
    private double defaultBuyerBudget = readConfigDouble("buyer.default_budget", 118000.0);
    private double dealerReservePrice = readConfigDouble("dealer.min_price", 105000.0);

    // NEW: Centralized Color Dictionary for UI Syncing
    private final String[] PALETTE = {"#4daafc", "#ff6b81", "#fbc02d", "#2e7d32", "#9c27b0", "#ff9800", "#00bcd4"};
    private Map<String, String> carColorMap = new HashMap<>();
    private int colorCounter = 0;

    // Helper method to ensure a car model ALWAYS gets the same color everywhere
    private String getColorForCar(String carModel) {
        if (!carColorMap.containsKey(carModel)) {
            carColorMap.put(carModel, PALETTE[colorCounter % PALETTE.length]);
            colorCounter++;
        }
        return carColorMap.get(carModel);
    }

    public VisualAnalyticsFX() {
        setPadding(new Insets(15));
        setSpacing(15);
        setStyle("-fx-background-color: #16161a;");

        // 1. Setup the Negotiation Tabs (Right Side)
        chartsContainer = new TabPane();
        chartsContainer.setMinWidth(520);
        chartsContainer.setMinHeight(340);
        chartsContainer.setStyle("-fx-background-color: transparent;");

        // 2. Setup the Fixed Ledger (Left Side)
        setupLedgerPanel(); 
        setupMarketChart();

        // NEW UI ARCHITECTURE: Split the top half horizontally
        HBox topSection = new HBox(15);
        topSection.setMinHeight(360);
        VBox.setVgrow(topSection, Priority.ALWAYS);

        // Put the Ledger in a styled container so it looks native
        VBox ledgerBox = new VBox(5);
        Label ledgerTitle = new Label("Official Deal Ledger");
        ledgerTitle.setStyle("-fx-text-fill: #a0a0ab; -fx-font-weight: bold; -fx-font-size: 14px;");
        VBox.setVgrow(ledgerTable, Priority.ALWAYS);
        VBox predictionPanel = createPredictionPanel();
        ledgerBox.getChildren().addAll(ledgerTitle, ledgerTable, predictionPanel);
        
        // Give the ledger a fixed footprint, give the tabs the rest of the screen
        ledgerBox.setMinWidth(620);
        ledgerBox.setPrefWidth(620);
        HBox.setHgrow(chartsContainer, Priority.ALWAYS);

        // Mount them side-by-side!
        topSection.getChildren().addAll(ledgerBox, chartsContainer);

        getChildren().addAll(topSection, marketChart);

        Platform.runLater(this::applyMarketAesthetic);
    }

    private VBox createPredictionPanel() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(12));
        panel.setMinHeight(170);
        panel.setPrefHeight(235);
        panel.setMaxHeight(250);
        panel.setStyle("-fx-background-color: #202027; -fx-border-color: #3b3b4a; -fx-border-radius: 8px; -fx-background-radius: 8px;");

        Label title = new Label("Prediction Advisor");
        title.setStyle("-fx-text-fill: #fbc02d; -fx-font-size: 15px; -fx-font-weight: bold;");

        predictionSelector = new ComboBox<>();
        predictionSelector.setMaxWidth(Double.MAX_VALUE);
        predictionSelector.getItems().add("No negotiation data yet");
        predictionSelector.getSelectionModel().selectFirst();
        styleComboBox(predictionSelector);
        predictionSelector.setOnAction(e -> refreshPredictionAdvisor());

        predictionHintLabel = new Label("Run a demo lineup or start a negotiation to generate predictions.");
        predictionHintLabel.setWrapText(true);
        predictionHintLabel.setStyle("-fx-text-fill: #a0a0ab; -fx-font-size: 12px;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(4);
        grid.setPadding(new Insets(2, 6, 2, 0));
        addPredictionRow(grid, 0, "Buyer", "buyer");
        addPredictionRow(grid, 1, "Dealer", "dealer");
        addPredictionRow(grid, 2, "Vehicle", "vehicle");
        addPredictionRow(grid, 3, "Buyer offer", "buyerOffer");
        addPredictionRow(grid, 4, "Dealer ask", "dealerAsk");
        addPredictionRow(grid, 5, "Acceptance", "acceptance");
        addPredictionRow(grid, 6, "Predicted price", "predictedPrice");
        addPredictionRow(grid, 7, "Warranty", "warranty");
        addPredictionRow(grid, 8, "Action", "action");
        addPredictionRow(grid, 9, "Recommended dealer", "recommendedDealer");
        addPredictionRow(grid, 10, "Strategy", "strategy");

        ScrollPane predictionRowsScroll = new ScrollPane(grid);
        predictionRowsScroll.setFitToWidth(true);
        predictionRowsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        predictionRowsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        predictionRowsScroll.setPrefViewportHeight(125);
        predictionRowsScroll.setMaxHeight(140);
        predictionRowsScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-control-inner-background: #202027;");

        panel.getChildren().addAll(title, predictionSelector, predictionHintLabel, predictionRowsScroll);
        return panel;
    }

    private void addPredictionRow(GridPane grid, int row, String labelText, String key) {
        Label label = new Label(labelText + ":");
        label.setStyle("-fx-text-fill: #a0a0ab; -fx-font-size: 12px;");
        Label value = new Label("-");
        value.setWrapText(true);
        value.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");
        predictionValueLabels.put(key, value);
        grid.add(label, 0, row);
        grid.add(value, 1, row);
    }

    private void styleComboBox(ComboBox<String> comboBox) {
        comboBox.setStyle("-fx-background-color: #111116; -fx-border-color: #555566; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-font-size: 12px;");
        comboBox.setButtonCell(new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item);
                setTextFill(Color.WHITE);
                setStyle("-fx-background-color: #111116; -fx-text-fill: white;");
            }
        });
        comboBox.setCellFactory(list -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item);
                setTextFill(empty ? Color.TRANSPARENT : Color.WHITE);
                setStyle(empty
                        ? "-fx-background-color: #111116;"
                        : "-fx-background-color: #202027; -fx-text-fill: white; -fx-padding: 6px;");
            }
        });
    }

    private void setupLedgerPanel() {
        ledgerTable = new TableView<>();
        ledgerTable.setStyle("-fx-background-color: #16161a; -fx-control-inner-background: #2a2a35; "
                + "-fx-control-inner-background-alt: #242430; -fx-table-cell-border-color: #3b3b4a; "
                + "-fx-text-fill: white; -fx-selection-bar: #00838f; -fx-selection-bar-non-focused: #35515a;");

        TableColumn<String[], String> buyerCol = new TableColumn<>("Buyer Name");
        buyerCol.setPrefWidth(120);
        buyerCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[0]));

        TableColumn<String[], String> dealerCol = new TableColumn<>("Dealer Name");
        dealerCol.setPrefWidth(120);
        dealerCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[1]));

        TableColumn<String[], String> carCol = new TableColumn<>("Car Model");
        carCol.setPrefWidth(170);
        carCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[2]));

        TableColumn<String[], String> priceCol = new TableColumn<>("Final Price");
        priceCol.setPrefWidth(120);
        priceCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[3]));

        TableColumn<String[], String> warrantyCol = new TableColumn<>("Warranty");
        warrantyCol.setPrefWidth(100);
        warrantyCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[4]));

        ledgerTable.getColumns().addAll(buyerCol, dealerCol, carCol, priceCol, warrantyCol);

        // NEW: Color-Synced Hover Tooltips!
        ledgerTable.setRowFactory(tv -> {
            javafx.scene.control.TableRow<String[]> row = new javafx.scene.control.TableRow<>();
            Tooltip tooltip = new Tooltip();
            row.setStyle("-fx-background-color: #2a2a35; -fx-text-background-color: white;");

            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem != null) {
                    String carModel = newItem[2];
                    String syncColor = getColorForCar(carModel); // Get the matching color!

                    tooltip.setText("🏆 DEAL SUMMARY 🏆\n------------------------\nBuyer: " + newItem[0] + "\nDealer: " + newItem[1] + "\nCar: " + carModel + "\nAgreed Price: " + newItem[3] + "\nCoverage: " + newItem[4]);
                    // Apply dynamic color to the popup
                    tooltip.setStyle("-fx-background-color: " + syncColor + "; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10px;");
                    row.setTooltip(tooltip);
                } else {
                    row.setTooltip(null);
                }
            });
            return row;
        });

        // NEW: Click a row to instantly switch to its negotiation chart!
        ledgerTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                // Grab the Buyer (Index 0) and Dealer (Index 1) from the clicked row
                String targetTabTitle = newSelection[0] + " vs " + newSelection[1];

                // Search the tabs to find the matching chart and bring it to the front
                for (Tab tab : chartsContainer.getTabs()) {
                    if (tab.getText().equals(targetTabTitle)) {
                        chartsContainer.getSelectionModel().select(tab);
                        break; // Stop searching once we find it
                    }
                }
                if (predictionSelector != null && predictionSelector.getItems().contains(targetTabTitle)) {
                    predictionSelector.getSelectionModel().select(targetTabTitle);
                    refreshPredictionAdvisor();
                }
            }
        });
    }

    private LineChart<Number, Number> createNewNegotiationChart(String title) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Round");
        xAxis.setTickLabelFill(javafx.scene.paint.Color.web("#a0a0ab"));

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Price (RM)");
        yAxis.setTickLabelFill(javafx.scene.paint.Color.web("#a0a0ab"));
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(80000);
        yAxis.setUpperBound(140000);
        yAxis.setTickUnit(10000);

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        return chart;
    }

    private void setupMarketChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Car Model (Average Price)");
        xAxis.setTickLabelFill(javafx.scene.paint.Color.web("#a0a0ab"));

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Units Sold");
        yAxis.setTickLabelFill(javafx.scene.paint.Color.web("#a0a0ab"));

        // --- NEW: FORCING WHOLE NUMBERS ONLY ---
        yAxis.setMinorTickVisible(false); // Removes the tiny tick marks between numbers
        yAxis.setAutoRanging(true);      // Let it grow, but...
        yAxis.setForceZeroInRange(true); // Always start from 0
        
        // This is the magic part: it forces the tick labels to be integers
        yAxis.setTickUnit(1);
        yAxis.setTickLabelFormatter(new javafx.util.StringConverter<Number>() {
            @Override
            public String toString(Number object) {
                if (object.doubleValue() % 1 == 0) {
                    return String.format("%.0f", object.doubleValue());
                } else {
                    return ""; // Hide the label if it's a decimal
                }
            }

            @Override
            public Number fromString(String string) {
                return Double.parseDouble(string);
            }
        });
        // ---------------------------------------

        marketChart = new BarChart<>(xAxis, yAxis);
        marketChart.setTitle("Live Market Analytics");
        marketChart.setMinHeight(230);
        marketChart.setLegendVisible(false);
        marketChart.setAnimated(false);
        marketChart.setStyle("-fx-background-color: #16161a;");
        
        Platform.runLater(this::applyMarketAesthetic);
    }

    public void updateChart(String buyer, String dealer, String carModel, int round, double buyerOffer, double dealerAsk, int buyerWarranty, int dealerWarranty) {
        Platform.runLater(() -> {
            String sessionKey = buyer + " vs " + dealer;
            
            if (!activeCharts.containsKey(sessionKey)) {
                LineChart<Number, Number> newChart = createNewNegotiationChart(sessionKey);
                XYChart.Series<Number, Number> bSeries = new XYChart.Series<>();
                bSeries.setName("Buyer Offer");
                XYChart.Series<Number, Number> dSeries = new XYChart.Series<>();
                dSeries.setName("Dealer Ask");
                
                bSeries.nodeProperty().addListener((obs, o, n) -> {
                    if (n != null) n.setStyle("-fx-stroke: #ff6b81; -fx-stroke-width: 3px;");
                });
                dSeries.nodeProperty().addListener((obs, o, n) -> {
                    if (n != null) n.setStyle("-fx-stroke: #4daafc; -fx-stroke-width: 3px;");
                });
                
                newChart.getData().addAll(bSeries, dSeries);
                
                activeCharts.put(sessionKey, newChart);
                activeSeries.put(sessionKey, new XYChart.Series[]{bSeries, dSeries});
                priceAxes.put(sessionKey, (NumberAxis) newChart.getYAxis());
                
                Tab newTab = new Tab(sessionKey, newChart);
                newTab.setStyle("-fx-background-color: #2a2a35; -fx-text-base-color: white;");
                chartsContainer.getTabs().add(newTab);
                
                // Only select the new tab if we aren't completely flooded to prevent annoying jumps
                if (chartsContainer.getTabs().size() < 15) {
                    chartsContainer.getSelectionModel().select(newTab);
                }
            }

            XYChart.Data<Number, Number> bData = new XYChart.Data<>(round, buyerOffer);
            XYChart.Data<Number, Number> dData = new XYChart.Data<>(round, dealerAsk);

            boolean isDeal = Math.abs(buyerOffer - dealerAsk) < 0.01;
            
            if (isDeal) {
                styleDataPoint(bData, "#2e7d32", "Deal Successful!\nCar: " + carModel + "\nWarranty: " + buyerWarranty + " Months\nPrice:");
                styleDataPoint(dData, "#2e7d32", "Deal Successful!\nCar: " + carModel + "\nWarranty: " + dealerWarranty + " Months\nPrice:");
            } else {
                styleDataPoint(bData, "#ff6b81", "Buyer Offer\nCar: " + carModel + "\nWanted: " + buyerWarranty + " Months\nPrice:");
                styleDataPoint(dData, "#4daafc", "Dealer Ask\nCar: " + carModel + "\nOffering: " + dealerWarranty + " Months\nPrice:");
            }

            XYChart.Series<Number, Number>[] seriesPair = activeSeries.get(sessionKey);
            seriesPair[0].getData().add(bData);
            seriesPair[1].getData().add(dData);

            updatePriceAxis(sessionKey, buyerOffer, dealerAsk);
            updatePredictionSnapshot(sessionKey, buyer, dealer, carModel, round, buyerOffer, dealerAsk, buyerWarranty, dealerWarranty, isDeal);
            applyWidgetAesthetic(activeCharts.get(sessionKey));
        });
    }

    private void updatePriceAxis(String sessionKey, double buyerOffer, double dealerAsk) {
        NumberAxis yAxis = priceAxes.get(sessionKey);
        if (yAxis == null) {
            return;
        }

        double minPrice = Math.min(buyerOffer, dealerAsk);
        double maxPrice = Math.max(buyerOffer, dealerAsk);
        chartMinPrices.merge(sessionKey, minPrice, Math::min);
        chartMaxPrices.merge(sessionKey, maxPrice, Math::max);

        double chartMin = chartMinPrices.get(sessionKey);
        double chartMax = chartMaxPrices.get(sessionKey);
        double range = Math.max(1000.0, chartMax - chartMin);
        double padding = Math.max(3000.0, range * 0.14);
        double tickUnit = calculateTickUnit(range + (padding * 2.0));
        double lower = Math.max(0.0, Math.floor((chartMin - padding) / tickUnit) * tickUnit);
        double upper = Math.ceil((chartMax + padding) / tickUnit) * tickUnit;

        if (upper <= lower) {
            upper = lower + tickUnit;
        }
        yAxis.setLowerBound(lower);
        yAxis.setUpperBound(upper);
        yAxis.setTickUnit(tickUnit);
    }

    private double calculateTickUnit(double range) {
        if (range <= 15000) {
            return 2500;
        }
        if (range <= 35000) {
            return 5000;
        }
        if (range <= 80000) {
            return 10000;
        }
        return 20000;
    }

    private void styleDataPoint(XYChart.Data<Number, Number> data, String colorHex, String label) {
        data.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                newNode.setStyle("-fx-background-color: " + colorHex + ", #16161a; -fx-background-insets: 0, 2; -fx-padding: 5px;");
                
                Tooltip t = new Tooltip(String.format(label + " RM %,.2f", data.getYValue().doubleValue()));
                t.setStyle("-fx-background-color: " + colorHex + "; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");
                Tooltip.install(newNode, t);
                
                newNode.setOnMouseEntered(e -> {
                    newNode.setStyle("-fx-background-color: white, " + colorHex + "; -fx-background-insets: 0, 2; -fx-padding: 7px;");
                    newNode.toFront();
                });
                newNode.setOnMouseExited(e -> {
                    newNode.setStyle("-fx-background-color: " + colorHex + ", #16161a; -fx-background-insets: 0, 2; -fx-padding: 5px;");
                });
            }
        });
    }

    private void updatePredictionSnapshot(String sessionKey, String buyer, String dealer, String carModel,
                                          int round, double buyerOffer, double dealerAsk,
                                          int buyerWarranty, int dealerWarranty, boolean dealClosed) {
        NegotiationSnapshot snapshot = negotiationSnapshots.get(sessionKey);
        if (snapshot == null) {
            snapshot = new NegotiationSnapshot(buyer, dealer, carModel, buyerOffer, dealerAsk);
            negotiationSnapshots.put(sessionKey, snapshot);
        }

        snapshot.carModel = carModel;
        snapshot.round = round;
        snapshot.buyerOffer = buyerOffer;
        snapshot.dealerAsk = dealerAsk;
        snapshot.buyerWarranty = buyerWarranty;
        snapshot.dealerWarranty = dealerWarranty;
        snapshot.dealClosed = dealClosed;
        snapshot.inferredStrategy = NegotiationPredictor.inferDealerStrategy(
                round,
                snapshot.firstDealerAsk,
                dealerAsk,
                snapshot.firstBuyerOffer,
                buyerOffer
        );

        if (predictionSelector != null) {
            if (predictionSelector.getItems().size() == 1
                    && "No negotiation data yet".equals(predictionSelector.getItems().get(0))) {
                predictionSelector.getItems().clear();
            }
            if (!predictionSelector.getItems().contains(sessionKey)) {
                predictionSelector.getItems().add(sessionKey);
            }
            if (predictionSelector.getSelectionModel().getSelectedItem() == null
                    || "No negotiation data yet".equals(predictionSelector.getSelectionModel().getSelectedItem())) {
                predictionSelector.getSelectionModel().select(sessionKey);
            }
            if (sessionKey.equals(predictionSelector.getSelectionModel().getSelectedItem())) {
                refreshPredictionAdvisor();
            }
        }
    }

    private void refreshPredictionAdvisor() {
        if (predictionSelector == null) {
            return;
        }
        String selected = predictionSelector.getSelectionModel().getSelectedItem();
        NegotiationSnapshot snapshot = selected == null ? null : negotiationSnapshots.get(selected);
        if (snapshot == null) {
            predictionHintLabel.setText("Run a demo lineup or start a negotiation to generate predictions.");
            setPredictionValue("buyer", "-");
            setPredictionValue("dealer", "-");
            setPredictionValue("vehicle", "-");
            setPredictionValue("buyerOffer", "-");
            setPredictionValue("dealerAsk", "-");
            setPredictionValue("acceptance", "-");
            setPredictionValue("predictedPrice", "-");
            setPredictionValue("warranty", "-");
            setPredictionValue("action", "-");
            setPredictionValue("recommendedDealer", "-");
            setPredictionValue("strategy", "-");
            return;
        }

        NegotiationPredictor.Result result = predictFor(snapshot);
        setPredictionValue("buyer", snapshot.buyer);
        setPredictionValue("dealer", snapshot.dealer);
        setPredictionValue("vehicle", snapshot.carModel);
        setPredictionValue("buyerOffer", currency(snapshot.buyerOffer) + " / " + snapshot.buyerWarranty + "mo");
        setPredictionValue("dealerAsk", currency(snapshot.dealerAsk) + " / " + snapshot.dealerWarranty + "mo");
        setPredictionValue("acceptance", String.format("%.0f%%", result.acceptanceProbabilityPercent));
        setPredictionValue("predictedPrice", currency(result.predictedFinalPrice));
        setPredictionValue("warranty", result.predictedWarrantyMonths + "mo predicted");
        setPredictionValue("action", result.recommendedAction);
        setPredictionValue("recommendedDealer", recommendedDealerFor(snapshot.buyer));
        setPredictionValue("strategy", result.dealerStrategy + " (" + result.remainingRounds + " rounds left)");
        predictionHintLabel.setText(result.explanation);
    }

    private NegotiationPredictor.Result predictFor(NegotiationSnapshot snapshot) {
        double budget = Math.max(defaultBuyerBudget, snapshot.buyerOffer);
        double historicalAverage = historicalAverageForCar(snapshot.carModel);
        NegotiationPredictor.Input input = new NegotiationPredictor.Input(
                snapshot.buyer,
                snapshot.dealer,
                snapshot.carModel,
                budget,
                snapshot.buyerOffer,
                snapshot.dealerAsk,
                dealerReservePrice,
                snapshot.buyerWarranty,
                snapshot.dealerWarranty,
                snapshot.round,
                NegotiationPredictor.DEFAULT_MAX_ROUNDS,
                snapshot.inferredStrategy,
                historicalAverage
        );
        return NegotiationPredictor.predict(input);
    }

    private String recommendedDealerFor(String buyer) {
        String bestDealer = "-";
        double bestScore = Double.NEGATIVE_INFINITY;
        for (NegotiationSnapshot candidate : negotiationSnapshots.values()) {
            if (!buyer.equals(candidate.buyer)) {
                continue;
            }
            NegotiationPredictor.Result result = predictFor(candidate);
            if (result.recommendationScore > bestScore) {
                bestScore = result.recommendationScore;
                bestDealer = candidate.dealer + " (" + String.format("%.0f%%", result.acceptanceProbabilityPercent) + ")";
            }
        }
        return bestDealer;
    }

    private double historicalAverageForCar(String carModel) {
        if (ledgerTable == null || ledgerTable.getItems().isEmpty()) {
            return Double.NaN;
        }
        double total = 0.0;
        int count = 0;
        for (String[] row : ledgerTable.getItems()) {
            if (row.length >= 4 && carModel.equals(row[2])) {
                double value = parseCurrency(row[3]);
                if (!Double.isNaN(value)) {
                    total += value;
                    count++;
                }
            }
        }
        return count == 0 ? Double.NaN : total / count;
    }

    private void setPredictionValue(String key, String value) {
        Label label = predictionValueLabels.get(key);
        if (label != null) {
            label.setText(value);
        }
    }

    private static double parseCurrency(String value) {
        if (value == null) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(value.replace("RM", "").replace(",", "").trim());
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    private static String currency(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "-";
        }
        return String.format("RM %,.2f", value);
    }

    // NEW: Method to render the live market data with vibrant, distinct colors
    public void updateMarketChart(java.util.Map<String, java.util.List<Double>> marketData) {
        Platform.runLater(() -> {
            marketChart.getData().clear(); 
            
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            
            for (java.util.Map.Entry<String, java.util.List<Double>> entry : marketData.entrySet()) {
                String car = entry.getKey();
                java.util.List<Double> prices = entry.getValue();
                
                double sum = 0;
                for (double p : prices) sum += p;
                
                // Calculate both metrics
                double avg = sum / prices.size();
                int unitsSold = prices.size();
                
                // Swap the label: Now shows Car Name + Avg Price
                String label = String.format("%s\n(Avg: RM %,.2f)", car, avg);
                
                // Swap the Data Node: Bar height is now the Units Sold
                XYChart.Data<String, Number> dataNode = new XYChart.Data<>(label, unitsSold);
                
                // Fetch the synchronized color!
                final String barColor = getColorForCar(car);
                
                dataNode.nodeProperty().addListener((obs, oldN, newN) -> {
                    if (newN != null) {
                        newN.setStyle("-fx-bar-fill: " + barColor + ";"); 
                        
                        // Tooltip now shows BOTH Units Sold and Average Price
                        Tooltip t = new Tooltip(String.format("Units Sold: %d\nAvg Price: RM %,.2f", unitsSold, avg));
                        t.setStyle("-fx-background-color: " + barColor + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
                        Tooltip.install(newN, t);
                    }
                });

                series.getData().add(dataNode);
            }
            
            marketChart.getData().add(series);
        });
    }

    public void addDealToLedger(String buyer, String dealer, String carModel, double price, int warranty) {
        Platform.runLater(() -> {
            String priceStr = String.format("RM %,.2f", price);
            String warrantyStr = warranty + " Months";
            ledgerTable.getItems().add(new String[]{buyer, dealer, carModel, priceStr, warrantyStr});
            refreshPredictionAdvisor();
        });
    }

    public void clearCharts() {
        Platform.runLater(() -> {
            // Because the ledger is out of the tabs, we can just completely clear the TabPane safely!
            chartsContainer.getTabs().clear();
            activeCharts.clear();
            activeSeries.clear();
            priceAxes.clear();
            chartMinPrices.clear();
            chartMaxPrices.clear();
            negotiationSnapshots.clear();
            
            ledgerTable.getItems().clear();
            marketChart.getData().clear(); 
            if (predictionSelector != null) {
                predictionSelector.getItems().setAll("No negotiation data yet");
                predictionSelector.getSelectionModel().selectFirst();
            }
            refreshPredictionAdvisor();
            
            // Note: We deliberately do NOT clear the carColorMap so colors stay consistent across resets
        });
    }

    private void applyMarketAesthetic() {
        if (marketChart.lookup(".chart-plot-background") != null) marketChart.lookup(".chart-plot-background").setStyle("-fx-background-color: transparent;");
        if (marketChart.lookup(".chart-background") != null) marketChart.lookup(".chart-background").setStyle("-fx-background-color: transparent;");
        if (marketChart.lookup(".axis") != null) marketChart.lookup(".axis").setStyle("-fx-border-color: transparent;");
        if (marketChart.lookup(".chart-title") != null) marketChart.lookup(".chart-title").setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; -fx-alignment: center-left;");
    }

    private void applyWidgetAesthetic(LineChart<Number, Number> chart) {
        chart.applyCss();
        chart.layout();
        
        if (chart.lookup(".chart-plot-background") != null) chart.lookup(".chart-plot-background").setStyle("-fx-background-color: transparent;");
        if (chart.lookup(".chart-title") != null) chart.lookup(".chart-title").setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; -fx-alignment: center-left;");
        if (chart.lookup(".axis") != null) chart.lookup(".axis").setStyle("-fx-border-color: transparent;");
        
        for (Node gridLine : chart.lookupAll(".chart-vertical-grid-lines")) gridLine.setStyle("-fx-stroke: #2a2a35;");
        for (Node gridLine : chart.lookupAll(".chart-horizontal-grid-lines")) gridLine.setStyle("-fx-stroke: #2a2a35;");
        
        Node legend = chart.lookup(".chart-legend");
        if (legend != null) {
            legend.setStyle("-fx-background-color: transparent;");
            int i = 0;
            for (Node symbol : legend.lookupAll(".chart-legend-item-symbol")) {
                if (i == 0) symbol.setStyle("-fx-background-color: #ff6b81; -fx-background-radius: 10px;");
                if (i == 1) symbol.setStyle("-fx-background-color: #4daafc; -fx-background-radius: 10px;");
                i++;
            }
            for (Node label : legend.lookupAll(".chart-legend-item")) label.setStyle("-fx-text-fill: #a0a0ab;");
        }
    }

    private static double readConfigDouble(String key, double fallback) {
        Properties props = new Properties();
        try (java.io.FileInputStream in = new java.io.FileInputStream("src/config.properties")) {
            props.load(in);
            return Double.parseDouble(props.getProperty(key, String.valueOf(fallback)));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static final class NegotiationSnapshot {
        private final String buyer;
        private final String dealer;
        private final double firstBuyerOffer;
        private final double firstDealerAsk;
        private String carModel;
        private int round;
        private double buyerOffer;
        private double dealerAsk;
        private int buyerWarranty;
        private int dealerWarranty;
        private boolean dealClosed;
        private String inferredStrategy = "Matcher";

        private NegotiationSnapshot(String buyer, String dealer, String carModel,
                                    double firstBuyerOffer, double firstDealerAsk) {
            this.buyer = buyer;
            this.dealer = dealer;
            this.carModel = carModel;
            this.firstBuyerOffer = firstBuyerOffer;
            this.firstDealerAsk = firstDealerAsk;
        }
    }
}
