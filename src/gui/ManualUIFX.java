package gui;

import jade.content.onto.basic.Action;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ontology.CarOffer;

public class ManualUIFX {

// Helps arrange multiple windows side-by-side so they don't overlap!
private static int windowOffset = 0; 

// NEW: Asynchronous floating window method including car model
public static void spawnFloatingWindow(Agent agent, ACLMessage cfp, String carModel, double dealerPrice, int dealerWarranty, double maxBudget) {
    Platform.runLater(() -> {
        Stage stage = new Stage();
        stage.setTitle("Nego: " + cfp.getSender().getLocalName());

        // Auto-arrange windows neatly across the screen
        stage.setX(100 + (windowOffset * 400));
        stage.setY(200);
        windowOffset = (windowOffset + 1) % 3; // Cycles 0, 1, 2 for the 3 dealers

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #2a2a35;");

        Label dealerHeader = new Label("Dealer's Current Offer:");
        dealerHeader.setStyle("-fx-text-fill: #4daafc; -fx-font-size: 14px; -fx-font-weight: bold;");
        
        // DISPLAY: Car Model, Price, and Warranty
        Label infoLabel = new Label(String.format("%s asks:\nCar: %s\nRM %,.2f w/ %dmo warranty", 
                                    cfp.getSender().getLocalName(), carModel, dealerPrice, dealerWarranty));
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
        priceInput.setStyle("-fx-font-size: 14px;");

        Label warrantyHeading = new Label("Enter Desired Warranty (Months):");
        warrantyHeading.setStyle("-fx-text-fill: lightgray; -fx-font-size: 13px; -fx-font-weight: bold;");
        TextField warrantyInput = new TextField();
        warrantyInput.setPromptText("e.g. 24");
        warrantyInput.setStyle("-fx-font-size: 14px;");

        Button btnCounter = new Button("Submit Counter");
        btnCounter.setStyle("-fx-background-color: #1565c0; -fx-text-fill: white; -fx-font-weight: bold;");
        
        Button btnWalkAway = new Button("Walk Away");
        btnWalkAway.setStyle("-fx-background-color: #c62828; -fx-text-fill: white; -fx-font-weight: bold;");

        HBox btnBox = new HBox(15, btnCounter, btnWalkAway);
        btnBox.setAlignment(javafx.geometry.Pos.CENTER);
        btnBox.setPadding(new Insets(10, 0, 0, 0));

        btnCounter.setOnAction(e -> {
            try {
                double price = Double.parseDouble(priceInput.getText());
                int warranty = Integer.parseInt(warrantyInput.getText());
                
                // NEW: Use Ontology to build the reply
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
                } catch (Exception ex) { ex.printStackTrace(); }
                
                MainDashboardFX.getInstance().log(agent.getLocalName(), "[MANUAL] Sent counter-offer to " + cfp.getSender().getLocalName() + ": RM " + price + " w/ " + warranty + "mo");
                stage.close();
            } catch (NumberFormatException ex) {
                priceInput.setText("");
                priceInput.setPromptText("Invalid Number!");
            }
        });

        btnWalkAway.setOnAction(e -> {
            ACLMessage reply = cfp.createReply();
            reply.setPerformative(ACLMessage.REFUSE);
            agent.send(reply);
            MainDashboardFX.getInstance().log(agent.getLocalName(), "[MANUAL] Walked away from " + cfp.getSender().getLocalName());
            stage.close();
        });

        stage.setOnCloseRequest(e -> {
            ACLMessage reply = cfp.createReply();
            reply.setPerformative(ACLMessage.REFUSE);
            agent.send(reply);
            MainDashboardFX.getInstance().log(agent.getLocalName(), "[MANUAL] Walked away from " + cfp.getSender().getLocalName());
        });

        root.getChildren().addAll(dealerHeader, infoLabel, constraintHeader, budgetLabel, priceHeading, priceInput, warrantyHeading, warrantyInput, btnBox);
        
        Scene scene = new Scene(root, 380, 420);
        stage.setScene(scene);
        
        // Keep the negotiation windows floating nicely above the main dashboard
        stage.setAlwaysOnTop(true); 
        stage.show(); 
    });
}
}