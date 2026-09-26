package com.example.myapplication.network;

import java.util.Collections;
import java.util.List;

/**
 * Cuerpo de GET /inventories/normal/{id}: {"products":[...], "productsCount":[...],
 * "stock":[...], "barcodes":[...]}. "barcodes" son las presentaciones que ya
 * tienen codigo de barras, para buscar por codigo sin ir a la red.
 */
public class NormalInventoryDataDTO {
    private List<ProductDTO> products;
    private List<ProductCountEntryDTO> productsCount;
    private List<StockItemDTO> stock;
    private List<ProductPresentationDTO> barcodes;

    public List<ProductDTO> getProducts() {
        return products != null ? products : Collections.emptyList();
    }

    public List<ProductCountEntryDTO> getProductsCount() {
        return productsCount != null ? productsCount : Collections.emptyList();
    }

    public List<StockItemDTO> getStock() {
        return stock != null ? stock : Collections.emptyList();
    }

    public List<ProductPresentationDTO> getBarcodes() {
        return barcodes != null ? barcodes : Collections.emptyList();
    }
}
