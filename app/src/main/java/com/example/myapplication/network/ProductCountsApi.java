package com.example.myapplication.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ProductCountsApi {
    @POST("productCounts")
    Call<ProductCountCreatedDTO> addProductCount(@Body NewProductCountRequest request);
}
