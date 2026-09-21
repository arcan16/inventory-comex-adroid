package com.example.myapplication.network;

/**
 * Cuerpo de POST /productCounts (NewProductCountDTO en el backend). "place"
 * es obligatorio ahi (@NotNull ProductLocation) aunque el frontend web
 * original no lo envia todavia - sin el, el backend responde 400.
 */
public class NewProductCountRequest {
    private final long idInventory;
    private final String idProduct;
    private final float quantity;
    private final String place;

    public NewProductCountRequest(long idInventory, String idProduct, float quantity, String place) {
        this.idInventory = idInventory;
        this.idProduct = idProduct;
        this.quantity = quantity;
        this.place = place;
    }
}
