package com.example.myapplication.network;

import org.json.JSONObject;

import retrofit2.Response;

/**
 * Los controllers del backend responden errores como {"err": "mensaje"} (ver
 * MyIntegrityValidation-style responses de InventoryUploadController,
 * ProductCountsController, etc.). Retrofit no auto-parsea errorBody(), asi
 * que esto se hace a mano una sola vez y se reutiliza.
 */
public final class ApiErrorUtils {

    private ApiErrorUtils() {
    }

    public static String parseErrorMessage(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String raw = response.errorBody().string();
                JSONObject json = new JSONObject(raw);
                if (json.has("err")) {
                    return json.getString("err");
                }
                return raw;
            }
        } catch (Exception ignored) {
            // Cuerpo de error no es JSON; se usa el codigo HTTP como fallback abajo.
        }
        return "HTTP " + response.code();
    }
}
