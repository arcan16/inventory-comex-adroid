package com.example.myapplication.network;

/**
 * Espejo de InventoryUploadResultDTO (backend): resultado de
 * POST /inventories/upload.
 */
public class InventoryUploadResultDTO {
    private long inventoryId;
    private String presentation;
    private int rowsProcessed;
    private int productsCreated;
    private int stockRowsCreated;

    public long getInventoryId() {
        return inventoryId;
    }

    public String getPresentation() {
        return presentation;
    }

    public int getRowsProcessed() {
        return rowsProcessed;
    }

    public int getProductsCreated() {
        return productsCreated;
    }

    public int getStockRowsCreated() {
        return stockRowsCreated;
    }
}
