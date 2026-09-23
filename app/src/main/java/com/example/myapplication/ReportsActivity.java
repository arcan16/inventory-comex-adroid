package com.example.myapplication;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.myapplication.adapter.ReportAdapter;
import com.example.myapplication.data.ServerPreferences;
import com.example.myapplication.data.SessionPreferences;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.ApiErrorUtils;
import com.example.myapplication.network.ProductCountsApi;
import com.example.myapplication.network.ReportDTO;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ReportsActivity extends BaseActivity implements ReportAdapter.OnReportClickListener {

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private ServerPreferences serverPreferences;
    private SessionPreferences sessionPreferences;
    private ReportAdapter adapter;

    private SwipeRefreshLayout swipeRefresh;
    private View progressLoad;
    private View emptyStateContainer;
    private TextView tvEmptyMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        serverPreferences = new ServerPreferences(this);
        sessionPreferences = new SessionPreferences(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        swipeRefresh = findViewById(R.id.swipeRefresh);
        progressLoad = findViewById(R.id.progressLoad);
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        RecyclerView recyclerReports = findViewById(R.id.recyclerReports);

        adapter = new ReportAdapter(this);
        recyclerReports.setLayoutManager(new LinearLayoutManager(this));
        recyclerReports.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(() -> loadReports(false));
        findViewById(R.id.btnRetry).setOnClickListener(v -> loadReports(true));

        loadReports(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }

    private void loadReports(boolean showFullProgress) {
        if (!serverPreferences.hasServerConfigured() || !sessionPreferences.isLoggedIn()) {
            handleSessionExpired();
            return;
        }

        emptyStateContainer.setVisibility(View.GONE);
        if (showFullProgress) {
            progressLoad.setVisibility(View.VISIBLE);
        }

        ProductCountsApi api = ApiClient.createProductCountsApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());

        api.getAllReports().enqueue(new Callback<List<ReportDTO>>() {
            @Override
            public void onResponse(@NonNull Call<List<ReportDTO>> call, @NonNull Response<List<ReportDTO>> response) {
                progressLoad.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);

                if (response.code() == 401) {
                    handleSessionExpired();
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    List<ReportDTO> reports = new ArrayList<>(response.body());
                    reports.sort(Comparator.comparing(ReportDTO::getInventoryDate).reversed());
                    adapter.setItems(reports);
                    tvEmptyMessage.setText(R.string.reports_empty);
                    emptyStateContainer.setVisibility(adapter.isEmpty() ? View.VISIBLE : View.GONE);
                } else {
                    showLoadError();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<ReportDTO>> call, @NonNull Throwable t) {
                progressLoad.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                showLoadError();
            }
        });
    }

    private void showLoadError() {
        adapter.setItems(Collections.emptyList());
        tvEmptyMessage.setText(R.string.reports_load_error);
        emptyStateContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDownloadReport(ReportDTO report) {
        if (!serverPreferences.hasServerConfigured() || !sessionPreferences.isLoggedIn()) {
            handleSessionExpired();
            return;
        }

        adapter.setDownloadingInventoryId(report.getIdInventory());

        ProductCountsApi api = ApiClient.createProductCountsApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());

        api.downloadReport(report.getIdInventory()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.code() == 401) {
                    adapter.setDownloadingInventoryId(-1);
                    handleSessionExpired();
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    saveAndOpenPdf(report.getIdInventory(), response.body());
                } else {
                    adapter.setDownloadingInventoryId(-1);
                    Toast.makeText(ReportsActivity.this,
                            getString(R.string.reports_download_error, ApiErrorUtils.parseErrorMessage(response)),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                adapter.setDownloadingInventoryId(-1);
                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Toast.makeText(ReportsActivity.this,
                        getString(R.string.reports_download_error, reason), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Copia el PDF descargado a cacheDir/reports/ y lo abre con un visor externo via FileProvider. */
    private void saveAndOpenPdf(long idInventory, ResponseBody body) {
        ioExecutor.execute(() -> {
            File reportsDir = new File(getCacheDir(), "reports");
            File pdfFile = new File(reportsDir, "summary_" + idInventory + ".pdf");

            try {
                if (!reportsDir.exists() && !reportsDir.mkdirs()) {
                    throw new IOException("No se pudo crear el directorio de reportes");
                }
                try (InputStream input = body.byteStream();
                     OutputStream output = new FileOutputStream(pdfFile)) {
                    byte[] chunk = new byte[8192];
                    int read;
                    while ((read = input.read(chunk)) != -1) {
                        output.write(chunk, 0, read);
                    }
                }
            } catch (IOException e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                runOnUiThread(() -> {
                    adapter.setDownloadingInventoryId(-1);
                    Toast.makeText(this, getString(R.string.reports_download_error, reason), Toast.LENGTH_LONG).show();
                });
                return;
            }

            runOnUiThread(() -> {
                adapter.setDownloadingInventoryId(-1);
                openPdf(pdfFile);
            });
        });
    }

    private void openPdf(File pdfFile) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pdfFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.reports_download_open_chooser)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.reports_download_no_viewer, Toast.LENGTH_LONG).show();
        }
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
