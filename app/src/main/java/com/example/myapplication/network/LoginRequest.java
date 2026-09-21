package com.example.myapplication.network;

/**
 * Cuerpo de POST /login. Los nombres de campo deben coincidir exactamente con
 * UserEntity ("usuario"/"password"): JwtAuthenticationFilter.attemptAuthentication
 * deserializa el body directo a UserEntity con Jackson.
 */
public class LoginRequest {
    private final String usuario;
    private final String password;

    public LoginRequest(String usuario, String password) {
        this.usuario = usuario;
        this.password = password;
    }
}
