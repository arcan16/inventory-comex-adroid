package com.example.myapplication.network;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * El id del producto se envia como parametro (?productId=) y nunca en la ruta:
 * hay ids con "/" (p. ej. "860/P5") que, codificados como %2F en la ruta, el
 * backend rechaza con HTTP 400 antes de procesar la peticion.
 */
public interface ProductsApi {
    @GET("products")
    Call<PageResponse<ProductDTO>> getProducts(
            @Query("page") int page, @Query("size") int size, @Query("q") String query);

    @PUT("products")
    Call<ProductDTO> updateProduct(@Query("productId") String productId, @Body UpdateProductRequest request);

    @GET("products/presentations")
    Call<List<ProductPresentationDTO>> getPresentations(@Query("productId") String productId);

    /** 404 si ninguna presentacion tiene ese codigo. */
    @GET("products/presentations/barcode/{barcode}")
    Call<ProductPresentationDTO> getPresentationByBarcode(@Path("barcode") String barcode);

    @POST("products/presentations/barcode")
    Call<ProductPresentationDTO> assignBarcode(@Query("productId") String productId,
                                              @Body AssignBarcodeRequest request);

    /**
     * El @Path va antes del @Query a proposito: Retrofit lanza IllegalArgumentException
     * ("A @Path parameter must not come after a @Query") al usar el metodo si se invierten.
     */
    @PUT("products/presentations/{presentationId}")
    Call<ProductPresentationDTO> updatePresentation(@Path("presentationId") long presentationId,
                                                    @Query("productId") String productId,
                                                    @Body UpdateProductPresentationRequest request);
}
