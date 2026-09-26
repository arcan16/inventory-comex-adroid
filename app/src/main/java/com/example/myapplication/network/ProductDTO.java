package com.example.myapplication.network;

/** Espejo de ProductsDTO (backend): catálogo completo de productos. */
public class ProductDTO {
    private String id;
    private String description;

    public ProductDTO() {
    }

    public ProductDTO(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }
}
