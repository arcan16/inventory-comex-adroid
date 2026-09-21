package com.example.myapplication.network;

/**
 * Subconjunto de InventoriesDTO (backend) que la lista de inventarios
 * necesita mostrar. Gson ignora los campos del JSON que no se declaren aqui
 * (createdBy, status, lockedBy, etc.), asi que no hace falta mapearlos todos.
 */
public class InventoryDTO {
    private long id;
    private String date;
    private String presentation;

    public long getId() {
        return id;
    }

    public String getDate() {
        return date;
    }

    public String getPresentation() {
        return presentation;
    }
}
