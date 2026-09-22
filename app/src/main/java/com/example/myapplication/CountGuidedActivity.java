package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.adapter.ProductCountAdapter;
import com.example.myapplication.data.ServerPreferences;
import com.example.myapplication.data.SessionPreferences;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.ApiErrorUtils;
import com.example.myapplication.network.InventoriesApi;
import com.example.myapplication.network.NewProductCountRequest;
import com.example.myapplication.network.NormalInventoryDataDTO;
import com.example.myapplication.network.ProductCountCreatedDTO;
import com.example.myapplication.network.ProductCountEntryDTO;
import com.example.myapplication.network.ProductCountsApi;
import com.example.myapplication.network.StockItemDTO;
import com.example.myapplication.util.DateFormatUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Recorre el stock del inventario uno por uno para capturar el conteo fisico,
 * en lugar de buscar el codigo manualmente (ver CountNormalActivity). Reusa
 * los mismos endpoints: GET /inventories/normal/{id} para la secuencia y
 * POST /productCounts para guardar cada renglon.
 */
public class CountGuidedActivity extends BaseActivity implements ProductCountAdapter.OnProductCountActionListener {

    public static final String EXTRA_INVENTORY_ID = "extra_inventory_id";
    public static final String EXTRA_PRESENTATION = "extra_presentation";
    public static final String EXTRA_DATE = "extra_date";

    /** Debe coincidir en orden con R.array.product_location_labels y con el enum ProductLocation del backend. */
    private static final String[] PLACE_API_VALUES = {"SALES_AREA", "WAREHOUSE", "STORAGE_AREA", "NOTE"};
    private static final int PLACE_DEFAULT_INDEX = 1; // WAREHOUSE / "Almacén"

    private ServerPreferences serverPreferences;
    private SessionPreferences sessionPreferences;

    private long inventoryId;
    private String presentation;
    private String dateIso;
    private List<StockItemDTO> sequence = Collections.emptyList();
    private List<ProductCountEntryDTO> productCounts = Collections.emptyList();
    private boolean[] submitted = new boolean[0];
    private int currentIndex = 0;
    private boolean isSaving;
    private boolean isDeletingCount;

    private View contentScroll;
    private View progressLoad;
    private View errorState;
    private TextView tvErrorMessage;

    private TextView tvGuidedProgress;
    private MaterialCardView cardPrevious;
    private TextView tvPrevId;
    private TextView tvPrevDescription;
    private TextView tvCurrentId;
    private TextView tvCurrentDescription;
    private TextView tvCurrentStock;
    private EditText etQuantity;
    private Spinner spinnerPlace;
    private MaterialCardView cardNext;
    private TextView tvNextId;
    private TextView tvNextDescription;
    private MaterialButton btnPrev;
    private MaterialButton btnNext;
    private View progressSubmit;
    private RecyclerView recyclerProductCounts;
    private TextView tvCountsEmpty;
    private ProductCountAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_count_guided);

        serverPreferences = new ServerPreferences(this);
        sessionPreferences = new SessionPreferences(this);

        inventoryId = getIntent().getLongExtra(EXTRA_INVENTORY_ID, -1);
        presentation = getIntent().getStringExtra(EXTRA_PRESENTATION);
        dateIso = getIntent().getStringExtra(EXTRA_DATE);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setSubtitle(presentation + " · " + DateFormatUtils.toShortSpanishDate(dateIso));

        contentScroll = findViewById(R.id.contentScroll);
        progressLoad = findViewById(R.id.progressLoad);
        errorState = findViewById(R.id.errorState);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);

        tvGuidedProgress = findViewById(R.id.tvGuidedProgress);
        cardPrevious = findViewById(R.id.cardPrevious);
        tvPrevId = findViewById(R.id.tvPrevId);
        tvPrevDescription = findViewById(R.id.tvPrevDescription);
        tvCurrentId = findViewById(R.id.tvCurrentId);
        tvCurrentDescription = findViewById(R.id.tvCurrentDescription);
        tvCurrentStock = findViewById(R.id.tvCurrentStock);
        etQuantity = findViewById(R.id.etQuantity);
        spinnerPlace = findViewById(R.id.spinnerPlace);
        cardNext = findViewById(R.id.cardNext);
        tvNextId = findViewById(R.id.tvNextId);
        tvNextDescription = findViewById(R.id.tvNextDescription);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        progressSubmit = findViewById(R.id.progressSubmit);
        recyclerProductCounts = findViewById(R.id.recyclerProductCounts);
        tvCountsEmpty = findViewById(R.id.tvCountsEmpty);

        ArrayAdapter<CharSequence> placeAdapter = ArrayAdapter.createFromResource(
                this, R.array.product_location_labels, android.R.layout.simple_spinner_item);
        placeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPlace.setAdapter(placeAdapter);
        spinnerPlace.setSelection(PLACE_DEFAULT_INDEX);

        adapter = new ProductCountAdapter(this);
        recyclerProductCounts.setLayoutManager(new LinearLayoutManager(this));
        recyclerProductCounts.setAdapter(adapter);

        btnPrev.setOnClickListener(v -> goToPrevious());
        btnNext.setOnClickListener(v -> goToNextOrFinish());
        findViewById(R.id.btnRetryLoad).setOnClickListener(v -> loadData());
        findViewById(R.id.btnSummary).setOnClickListener(v -> openSummary());

        loadData();
    }

    private void loadData() {
        if (inventoryId <= 0) {
            showError(getString(R.string.count_guided_load_error));
            return;
        }
        if (!serverPreferences.hasServerConfigured() || !sessionPreferences.isLoggedIn()) {
            handleSessionExpired();
            return;
        }

        progressLoad.setVisibility(View.VISIBLE);
        errorState.setVisibility(View.GONE);
        contentScroll.setVisibility(View.GONE);

        InventoriesApi api = ApiClient.createInventoriesApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());

        api.getNormalInventoryData(inventoryId).enqueue(new Callback<NormalInventoryDataDTO>() {
            @Override
            public void onResponse(@NonNull Call<NormalInventoryDataDTO> call,
                                    @NonNull Response<NormalInventoryDataDTO> response) {
                progressLoad.setVisibility(View.GONE);

                if (response.code() == 401) {
                    handleSessionExpired();
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    sequence = response.body().getStock();
                    submitted = new boolean[sequence.size()];
                    currentIndex = 0;

                    productCounts = response.body().getProductsCount();
                    adapter.setItems(productCounts);
                    tvCountsEmpty.setVisibility(productCounts.isEmpty() ? View.VISIBLE : View.GONE);

                    if (sequence.isEmpty()) {
                        showError(getString(R.string.count_guided_empty));
                        return;
                    }

                    contentScroll.setVisibility(View.VISIBLE);
                    renderStep();
                } else {
                    showError(getString(R.string.count_guided_load_error));
                }
            }

            @Override
            public void onFailure(@NonNull Call<NormalInventoryDataDTO> call, @NonNull Throwable t) {
                progressLoad.setVisibility(View.GONE);
                showError(getString(R.string.count_guided_load_error));
            }
        });
    }

    private void renderStep() {
        StockItemDTO current = sequence.get(currentIndex);
        tvGuidedProgress.setText(getString(R.string.count_guided_progress, currentIndex + 1, sequence.size()));

        tvCurrentId.setText(current.getIdProduct());
        tvCurrentDescription.setText(current.getDescription());
        tvCurrentStock.setText(String.format(Locale.US, "%.3f", current.getStock()));
        etQuantity.setText("");
        etQuantity.setError(null);

        if (currentIndex > 0) {
            StockItemDTO previous = sequence.get(currentIndex - 1);
            tvPrevId.setText(previous.getIdProduct());
            tvPrevDescription.setText(previous.getDescription());
            cardPrevious.setVisibility(View.VISIBLE);
        } else {
            cardPrevious.setVisibility(View.GONE);
        }

        if (currentIndex < sequence.size() - 1) {
            StockItemDTO next = sequence.get(currentIndex + 1);
            tvNextId.setText(next.getIdProduct());
            tvNextDescription.setText(next.getDescription());
            cardNext.setVisibility(View.VISIBLE);
        } else {
            cardNext.setVisibility(View.GONE);
        }

        btnPrev.setEnabled(currentIndex > 0);
        btnNext.setText(currentIndex == sequence.size() - 1
                ? R.string.count_guided_btn_finish
                : R.string.count_guided_btn_next);
    }

    private void goToPrevious() {
        if (currentIndex == 0) {
            return;
        }
        currentIndex--;
        renderStep();
    }

    private void goToNextOrFinish() {
        if (isSaving) {
            return;
        }

        String quantityText = etQuantity.getText().toString().trim();
        if (TextUtils.isEmpty(quantityText) || submitted[currentIndex]) {
            advanceOrFinish();
            return;
        }

        float quantity;
        try {
            quantity = Float.parseFloat(quantityText.replace(',', '.'));
        } catch (NumberFormatException e) {
            etQuantity.setError(getString(R.string.count_error_quantity_required));
            return;
        }
        if (quantity <= 0) {
            etQuantity.setError(getString(R.string.count_error_quantity_positive));
            return;
        }

        submitCurrentCount(quantity);
    }

    private void submitCurrentCount(float quantity) {
        StockItemDTO current = sequence.get(currentIndex);
        String place = PLACE_API_VALUES[spinnerPlace.getSelectedItemPosition()];

        setSavingState(true);

        ProductCountsApi api = ApiClient.createProductCountsApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());

        api.addProductCount(new NewProductCountRequest(inventoryId, current.getIdProduct(), quantity, place))
                .enqueue(new Callback<ProductCountCreatedDTO>() {
                    @Override
                    public void onResponse(@NonNull Call<ProductCountCreatedDTO> call,
                                            @NonNull Response<ProductCountCreatedDTO> response) {
                        setSavingState(false);

                        if (response.code() == 401) {
                            handleSessionExpired();
                            return;
                        }

                        if (response.isSuccessful() && response.body() != null) {
                            submitted[currentIndex] = true;
                            refreshCounts();
                            advanceOrFinish();
                        } else {
                            Toast.makeText(CountGuidedActivity.this,
                                    getString(R.string.count_guided_save_error, ApiErrorUtils.parseErrorMessage(response)),
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ProductCountCreatedDTO> call, @NonNull Throwable t) {
                        setSavingState(false);
                        String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                        Toast.makeText(CountGuidedActivity.this,
                                getString(R.string.count_guided_save_error, reason), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void advanceOrFinish() {
        if (currentIndex == sequence.size() - 1) {
            Toast.makeText(this, R.string.count_guided_finished_toast, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentIndex++;
        renderStep();
    }

    private void setSavingState(boolean saving) {
        isSaving = saving;
        btnPrev.setEnabled(!saving && currentIndex > 0);
        btnNext.setEnabled(!saving);
        btnNext.setText(saving
                ? R.string.count_guided_btn_loading
                : (currentIndex == sequence.size() - 1 ? R.string.count_guided_btn_finish : R.string.count_guided_btn_next));
        progressSubmit.setVisibility(saving ? View.VISIBLE : View.GONE);
    }

    /** Vuelve a pedir solo la lista de conteos ya registrados, sin reiniciar la secuencia guiada en curso. */
    private void refreshCounts() {
        InventoriesApi api = ApiClient.createInventoriesApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());

        api.getNormalInventoryData(inventoryId).enqueue(new Callback<NormalInventoryDataDTO>() {
            @Override
            public void onResponse(@NonNull Call<NormalInventoryDataDTO> call,
                                    @NonNull Response<NormalInventoryDataDTO> response) {
                if (response.code() == 401) {
                    handleSessionExpired();
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    productCounts = response.body().getProductsCount();
                    adapter.setItems(productCounts);
                    tvCountsEmpty.setVisibility(productCounts.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onFailure(@NonNull Call<NormalInventoryDataDTO> call, @NonNull Throwable t) {
                // Se ignora: la lista se queda con los datos anteriores y se reintenta en la siguiente accion.
            }
        });
    }

    private void openSummary() {
        Intent intent = new Intent(this, CountSummaryActivity.class);
        intent.putExtra(CountSummaryActivity.EXTRA_INVENTORY_ID, inventoryId);
        intent.putExtra(CountSummaryActivity.EXTRA_PRESENTATION, presentation);
        intent.putExtra(CountSummaryActivity.EXTRA_DATE, dateIso);
        startActivity(intent);
    }

    @Override
    public void onSelect(ProductCountEntryDTO entry) {
        String code = entry.getIdProduct() != null ? entry.getIdProduct().getId() : "";
        for (int i = 0; i < sequence.size(); i++) {
            if (code.equals(sequence.get(i).getIdProduct())) {
                currentIndex = i;
                renderStep();
                return;
            }
        }
    }

    @Override
    public void onDelete(ProductCountEntryDTO entry) {
        String productId = entry.getIdProduct() != null ? entry.getIdProduct().getId() : "";
        String quantityText = String.format(Locale.US, "%.3f", entry.getQuantity());
        new AlertDialog.Builder(this)
                .setTitle(R.string.count_delete_confirm_title)
                .setMessage(getString(R.string.count_delete_confirm_message, productId, quantityText))
                .setPositiveButton(R.string.inventories_menu_delete, (dialog, which) -> deleteProductCount(entry))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void deleteProductCount(ProductCountEntryDTO entry) {
        if (isDeletingCount) {
            return;
        }
        isDeletingCount = true;

        ProductCountsApi api = ApiClient.createProductCountsApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());

        api.deleteProductCount(entry.getId()).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                isDeletingCount = false;

                if (response.code() == 401) {
                    handleSessionExpired();
                    return;
                }

                if (response.isSuccessful()) {
                    Toast.makeText(CountGuidedActivity.this, R.string.count_deleted_toast, Toast.LENGTH_SHORT).show();
                    refreshCounts();
                } else {
                    Toast.makeText(CountGuidedActivity.this,
                            getString(R.string.count_delete_error, ApiErrorUtils.parseErrorMessage(response)),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                isDeletingCount = false;
                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Toast.makeText(CountGuidedActivity.this,
                        getString(R.string.count_delete_error, reason), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showError(String message) {
        tvErrorMessage.setText(message);
        errorState.setVisibility(View.VISIBLE);
        contentScroll.setVisibility(View.GONE);
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
