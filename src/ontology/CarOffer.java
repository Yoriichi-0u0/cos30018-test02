package ontology;

import jade.content.Concept;

public class CarOffer implements Concept {
    private String carModel;
    private float price;
    private int warranty;

    // Getters and Setters are required by JADE
    public String getCarModel() { return carModel; }
    public void setCarModel(String carModel) { this.carModel = carModel; }

    public float getPrice() { return price; }
    public void setPrice(float price) { this.price = price; }

    public int getWarranty() { return warranty; }
    public void setWarranty(int warranty) { this.warranty = warranty; }
}