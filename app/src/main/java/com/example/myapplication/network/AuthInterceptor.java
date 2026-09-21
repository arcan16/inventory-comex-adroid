package com.example.myapplication.network;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Adjunta el JWT de la sesion activa a cada peticion saliente. La mayoria de
 * las rutas del backend exigen "Authorization: Bearer <token>"
 * (SecurityConfig: anyRequest().authenticated()), salvo /login y
 * /actuator/health.
 */
public class AuthInterceptor implements Interceptor {

    private final String token;

    public AuthInterceptor(String token) {
        this.token = token;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        if (TextUtils.isEmpty(token)) {
            return chain.proceed(original);
        }

        Request authorized = original.newBuilder()
                .header("Authorization", "Bearer " + token)
                .build();
        return chain.proceed(authorized);
    }
}
