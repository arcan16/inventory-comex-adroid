package com.example.myapplication.network;

/** Espejo de CountsdifferenceDTO (backend): renglon de GET /productCounts/summary/{idInventory}. */
public class CountsDifferenceDTO {
    private long id;
    private String description;
    private String idProduct;
    private float stock;
    private double sum;
    private double difference;

    public long getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public String getIdProduct() {
        return idProduct;
    }

    public float getStock() {
        return stock;
    }

    public double getSum() {
        return sum;
    }

    public double getDifference() {
        return difference;
    }
}
