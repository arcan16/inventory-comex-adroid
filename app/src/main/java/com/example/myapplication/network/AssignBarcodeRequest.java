package com.example.myapplication.network;

/**
 * Cuerpo de POST /products/{id}/presentations/barcode (AssignBarcodeDTO en el
 * backend): asigna el codigo a esa presentacion ("1 LT", "4 LTS"...), creandola
 * si el producto aun no la tiene.
 */
public class AssignBarcodeRequest {
    private final String presentation;
    private final String barcode;

    public AssignBarcodeRequest(String presentation, String barcode) {
        this.presentation = presentation;
        this.barcode = barcode;
    }
}
