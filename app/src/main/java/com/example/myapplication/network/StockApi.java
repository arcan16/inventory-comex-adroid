package com.example.myapplication.network;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface StockApi {
    @GET("stock/{idInventory}")
    Call<List<StockItemDTO>> getStock(@Path("idInventory") long idInventory);
}
