package com.example.myapplication.network;

import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface InventoriesApi {
    @GET("inventories")
    Call<PageResponse<InventoryDTO>> getInventories(@Query("page") int page, @Query("size") int size);

    @DELETE("inventories/{id}")
    Call<Void> deleteInventory(@Path("id") long id);

    @GET("inventories/normal/{id}")
    Call<NormalInventoryDataDTO> getNormalInventoryData(@Path("id") long id);
}
