package agents;

import gui.MainDashboardFX;
import gui.ManualUIFX;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

public class BuyerAgent extends Agent {
private boolean isManualMode = false;
private double maxBudget = 118000.0;
private Random rand = new Random();

private Map<AID, Double> offerMemory = new HashMap<>();
private Map<AID, Integer> warrantyMemory = new HashMap<>();
private Map<AID, Integer> minWarrantyThresholds = new HashMap<>();

private boolean lockedIn = false;

@Override
protected void setup() {
    // Register the SLCodec and Ontology
    Codec codec = new SLCodec();
    Ontology ontology = AuctionOntology.getInstance();
    getContentManager().registerLanguage(codec);
    getContentManager().registerOntology(ontology);

    // Read from the config file!
    java.util.Properties props = new java.util.Properties();
    try {
        props.load(new java.io.FileInputStream("src/config.properties"));
        maxBudget = Double.parseDouble(props.getProperty("buyer.default_budget", "118000.0"));
    } catch (Exception e) {
        System.out.println("Warning: Could not load config.properties. Using fallback defaults.");
        maxBudget = 118000.0;
    }

    Object[] args = getArguments();
    if (args != null && args.length > 0) {
        isManualMode = Boolean.parseBoolean(args[0].toString());
    }

    // 1. Buyer picks a car they want to buy
    String[] possibleCars = {
        "2019 Toyota Camry 2.5V", 
        "2021 Honda CR-V 1.5", 
        "2018 BMW 320i M Sport", 
        "2018 Mercedes C200", 
        "2020 Ford Ranger Wildtrak"
    };
    String targetCar = possibleCars[rand.nextInt(possibleCars.length)];
    MainDashboardFX.getInstance().log(getLocalName(), String.format("Asking Broker for ANY car near RM %,.2f budget", maxBudget));

    // 2. Ask the Broker who is selling it
    ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
    request.addReceiver(new AID("Broker", AID.ISLOCALNAME));
    request.setOntology("find-dealers");
    request.setContent(String.valueOf(maxBudget)); 
    send(request);

    // 3. Wait for the Broker's reply to get the shortlist
    addBehaviour(new Behaviour() {
        boolean done = false;
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchOntology("search-results");
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                if (!msg.getContent().equals("NONE")) {
                    String[] dealerNames = msg.getContent().split(",");
                    StringBuilder shortlistContent = new StringBuilder();
                    
                    for (String name : dealerNames) {
                        AID dealerAID = new AID(name, AID.ISLOCALNAME);
                        double startingOffer = 100000.0 + (rand.nextInt(6) * 1000); 
                        offerMemory.put(dealerAID, startingOffer); 
                        
                        // NEW: Randomize starting warranty (24 to 48 months)
                        int[] possibleWarranties = {24, 30, 36, 48};
                        int randomStartWarranty = possibleWarranties[rand.nextInt(possibleWarranties.length)];
                        warrantyMemory.put(dealerAID, randomStartWarranty); 

                        // NEW: Randomize individual threshold (12 to 24 months)
                        int[] possibleThresholds = {12, 18, 24};
                        minWarrantyThresholds.put(dealerAID, possibleThresholds[rand.nextInt(possibleThresholds.length)]);
                        
                        shortlistContent.append(name).append(":").append(startingOffer).append(";");
                    }
                    
                    // 4. Send Shortlist back to Broker to trigger engagement
                    ACLMessage shortlist = new ACLMessage(ACLMessage.PROPOSE);
                    shortlist.addReceiver(new AID("Broker", AID.ISLOCALNAME));
                    shortlist.setOntology("shortlist");
                    shortlist.setContent(shortlistContent.toString()); 
                    myAgent.send(shortlist);
                    MainDashboardFX.getInstance().log(getLocalName(), "Shortlisted " + dealerNames.length + " dealers within budget.");
                    
                    setupNegotiationResponder();
                } else {
                    MainDashboardFX.getInstance().log(getLocalName(), "Broker said no cars are available within budget.");
                }
                done = true;
            } else {
                block();
            }
        }
        @Override
        public boolean done() { return done; }
    });
}

private void setupNegotiationResponder() {
    jade.lang.acl.MessageTemplate template = jade.lang.acl.MessageTemplate.and(
            jade.lang.acl.MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET),
            jade.lang.acl.MessageTemplate.MatchPerformative(ACLMessage.CFP)
    );

    if (isManualMode) {
        addBehaviour(new jade.core.behaviours.CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage cfp = myAgent.receive(template);
                if (cfp != null) {
                    // NEW: Check if the proposal has expired
                    java.util.Date timeout = cfp.getReplyByDate();
                    if (timeout != null && timeout.before(new java.util.Date())) {
                        MainDashboardFX.getInstance().log(getLocalName(), "Proposal from " + cfp.getSender().getLocalName() + " expired! Ignoring.");
                        return; 
                    }

                    if (lockedIn) {
                        ACLMessage reply = cfp.createReply();
                        reply.setPerformative(ACLMessage.REFUSE);
                        myAgent.send(reply);
                        MainDashboardFX.getInstance().log(getLocalName(), "[DROPPING OUT] Already bought a car! Rejecting " + cfp.getSender().getLocalName());
                        return;
                    }

                    // NEW ONTOLOGY EXTRACTION
                    String dealerCar = "Unknown";
                    double dealerPrice = 0.0;
                    int dealerWarranty = 0;

                    try {
                        Action action = (Action) myAgent.getContentManager().extractContent(cfp);
                        CarOffer offer = (CarOffer) action.getAction();
                        dealerCar = offer.getCarModel();
                        dealerPrice = offer.getPrice();
                        dealerWarranty = offer.getWarranty();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                    MainDashboardFX.getInstance().log(getLocalName(), String.format("[EVALUATING] %s offers %s for RM%.2f (Warranty: %dmo)", 
                            cfp.getSender().getLocalName(), dealerCar, dealerPrice, dealerWarranty));

                    // Spawns the non-blocking window with car model!
                    ManualUIFX.spawnFloatingWindow(myAgent, cfp, dealerCar, dealerPrice, dealerWarranty, maxBudget);
                } else {
                    block();
                }
            }
        });
    } else {
        addBehaviour(new ContractNetResponder(this, template) {
            @Override
            protected ACLMessage handleCfp(ACLMessage cfp) {
                AID dealer = cfp.getSender();
                ACLMessage reply = cfp.createReply();
                if (lockedIn) {
                    reply.setPerformative(ACLMessage.REFUSE);
                    return reply;
                }

                // NEW ONTOLOGY EXTRACTION
                String dealerCar = "Unknown";
                double dealerPrice = 0.0;
                int dealerWarranty = 0;

                try {
                    Action action = (Action) myAgent.getContentManager().extractContent(cfp);
                    CarOffer offer = (CarOffer) action.getAction();
                    dealerCar = offer.getCarModel();
                    dealerPrice = offer.getPrice();
                    dealerWarranty = offer.getWarranty();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                MainDashboardFX.getInstance().log(getLocalName(), String.format("[EVALUATING] %s offers %s for RM%.2f (Warranty: %dmo)", 
                        dealer.getLocalName(), dealerCar, dealerPrice, dealerWarranty));

                double myOffer = offerMemory.get(dealer);
                int myWarranty = warrantyMemory.get(dealer);
                int myThreshold = minWarrantyThresholds.get(dealer);

                if (dealerPrice <= maxBudget && dealerWarranty >= myThreshold) {
                    lockedIn = true; 
                    reply.setPerformative(ACLMessage.PROPOSE);
                    
                    // NEW ONTOLOGY FILL
                    CarOffer finalProposal = new CarOffer();
                    finalProposal.setCarModel(dealerCar);
                    finalProposal.setPrice(dealerPrice);
                    finalProposal.setWarranty(dealerWarranty);
                    Action act = new Action(cfp.getSender(), finalProposal);
                    try {
                        myAgent.getContentManager().fillContent(reply, act);
                    } catch (Exception ex) { ex.printStackTrace(); }

                    MainDashboardFX.getInstance().log(getLocalName(), "[MATCH FOUND] Buying " + dealer.getLocalName() + "'s " + dealerCar + "!");
                } else {
                    myOffer += (myOffer * 0.04); 
                    myOffer = Math.round(myOffer);
                    if (myOffer > maxBudget) myOffer = Math.round(maxBudget);
                    myWarranty -= 2;
                    
                    offerMemory.put(dealer, myOffer);
                    warrantyMemory.put(dealer, myWarranty);
                    
                    reply.setPerformative(ACLMessage.PROPOSE);
                    
                    // NEW ONTOLOGY FILL
                    CarOffer counter = new CarOffer();
                    counter.setCarModel(dealerCar);
                    counter.setPrice(myOffer);
                    counter.setWarranty(myWarranty);
                    Action act = new Action(cfp.getSender(), counter);
                    try {
                        myAgent.getContentManager().fillContent(reply, act);
                    } catch (Exception ex) { ex.printStackTrace(); }

                    MainDashboardFX.getInstance().log(getLocalName(), String.format("[COUNTER] Proposing RM%.2f to %s", myOffer, dealer.getLocalName()));
                }
                return reply;
            }
        });
    }
}
}