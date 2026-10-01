package com.example.myapplication.data;

import android.os.Handler;
import android.os.Looper;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.ApiErrorUtils;
import com.example.myapplication.network.InventoriesApi;
import com.example.myapplication.network.InventoryDTO;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Mantiene un inventario marcado como en uso (LOCKED) mientras su conteo
 * (normal o guiado) esta abierto y la app en primer plano:
 * - App en primer plano (incluido al abrir el conteo): PUT /inventories/{id}/lock
 *   y renovacion cada minuto, para que otros dispositivos lo vean ocupado.
 * - App a segundo plano (Home, cambiar de app, apagar la pantalla, cerrarla desde
 *   recientes) o salir del conteo: PUT /inventories/{id}/unlock (vuelve a OPENED).
 * Las pantallas que se abren desde el conteo (Resumen, buscador de productos) no
 * lo liberan, porque la app sigue en primer plano y el conteo sigue abierto.
 *
 * Si la app muere sin avisar (o pierde la red), el backend libera el bloqueo
 * solo a los 3 minutos sin renovacion (InventoryLockService.LOCK_TIMEOUT).
 * Debe crearse en onCreate de la Activity del conteo.
 */
public class InventoryLock {

    public interface Listener {
        /** Otro usuario tiene el inventario (al entrar, o porque se perdio el bloqueo). */
        void onLockedByOther(@NonNull String message);

        void onSessionExpired();
    }

    /** Renovacion; debe ser bastante menor que el LOCK_TIMEOUT del backend (3 min). */
    private static final long HEARTBEAT_MS = 60_000;

    private final ComponentActivity activity;
    private final long inventoryId;
    private final ServerPreferences serverPreferences;
    private final SessionPreferences sessionPreferences;
    /** El bloqueo es por dispositivo: otro telefono no entra ni con el mismo usuario. */
    private final String deviceId;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable heartbeat = this::acquire;
    private boolean active = true;

    /** Observa toda la app: se dispara al pasar entre primer y segundo plano, no entre pantallas. */
    private final LifecycleObserver processObserver = new DefaultLifecycleObserver() {
        @Override
        public void onStart(@NonNull LifecycleOwner owner) {
            acquire();
        }

        @Override
        public void onStop(@NonNull LifecycleOwner owner) {
            release();
        }
    };

    public InventoryLock(@NonNull ComponentActivity activity, long inventoryId, @NonNull Listener listener) {
        this.activity = activity;
        this.inventoryId = inventoryId;
        this.serverPreferences = new ServerPreferences(activity);
        this.sessionPreferences = new SessionPreferences(activity);
        this.deviceId = new DevicePreferences(activity).getDeviceId();
        this.listener = listener;

        // Si la app ya esta en primer plano, este observador recibe onStart de inmediato y toma el bloqueo.
        ProcessLifecycleOwner.get().getLifecycle().addObserver(processObserver);
        activity.getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onPause(@NonNull LifecycleOwner owner) {
                // Saliendo del conteo: se suelta ya (y no hasta onDestroy, que llega despues de que
                // la lista de inventarios se recarga y la mostraria todavia en uso).
                if (activity.isFinishing() && active) {
                    active = false;
                    release();
                }
            }

            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                ProcessLifecycleOwner.get().getLifecycle().removeObserver(processObserver);
                // Al girar la pantalla la Activity se recrea y toma el bloqueo de nuevo: no se suelta.
                if (activity.isFinishing() && active) {
                    release();
                }
                active = false;
                handler.removeCallbacksAndMessages(null);
            }
        });
    }

    /** Toma o renueva el bloqueo; si sale bien, programa la siguiente renovacion. */
    private void acquire() {
        handler.removeCallbacks(heartbeat);
        if (!active || inventoryId <= 0 || !sessionPreferences.isLoggedIn()) {
            return;
        }
        api().lockInventory(inventoryId, deviceId).enqueue(new Callback<InventoryDTO>() {
            @Override
            public void onResponse(@NonNull Call<InventoryDTO> call, @NonNull Response<InventoryDTO> response) {
                if (!active) {
                    return;
                }
                if (response.code() == 401) {
                    listener.onSessionExpired();
                } else if (response.code() == 409) {
                    active = false;
                    listener.onLockedByOther(ApiErrorUtils.parseErrorMessage(response));
                } else {
                    // Exito, o un error que no es de bloqueo (p. ej. inventario cerrado): se reintenta en la renovacion.
                    scheduleHeartbeat();
                }
            }

            @Override
            public void onFailure(@NonNull Call<InventoryDTO> call, @NonNull Throwable t) {
                // Sin red no se saca al usuario del conteo: se reintenta en la siguiente renovacion.
                if (active) {
                    scheduleHeartbeat();
                }
            }
        });
    }

    private void scheduleHeartbeat() {
        handler.removeCallbacks(heartbeat);
        handler.postDelayed(heartbeat, HEARTBEAT_MS);
    }

    /** Suelta el bloqueo (sin esperar respuesta: la app puede estar yendose a segundo plano). */
    private void release() {
        handler.removeCallbacks(heartbeat);
        if (inventoryId <= 0 || !sessionPreferences.isLoggedIn()) {
            return;
        }
        api().unlockInventory(inventoryId, deviceId).enqueue(new Callback<InventoryDTO>() {
            @Override
            public void onResponse(@NonNull Call<InventoryDTO> call, @NonNull Response<InventoryDTO> response) {
                // Nada que hacer: si falla, el backend lo libera solo por falta de renovacion.
            }

            @Override
            public void onFailure(@NonNull Call<InventoryDTO> call, @NonNull Throwable t) {
                // Igual que arriba.
            }
        });
    }

    private InventoriesApi api() {
        return ApiClient.createInventoriesApi(serverPreferences.getBaseUrl(), sessionPreferences.getToken());
    }
}
