package com.example.myapplication.scanner;

import androidx.annotation.NonNull;

import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.ApiErrorUtils;
import com.example.myapplication.network.AssignBarcodeRequest;
import com.example.myapplication.network.ProductPresentationDTO;
import com.example.myapplication.network.ProductsApi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Busqueda y asignacion de codigos de barras para los conteos (normal y
 * guiado). Busca primero en las presentaciones que llegan con el inventario
 * (GET /inventories/normal/{id} -> "barcodes"), inmediato y sin red, y si no
 * esta, en el servidor (GET /products/presentations/barcode/{codigo}).
 */
public class BarcodeLookup {

    public interface Callback {
        void onFound(@NonNull String code, @NonNull ProductPresentationDTO presentation);

        /** Ninguna presentacion tiene ese codigo (404). */
        void onNotFound(@NonNull String code);

        void onError(@NonNull String reason);

        void onSessionExpired();
    }

    private final String baseUrl;
    private final String token;
    private final Map<String, ProductPresentationDTO> presentationByBarcode = new HashMap<>();
    /** Descarta respuestas de busquedas anteriores (otra lectura o un reset del formulario). */
    private int generation;

    public BarcodeLookup(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
    }

    /** Reemplaza la lista local con la que llega al (re)cargar el inventario. */
    public void setKnownPresentations(List<ProductPresentationDTO> presentations) {
        presentationByBarcode.clear();
        for (ProductPresentationDTO presentation : presentations) {
            if (presentation.getBarcode() != null && !presentation.getBarcode().isEmpty()) {
                presentationByBarcode.put(presentation.getBarcode(), presentation);
            }
        }
    }

    /** Invalida la busqueda en curso: su respuesta se ignorara. */
    public void cancelPending() {
        generation++;
    }

    public void resolve(@NonNull String code, @NonNull Callback callback) {
        ProductPresentationDTO local = presentationByBarcode.get(code);
        if (local != null) {
            generation++;
            callback.onFound(code, local);
            return;
        }

        int requestGeneration = ++generation;
        api().getPresentationByBarcode(code).enqueue(new retrofit2.Callback<ProductPresentationDTO>() {
            @Override
            public void onResponse(@NonNull Call<ProductPresentationDTO> call,
                                   @NonNull Response<ProductPresentationDTO> response) {
                if (requestGeneration != generation) {
                    return;
                }
                if (response.code() == 401) {
                    callback.onSessionExpired();
                } else if (response.isSuccessful() && response.body() != null) {
                    presentationByBarcode.put(code, response.body());
                    callback.onFound(code, response.body());
                } else if (response.code() == 404) {
                    callback.onNotFound(code);
                } else {
                    callback.onError(ApiErrorUtils.parseErrorMessage(response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ProductPresentationDTO> call, @NonNull Throwable t) {
                if (requestGeneration != generation) {
                    return;
                }
                callback.onError(t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
            }
        });
    }

    /**
     * POST /products/presentations/barcode?productId=: asigna el codigo a esa
     * presentacion del producto (creandola si no existe). En exito, el codigo
     * queda en la lista local y se llama onFound.
     */
    public void assign(@NonNull String productId, @NonNull String presentation, @NonNull String code,
                       @NonNull Callback callback) {
        api().assignBarcode(productId, new AssignBarcodeRequest(presentation, code))
                .enqueue(new retrofit2.Callback<ProductPresentationDTO>() {
                    @Override
                    public void onResponse(@NonNull Call<ProductPresentationDTO> call,
                                           @NonNull Response<ProductPresentationDTO> response) {
                        if (response.code() == 401) {
                            callback.onSessionExpired();
                        } else if (response.isSuccessful() && response.body() != null) {
                            presentationByBarcode.put(code, response.body());
                            callback.onFound(code, response.body());
                        } else {
                            callback.onError(ApiErrorUtils.parseErrorMessage(response));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ProductPresentationDTO> call, @NonNull Throwable t) {
                        callback.onError(t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
                    }
                });
    }

    /** Compara presentaciones ignorando mayusculas y espacios ("4 lts" == "4 LTS"). */
    public static boolean isSamePresentation(String a, String b) {
        if (a == null || b == null) {
            return true; // Sin presentacion del inventario no hay contra que comparar.
        }
        return a.trim().replaceAll("\\s+", " ").equalsIgnoreCase(b.trim().replaceAll("\\s+", " "));
    }

    private ProductsApi api() {
        return ApiClient.createProductsApi(baseUrl, token);
    }
}
