package com.example.myapplication.network;

/**
 * Cuerpo de POST /productCounts/createProductAddCount (CreateProductCountDTO en el
 * backend). A diferencia de NewProductCountRequest, este endpoint da de alta el
 * producto en el catalogo (con "description") y crea el conteo en la misma llamada,
 * para el caso de un producto encontrado fisicamente pero no registrado por error humano.
 */
public class CreateProductCountRequest {
    private final long idInventory;
    private final String idProduct;
    private final String description;
    private final float quantity;
    private final String place;

    public CreateProductCountRequest(long idInventory, String idProduct, String description,
                                      float quantity, String place) {
        this.idInventory = idInventory;
        this.idProduct = idProduct;
        this.description = description;
        this.quantity = quantity;
        this.place = place;
    }
}
