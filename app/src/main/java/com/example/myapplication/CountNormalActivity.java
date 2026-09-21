package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
import com.example.myapplication.network.ProductDTO;
import com.example.myapplication.network.StockItemDTO;
import com.example.myapplication.util.DateFormatUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CountNormalActivity extends AppCompatActivity {

    public static final String EXTRA_INVENTORY_ID = "extra_inventory_id";
    public static final String EXTRA_PRESENTATION = "extra_presentation";
    public static final String EXTRA_DATE = "extra_date";

    /** Debe coincidir en orden con R.array.product_location_labels y con el enum ProductLocation del backend. */
    private static final String[] PLACE_API_VALUES = {"SALES_AREA", "WAREHOUSE", "STORAGE_AREA", "NOTE"};
    private static final int PLACE_DEFAULT_INDEX = 1; // WAREHOUSE / "Almacén"

    private ServerPreferences serverPreferences;
    private SessionPreferences sessionPreferences;

    private long inventoryId;
    private List<ProductDTO> products = Collections.emptyList();
    private List<StockItemDTO> stock = Collections.emptyList();
    private List<ProductCountEntryDTO> productCounts = Collections.emptyList();
    private float existingDifference;

    private View contentScroll;
    private View progressLoad;
    private View errorState;
    private TextView tvErrorMessage;

    private EditText etCode;
    private TextView tvStockValue;
    private TextView tvProductDescription;
    private EditText etQuantity;
    private TextView tvDifference;
    private Spinner spinnerPlace;
    private MaterialButton btnAdd;
    private MaterialButton btnCancel;
    private View progressAdd;
    private RecyclerView recyclerProductCounts;
    private TextView tvCountsEmpty;
    private ProductCountAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_count_normal);

        serverPreferences = new ServerPreferences(this);
        sessionPreferences = new SessionPreferences(this);

        inventoryId = getIntent().getLongExtra(EXTRA_INVENTORY_ID, -1);
        String presentation = getIntent().getStringExtra(EXTRA_PRESENTATION);
        String dateIso = getIntent().getStringExtra(EXTRA_DATE);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setSubtitle(presentation + " · " + DateFormatUtils.toShortSpanishDate(dateIso));

        contentScroll = findViewById(R.id.contentScroll);
        progressLoad = findViewById(R.id.progressLoad);
        errorState = findViewById(R.id.errorState);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);

        MaterialButtonToggleGroup toggleGroup = findViewById(R.id.toggleGroup);
        etCode = findViewById(R.id.etCode);
        tvStockValue = findViewById(R.id.tvStockValue);
        tvProductDescription = findViewById(R.id.tvProductDescription);
        etQuantity = findViewById(R.id.etQuantity);
        tvDifference = findViewById(R.id.tvDifference);
        spinnerPlace = findViewById(R.id.spinnerPlace);
        btnAdd = findViewById(R.id.btnAdd);
        btnCancel = findViewById(R.id.btnCancel);
        progressAdd = findViewById(R.id.progressAdd);
        recyclerProductCounts = findViewById(R.id.recyclerProductCounts);
        tvCountsEmpty = findViewById(R.id.tvCountsEmpty);

        ArrayAdapter<CharSequence> placeAdapter = ArrayAdapter.createFromResource(
                this, R.array.product_location_labels, android.R.layout.simple_spinner_item);
        placeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPlace.setAdapter(placeAdapter);
        spinnerPlace.setSelection(PLACE_DEFAULT_INDEX);

        adapter = new ProductCountAdapter();
        recyclerProductCounts.setLayoutManager(new LinearLayoutManager(this));
        recyclerProductCounts.setAdapter(adapter);

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            etCode.setInputType(checkedId == R.id.btnToggleNumber
                    ? InputType.TYPE_CLASS_NUMBER
                    : InputType.TYPE_CLASS_TEXT);
            resetForm();
        });

        etCode.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                performLookup();
            }
        });
        etCode.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                performLookup();
            }
            return false;
        });

        etQuantity.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshDifferenceDisplay();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnAdd.setOnClickListener(v -> addProductCount());
        btnCancel.setOnClickListener(v -> resetForm());
        findViewById(R.id.btnRetryLoad).setOnClickListener(v -> loadData());

        loadData();
    }

    private void loadData() {
        if (inventoryId <= 0) {
            showError(getString(R.string.count_normal_load_error));
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
                    products = response.body().getProducts();
                    stock = response.body().getStock();
                    productCounts = response.body().getProductsCount();

                    adapter.setItems(productCounts);
                    tvCountsEmpty.setVisibility(productCounts.isEmpty() ? View.VISIBLE : View.GONE);
                    contentScroll.setVisibility(View.VISIBLE);
                } else {
                    showError(getString(R.string.count_normal_load_error));
                }
            }

            @Override
            public void onFailure(@NonNull Call<NormalInventoryDataDTO> call, @NonNull Throwable t) {
                progressLoad.setVisibility(View.GONE);
                showError(getString(R.string.count_normal_load_error));
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

    private void performLookup() {
        String code = etCode.getText().toString().trim();
        if (TextUtils.isEmpty(code)) {
            return;
        }

        StockItemDTO stockMatch = findStockMatch(code);
        if (stockMatch != null) {
            tvStockValue.setText(String.format(Locale.US, "%.3f", stockMatch.getStock()));
            tvProductDescription.setText(stockMatch.getDescription());
            tvProductDescription.setVisibility(View.VISIBLE);
        } else {
            ProductDTO productMatch = findProductMatch(code);
            if (productMatch != null) {
                tvStockValue.setText(String.format(Locale.US, "%.3f", 0f));
                tvProductDescription.setText(productMatch.getDescription());
            } else {
                tvStockValue.setText("");
                tvProductDescription.setText(R.string.count_normal_unknown_code);
            }
            tvProductDescription.setVisibility(View.VISIBLE);
        }

        float stockValue = stockMatch != null ? stockMatch.getStock() : 0f;
        float countedTotal = 0f;
        for (ProductCountEntryDTO entry : productCounts) {
            if (entry.getIdProduct() != null && code.equals(entry.getIdProduct().getId())) {
                countedTotal += entry.getQuantity();
            }
        }
        existingDifference = countedTotal - stockValue;
        refreshDifferenceDisplay();
    }

    private void refreshDifferenceDisplay() {
        String quantityText = etQuantity.getText().toString().trim();
        if (TextUtils.isEmpty(quantityText)) {
            tvDifference.setText(String.format(Locale.US, "%+.3f", existingDifference));
            return;
        }
        try {
            float quantity = Float.parseFloat(quantityText);
            tvDifference.setText(String.format(Locale.US, "%+.3f", quantity + existingDifference));
        } catch (NumberFormatException ignored) {
            // El usuario todavia esta escribiendo (ej. "1."); se actualiza en el siguiente caracter.
        }
    }

    private StockItemDTO findStockMatch(String code) {
        for (StockItemDTO item : stock) {
            if (code.equals(item.getIdProduct())) {
                return item;
            }
        }
        return null;
    }

    private ProductDTO findProductMatch(String code) {
        for (ProductDTO product : products) {
            if (code.equals(product.getId())) {
                return product;
            }
        }
        return null;
    }

    private void addProductCount() {
        String code = etCode.getText().toString().trim();
        if (TextUtils.isEmpty(code)) {
            etCode.setError(getString(R.string.count_error_code_required));
            etCode.requestFocus();
            return;
        }

        boolean knownProduct = findStockMatch(code) != null || findProductMatch(code) != null;
        if (!knownProduct) {
            // POST /productCounts exige que el producto ya exista en el catalogo (ver
            // ProductCountsController.addPhysicalProduct); dar de alta uno nuevo es
            // POST /productCounts/createProductAddCount, todavia no implementado aqui.
            Toast.makeText(this, R.string.count_error_unknown_product, Toast.LENGTH_LONG).show();
            return;
        }

        String quantityText = etQuantity.getText().toString().trim();
        if (TextUtils.isEmpty(quantityText)) {
            etQuantity.setError(getString(R.string.count_error_quantity_required));
            etQuantity.requestFocus();
            return;
        }

        float quantity;
        try {
            quantity = Float.parseFloat(quantityText);
        } catch (NumberFormatException e) {
            etQuantity.setError(getString(R.string.count_error_quantity_required));
            return;
        }
        if (quantity <= 0) {
            etQuantity.setError(getString(R.string.count_error_quantity_positive));
            return;
        }

        String place = PLACE_API_VALUES[spinnerPlace.getSelectedItemPosition()];

        setAddLoadingState(true);

        ProductCountsApi api = ApiClient.createProductCountsApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());

        api.addProductCount(new NewProductCountRequest(inventoryId, code, quantity, place))
                .enqueue(new Callback<ProductCountCreatedDTO>() {
                    @Override
                    public void onResponse(@NonNull Call<ProductCountCreatedDTO> call,
                                            @NonNull Response<ProductCountCreatedDTO> response) {
                        setAddLoadingState(false);

                        if (response.code() == 401) {
                            handleSessionExpired();
                            return;
                        }

                        if (response.isSuccessful() && response.body() != null) {
                            Toast.makeText(CountNormalActivity.this, R.string.count_add_success, Toast.LENGTH_SHORT).show();
                            resetForm();
                            loadData();
                        } else {
                            Toast.makeText(CountNormalActivity.this,
                                    getString(R.string.count_add_error, ApiErrorUtils.parseErrorMessage(response)),
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ProductCountCreatedDTO> call, @NonNull Throwable t) {
                        setAddLoadingState(false);
                        String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                        Toast.makeText(CountNormalActivity.this,
                                getString(R.string.count_add_error, reason), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setAddLoadingState(boolean loading) {
        btnAdd.setEnabled(!loading);
        btnCancel.setEnabled(!loading);
        btnAdd.setText(loading ? R.string.count_add_loading : R.string.count_add);
        progressAdd.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void resetForm() {
        etCode.setText("");
        tvStockValue.setText("");
        tvProductDescription.setVisibility(View.GONE);
        etQuantity.setText("");
        tvDifference.setText("");
        existingDifference = 0f;
        etCode.requestFocus();
    }
}
