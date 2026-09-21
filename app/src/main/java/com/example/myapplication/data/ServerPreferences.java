package com.example.myapplication.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Guarda la IP/puerto del backend configurados por el usuario (no hay dominio:
 * la app se usa dentro de la red local del almacen, ver ServerConfigActivity)
 * y una lista corta de servidores usados recientemente para cambiar de red
 * sin volver a escribir la IP cada vez.
 */
public class ServerPreferences {

    private static final String PREFS_NAME = "server_config";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_RECENT = "recent_servers";

    public static final String DEFAULT_PORT = "8080";
    private static final int MAX_RECENT = 4;
    private static final String RECENT_SEPARATOR = ";";

    private final SharedPreferences prefs;

    public ServerPreferences(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean hasServerConfigured() {
        return !TextUtils.isEmpty(getHost());
    }

    public String getHost() {
        return prefs.getString(KEY_HOST, "");
    }

    public String getPort() {
        return prefs.getString(KEY_PORT, DEFAULT_PORT);
    }

    /** Ej. "http://192.168.1.72:8080/". Null si todavia no hay host guardado. */
    public String getBaseUrl() {
        if (!hasServerConfigured()) {
            return null;
        }
        return buildBaseUrl(getHost(), getPort());
    }

    public static String buildBaseUrl(String host, String port) {
        String effectivePort = TextUtils.isEmpty(port) ? DEFAULT_PORT : port;
        return "http://" + host.trim() + ":" + effectivePort.trim() + "/";
    }

    public void saveServer(String host, String port) {
        String effectivePort = TextUtils.isEmpty(port) ? DEFAULT_PORT : port;
        prefs.edit()
                .putString(KEY_HOST, host.trim())
                .putString(KEY_PORT, effectivePort.trim())
                .apply();
        addRecent(host.trim(), effectivePort.trim());
    }

    /** Mas reciente primero, formato "host:puerto". */
    public List<String> getRecentServers() {
        String raw = prefs.getString(KEY_RECENT, "");
        List<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) {
            return result;
        }
        for (String entry : raw.split(RECENT_SEPARATOR)) {
            if (!TextUtils.isEmpty(entry)) {
                result.add(entry);
            }
        }
        return result;
    }

    private void addRecent(String host, String port) {
        String entry = host + ":" + port;
        List<String> recent = getRecentServers();
        recent.remove(entry);
        recent.add(0, entry);
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }
        prefs.edit().putString(KEY_RECENT, TextUtils.join(RECENT_SEPARATOR, recent)).apply();
    }
}
