package com.example.myapplication.network;

/**
 * Cuerpo de PUT /products/{id}/presentations/{presentationId}
 * (UpdateProductPresentationDTO en el backend): actualiza la descripcion del
 * producto y el codigo de barras de esa presentacion. Un barcode vacio conserva
 * el codigo guardado, salvo que clearBarcode sea true (boton "Quitar codigo").
 */
public class UpdateProductPresentationRequest {
    private final String description;
    private final String barcode;
    private final boolean clearBarcode;

    public UpdateProductPresentationRequest(String description, String barcode, boolean clearBarcode) {
        this.description = description;
        this.barcode = barcode;
        this.clearBarcode = clearBarcode;
    }
}
