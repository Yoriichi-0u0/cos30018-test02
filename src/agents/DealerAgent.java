package agents;

import analytics.NegotiationPredictor;

// 1. GUI Imports
import gui.MainDashboardFX;

// 2. JADE Core Imports
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.ContractNetInitiator;

// 3. JADE Ontology Imports (These are the ones your Dealer is missing!)
import jade.content.lang.Codec;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.basic.Action;
import ontology.AuctionOntology;
import ontology.CarOffer;

// 4. Java Util Imports
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

public class DealerAgent extends Agent {
    private String myCarModel;
    private double minPrice = 105000.0;
    private Random rand = new Random();

    private Map<AID, Double> priceMemory = new HashMap<>();
    private Map<AID, Double> firstOfferMemory = new HashMap<>();
    private Map<AID, Double> initialDealerPriceMemory = new HashMap<>();
    private Map<AID, Integer> warrantyMemory = new HashMap<>();
    private Map<AID, Integer> maxWarrantyThresholds = new HashMap<>();

    @Override
    protected void setup() {
        // Register the SLCodec and Ontology
        Codec codec = new SLCodec();
        Ontology ontology = AuctionOntology.getInstance();
        getContentManager().registerLanguage(codec);
        getContentManager().registerOntology(ontology);

        java.util.Properties props = new java.util.Properties();
        try {
            props.load(new java.io.FileInputStream("src/config.properties"));
            minPrice = Double.parseDouble(props.getProperty("dealer.min_price", "105000.0"));
        } catch (Exception e) {
            minPrice = 105000.0;
        }

        // 1. Dealer picks a random car to sell
        String[] possibleCars = {
            "2019 Toyota Camry 2.5V", 
            "2021 Honda CR-V 1.5", 
            "2018 BMW 320i M Sport", 
            "2018 Mercedes C200", 
            "2020 Ford Ranger Wildtrak"
        };
        myCarModel = possibleCars[rand.nextInt(possibleCars.length)];
        double stickerPrice = minPrice + 12000.0; // Dealer sets an initial high asking price

        // 2. Register this specific car with the Broker
        ACLMessage listing = new ACLMessage(ACLMessage.INFORM);
        listing.addReceiver(new AID("Broker", AID.ISLOCALNAME));
        listing.setOntology("register-inventory");
        
        // NEW: Send both the Model AND the Price to the Broker!
        listing.setContent(myCarModel + "," + stickerPrice); 
        send(listing);

        MainDashboardFX.getInstance().log(getLocalName(), "Registered inventory with Broker: " + myCarModel + " @ RM " + stickerPrice);

        MessageTemplate mt = MessageTemplate.MatchOntology("evaluate-buyer");
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = myAgent.receive(mt);
                if (msg != null) {
                    String[] parts = msg.getContent().split(":");
                    String buyerName = parts[0];
                    double firstOffer = Double.parseDouble(parts[1]);
                    AID buyerAID = new AID(buyerName, AID.ISLOCALNAME);
                    
                    if (firstOffer >= (minPrice * 0.75)) { 
                        double startingPrice = 120000.0 + (rand.nextInt(5) * 1000);
                        priceMemory.put(buyerAID, startingPrice);
                        firstOfferMemory.put(buyerAID, firstOffer);
                        initialDealerPriceMemory.put(buyerAID, startingPrice);
                        
                        // NEW: Randomize starting warranty offer (6 to 18 months)
                        int[] startingWarranties = {6, 12, 18};
                        int randomStartWarranty = clampWarranty(startingWarranties[rand.nextInt(startingWarranties.length)]);
                        warrantyMemory.put(buyerAID, randomStartWarranty); 

                        // NEW: Randomize individual max threshold (18 to 36 months)
                        int[] possibleThresholds = {18, 24, 30, 36};
                        maxWarrantyThresholds.put(buyerAID, possibleThresholds[rand.nextInt(possibleThresholds.length)]);
                        
                        ACLMessage approved = new ACLMessage(ACLMessage.INFORM);
                        approved.addReceiver(new AID("Broker", AID.ISLOCALNAME));
                        approved.setOntology("buyer-approved");
                        approved.setContent(buyerName);
                        myAgent.send(approved);
                        
                        // Round 0 Baseline Plotted Before Negotiation Starts
                        MainDashboardFX.getInstance().updateAnalytics(buyerName, getLocalName(), myCarModel, 0, firstOffer, startingPrice, 30, randomStartWarranty);
                        
                        initiateNegotiation(buyerAID, 1);
                    } else {
                        MainDashboardFX.getInstance().log(getLocalName(), "Ignored " + buyerName + " (Lowball)");
                    }
                } else {
                    block();
                }
            }
        });
    }

    private void initiateNegotiation(AID buyer, int round) {
        ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
        cfp.addReceiver(buyer);
        cfp.setProtocol(jade.domain.FIPANames.InteractionProtocol.FIPA_CONTRACT_NET);
        
        // NEW: Set a 2-minute deadline for the buyer to respond
        java.util.Date deadline = new java.util.Date(System.currentTimeMillis() + 120000);
        cfp.setReplyByDate(deadline); 

        // NEW ONTOLOGY SETUP
        cfp.setLanguage(new SLCodec().getName());
        cfp.setOntology(AuctionOntology.getInstance().getName());

        double currentPrice = priceMemory.get(buyer);
        int currentWarranty = clampWarranty(warrantyMemory.get(buyer));
        int roundForPrediction = Math.max(0, round - 1);
        String inferredStrategy = "Inferred " + NegotiationPredictor.inferDealerStrategy(
                roundForPrediction,
                initialDealerPriceMemory.getOrDefault(buyer, currentPrice),
                currentPrice,
                firstOfferMemory.getOrDefault(buyer, currentPrice),
                firstOfferMemory.getOrDefault(buyer, currentPrice)
        );
        cfp.addUserDefinedParameter("negotiation-round", String.valueOf(round));
        cfp.addUserDefinedParameter("dealer-strategy", inferredStrategy);
        
        CarOffer initialOffer = new CarOffer();
        initialOffer.setCarModel(myCarModel);
        initialOffer.setPrice(currentPrice);
        initialOffer.setWarranty(currentWarranty);

        Action act = new Action(buyer, initialOffer);
        try {
            getContentManager().fillContent(cfp, act);
        } catch (Exception ex) { ex.printStackTrace(); }

        MainDashboardFX.getInstance().log(getLocalName(), String.format("[Round %d] Sent CFP to %s: %s for RM%.2f w/ %dmo (2 min limit)", 
                                round, buyer.getLocalName(), myCarModel, currentPrice, currentWarranty));

        addBehaviour(new ContractNetInitiator(this, cfp) {
            @Override
            protected void handleAllResponses(Vector responses, Vector acceptances) {
                if (responses.isEmpty()) return;
                ACLMessage reply = (ACLMessage) responses.get(0);
                AID responder = reply.getSender();
                
                if (reply.getPerformative() == ACLMessage.PROPOSE) {
                    
                    // NEW ONTOLOGY EXTRACTION
                    double buyerOffer = 0.0;
                    int buyerWarrantyReq = 0;

                    try {
                        Action action = (Action) myAgent.getContentManager().extractContent(reply);
                        CarOffer offer = (CarOffer) action.getAction();
                        buyerOffer = offer.getPrice();
                        buyerWarrantyReq = clampWarranty(offer.getWarranty());
                    } catch (Exception ex) { ex.printStackTrace(); }
                    
                    double myPrice = priceMemory.get(responder);
                    int myWarranty = clampWarranty(warrantyMemory.get(responder));
                    int myMaxWarranty = clampWarranty(maxWarrantyThresholds.get(responder));

                    if (buyerOffer >= minPrice && buyerWarrantyReq <= myMaxWarranty) {
                        
                        synchronized (agents.BrokerAgent.securedBuyers) {
                            if (agents.BrokerAgent.securedBuyers.contains(responder.getLocalName())) {
                                MainDashboardFX.getInstance().log(getLocalName(), "[TRANSACTION VOIDED] " + responder.getLocalName() + " already bought a car elsewhere! Cancelling deal.");
                                ACLMessage reject = reply.createReply();
                                reject.setPerformative(ACLMessage.REJECT_PROPOSAL);
                                acceptances.add(reject);
                                return; 
                            }
                            agents.BrokerAgent.securedBuyers.add(responder.getLocalName());
                        }

                        // UPDATED: Pass car model down for the successful deal!
                        MainDashboardFX.getInstance().updateAnalytics(responder.getLocalName(), getLocalName(), myCarModel, round, buyerOffer, buyerOffer, buyerWarrantyReq, buyerWarrantyReq);
                        
                        ACLMessage accept = reply.createReply();
                        accept.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                        accept.setContent(buyerOffer + "," + buyerWarrantyReq);
                        acceptances.add(accept);
                        
                        MainDashboardFX.getInstance().log("SYSTEM", String.format(">>> WIN-WIN DEAL! %s sold to %s for RM%.2f <<<", getLocalName(), responder.getLocalName(), buyerOffer));
                        
                        // NEW: Send the deal details straight to the Admin Table!
                        MainDashboardFX.getInstance().recordSuccessfulDeal(responder.getLocalName(), getLocalName(), myCarModel, buyerOffer, buyerWarrantyReq);
                        
                        ACLMessage feeMsg = new ACLMessage(ACLMessage.INFORM);
                        feeMsg.addReceiver(new AID("Broker", AID.ISLOCALNAME));
                        feeMsg.setOntology("deal-closed");
                        feeMsg.setContent(myCarModel + "," + buyerOffer);
                        myAgent.send(feeMsg);

                    } else if (myPrice <= minPrice) {
                        MainDashboardFX.getInstance().updateAnalytics(responder.getLocalName(), getLocalName(), myCarModel, round, buyerOffer, myPrice, buyerWarrantyReq, myWarranty);
                        ACLMessage reject = reply.createReply();
                        reject.setPerformative(ACLMessage.REJECT_PROPOSAL);
                        // Make sure the counter-offer also includes the car model!
                        reject.setContent(myCarModel + "," + myPrice + "," + myWarranty); 
                        acceptances.add(reject);

                    } else if (round < 5) {
                        myPrice -= (myPrice * 0.03);
                        myPrice = Math.round(myPrice);
                        if (myPrice < minPrice) myPrice = Math.round(minPrice);

                        if (buyerWarrantyReq > myWarranty) {
                            int oldWarranty = myWarranty;
                            int targetWarranty = Math.min(buyerWarrantyReq, myMaxWarranty);
                            int step = (buyerWarrantyReq - myWarranty) > 8 ? 4 : 2;
                            myWarranty = clampWarranty(Math.min(myWarranty + step, targetWarranty));
                            int addedMonths = Math.max(0, myWarranty - oldWarranty);
                            if (addedMonths > 0) {
                                double warrantySurcharge = addedMonths * 500.0;
                                myPrice += warrantySurcharge;
                                MainDashboardFX.getInstance().log(getLocalName(), String.format(
                                        "Adjusted warranty from %dmo to %dmo toward buyer request of %dmo. Added RM%d for %d extra month(s).",
                                        oldWarranty, myWarranty, buyerWarrantyReq, (int) warrantySurcharge, addedMonths));
                            }
                            if (buyerWarrantyReq > myMaxWarranty && myWarranty == myMaxWarranty) {
                                MainDashboardFX.getInstance().log(getLocalName(), "Capped warranty at dealer maximum of " + myMaxWarranty + "mo.");
                            }
                        } else if (buyerWarrantyReq < myWarranty) {
                            MainDashboardFX.getInstance().log(getLocalName(), String.format(
                                    "Warranty already satisfies buyer request (%dmo offered vs %dmo requested). Focusing on price.",
                                    myWarranty, buyerWarrantyReq));
                        } else {
                            MainDashboardFX.getInstance().log(getLocalName(), "Warranty aligned at " + myWarranty + "mo. Focusing on price.");
                        }

                        myWarranty = clampWarranty(myWarranty);
                        priceMemory.put(responder, myPrice);
                        warrantyMemory.put(responder, myWarranty);

                        MainDashboardFX.getInstance().updateAnalytics(responder.getLocalName(), getLocalName(), myCarModel, round, buyerOffer, myPrice, buyerWarrantyReq, myWarranty);

                        ACLMessage reject = reply.createReply();
                        reject.setPerformative(ACLMessage.REJECT_PROPOSAL);
                        
                        // NEW ONTOLOGY FILL FOR COUNTER-OFFER
                        CarOffer counter = new CarOffer();
                        counter.setCarModel(myCarModel);
                        counter.setPrice(myPrice);
                        counter.setWarranty(myWarranty);
                        Action counterAct = new Action(responder, counter);
                        try {
                            myAgent.getContentManager().fillContent(reject, counterAct);
                        } catch (Exception ex) { ex.printStackTrace(); }
                        
                        acceptances.add(reject);
                        
                        initiateNegotiation(responder, round + 1);
                    } else {
                        MainDashboardFX.getInstance().updateAnalytics(responder.getLocalName(), getLocalName(), myCarModel, round, buyerOffer, myPrice, buyerWarrantyReq, myWarranty);
                        ACLMessage reject = reply.createReply();
                        reject.setPerformative(ACLMessage.REJECT_PROPOSAL);
                        acceptances.add(reject);
                    }
                }
            }
        });
    }

    private int clampWarranty(int months) {
        return Math.max(0, Math.min(72, months));
    }
}
