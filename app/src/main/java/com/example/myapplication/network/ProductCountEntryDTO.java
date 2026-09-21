package com.example.myapplication.network;

/**
 * "productsCount" dentro de GET /inventories/normal/{id} es la entidad cruda
 * ProductCountsEntity serializada (no un DTO), con idProduct e idInventory
 * anidados como el objeto completo. Solo mapeamos lo que usa esta pantalla;
 * Gson ignora el resto del JSON (idInventory completo, place, etc.).
 */
public class ProductCountEntryDTO {
    private long id;
    private float quantity;
    private ProductRef idProduct;

    public long getId() {
        return id;
    }

    public float getQuantity() {
        return quantity;
    }

    public ProductRef getIdProduct() {
        return idProduct;
    }

    public static class ProductRef {
        private String id;
        private String description;

        public String getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }
    }
}
