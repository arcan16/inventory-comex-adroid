package com.example.myapplication.network;

import android.text.TextUtils;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Construye clientes Retrofit apuntando a la IP:puerto que el usuario haya
 * configurado (ver ServerPreferences). No se cachea una unica instancia
 * "global" porque la base URL puede cambiar en cualquier momento desde
 * ServerConfigActivity; construir un Retrofit es barato.
 */
public final class ApiClient {

    /** Corto a proposito: "Probar conexion" necesita fallar rapido si la IP no responde. */
    private static final long DEFAULT_TIMEOUT_SECONDS = 8;
    /** Subir el CSV puede tardar mas en una red local lenta. */
    private static final long UPLOAD_TIMEOUT_SECONDS = 30;

    private ApiClient() {
    }

    public static HealthApi createHealthApi(String baseUrl) {
        return create(baseUrl, HealthApi.class, null, DEFAULT_TIMEOUT_SECONDS);
    }

    public static LoginApi createLoginApi(String baseUrl) {
        return create(baseUrl, LoginApi.class, null, DEFAULT_TIMEOUT_SECONDS);
    }

    /** token: JWT de la sesion activa (ver SessionPreferences); las rutas de Inventories lo exigen. */
    public static InventoriesApi createInventoriesApi(String baseUrl, String token) {
        return create(baseUrl, InventoriesApi.class, token, DEFAULT_TIMEOUT_SECONDS);
    }

    public static InventoryUploadApi createInventoryUploadApi(String baseUrl, String token) {
        return create(baseUrl, InventoryUploadApi.class, token, UPLOAD_TIMEOUT_SECONDS);
    }

    public static ProductCountsApi createProductCountsApi(String baseUrl, String token) {
        return create(baseUrl, ProductCountsApi.class, token, DEFAULT_TIMEOUT_SECONDS);
    }

    public static ProductsApi createProductsApi(String baseUrl, String token) {
        return create(baseUrl, ProductsApi.class, token, DEFAULT_TIMEOUT_SECONDS);
    }

    public static StockApi createStockApi(String baseUrl, String token) {
        return create(baseUrl, StockApi.class, token, DEFAULT_TIMEOUT_SECONDS);
    }

    private static <T> T create(String baseUrl, Class<T> service, String token, long timeoutSeconds) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(buildHttpClient(token, timeoutSeconds))
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        return retrofit.create(service);
    }

    private static OkHttpClient buildHttpClient(String token, long timeoutSeconds) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS);

        if (!TextUtils.isEmpty(token)) {
            builder.addInterceptor(new AuthInterceptor(token));
        }

        return builder.addInterceptor(logging).build();
    }
}
