package agents;

import gui.MainDashboardFX;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.ContractNetInitiator;
import jade.proto.ContractNetResponder;

import jade.content.lang.Codec;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.basic.Action;
import ontology.AuctionOntology;
import ontology.CarOffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Vector;

public class BrokerAgent extends Agent {
    // NEW: Allow the UI to talk to the Broker directly
    public static BrokerAgent instance;
    
    // NEW: The Broker's Analytics Memory (Car Model -> List of Prices)
    private Map<String, List<Double>> marketData = new HashMap<>();

    // Global Title Registry to prevent double-buying across concurrent negotiations
    public static Set<String> securedBuyers = new HashSet<>();

    private Map<AID, String> inventoryCatalog = new HashMap<>();
    private double commissionRate = 0.02; // 2% commission
    private double negotiationFee = 50.0; // Fixed fee per negotiation
    private int negotiationsCount = 0;
    private double totalCommission = 0.0;

    @Override
    protected void setup() {
        instance = this; // Link the instance!
        marketData.clear(); // Reset data on startup

        // Register the SLCodec and Ontology
        Codec codec = new SLCodec();
        Ontology ontology = AuctionOntology.getInstance();
        getContentManager().registerLanguage(codec);
        getContentManager().registerOntology(ontology);

        securedBuyers.clear(); // Reset market lock for new simulation runs
        registerWithDF();

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = myAgent.receive();
                if (msg != null) {
                    String ontology = msg.getOntology();
                    
                    if ("register-inventory".equals(ontology)) {
                        // Dealer sends: "2018 BMW 320i M Sport,125000"
                        inventoryCatalog.put(msg.getSender(), msg.getContent());
                        
                        String[] parts = msg.getContent().split(",");
                        MainDashboardFX.getInstance().log("BROKER", "Cataloged " + msg.getSender().getLocalName() + " selling " + parts[0] + " at RM " + parts[1]);
                        
                    } else if ("find-dealers".equals(ontology)) {
                        // Buyer sends their budget: e.g., "118000.0"
                        double buyerBudget = Double.parseDouble(msg.getContent());
                        List<String> shortlist = new ArrayList<>();
                        
                        // BROKER REASONING LOGIC: Find cars near the budget
                        for (Map.Entry<AID, String> entry : inventoryCatalog.entrySet()) {
                            String[] parts = entry.getValue().split(",");
                            double dealerAskingPrice = Double.parseDouble(parts[1]);
                            
                            // If the dealer's price is within RM 10,000 of the buyer's budget, they can negotiate!
                            if (dealerAskingPrice <= buyerBudget + 10000) {
                                shortlist.add(entry.getKey().getLocalName());
                                if (shortlist.size() >= 3) break; // Cap at 3 dealers
                            }
                        }
                        
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setOntology("search-results");
                        
                        if (!shortlist.isEmpty()) {
                            reply.setContent(String.join(",", shortlist));
                            MainDashboardFX.getInstance().log("BROKER", String.format("Found %d dealers matching RM %,.2f budget for %s", 
                                                                        shortlist.size(), buyerBudget, msg.getSender().getLocalName()));
                        } else {
                            reply.setContent("NONE");
                        }
                        myAgent.send(reply);
                        
                    } else if ("shortlist".equals(ontology)) {
                        String[] dealerOffers = msg.getContent().split(";");
                        for (String data : dealerOffers) {
                            if (data.contains(":")) {
                                String[] parts = data.split(":");
                                ACLMessage engageMsg = new ACLMessage(ACLMessage.REQUEST);
                                engageMsg.addReceiver(new AID(parts[0], AID.ISLOCALNAME));
                                engageMsg.setOntology("evaluate-buyer");
                                engageMsg.setContent(msg.getSender().getLocalName() + ":" + parts[1]);
                                myAgent.send(engageMsg);
                                negotiationsCount++;
                            }
                        }
                        
                    } else if ("buyer-approved".equals(ontology)) {
                        MainDashboardFX.getInstance().log("BROKER", "Dealer approved buyer: " + msg.getContent());

                    } else if ("deal-closed".equals(ontology)) {
                        // Read the new format: "Toyota Camry,115000.0"
                        String[] parts = msg.getContent().split(",");
                        String closedCar = parts[0];
                        double finalPrice = Double.parseDouble(parts[1]);

                        // 1. Save to Market Analytics Memory!
                        marketData.putIfAbsent(closedCar, new ArrayList<>());
                        marketData.get(closedCar).add(finalPrice);
                        
                        // Calculate and add commission
                        totalCommission += finalPrice * commissionRate;

                        // 2. Update Market Chart UI
                        MainDashboardFX.getInstance().log("BROKER", String.format("Deal closed for a %s at RM%,.2f", closedCar, finalPrice));
                        MainDashboardFX.getInstance().updateMarketChart(marketData);
                    }
                } else {
                    block();
                }
            }
        });
    }

    private void registerWithDF() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("car-broker");
            sd.setName("JADE-Auto-Auction");
            dfd.addServices(sd);
            DFService.register(this, dfd);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // NEW: Generates the Analytics Report
    public void generateReport() {
        StringBuilder report = new StringBuilder("\n📊 === BROKER MARKET ANALYTICS REPORT === 📊\n");

        if (marketData.isEmpty()) {
            report.append("No deals have been closed yet.\n");
        } else {
            for (Map.Entry<String, List<Double>> entry : marketData.entrySet()) {
                String car = entry.getKey();
                List<Double> prices = entry.getValue();

                double sum = 0;
                for (double p : prices) sum += p;
                double avg = sum / prices.size();

                report.append(String.format(" > %s | Units Sold: %d | Avg Selling Price: RM %,.2f\n", car, prices.size(), avg));
            }
        }
        
        double totalFees = negotiationsCount * negotiationFee;
        report.append(String.format("\nTotal Negotiations Facilitated: %d", negotiationsCount));
        report.append(String.format("\nTotal Fees Collected: RM %,.2f", totalFees));
        report.append(String.format("\nTotal Commission Earned: RM %,.2f", totalCommission));
        report.append(String.format("\nTotal Broker Earnings: RM %,.2f", totalFees + totalCommission));
        report.append("\n=============================================");
        MainDashboardFX.getInstance().log("BROKER", report.toString());
    }
}