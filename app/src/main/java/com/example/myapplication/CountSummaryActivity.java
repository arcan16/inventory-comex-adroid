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

import com.example.myapplication.adapter.CountSummaryAdapter;
import com.example.myapplication.data.ServerPreferences;
import com.example.myapplication.data.SessionPreferences;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.ApiErrorUtils;
import com.example.myapplication.network.CountsDifferenceDTO;
import com.example.myapplication.network.PageResponse;
import com.example.myapplication.network.ProductCountsApi;
import com.example.myapplication.util.DateFormatUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CountSummaryActivity extends BaseActivity {

    public static final String EXTRA_INVENTORY_ID = "extra_inventory_id";
    public static final String EXTRA_PRESENTATION = "extra_presentation";
    public static final String EXTRA_DATE = "extra_date";

    private static final int PAGE_SIZE = 20;
    /** Cuantos renglones antes del final se dispara la siguiente pagina. */
    private static final int LOAD_MORE_THRESHOLD = 4;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private ServerPreferences serverPreferences;
    private SessionPreferences sessionPreferences;

    private long inventoryId;
    private int currentPage = 0;
    private int totalPages = 1;
    private boolean isLoadingPage;

    private View contentContainer;
    private View progressLoad;
    private View progressLoadMore;
    private View errorState;
    private TextView tvErrorMessage;
    private RecyclerView recyclerSummary;
    private TextView tvSummaryEmpty;
    private CountSummaryAdapter adapter;
    private MaterialButton btnExport;
    private View progressExport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_count_summary);

        serverPreferences = new ServerPreferences(this);
        sessionPreferences = new SessionPreferences(this);

        inventoryId = getIntent().getLongExtra(EXTRA_INVENTORY_ID, -1);
        String presentation = getIntent().getStringExtra(EXTRA_PRESENTATION);
        String dateIso = getIntent().getStringExtra(EXTRA_DATE);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setSubtitle(presentation + " · " + DateFormatUtils.toShortSpanishDate(dateIso));

        contentContainer = findViewById(R.id.contentContainer);
        progressLoad = findViewById(R.id.progressLoad);
        progressLoadMore = findViewById(R.id.progressLoadMore);
        errorState = findViewById(R.id.errorState);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);
        recyclerSummary = findViewById(R.id.recyclerSummary);
        tvSummaryEmpty = findViewById(R.id.tvSummaryEmpty);
        btnExport = findViewById(R.id.btnExport);
        progressExport = findViewById(R.id.progressExport);

        adapter = new CountSummaryAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerSummary.setLayoutManager(layoutManager);
        recyclerSummary.setAdapter(adapter);
        recyclerSummary.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                if (dy <= 0) {
                    return;
                }
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                if (visibleItemCount + firstVisibleItemPosition >= totalItemCount - LOAD_MORE_THRESHOLD) {
                    loadNextPage();
                }
            }
        });

        findViewById(R.id.btnRetryLoad).setOnClickListener(v -> loadSummary());
        btnExport.setOnClickListener(v -> exportPdf());

        loadSummary();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }

    private void loadSummary() {
        if (inventoryId <= 0) {
            showError(getString(R.string.count_summary_load_error));
            return;
        }
        if (!serverPreferences.hasServerConfigured() || !sessionPreferences.isLoggedIn()) {
            handleSessionExpired();
            return;
        }

        fetchPage(0, false);
    }

    private void loadNextPage() {
        if (isLoadingPage || currentPage + 1 >= totalPages) {
            return;
        }
        fetchPage(currentPage + 1, true);
    }

    private void fetchPage(int page, boolean appending) {
        isLoadingPage = true;
        if (appending) {
            progressLoadMore.setVisibility(View.VISIBLE);
        } else {
            progressLoad.setVisibility(View.VISIBLE);
            errorState.setVisibility(View.GONE);
            contentContainer.setVisibility(View.GONE);
        }

        ProductCountsApi api = ApiClient.createProductCountsApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());

        api.getSummary(inventoryId, page, PAGE_SIZE).enqueue(new Callback<PageResponse<CountsDifferenceDTO>>() {
            @Override
            public void onResponse(@NonNull Call<PageResponse<CountsDifferenceDTO>> call,
                                    @NonNull Response<PageResponse<CountsDifferenceDTO>> response) {
                isLoadingPage = false;
                progressLoad.setVisibility(View.GONE);
                progressLoadMore.setVisibility(View.GONE);

                if (response.code() == 401) {
                    handleSessionExpired();
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    currentPage = page;
                    totalPages = response.body().getTotalPages();
                    if (appending) {
                        adapter.addItems(response.body().getContent());
                    } else {
                        adapter.setItems(response.body().getContent());
                    }
                    tvSummaryEmpty.setVisibility(adapter.isEmpty() ? View.VISIBLE : View.GONE);
                    contentContainer.setVisibility(View.VISIBLE);
                } else if (!appending && response.code() == 400) {
                    // El backend responde 400 cuando el inventario todavia no tiene stock ni conteos.
                    adapter.setItems(Collections.emptyList());
                    totalPages = 0;
                    tvSummaryEmpty.setVisibility(View.VISIBLE);
                    contentContainer.setVisibility(View.VISIBLE);
                } else if (!appending) {
                    showError(getString(R.string.count_summary_load_error));
                } else {
                    Toast.makeText(CountSummaryActivity.this, R.string.count_summary_load_more_error, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<PageResponse<CountsDifferenceDTO>> call, @NonNull Throwable t) {
                isLoadingPage = false;
                progressLoad.setVisibility(View.GONE);
                progressLoadMore.setVisibility(View.GONE);
                if (appending) {
                    Toast.makeText(CountSummaryActivity.this, R.string.count_summary_load_more_error, Toast.LENGTH_SHORT).show();
                } else {
                    showError(getString(R.string.count_summary_load_error));
                }
            }
        });
    }

    private void showError(String message) {
        tvErrorMessage.setText(message);
        errorState.setVisibility(View.VISIBLE);
        contentContainer.setVisibility(View.GONE);
    }

    private void exportPdf() {
        if (inventoryId <= 0) {
            return;
        }
        if (!serverPreferences.hasServerConfigured() || !sessionPreferences.isLoggedIn()) {
            handleSessionExpired();
            return;
        }

        setExportLoadingState(true);

        ProductCountsApi api = ApiClient.createProductCountsApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());

        api.downloadReport(inventoryId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.code() == 401) {
                    setExportLoadingState(false);
                    handleSessionExpired();
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    saveAndOpenPdf(response.body());
                } else {
                    setExportLoadingState(false);
                    Toast.makeText(CountSummaryActivity.this,
                            getString(R.string.count_summary_export_error, ApiErrorUtils.parseErrorMessage(response)),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                setExportLoadingState(false);
                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Toast.makeText(CountSummaryActivity.this,
                        getString(R.string.count_summary_export_error, reason), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Copia el PDF descargado a cacheDir/reports/ y lo abre con un visor externo via FileProvider. */
    private void saveAndOpenPdf(ResponseBody body) {
        ioExecutor.execute(() -> {
            File reportsDir = new File(getCacheDir(), "reports");
            File pdfFile = new File(reportsDir, "summary_" + inventoryId + ".pdf");

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
                    setExportLoadingState(false);
                    Toast.makeText(this, getString(R.string.count_summary_export_error, reason), Toast.LENGTH_LONG).show();
                });
                return;
            }

            runOnUiThread(() -> {
                setExportLoadingState(false);
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
            startActivity(Intent.createChooser(intent, getString(R.string.count_summary_export_open_chooser)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.count_summary_export_no_viewer, Toast.LENGTH_LONG).show();
        }
    }

    private void setExportLoadingState(boolean loading) {
        btnExport.setEnabled(!loading);
        btnExport.setText(loading ? R.string.count_summary_export_loading : R.string.count_summary_export);
        progressExport.setVisibility(loading ? View.VISIBLE : View.GONE);
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
