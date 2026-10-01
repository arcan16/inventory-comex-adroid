package com.example.myapplication;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.example.myapplication.data.ServerPreferences;
import com.example.myapplication.data.SessionPreferences;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.ApiErrorUtils;
import com.example.myapplication.network.DataApi;
import com.example.myapplication.network.ImportResultDTO;
import com.example.myapplication.util.BackupFileInfo;
import com.example.myapplication.util.FileUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Exporta todos los datos del servidor a un archivo JSON y los importa de
 * vuelta (combinando, sin borrar). Es el mismo formato que usa la app local
 * ComexBeta, asi que sirve para pasar los datos de una a otra.
 *
 * Exportar descarga primero el respaldo y despues pregunta donde guardarlo,
 * para no dejar un archivo vacio si la descarga falla.
 */
public class DataTransferActivity extends BaseActivity {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String> exportDestinationPicker =
            registerForActivityResult(new ActivityResultContracts.CreateDocument(BackupFileInfo.MIME_TYPE),
                    this::onExportDestinationChosen);
    private final ActivityResultLauncher<String[]> importFilePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onImportFileChosen);

    private ServerPreferences serverPreferences;
    private SessionPreferences sessionPreferences;

    /** Respaldo descargado que espera a que el usuario elija donde guardarlo. */
    private byte[] pendingExport;
    private BackupFileInfo pendingExportInfo;

    private MaterialButton btnExport;
    private MaterialButton btnImport;
    private View progressExport;
    private View progressImport;
    private View cardResult;
    private TextView tvResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_transfer);

        serverPreferences = new ServerPreferences(this);
        sessionPreferences = new SessionPreferences(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        btnExport = findViewById(R.id.btnExport);
        btnImport = findViewById(R.id.btnImport);
        progressExport = findViewById(R.id.progressExport);
        progressImport = findViewById(R.id.progressImport);
        cardResult = findViewById(R.id.cardResult);
        tvResult = findViewById(R.id.tvResult);

        btnExport.setOnClickListener(v -> exportData());
        btnImport.setOnClickListener(v -> importFilePicker.launch(
                new String[]{BackupFileInfo.MIME_TYPE, "application/octet-stream", "text/plain"}));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }

    // ------------------------------------------------------------------
    // Exportar
    // ------------------------------------------------------------------

    private void exportData() {
        if (!serverPreferences.hasServerConfigured() || !sessionPreferences.isLoggedIn()) {
            handleSessionExpired();
            return;
        }
        setBusy(progressExport, true);
        api().exportData().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.code() == 401) {
                    setBusy(progressExport, false);
                    handleSessionExpired();
                    return;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    exportFailed(ApiErrorUtils.parseErrorMessage(response));
                    return;
                }
                ResponseBody body = response.body();
                ioExecutor.execute(() -> {
                    try {
                        byte[] bytes = body.bytes();
                        BackupFileInfo info = BackupFileInfo.read(bytes);
                        runOnUiThread(() -> {
                            pendingExport = bytes;
                            pendingExportInfo = info;
                            exportDestinationPicker.launch(BackupFileInfo.suggestedFileName());
                        });
                    } catch (IOException | IllegalArgumentException e) {
                        runOnUiThread(() -> exportFailed(reason(e)));
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                exportFailed(reason(t));
            }
        });
    }

    private void onExportDestinationChosen(@Nullable Uri uri) {
        byte[] bytes = pendingExport;
        BackupFileInfo info = pendingExportInfo;
        pendingExport = null;
        pendingExportInfo = null;
        if (uri == null || bytes == null) {
            // Cancelado (o la pantalla se recreo y se perdio el respaldo descargado).
            setBusy(progressExport, false);
            return;
        }
        ioExecutor.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) {
                    throw new IOException("No se pudo abrir el archivo de destino");
                }
                output.write(bytes);
                runOnUiThread(() -> {
                    setBusy(progressExport, false);
                    showResult(getString(R.string.backup_export_done, info.products, info.presentations, info.inventories));
                });
            } catch (IOException | SecurityException e) {
                runOnUiThread(() -> exportFailed(reason(e)));
            }
        });
    }

    private void exportFailed(String reason) {
        setBusy(progressExport, false);
        Toast.makeText(this, getString(R.string.backup_export_error, reason), Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------
    // Importar
    // ------------------------------------------------------------------

    private void onImportFileChosen(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        setBusy(progressImport, true);
        ioExecutor.execute(() -> {
            try {
                byte[] bytes = FileUtils.readAllBytes(getContentResolver(), uri);
                BackupFileInfo info = BackupFileInfo.read(bytes);
                runOnUiThread(() -> {
                    setBusy(progressImport, false);
                    confirmImport(bytes, info);
                });
            } catch (IOException | IllegalArgumentException | SecurityException e) {
                runOnUiThread(() -> importFailed(reason(e)));
            }
        });
    }

    private void confirmImport(byte[] bytes, BackupFileInfo info) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.backup_import_confirm_title)
                .setMessage(getString(R.string.backup_import_confirm_message, info.products, info.presentations, info.inventories))
                .setPositiveButton(R.string.backup_import_confirm_action, (dialog, which) -> importData(bytes))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void importData(byte[] bytes) {
        if (!serverPreferences.hasServerConfigured() || !sessionPreferences.isLoggedIn()) {
            handleSessionExpired();
            return;
        }
        setBusy(progressImport, true);
        api().importData(RequestBody.create(bytes, JSON)).enqueue(new Callback<ImportResultDTO>() {
            @Override
            public void onResponse(@NonNull Call<ImportResultDTO> call, @NonNull Response<ImportResultDTO> response) {
                if (response.code() == 401) {
                    setBusy(progressImport, false);
                    handleSessionExpired();
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    setBusy(progressImport, false);
                    showImportResult(response.body());
                } else {
                    importFailed(ApiErrorUtils.parseErrorMessage(response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ImportResultDTO> call, @NonNull Throwable t) {
                importFailed(reason(t));
            }
        });
    }

    private void showImportResult(ImportResultDTO result) {
        showResult(getString(R.string.backup_import_done,
                result.getProductsCreated(), result.getProductsUpdated(), result.getPresentationsCreated(),
                result.getBarcodesAssigned(), result.getBarcodeConflicts(), result.getInventoriesCreated(),
                result.getStockRowsCreated(), result.getCountsCreated(), result.getRowsSkipped()));
    }

    private void importFailed(String reason) {
        setBusy(progressImport, false);
        Toast.makeText(this, getString(R.string.backup_import_error, reason), Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------

    /** Mientras una operacion corre, ambos botones se deshabilitan para no mezclar exportar e importar. */
    private void setBusy(View progress, boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        btnExport.setEnabled(!busy);
        btnImport.setEnabled(!busy);
    }

    private void showResult(String text) {
        tvResult.setText(text);
        cardResult.setVisibility(View.VISIBLE);
    }

    private DataApi api() {
        return ApiClient.createDataApi(serverPreferences.getBaseUrl(), sessionPreferences.getToken());
    }

    private static String reason(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }

    private void handleSessionExpired() {
        Toast.makeText(this, R.string.inventories_session_expired, Toast.LENGTH_LONG).show();
        sessionPreferences.clearSession();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
