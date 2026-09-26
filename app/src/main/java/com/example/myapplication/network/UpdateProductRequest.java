package com.example.myapplication.network;

/**
 * Cuerpo de PUT /products/{id} (UpdateProductDTO en el backend): solo la
 * descripcion. Los codigos de barras se editan por presentacion con
 * UpdateProductPresentationRequest.
 */
public class UpdateProductRequest {
    private final String description;

    public UpdateProductRequest(String description) {
        this.description = description;
    }
}
