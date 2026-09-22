package com.example.myapplication.network;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Streaming;

public interface ProductCountsApi {
    @POST("productCounts")
    Call<ProductCountCreatedDTO> addProductCount(@Body NewProductCountRequest request);

    @DELETE("productCounts/{id}")
    Call<Void> deleteProductCount(@Path("id") long id);

    @GET("productCounts/summary/{idInventory}")
    Call<PageResponse<CountsDifferenceDTO>> getSummary(
            @Path("idInventory") long idInventory, @Query("page") int page, @Query("size") int size);

    @Streaming
    @GET("productCounts/report/{idInventory}")
    Call<ResponseBody> downloadReport(@Path("idInventory") long idInventory);
}
