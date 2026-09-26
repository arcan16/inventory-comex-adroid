package com.example.myapplication.network;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ProductsApi {
    @GET("products")
    Call<PageResponse<ProductDTO>> getProducts(
            @Query("page") int page, @Query("size") int size, @Query("q") String query);

    @PUT("products/{id}")
    Call<ProductDTO> updateProduct(@Path("id") String id, @Body UpdateProductRequest request);

    @GET("products/{id}/presentations")
    Call<List<ProductPresentationDTO>> getPresentations(@Path("id") String id);

    /** 404 si ninguna presentacion tiene ese codigo. */
    @GET("products/presentations/barcode/{barcode}")
    Call<ProductPresentationDTO> getPresentationByBarcode(@Path("barcode") String barcode);

    @POST("products/{id}/presentations/barcode")
    Call<ProductPresentationDTO> assignBarcode(@Path("id") String id, @Body AssignBarcodeRequest request);

    @PUT("products/{id}/presentations/{presentationId}")
    Call<ProductPresentationDTO> updatePresentation(@Path("id") String id,
                                                    @Path("presentationId") long presentationId,
                                                    @Body UpdateProductPresentationRequest request);
}
