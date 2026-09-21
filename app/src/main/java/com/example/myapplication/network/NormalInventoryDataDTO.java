package com.example.myapplication.network;

import java.util.Collections;
import java.util.List;

/** Cuerpo de GET /inventories/normal/{id}: {"products":[...], "productsCount":[...], "stock":[...]}. */
public class NormalInventoryDataDTO {
    private List<ProductDTO> products;
    private List<ProductCountEntryDTO> productsCount;
    private List<StockItemDTO> stock;

    public List<ProductDTO> getProducts() {
        return products != null ? products : Collections.emptyList();
    }

    public List<ProductCountEntryDTO> getProductsCount() {
        return productsCount != null ? productsCount : Collections.emptyList();
    }

    public List<StockItemDTO> getStock() {
        return stock != null ? stock : Collections.emptyList();
    }
}
