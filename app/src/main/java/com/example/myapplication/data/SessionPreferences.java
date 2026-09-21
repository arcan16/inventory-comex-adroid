package com.example.myapplication.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

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

    public boolean isLoggedIn() {
        return !TextUtils.isEmpty(getToken());
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
