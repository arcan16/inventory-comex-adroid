package com.example.myapplication.scanner;

import android.content.Context;
import android.media.Image;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lee códigos de barra con la cámara trasera sin mostrar nunca la imagen:
 * solo se vincula el caso de uso ImageAnalysis de CameraX (no hay Preview).
 * La cámara se apaga en cuanto se lee el primer código o al llamar a {@link #stop()}.
 */
public class HeadlessBarcodeScanner {

    public interface Listener {
        void onBarcode(@NonNull Barcode barcode);
        void onError(@NonNull Exception error);
    }

    private final Context context;
    private final LifecycleOwner owner;
    private final Executor mainExecutor;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final BarcodeScanner scanner = BarcodeScanning.getClient();

    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis analysis;
    private Listener listener;
    // Solo se modifica en el hilo principal.
    private boolean running;

    public HeadlessBarcodeScanner(Context context, LifecycleOwner owner) {
        this.context = context.getApplicationContext();
        this.owner = owner;
        this.mainExecutor = ContextCompat.getMainExecutor(context);
    }

    public boolean isRunning() {
        return running;
    }

    public void start(@NonNull Listener listener) {
        if (running) return;
        this.listener = listener;
        running = true;

        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context);
        future.addListener(() -> {
            if (!running) return; // Se detuvo antes de que la cámara estuviera lista.
            try {
                cameraProvider = future.get();
                analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(analysisExecutor, this::analyze);
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, analysis);
            } catch (Exception e) {
                stop();
                listener.onError(e);
            }
        }, mainExecutor);
    }

    public void stop() {
        running = false;
        if (analysis != null) {
            analysis.clearAnalyzer();
            analysis = null;
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    public void release() {
        stop();
        scanner.close();
        analysisExecutor.shutdown();
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyze(@NonNull ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }
        InputImage input = InputImage.fromMediaImage(mediaImage,
                imageProxy.getImageInfo().getRotationDegrees());

        scanner.process(input)
                .addOnSuccessListener(mainExecutor, barcodes -> {
                    if (!running) return;
                    for (Barcode barcode : barcodes) {
                        if (barcode.getRawValue() != null && !barcode.getRawValue().isEmpty()) {
                            Listener l = listener;
                            stop();
                            l.onBarcode(barcode);
                            return;
                        }
                    }
                })
                .addOnCompleteListener(mainExecutor, task -> imageProxy.close());
    }
}
