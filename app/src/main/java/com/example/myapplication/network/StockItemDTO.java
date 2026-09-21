package com.example.myapplication.network;

/** Espejo de StockListDTO (backend): existencia esperada de un producto en el inventario. */
public class StockItemDTO {
    private long id;
    private String idProduct;
    private String description;
    private float stock;
    private long idInventory;

    public String getIdProduct() {
        return idProduct;
    }

    public String getDescription() {
        return description;
    }

    public float getStock() {
        return stock;
    }
}
