package com.example.myapplication.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

/**
 * Identificador de esta instalacion de la app (UUID aleatorio, no un dato del
 * telefono). El bloqueo de inventarios es por dispositivo: con la misma cuenta en
 * dos telefonos, solo el que tiene el bloqueo puede usar el inventario.
 * Se guarda aparte de SessionPreferences para que cerrar sesion no lo cambie
 * (y el telefono pueda retomar su propio bloqueo al volver a entrar).
 */
public class DevicePreferences {

    private static final String PREFS_NAME = "device";
    private static final String KEY_DEVICE_ID = "device_id";

    private final SharedPreferences prefs;

    public DevicePreferences(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public synchronized String getDeviceId() {
        String id = prefs.getString(KEY_DEVICE_ID, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }
}
