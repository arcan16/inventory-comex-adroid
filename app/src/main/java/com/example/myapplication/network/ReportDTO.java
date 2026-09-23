package com.example.myapplication.network;

/** Espejo de ReportsDTO (backend): un inventario con reporte disponible para descargar. */
public class ReportDTO {
    private long idInventory;
    private String inventoryDate;
    private String presentation;

    public long getIdInventory() {
        return idInventory;
    }

    public String getInventoryDate() {
        return inventoryDate;
    }

    public String getPresentation() {
        return presentation;
    }
}
