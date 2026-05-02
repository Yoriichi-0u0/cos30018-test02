package ontology;

import jade.content.onto.BasicOntology;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.schema.ConceptSchema;
import jade.content.schema.PrimitiveSchema;

public class AuctionOntology extends Ontology {
    public static final String VOCABULARY_NAME = "auction-ontology";
    private static Ontology instance = new AuctionOntology();
    
    public static Ontology getInstance() { 
        return instance; 
    }

    private AuctionOntology() {
        super(VOCABULARY_NAME, BasicOntology.getInstance());
        try {
            // Register the CarOffer concept
            add(new ConceptSchema("CarOffer"), CarOffer.class);
            ConceptSchema cs = (ConceptSchema) getSchema("CarOffer");
            cs.add("carModel", (PrimitiveSchema) getSchema(BasicOntology.STRING));
            cs.add("price", (PrimitiveSchema) getSchema(BasicOntology.FLOAT)); // JADE uses FLOAT for decimals
            cs.add("warranty", (PrimitiveSchema) getSchema(BasicOntology.INTEGER));
        } catch (OntologyException e) {
            e.printStackTrace();
        }
    }
}