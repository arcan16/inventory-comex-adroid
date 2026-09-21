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
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.data.ServerPreferences;
import com.example.myapplication.data.SessionPreferences;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.ApiErrorUtils;
import com.example.myapplication.network.InventoryUploadApi;
import com.example.myapplication.network.InventoryUploadResultDTO;
import com.example.myapplication.util.FileUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InventoryUploadActivity extends AppCompatActivity {

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private ServerPreferences serverPreferences;
    private SessionPreferences sessionPreferences;

    private Uri selectedFileUri;
    private String selectedFileName;

    private TextView tvFileName;
    private TextView tvFileMeta;
    private MaterialButton btnUpload;
    private View progressUpload;
    private View resultCard;
    private TextView tvResultTitle;
    private TextView tvResultBody;

    private final ActivityResultLauncher<String> filePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onFilePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_upload);

        serverPreferences = new ServerPreferences(this);
        sessionPreferences = new SessionPreferences(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvFileName = findViewById(R.id.tvFileName);
        tvFileMeta = findViewById(R.id.tvFileMeta);
        btnUpload = findViewById(R.id.btnUpload);
        progressUpload = findViewById(R.id.progressUpload);
        resultCard = findViewById(R.id.resultCard);
        tvResultTitle = findViewById(R.id.tvResultTitle);
        tvResultBody = findViewById(R.id.tvResultBody);

        findViewById(R.id.dropzone).setOnClickListener(v -> filePicker.launch("*/*"));
        btnUpload.setOnClickListener(v -> uploadSelectedFile());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }

    private void onFilePicked(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }

        String name = FileUtils.getDisplayName(getContentResolver(), uri);
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            Toast.makeText(this, R.string.upload_error_not_csv, Toast.LENGTH_SHORT).show();
            return;
        }

        selectedFileUri = uri;
        selectedFileName = name;

        long size = FileUtils.getSize(getContentResolver(), uri);
        tvFileName.setText(name);
        tvFileMeta.setText(getString(R.string.upload_dropzone_selected_meta, FileUtils.formatFileSize(size)));

        resultCard.setVisibility(View.GONE);
        btnUpload.setEnabled(true);
    }

    private void uploadSelectedFile() {
        if (selectedFileUri == null) {
            return;
        }
        if (!serverPreferences.hasServerConfigured() || !sessionPreferences.isLoggedIn()) {
            Toast.makeText(this, R.string.upload_error_server_required, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoadingState(true);

        Uri fileUri = selectedFileUri;
        String fileName = selectedFileName;

        ioExecutor.execute(() -> {
            byte[] bytes;
            try {
                bytes = FileUtils.readAllBytes(getContentResolver(), fileUri);
            } catch (IOException e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                runOnUiThread(() -> {
                    setLoadingState(false);
                    Toast.makeText(this, getString(R.string.upload_error_read_file, reason), Toast.LENGTH_SHORT).show();
                });
                return;
            }

            RequestBody requestBody = RequestBody.create(bytes, MediaType.parse("text/csv"));
            MultipartBody.Part part = MultipartBody.Part.createFormData("file", fileName, requestBody);

            InventoryUploadApi api = ApiClient.createInventoryUploadApi(
                    serverPreferences.getBaseUrl(), sessionPreferences.getToken());

            api.upload(part).enqueue(new Callback<InventoryUploadResultDTO>() {
                @Override
                public void onResponse(@NonNull Call<InventoryUploadResultDTO> call,
                                        @NonNull Response<InventoryUploadResultDTO> response) {
                    runOnUiThread(() -> {
                        setLoadingState(false);
                        handleUploadResponse(response);
                    });
                }

                @Override
                public void onFailure(@NonNull Call<InventoryUploadResultDTO> call, @NonNull Throwable t) {
                    runOnUiThread(() -> {
                        setLoadingState(false);
                        String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                        Toast.makeText(InventoryUploadActivity.this,
                                getString(R.string.upload_error_generic, reason), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
    }

    private void handleUploadResponse(Response<InventoryUploadResultDTO> response) {
        if (response.code() == 401) {
            Toast.makeText(this, R.string.inventories_session_expired, Toast.LENGTH_LONG).show();
            sessionPreferences.clearSession();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        if (response.isSuccessful() && response.body() != null) {
            InventoryUploadResultDTO result = response.body();
            tvResultTitle.setText(getString(R.string.upload_result_title, result.getInventoryId()));
            tvResultBody.setText(getString(R.string.upload_result_body,
                    result.getRowsProcessed(), result.getPresentation(),
                    result.getProductsCreated(), result.getStockRowsCreated()));
            resultCard.setVisibility(View.VISIBLE);

            // Evita volver a procesar el mismo archivo por accidente y crear un inventario duplicado.
            selectedFileUri = null;
            selectedFileName = null;
            btnUpload.setEnabled(false);
            tvFileName.setText(R.string.upload_dropzone_placeholder_title);
            tvFileMeta.setText(R.string.upload_dropzone_placeholder_meta);
        } else {
            Toast.makeText(this, getString(R.string.upload_error_generic, ApiErrorUtils.parseErrorMessage(response)), Toast.LENGTH_LONG).show();
        }
    }

    private void setLoadingState(boolean loading) {
        btnUpload.setEnabled(!loading && selectedFileUri != null);
        btnUpload.setText(loading ? R.string.upload_submit_loading : R.string.upload_submit);
        progressUpload.setVisibility(loading ? View.VISIBLE : View.GONE);
        findViewById(R.id.dropzone).setEnabled(!loading);
    }
}
