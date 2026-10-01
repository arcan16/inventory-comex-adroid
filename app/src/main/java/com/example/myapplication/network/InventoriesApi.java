package com.example.myapplication.network;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface InventoriesApi {
    /**
     * Mas recientes primero: por fecha y, entre inventarios del mismo dia, por id
     * (orden de carga). Se ordena en el servidor porque la lista es paginada.
     */
    @GET("inventories?sort=inventoryDate,desc&sort=id,desc")
    Call<PageResponse<InventoryDTO>> getInventories(@Query("page") int page, @Query("size") int size);

    @GET("inventories/allByType/{type}")
    Call<List<InventoryDTO>> getInventoriesByType(@Path("type") String type);

    @PUT("inventories/{id}/close")
    Call<InventoryDTO> closeInventory(@Path("id") long id);

    @PUT("inventories/{id}/reopen")
    Call<InventoryDTO> reopenInventory(@Path("id") long id);

    @DELETE("inventories/{id}")
    Call<Void> deleteInventory(@Path("id") long id);

    @GET("inventories/normal/{id}")
    Call<NormalInventoryDataDTO> getNormalInventoryData(@Path("id") long id);
}
