package com.example.myapplication.network;

/** Espejo de ProductCountsEntryDTO (backend): un renglon de "productsCount" dentro de GET /inventories/normal/{id}. */
public class ProductCountEntryDTO {
    private long id;
    private float quantity;
    private String place;
    private ProductRef idProduct;

    public long getId() {
        return id;
    }

    public float getQuantity() {
        return quantity;
    }

    public String getPlace() {
        return place;
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
