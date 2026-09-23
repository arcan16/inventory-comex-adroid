package com.example.myapplication.network;

/** Respuesta de POST /productCounts/createProductAddCount: {"newProduct": ..., "countCreated": ...}. */
public class CreateProductCountResultDTO {
    private NewProduct newProduct;
    private CountCreated countCreated;

    public NewProduct getNewProduct() {
        return newProduct;
    }

    public CountCreated getCountCreated() {
        return countCreated;
    }

    public static class NewProduct {
        private String id;
        private String description;

        public String getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }
    }

    public static class CountCreated {
        private long id;
        private float quantity;
        private String place;

        public long getId() {
            return id;
        }

        public float getQuantity() {
            return quantity;
        }

        public String getPlace() {
            return place;
        }
    }
}
