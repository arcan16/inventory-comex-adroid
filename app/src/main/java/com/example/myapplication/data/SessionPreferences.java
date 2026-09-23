package com.example.myapplication.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.example.myapplication.util.JwtUtils;

/**
 * Guarda el token JWT y el usuario de la sesion activa. Se mantiene separado
 * de ServerPreferences porque son preocupaciones distintas: a que servidor le
 * hablamos vs. quien esta autenticado en el.
 */
public class SessionPreferences {

    private static final String PREFS_NAME = "session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USERNAME = "username";

    private final SharedPreferences prefs;

    public SessionPreferences(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * true solo si hay token guardado Y todavia no expira. La expiracion se
     * decodifica localmente (ver JwtUtils.isExpired) para no depender de una
     * llamada de red antes de mostrar cada pantalla.
     */
    public boolean isLoggedIn() {
        String token = getToken();
        return !TextUtils.isEmpty(token) && !JwtUtils.isExpired(token);
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, null);
    }

    public void saveSession(String token, String username) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_USERNAME, username)
                .apply();
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }
}
