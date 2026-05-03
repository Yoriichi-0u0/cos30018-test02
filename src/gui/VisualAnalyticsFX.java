package gui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.util.HashMap;
import java.util.Map;

public class VisualAnalyticsFX extends VBox {

    private TabPane chartsContainer;
    private Map<String, LineChart<Number, Number>> activeCharts = new HashMap<>();
    private Map<String, XYChart.Series<Number, Number>[]> activeSeries = new HashMap<>();

    private BarChart<String, Number> marketChart;
    private TableView<String[]> ledgerTable;

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
        chartsContainer.setStyle("-fx-background-color: transparent;");
        chartsContainer.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        // 2. Setup the Fixed Ledger (Left Side)
        setupLedgerPanel(); 
        setupMarketChart();

        // NEW UI ARCHITECTURE: Split the top half horizontally
        HBox topSection = new HBox(15);
        VBox.setVgrow(topSection, Priority.ALWAYS);

        // Put the Ledger in a styled container so it looks native
        VBox ledgerBox = new VBox(5);
        Label ledgerTitle = new Label("🏆 OFFICIAL LEDGER");
        ledgerTitle.setStyle("-fx-text-fill: #a0a0ab; -fx-font-weight: bold; -fx-font-size: 14px;");
        VBox.setVgrow(ledgerTable, Priority.ALWAYS);
        ledgerBox.getChildren().addAll(ledgerTitle, ledgerTable);
        
        // Give the ledger a fixed footprint, give the tabs the rest of the screen
        ledgerBox.setMinWidth(650);
        ledgerBox.setPrefWidth(650); 
        HBox.setHgrow(chartsContainer, Priority.ALWAYS);

        // Mount them side-by-side!
        topSection.getChildren().addAll(ledgerBox, chartsContainer);

        getChildren().addAll(topSection, marketChart);

        Platform.runLater(this::applyMarketAesthetic);
    }

    private void setupLedgerPanel() {
        ledgerTable = new TableView<>();
        ledgerTable.setStyle("-fx-background-color: #16161a; -fx-control-inner-background: #2a2a35; -fx-table-cell-border-color: #3b3b4a;");

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
            }
        });
    }

    private LineChart<Number, Number> createNewNegotiationChart(String title) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Round");
        xAxis.setTickLabelFill(javafx.scene.paint.Color.web("#a0a0ab"));

        // --- NEW: FORCING DISCRETE ROUNDS (WHOLE NUMBERS) ---
        xAxis.setMinorTickVisible(false);
        xAxis.setForceZeroInRange(true);
        xAxis.setTickUnit(1);
        xAxis.setTickLabelFormatter(new javafx.util.StringConverter<Number>() {
            @Override
            public String toString(Number object) {
                if (object.doubleValue() % 1 == 0) {
                    return String.format("%.0f", object.doubleValue());
                } else {
                    return "";
                }
            }

            @Override
            public Number fromString(String string) {
                return Double.parseDouble(string);
            }
        });
        // --------------------------------------------------

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Price (RM)");
        yAxis.setTickLabelFill(javafx.scene.paint.Color.web("#a0a0ab"));

        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(90000);  
        yAxis.setUpperBound(135000); 
        yAxis.setTickUnit(5000);     

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

            applyWidgetAesthetic(activeCharts.get(sessionKey));
        });
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
        });
    }

    public void clearCharts() {
        Platform.runLater(() -> {
            // Because the ledger is out of the tabs, we can just completely clear the TabPane safely!
            chartsContainer.getTabs().clear();
            activeCharts.clear();
            activeSeries.clear();
            
            ledgerTable.getItems().clear();
            marketChart.getData().clear(); 
            
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
}