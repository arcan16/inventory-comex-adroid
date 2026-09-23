package com.example.myapplication;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
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

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.adapter.ProductCountAdapter;
import com.example.myapplication.data.ServerPreferences;
import com.example.myapplication.data.SessionPreferences;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.ApiErrorUtils;
import com.example.myapplication.network.CreateProductCountRequest;
import com.example.myapplication.network.CreateProductCountResultDTO;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CountNormalActivity extends BaseActivity implements ProductCountAdapter.OnProductCountActionListener {

    public static final String EXTRA_INVENTORY_ID = "extra_inventory_id";
    public static final String EXTRA_PRESENTATION = "extra_presentation";
    public static final String EXTRA_DATE = "extra_date";

    /** Debe coincidir en orden con R.array.product_location_labels y con el enum ProductLocation del backend. */
    private static final String[] PLACE_API_VALUES = {"SALES_AREA", "WAREHOUSE", "STORAGE_AREA", "NOTE"};
    private static final int PLACE_DEFAULT_INDEX = 1; // WAREHOUSE / "Almacén"

    private final ActivityResultLauncher<Intent> productPicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::onProductPicked);

    private ServerPreferences serverPreferences;
    private SessionPreferences sessionPreferences;

    private long inventoryId;
    private List<ProductDTO> products = Collections.emptyList();
    private List<StockItemDTO> stock = Collections.emptyList();
    private List<ProductCountEntryDTO> productCounts = Collections.emptyList();
    private String currentCountSearch = "";
    private float existingDifference;
    private boolean isDeletingCount;
    private ProductCountEntryDTO editingEntry;

    private View contentScroll;
    private View progressLoad;
    private View errorState;
    private TextView tvErrorMessage;

    private EditText etCode;
    private TextView tvStockValue;
    private TextView tvProductDescription;
    private View newProductContainer;
    private EditText etNewProductDescription;
    private EditText etQuantity;
    private TextView tvDifference;
    private Spinner spinnerPlace;
    private MaterialButton btnAdd;
    private MaterialButton btnRegisterNewProduct;
    private MaterialButton btnCancel;
    private View progressAdd;
    private EditText etSearchCount;
    private TextView tvSearchCountResult;
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
        newProductContainer = findViewById(R.id.newProductContainer);
        etNewProductDescription = findViewById(R.id.etNewProductDescription);
        etQuantity = findViewById(R.id.etQuantity);
        tvDifference = findViewById(R.id.tvDifference);
        spinnerPlace = findViewById(R.id.spinnerPlace);
        btnAdd = findViewById(R.id.btnAdd);
        btnRegisterNewProduct = findViewById(R.id.btnRegisterNewProduct);
        btnCancel = findViewById(R.id.btnCancel);
        progressAdd = findViewById(R.id.progressAdd);
        etSearchCount = findViewById(R.id.etSearchCount);
        tvSearchCountResult = findViewById(R.id.tvSearchCountResult);
        recyclerProductCounts = findViewById(R.id.recyclerProductCounts);
        tvCountsEmpty = findViewById(R.id.tvCountsEmpty);

        InputFilter[] uppercaseFilter = {new InputFilter.AllCaps()};
        etCode.setFilters(uppercaseFilter);
        etNewProductDescription.setFilters(uppercaseFilter);
        etSearchCount.setFilters(uppercaseFilter);

        ArrayAdapter<CharSequence> placeAdapter = ArrayAdapter.createFromResource(
                this, R.array.product_location_labels, android.R.layout.simple_spinner_item);
        placeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPlace.setAdapter(placeAdapter);
        spinnerPlace.setSelection(PLACE_DEFAULT_INDEX);

        adapter = new ProductCountAdapter(this);
        recyclerProductCounts.setLayoutManager(new LinearLayoutManager(this));
        recyclerProductCounts.setAdapter(adapter);

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            etCode.setInputType(checkedId == R.id.btnToggleNumber
                    ? InputType.TYPE_CLASS_NUMBER
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
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

        etSearchCount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentCountSearch = s.toString().trim();
                applyCountSearchFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnAdd.setOnClickListener(v -> addProductCount());
        btnRegisterNewProduct.setOnClickListener(v -> registerNewProduct());
        btnCancel.setOnClickListener(v -> resetForm());
        findViewById(R.id.btnRetryLoad).setOnClickListener(v -> loadData());
        findViewById(R.id.btnSummary).setOnClickListener(v -> openSummary(presentation, dateIso));
        findViewById(R.id.btnSearchProduct).setOnClickListener(v ->
                productPicker.launch(new Intent(this, ProductPickerActivity.class)));

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
                    // El backend regresa los conteos en orden de insercion (mas viejo primero);
                    // se invierte para que el ultimo producto agregado aparezca al inicio.
                    productCounts = response.body().getProductsCount();
                    Collections.reverse(productCounts);

                    applyCountSearchFilter();
                    contentScroll.setVisibility(View.VISIBLE);
                    etCode.requestFocus();
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

    /**
     * Filtra la lista de conteos ya registrados (productCounts) por codigo o
     * descripcion, para saber cuantos renglones existen ya de un producto
     * especifico sin tener que contarlos a ojo en una lista larga. Se vuelve a
     * aplicar cada vez que se recarga la lista para que la busqueda activa
     * sobreviva a un alta/edicion/borrado.
     */
    private void applyCountSearchFilter() {
        List<ProductCountEntryDTO> filtered;
        if (TextUtils.isEmpty(currentCountSearch)) {
            filtered = productCounts;
            tvSearchCountResult.setVisibility(View.GONE);
        } else {
            filtered = new ArrayList<>();
            String needle = currentCountSearch.toLowerCase(Locale.getDefault());
            for (ProductCountEntryDTO entry : productCounts) {
                String code = entry.getIdProduct() != null ? entry.getIdProduct().getId() : null;
                String description = entry.getIdProduct() != null ? entry.getIdProduct().getDescription() : null;
                boolean matchesCode = code != null && code.toLowerCase(Locale.getDefault()).contains(needle);
                boolean matchesDescription = description != null && description.toLowerCase(Locale.getDefault()).contains(needle);
                if (matchesCode || matchesDescription) {
                    filtered.add(entry);
                }
            }
            tvSearchCountResult.setText(getResources().getQuantityString(
                    R.plurals.count_search_results, filtered.size(), filtered.size()));
            tvSearchCountResult.setVisibility(View.VISIBLE);
        }

        adapter.setItems(filtered);
        if (filtered.isEmpty()) {
            tvCountsEmpty.setText(TextUtils.isEmpty(currentCountSearch)
                    ? R.string.count_normal_no_counts_yet
                    : R.string.count_search_no_results);
            tvCountsEmpty.setVisibility(View.VISIBLE);
        } else {
            tvCountsEmpty.setVisibility(View.GONE);
        }
    }

    private void openSummary(String presentation, String dateIso) {
        Intent intent = new Intent(this, CountSummaryActivity.class);
        intent.putExtra(CountSummaryActivity.EXTRA_INVENTORY_ID, inventoryId);
        intent.putExtra(CountSummaryActivity.EXTRA_PRESENTATION, presentation);
        intent.putExtra(CountSummaryActivity.EXTRA_DATE, dateIso);
        startActivity(intent);
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

        updateStockDisplay(code);
        existingDifference = computeExistingDifference(code, null);
        refreshDifferenceDisplay();
    }

    /**
     * Resultado de ProductPickerActivity: el codigo del envase puede ser
     * ilegible durante el conteo fisico, asi que se permite buscar el
     * producto por nombre y cargar aqui el codigo elegido.
     */
    private void onProductPicked(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            return;
        }
        String productId = result.getData().getStringExtra(ProductPickerActivity.EXTRA_PRODUCT_ID);
        if (TextUtils.isEmpty(productId)) {
            return;
        }
        etCode.setText(productId);
        performLookup();
        etQuantity.requestFocus();
    }

    /** Muestra el stock esperado y la descripcion del producto con el codigo dado. */
    private void updateStockDisplay(String code) {
        StockItemDTO stockMatch = findStockMatch(code);
        boolean known;
        if (stockMatch != null) {
            tvStockValue.setText(String.format(Locale.US, "%.3f", stockMatch.getStock()));
            tvProductDescription.setText(stockMatch.getDescription());
            known = true;
        } else {
            ProductDTO productMatch = findProductMatch(code);
            if (productMatch != null) {
                tvStockValue.setText(String.format(Locale.US, "%.3f", 0f));
                tvProductDescription.setText(productMatch.getDescription());
                known = true;
            } else {
                tvStockValue.setText("");
                tvProductDescription.setText(R.string.count_normal_unknown_code);
                known = false;
            }
        }
        tvProductDescription.setVisibility(View.VISIBLE);
        setUnknownProductUiVisible(!known);
    }

    /**
     * Alterna entre el boton normal de "Agregar" y el flujo de alta de un producto
     * encontrado fisicamente pero no registrado en el catalogo por error humano.
     */
    private void setUnknownProductUiVisible(boolean unknown) {
        newProductContainer.setVisibility(unknown ? View.VISIBLE : View.GONE);
        btnRegisterNewProduct.setVisibility(unknown ? View.VISIBLE : View.GONE);
        btnAdd.setVisibility(unknown ? View.GONE : View.VISIBLE);
        if (!unknown) {
            etNewProductDescription.setText("");
            etNewProductDescription.setError(null);
        }
    }

    /**
     * Suma los conteos ya registrados para el codigo dado (sin contar el renglon
     * excludeEntryId, si se recibe) y la compara contra el stock esperado.
     */
    private float computeExistingDifference(String code, Long excludeEntryId) {
        StockItemDTO stockMatch = findStockMatch(code);
        float stockValue = stockMatch != null ? stockMatch.getStock() : 0f;
        float countedTotal = 0f;
        for (ProductCountEntryDTO entry : productCounts) {
            if (excludeEntryId != null && entry.getId() == excludeEntryId) {
                continue;
            }
            if (entry.getIdProduct() != null && code.equals(entry.getIdProduct().getId())) {
                countedTotal += entry.getQuantity();
            }
        }
        return countedTotal - stockValue;
    }

    private void refreshDifferenceDisplay() {
        String quantityText = etQuantity.getText().toString().trim();
        if (TextUtils.isEmpty(quantityText)) {
            tvDifference.setText(String.format(Locale.US, "%+.3f", existingDifference));
            return;
        }
        try {
            float quantity = Float.parseFloat(quantityText.replace(',', '.'));
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
            quantity = Float.parseFloat(quantityText.replace(',', '.'));
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

        if (editingEntry != null) {
            updateProductCount(editingEntry, code, quantity, place);
        } else {
            createProductCount(code, quantity, place);
        }
    }

    /** Crea un nuevo renglon de conteo (POST /productCounts). */
    private void createProductCount(String code, float quantity, String place) {
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

    /**
     * Da de alta en el catalogo un producto encontrado en el conteo fisico pero
     * que no fue registrado por error humano, y crea su renglon de conteo en la
     * misma llamada (POST /productCounts/createProductAddCount).
     */
    private void registerNewProduct() {
        String code = etCode.getText().toString().trim();
        if (TextUtils.isEmpty(code)) {
            etCode.setError(getString(R.string.count_error_code_required));
            etCode.requestFocus();
            return;
        }

        String description = etNewProductDescription.getText().toString().trim();
        if (TextUtils.isEmpty(description)) {
            etNewProductDescription.setError(getString(R.string.count_error_description_required));
            etNewProductDescription.requestFocus();
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
            quantity = Float.parseFloat(quantityText.replace(',', '.'));
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

        api.createProductAddCount(new CreateProductCountRequest(inventoryId, code, description, quantity, place))
                .enqueue(new Callback<CreateProductCountResultDTO>() {
                    @Override
                    public void onResponse(@NonNull Call<CreateProductCountResultDTO> call,
                                            @NonNull Response<CreateProductCountResultDTO> response) {
                        setAddLoadingState(false);

                        if (response.code() == 401) {
                            handleSessionExpired();
                            return;
                        }

                        if (response.isSuccessful() && response.body() != null) {
                            Toast.makeText(CountNormalActivity.this, R.string.count_register_new_product_success, Toast.LENGTH_SHORT).show();
                            resetForm();
                            loadData();
                        } else {
                            Toast.makeText(CountNormalActivity.this,
                                    getString(R.string.count_register_new_product_error, ApiErrorUtils.parseErrorMessage(response)),
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<CreateProductCountResultDTO> call, @NonNull Throwable t) {
                        setAddLoadingState(false);
                        String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                        Toast.makeText(CountNormalActivity.this,
                                getString(R.string.count_register_new_product_error, reason), Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * El backend no tiene un endpoint de actualizacion para /productCounts, asi
     * que "actualizar" un renglon se implementa eliminando el registro original
     * y creando uno nuevo con los valores capturados.
     */
    private void updateProductCount(ProductCountEntryDTO original, String code, float quantity, String place) {
        ProductCountsApi api = ApiClient.createProductCountsApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());

        api.deleteProductCount(original.getId()).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.code() == 401) {
                    setAddLoadingState(false);
                    handleSessionExpired();
                    return;
                }

                if (!response.isSuccessful()) {
                    setAddLoadingState(false);
                    Toast.makeText(CountNormalActivity.this,
                            getString(R.string.count_update_error, ApiErrorUtils.parseErrorMessage(response)),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                recreateAfterUpdate(code, quantity, place);
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                setAddLoadingState(false);
                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Toast.makeText(CountNormalActivity.this,
                        getString(R.string.count_update_error, reason), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Segundo paso de updateProductCount: el registro original ya se elimino, ahora se crea el reemplazo. */
    private void recreateAfterUpdate(String code, float quantity, String place) {
        ProductCountsApi api = ApiClient.createProductCountsApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());

        api.addProductCount(new NewProductCountRequest(inventoryId, code, quantity, place))
                .enqueue(new Callback<ProductCountCreatedDTO>() {
                    @Override
                    public void onResponse(@NonNull Call<ProductCountCreatedDTO> call,
                                            @NonNull Response<ProductCountCreatedDTO> response) {
                        setAddLoadingState(false);

                        if (response.code() == 401) {
                            // El registro original ya se elimino del servidor aunque la sesion haya expirado aqui.
                            handleSessionExpired();
                            return;
                        }

                        if (response.isSuccessful() && response.body() != null) {
                            Toast.makeText(CountNormalActivity.this, R.string.count_update_success, Toast.LENGTH_SHORT).show();
                        } else {
                            // El renglon original ya no existe: se avisa y se recarga para reflejar el estado real.
                            Toast.makeText(CountNormalActivity.this,
                                    getString(R.string.count_update_partial_error, ApiErrorUtils.parseErrorMessage(response)),
                                    Toast.LENGTH_LONG).show();
                        }
                        resetForm();
                        loadData();
                    }

                    @Override
                    public void onFailure(@NonNull Call<ProductCountCreatedDTO> call, @NonNull Throwable t) {
                        setAddLoadingState(false);
                        String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                        Toast.makeText(CountNormalActivity.this,
                                getString(R.string.count_update_partial_error, reason), Toast.LENGTH_LONG).show();
                        resetForm();
                        loadData();
                    }
                });
    }

    private void setAddLoadingState(boolean loading) {
        btnAdd.setEnabled(!loading);
        btnRegisterNewProduct.setEnabled(!loading);
        btnCancel.setEnabled(!loading);
        if (loading) {
            btnAdd.setText(editingEntry != null ? R.string.count_update_loading : R.string.count_add_loading);
            btnRegisterNewProduct.setText(R.string.count_register_new_product_loading);
        } else {
            updateAddButtonLabel();
            btnRegisterNewProduct.setText(R.string.count_register_new_product);
        }
        progressAdd.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void resetForm() {
        editingEntry = null;
        updateAddButtonLabel();
        etCode.setText("");
        tvStockValue.setText("");
        tvProductDescription.setVisibility(View.GONE);
        etQuantity.setText("");
        tvDifference.setText("");
        existingDifference = 0f;
        setUnknownProductUiVisible(false);
        etCode.requestFocus();
    }

    @Override
    public void onSelect(ProductCountEntryDTO entry) {
        editingEntry = entry;
        updateAddButtonLabel();

        String code = entry.getIdProduct() != null ? entry.getIdProduct().getId() : "";
        etCode.setText(code);
        updateStockDisplay(code);
        selectPlace(entry.getPlace());

        existingDifference = computeExistingDifference(code, entry.getId());
        etQuantity.setText(String.format(Locale.US, "%.3f", entry.getQuantity()));
        etQuantity.setSelection(etQuantity.getText().length());
        refreshDifferenceDisplay();
    }

    private void selectPlace(String place) {
        for (int i = 0; i < PLACE_API_VALUES.length; i++) {
            if (PLACE_API_VALUES[i].equals(place)) {
                spinnerPlace.setSelection(i);
                return;
            }
        }
    }

    private void updateAddButtonLabel() {
        btnAdd.setText(editingEntry != null ? R.string.count_update : R.string.count_add);
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
                    Toast.makeText(CountNormalActivity.this, R.string.count_deleted_toast, Toast.LENGTH_SHORT).show();
                    if (editingEntry != null && editingEntry.getId() == entry.getId()) {
                        resetForm();
                    }
                    loadData();
                    etCode.requestFocus();
                } else {
                    Toast.makeText(CountNormalActivity.this,
                            getString(R.string.count_delete_error, ApiErrorUtils.parseErrorMessage(response)),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                isDeletingCount = false;
                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Toast.makeText(CountNormalActivity.this,
                        getString(R.string.count_delete_error, reason), Toast.LENGTH_LONG).show();
            }
        });
    }
}
