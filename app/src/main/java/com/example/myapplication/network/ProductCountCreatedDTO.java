package com.example.myapplication.network;

/** Espejo de ProductCountsCreatedDTO (backend): respuesta de POST /productCounts. */
public class ProductCountCreatedDTO {
    private long id;
    private String idProduct;
    private String description;
    private float quantity;
    private String place;

    public long getId() {
        return id;
    }

    public String getIdProduct() {
        return idProduct;
    }

    public String getDescription() {
        return description;
    }

    public float getQuantity() {
        return quantity;
    }

    public String getPlace() {
        return place;
    }
}
