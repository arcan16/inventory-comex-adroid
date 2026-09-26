package com.example.myapplication.network;

/**
 * Espejo de ProductPresentationDTO (backend): una presentacion ("1 LT",
 * "4 LTS"...) de un producto con su codigo de barras y la descripcion del producto.
 */
public class ProductPresentationDTO {
    private Long id;
    private String productId;
    private String description;
    private String presentation;
    private String barcode;

    public Long getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public String getDescription() {
        return description;
    }

    public String getPresentation() {
        return presentation;
    }

    public String getBarcode() {
        return barcode;
    }
}
