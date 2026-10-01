package com.example.myapplication.network;

/** Espejo de ImportResultDTO (backend): resultado de POST /data/import. */
public class ImportResultDTO {
    private int productsCreated;
    private int productsUpdated;
    private int presentationsCreated;
    private int barcodesAssigned;
    private int barcodeConflicts;
    private int inventoriesCreated;
    private int stockRowsCreated;
    private int countsCreated;
    private int rowsSkipped;

    public int getProductsCreated() {
        return productsCreated;
    }

    public int getProductsUpdated() {
        return productsUpdated;
    }

    public int getPresentationsCreated() {
        return presentationsCreated;
    }

    public int getBarcodesAssigned() {
        return barcodesAssigned;
    }

    public int getBarcodeConflicts() {
        return barcodeConflicts;
    }

    public int getInventoriesCreated() {
        return inventoriesCreated;
    }

    public int getStockRowsCreated() {
        return stockRowsCreated;
    }

    public int getCountsCreated() {
        return countsCreated;
    }

    public int getRowsSkipped() {
        return rowsSkipped;
    }
}
