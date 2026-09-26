package com.example.myapplication.scanner;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.example.myapplication.R;
import com.google.mlkit.vision.barcode.common.Barcode;

/**
 * Escaneo de codigos de barras con la camara sin salir de la pantalla
 * (HeadlessBarcodeScanner), con todo lo que lo rodea: permiso de camara,
 * aviso de "apunta la camara", apagado automatico a los 20 s y al pausar la
 * Activity. Debe crearse en onCreate (registra el launcher del permiso).
 */
public class BarcodeScanHelper {

    public interface Listener {
        void onBarcodeScanned(@NonNull String value);

        /** Para reflejar en la UI que la camara esta leyendo (p. ej. cambiar el hint del campo). */
        default void onScanningChanged(boolean scanning) {
        }
    }

    /** Si en este tiempo no se lee ningun codigo, se apaga la camara. */
    private static final long SCAN_TIMEOUT_MS = 20_000;

    private final ComponentActivity activity;
    private final Listener listener;
    private final HeadlessBarcodeScanner scanner;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeoutRunnable = this::stop;
    private final ActivityResultLauncher<String> permissionLauncher;

    public BarcodeScanHelper(@NonNull ComponentActivity activity, @NonNull Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.scanner = new HeadlessBarcodeScanner(activity, activity);
        this.permissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        startScanner();
                    } else {
                        Toast.makeText(activity, R.string.product_barcode_camera_permission_denied,
                                Toast.LENGTH_LONG).show();
                    }
                });

        activity.getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onPause(@NonNull LifecycleOwner owner) {
                // Solo si la camara ya esta encendida: el dialogo del permiso tambien pausa la Activity.
                if (scanner.isRunning()) {
                    stop();
                }
            }

            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                handler.removeCallbacksAndMessages(null);
                scanner.release();
            }
        });
    }

    public boolean isRunning() {
        return scanner.isRunning();
    }

    /** Enciende la camara (pidiendo el permiso si hace falta), o la apaga si ya estaba leyendo. */
    public void toggle() {
        if (scanner.isRunning()) {
            stop();
        } else {
            start();
        }
    }

    public void start() {
        if (scanner.isRunning()) {
            return;
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startScanner();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    public void stop() {
        boolean wasRunning = scanner.isRunning();
        handler.removeCallbacks(timeoutRunnable);
        scanner.stop();
        if (wasRunning) {
            listener.onScanningChanged(false);
        }
    }

    private void startScanner() {
        Toast.makeText(activity, R.string.product_barcode_scanning, Toast.LENGTH_SHORT).show();
        handler.postDelayed(timeoutRunnable, SCAN_TIMEOUT_MS);
        scanner.start(new HeadlessBarcodeScanner.Listener() {
            @Override
            public void onBarcode(@NonNull Barcode barcode) {
                handler.removeCallbacks(timeoutRunnable);
                listener.onScanningChanged(false);
                listener.onBarcodeScanned(barcode.getRawValue());
            }

            @Override
            public void onError(@NonNull Exception error) {
                // HeadlessBarcodeScanner ya se detuvo solo; se avisa a la UI directamente.
                handler.removeCallbacks(timeoutRunnable);
                listener.onScanningChanged(false);
                String reason = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
                Toast.makeText(activity, activity.getString(R.string.product_barcode_scan_error, reason),
                        Toast.LENGTH_LONG).show();
            }
        });
        listener.onScanningChanged(true);
    }
}
