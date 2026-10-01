package com.example.myapplication.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Lectura rapida de un archivo de respaldo (formato "comex-inventory-backup",
 * el mismo para el backend, esta app y la otra app de inventarios) para
 * validarlo y mostrar que contiene antes de importarlo o al guardarlo.
 */
public final class BackupFileInfo {

    public static final String FORMAT = "comex-inventory-backup";
    /** Ultima version del formato que esta app sabe importar. */
    public static final int MAX_VERSION = 1;
    public static final String MIME_TYPE = "application/json";

    public final int products;
    public final int presentations;
    public final int inventories;

    private BackupFileInfo(int products, int presentations, int inventories) {
        this.products = products;
        this.presentations = presentations;
        this.inventories = inventories;
    }

    /** IllegalArgumentException si no es un respaldo valido o es de una version mas nueva. */
    public static BackupFileInfo read(byte[] bytes) {
        JSONObject root;
        try {
            root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new IllegalArgumentException("El archivo no es un respaldo de inventarios");
        }
        if (!FORMAT.equals(root.optString("format"))) {
            throw new IllegalArgumentException("El archivo no es un respaldo de inventarios");
        }
        int version = root.optInt("version", 0);
        if (version < 1 || version > MAX_VERSION) {
            throw new IllegalArgumentException("Version de respaldo no soportada: " + version);
        }
        return new BackupFileInfo(length(root.optJSONArray("products")),
                length(root.optJSONArray("productPresentations")),
                length(root.optJSONArray("inventories")));
    }

    /** "comex-respaldo-20261001-1730.json". */
    public static String suggestedFileName() {
        return "comex-respaldo-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date()) + ".json";
    }

    private static int length(JSONArray array) {
        return array != null ? array.length() : 0;
    }
}
