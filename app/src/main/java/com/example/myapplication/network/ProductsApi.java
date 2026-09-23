package com.example.myapplication.network;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ProductsApi {
    @GET("products")
    Call<PageResponse<ProductDTO>> getProducts(
            @Query("page") int page, @Query("size") int size, @Query("q") String query);
}
