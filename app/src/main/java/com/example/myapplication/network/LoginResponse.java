package com.example.myapplication.network;

import com.google.gson.annotations.SerializedName;

/**
 * Respuesta de POST /login (JwtAuthenticationFilter.successfulAuthentication):
 * {"Authentication": "<jwt>", "message": "...", "username": "..."}.
 * El campo del token viene con "A" mayuscula en el JSON del backend.
 */
public class LoginResponse {

    @SerializedName("Authentication")
    private String token;

    private String message;
    private String username;

    public String getToken() {
        return token;
    }

    public String getMessage() {
        return message;
    }

    public String getUsername() {
        return username;
    }
}
