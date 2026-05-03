package gui;

import analytics.NegotiationPredictor;
import jade.content.onto.basic.Action;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ontology.CarOffer;

import java.util.Properties;

public class ManualUIFX {

// Helps arrange multiple windows side-by-side so they don't overlap!
private static int windowOffset = 0; 

// NEW: Asynchronous floating window method including car model
public static void spawnFloatingWindow(Agent agent, ACLMessage cfp, String carModel, double dealerPrice, int dealerWarranty, double maxBudget, int round, String strategyLabel) {
    Platform.runLater(() -> {
        Stage stage = new Stage();
        String dealerName = cfp.getSender().getLocalName();
        int currentRound = Math.max(1, round);
        String predictionStrategy = strategyLabel == null || strategyLabel.trim().isEmpty() ? "Inferred Matcher" : strategyLabel.trim();
        final boolean[] responded = {false};
        stage.setTitle("Manual Negotiation: " + dealerName);

        // Auto-arrange windows neatly across the screen
        stage.setX(100 + (windowOffset * 430));
        stage.setY(200);
        windowOffset = (windowOffset + 1) % 3; // Cycles 0, 1, 2 for the 3 dealers

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #2a2a35;");

        Label dealerHeader = new Label("Dealer's Current Offer:");
        dealerHeader.setStyle("-fx-text-fill: #4daafc; -fx-font-size: 14px; -fx-font-weight: bold;");
        
        // DISPLAY: Car Model, Price, and Warranty
        Label infoLabel = new Label(String.format("%s asks:\nCar: %s\nRound: %d\nRM %,.2f w/ %dmo warranty",
                                    dealerName, carModel, currentRound, dealerPrice, dealerWarranty));
        infoLabel.setStyle("-fx-text-fill: white; -fx-font-size: 15px;");
        infoLabel.setWrapText(true);

        Label constraintHeader = new Label("Your Profile Constraints:");
        constraintHeader.setStyle("-fx-text-fill: #ff6b81; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label budgetLabel = new Label(String.format("Max Budget Limit: RM %,.2f", maxBudget));
        budgetLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13px;");

        Label priceHeading = new Label("Enter Your Counter Price (RM):");
        priceHeading.setStyle("-fx-text-fill: lightgray; -fx-font-size: 13px; -fx-font-weight: bold;");
        TextField priceInput = new TextField();
        priceInput.setPromptText("e.g. 115000");
        priceInput.setText(String.format("%.0f", Math.min(maxBudget, dealerPrice * 0.96)));
        priceInput.setStyle(textFieldStyle());

        Label warrantyHeading = new Label("Enter Desired Warranty (Months):");
        warrantyHeading.setStyle("-fx-text-fill: lightgray; -fx-font-size: 13px; -fx-font-weight: bold;");
        TextField warrantyInput = new TextField();
        warrantyInput.setPromptText("e.g. 24");
        warrantyInput.setText(String.valueOf(Math.max(24, dealerWarranty)));
        warrantyInput.setStyle(textFieldStyle());

        Label validationLabel = new Label("");
        validationLabel.setWrapText(true);
        validationLabel.setStyle("-fx-text-fill: #ffb3b3; -fx-font-size: 12px; -fx-font-weight: bold;");

        Label predictionHeader = new Label("Prediction Advisor:");
        predictionHeader.setStyle("-fx-text-fill: #fbc02d; -fx-font-size: 14px; -fx-font-weight: bold;");
        Label predictionLabel = new Label();
        predictionLabel.setWrapText(true);
        predictionLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-background-color: #1e1e24; -fx-padding: 10px; -fx-background-radius: 6px;");

        Button btnCounter = new Button("Submit Counter");
        btnCounter.setStyle(buttonStyle("#1565c0", "white"));

        Button btnAccept = new Button("Accept Dealer Offer");
        btnAccept.setStyle(buttonStyle("#2e7d32", "white"));

        Button btnWalkAway = new Button("Walk Away");
        btnWalkAway.setStyle(buttonStyle("#c62828", "white"));

        HBox btnBox = new HBox(12, btnCounter, btnAccept, btnWalkAway);
        btnBox.setAlignment(Pos.CENTER);
        btnBox.setPadding(new Insets(10, 0, 0, 0));

        Runnable refreshPrediction = () -> {
            double currentOffer = parseDoubleOrFallback(priceInput.getText(), Math.min(maxBudget, dealerPrice * 0.96));
            int requestedWarranty = parseIntOrFallback(warrantyInput.getText(), Math.max(24, dealerWarranty));
            NegotiationPredictor.Result result = NegotiationPredictor.predict(new NegotiationPredictor.Input(
                    agent.getLocalName(),
                    dealerName,
                    carModel,
                    maxBudget,
                    currentOffer,
                    dealerPrice,
                    readConfigDouble("dealer.min_price", 105000.0),
                    requestedWarranty,
                    dealerWarranty,
                    currentRound,
                    NegotiationPredictor.DEFAULT_MAX_ROUNDS,
                    predictionStrategy,
                    Double.NaN
            ));
            predictionLabel.setText(String.format(
                    "Acceptance chance: %.0f%%\nPredicted final price: RM %,.2f\nPredicted warranty: %dmo\nRecommended action: %s\nStrategy estimate: %s\nReason: %s",
                    result.acceptanceProbabilityPercent,
                    result.predictedFinalPrice,
                    result.predictedWarrantyMonths,
                    result.recommendedAction,
                    predictionStrategy,
                    result.explanation
            ));
        };
        priceInput.textProperty().addListener((obs, oldValue, newValue) -> refreshPrediction.run());
        warrantyInput.textProperty().addListener((obs, oldValue, newValue) -> refreshPrediction.run());
        refreshPrediction.run();

        btnCounter.setOnAction(e -> {
            String validationError = validateCounter(priceInput.getText(), warrantyInput.getText(), maxBudget);
            if (validationError != null) {
                validationLabel.setText(validationError);
                return;
            }
            double price = Double.parseDouble(priceInput.getText().trim());
            int warranty = Integer.parseInt(warrantyInput.getText().trim());
            sendOffer(agent, cfp, carModel, price, warranty);
            responded[0] = true;
            MainDashboardFX.getInstance().log(agent.getLocalName(), "[MANUAL] Sent counter-offer to " + dealerName + ": RM " + String.format("%,.2f", price) + " w/ " + warranty + "mo");
            stage.close();
        });

        btnAccept.setOnAction(e -> {
            if (dealerPrice > maxBudget) {
                validationLabel.setText("Dealer ask is above your max budget. Send a lower counter or walk away.");
                MainDashboardFX.getInstance().log(agent.getLocalName(), "[MANUAL] Cannot accept " + dealerName + ": dealer ask exceeds max budget.");
                return;
            }
            sendOffer(agent, cfp, carModel, dealerPrice, dealerWarranty);
            responded[0] = true;
            MainDashboardFX.getInstance().log(agent.getLocalName(), "[MANUAL] Buyer accepted dealer current offer from " + dealerName + ": RM " + String.format("%,.2f", dealerPrice) + " w/ " + dealerWarranty + "mo");
            stage.close();
        });

        btnWalkAway.setOnAction(e -> {
            sendRefuse(agent, cfp);
            responded[0] = true;
            MainDashboardFX.getInstance().log(agent.getLocalName(), "[MANUAL] Walked away from " + dealerName);
            stage.close();
        });

        stage.setOnCloseRequest(e -> {
            if (!responded[0]) {
                sendRefuse(agent, cfp);
                responded[0] = true;
                MainDashboardFX.getInstance().log(agent.getLocalName(), "[MANUAL] Closed window and walked away from " + dealerName);
            }
        });

        root.getChildren().addAll(
                dealerHeader,
                infoLabel,
                constraintHeader,
                budgetLabel,
                priceHeading,
                priceInput,
                warrantyHeading,
                warrantyInput,
                validationLabel,
                predictionHeader,
                predictionLabel,
                btnBox
        );
        
        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background-color: #2a2a35; -fx-background: #2a2a35;");

        Scene scene = new Scene(scrollPane, 450, 580);
        stage.setScene(scene);
        
        // Keep the negotiation windows floating nicely above the main dashboard
        stage.setAlwaysOnTop(true); 
        stage.show(); 
    });
}

public static void showNoMatchWindow(String buyerName, double maxBudget) {
    Platform.runLater(() -> {
        Stage stage = new Stage();
        stage.setTitle("Manual Negotiation: No Matching Dealer");

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #2a2a35;");

        Label title = new Label("No matching dealer found");
        title.setStyle("-fx-text-fill: #fbc02d; -fx-font-size: 16px; -fx-font-weight: bold;");

        Label body = new Label(String.format(
                "%s could not find a dealer listing within the current RM %,.2f budget range.\n\nRun Auto Demo, spawn more dealers, or increase the buyer default budget in config.properties.",
                buyerName,
                maxBudget
        ));
        body.setWrapText(true);
        body.setStyle("-fx-text-fill: white; -fx-font-size: 13px;");

        Button close = new Button("Close");
        close.setStyle(buttonStyle("#1565c0", "white"));
        close.setOnAction(e -> stage.close());

        root.getChildren().addAll(title, body, close);
        stage.setScene(new Scene(root, 420, 230));
        stage.setAlwaysOnTop(true);
        stage.show();
    });
}

private static void sendOffer(Agent agent, ACLMessage cfp, String carModel, double price, int warranty) {
    ACLMessage reply = cfp.createReply();
    reply.setPerformative(ACLMessage.PROPOSE);

    CarOffer counter = new CarOffer();
    counter.setCarModel(carModel);
    counter.setPrice(price);
    counter.setWarranty(warranty);

    Action act = new Action(cfp.getSender(), counter);
    try {
        agent.getContentManager().fillContent(reply, act);
        agent.send(reply);
    } catch (Exception ex) {
        ex.printStackTrace();
        MainDashboardFX.getInstance().log(agent.getLocalName(), "[MANUAL] Failed to send counter-offer: " + ex.getMessage());
    }
}

private static void sendRefuse(Agent agent, ACLMessage cfp) {
    ACLMessage reply = cfp.createReply();
    reply.setPerformative(ACLMessage.REFUSE);
    agent.send(reply);
}

private static String validateCounter(String priceText, String warrantyText, double maxBudget) {
    if (priceText == null || priceText.trim().isEmpty()) {
        return "Enter a counter price before submitting.";
    }
    if (warrantyText == null || warrantyText.trim().isEmpty()) {
        return "Enter a desired warranty before submitting.";
    }

    double price;
    int warranty;
    try {
        price = Double.parseDouble(priceText.trim());
    } catch (NumberFormatException ex) {
        return "Counter price must be a valid number.";
    }
    try {
        warranty = Integer.parseInt(warrantyText.trim());
    } catch (NumberFormatException ex) {
        return "Warranty must be a whole number of months.";
    }

    if (price <= 0) {
        return "Counter price must be greater than zero.";
    }
    if (price > maxBudget) {
        return "Counter price cannot exceed your max budget.";
    }
    if (warranty < 0 || warranty > 72) {
        return "Warranty must be between 0 and 72 months.";
    }
    return null;
}

private static double parseDoubleOrFallback(String value, double fallback) {
    try {
        return Double.parseDouble(value.trim());
    } catch (Exception ex) {
        return fallback;
    }
}

private static int parseIntOrFallback(String value, int fallback) {
    try {
        return Integer.parseInt(value.trim());
    } catch (Exception ex) {
        return fallback;
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

private static String buttonStyle(String background, String textColor) {
    return "-fx-background-color: " + background + "; -fx-text-fill: " + textColor + "; -fx-font-weight: bold; "
            + "-fx-background-radius: 5px; -fx-border-color: transparent; -fx-padding: 8px 10px;";
}

private static String textFieldStyle() {
    return "-fx-font-size: 14px; -fx-background-color: #111116; -fx-text-fill: white; "
            + "-fx-prompt-text-fill: #a0a0ab; -fx-border-color: #555566; -fx-border-radius: 4px; -fx-background-radius: 4px;";
}
}
