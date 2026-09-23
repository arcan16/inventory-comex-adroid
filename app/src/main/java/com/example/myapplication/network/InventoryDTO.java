package com.example.myapplication.network;

/**
 * Subconjunto de InventoriesDTO (backend) que la lista de inventarios
 * necesita mostrar. Gson ignora los campos del JSON que no se declaren aqui
 * (createdBy, lockedBy, etc.), asi que no hace falta mapearlos todos.
 */
public class InventoryDTO {
    private long id;
    private String date;
    private String presentation;
    private String status;

    public long getId() {
        return id;
    }

    public String getDate() {
        return date;
    }

    public String getPresentation() {
        return presentation;
    }

    /** Uno de los valores del enum InventoryStatus del backend: OPENED, LOCKED, PENDING, CLOSED. */
    public String getStatus() {
        return status;
    }
}
