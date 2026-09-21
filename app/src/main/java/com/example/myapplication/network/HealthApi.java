package com.example.myapplication.network;

import retrofit2.Call;
import retrofit2.http.GET;

/**
 * Unico endpoint que necesita la pantalla de configuracion de servidor: el
 * health check publico del backend Inventories (ver InventoryUploadController /
 * SecurityConfig - es la unica ruta que no exige JWT).
 */
public interface HealthApi {
    @GET("actuator/health")
    Call<HealthResponse> checkHealth();
}
