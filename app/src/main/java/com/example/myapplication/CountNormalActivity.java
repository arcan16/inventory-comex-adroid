package com.example.myapplication;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.adapter.ProductCountAdapter;
import com.example.myapplication.data.ServerPreferences;
import com.example.myapplication.data.SessionPreferences;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.ApiErrorUtils;
import com.example.myapplication.network.AssignBarcodeRequest;
import com.example.myapplication.network.CreateProductCountRequest;
import com.example.myapplication.network.CreateProductCountResultDTO;
import com.example.myapplication.network.InventoriesApi;
import com.example.myapplication.network.NewProductCountRequest;
import com.example.myapplication.network.NormalInventoryDataDTO;
import com.example.myapplication.network.ProductCountCreatedDTO;
import com.example.myapplication.network.ProductCountEntryDTO;
import com.example.myapplication.network.ProductCountsApi;
import com.example.myapplication.network.ProductDTO;
import com.example.myapplication.network.ProductPresentationDTO;
import com.example.myapplication.network.ProductsApi;
import com.example.myapplication.network.StockItemDTO;
import com.example.myapplication.scanner.BarcodeScanHelper;
import com.example.myapplication.util.DateFormatUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    // ---- Busqueda por codigo de barras ----
    /** Presentacion del inventario ("1 LT", "4 LTS"...), para detectar codigos de otra presentacion. */
    private String inventoryPresentation;
    /** Presentaciones con codigo que llegan con el inventario, mas las encontradas por red despues. */
    private final Map<String, ProductPresentationDTO> presentationByBarcode = new HashMap<>();
    private BarcodeScanHelper barcodeScan;
    /** Modo "Barras": etCode contiene un codigo de barras en lugar del id del producto. */
    private boolean barcodeMode;
    private boolean suppressModeReset;
    /** En modo Barras: codigo ya resuelto y el id de producto al que pertenece (el que se cuenta). */
    private String resolvedBarcode;
    private String resolvedProductId;
    /** Producto del chip de codigo de barras visible, para no ocultarlo al re-validar el mismo id. */
    private String barcodeInfoProductId;
    /** Lectura en revision: aviso de otra presentacion o de codigo no registrado. */
    private String pendingBarcode;
    private ProductPresentationDTO pendingBarcodePresentation;
    /** Codigo no registrado que se asignara al producto elegido en ProductPickerActivity. */
    private String barcodeToAssign;
    /** Descarta respuestas de busquedas por red que llegan despues de un reset o de otra lectura. */
    private int barcodeLookupGeneration;

    private MaterialButtonToggleGroup toggleGroup;
    private TextView tvCodeLabel;
    private ImageView btnScanBarcode;
    private TextView tvBarcodeInfo;
    private View barcodeMismatchContainer;
    private TextView tvBarcodeMismatch;
    private MaterialButton btnBarcodeUseAnyway;
    private View barcodeUnknownContainer;
    private TextView tvBarcodeUnknown;

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

        inventoryPresentation = presentation;
        barcodeScan = new BarcodeScanHelper(this, new BarcodeScanHelper.Listener() {
            @Override
            public void onBarcodeScanned(@NonNull String value) {
                onScannedBarcode(value.trim());
            }

            @Override
            public void onScanningChanged(boolean scanning) {
                updateScanUi(scanning);
            }
        });

        toggleGroup = findViewById(R.id.toggleGroup);
        tvCodeLabel = findViewById(R.id.tvCodeLabel);
        btnScanBarcode = findViewById(R.id.btnScanBarcode);
        tvBarcodeInfo = findViewById(R.id.tvBarcodeInfo);
        barcodeMismatchContainer = findViewById(R.id.barcodeMismatchContainer);
        tvBarcodeMismatch = findViewById(R.id.tvBarcodeMismatch);
        btnBarcodeUseAnyway = findViewById(R.id.btnBarcodeUseAnyway);
        barcodeUnknownContainer = findViewById(R.id.barcodeUnknownContainer);
        tvBarcodeUnknown = findViewById(R.id.tvBarcodeUnknown);
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
            barcodeMode = checkedId == R.id.btnToggleBarcode;
            etCode.setInputType(checkedId == R.id.btnToggleText
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                    : InputType.TYPE_CLASS_NUMBER);
            tvCodeLabel.setText(barcodeMode ? R.string.count_barcode_label : R.string.count_code_label);
            updateScanUi(barcodeScan.isRunning());
            if (!suppressModeReset) {
                resetForm();
            }
        });

        btnScanBarcode.setOnClickListener(v -> barcodeScan.toggle());
        findViewById(R.id.btnBarcodeDiscard).setOnClickListener(v -> discardBarcodeReading());
        findViewById(R.id.btnBarcodeUnknownDiscard).setOnClickListener(v -> discardBarcodeReading());
        btnBarcodeUseAnyway.setOnClickListener(v -> {
            if (pendingBarcode != null && pendingBarcodePresentation != null) {
                applyBarcodeProduct(pendingBarcode, pendingBarcodePresentation, true);
            }
        });
        findViewById(R.id.btnBarcodeAssign).setOnClickListener(v -> {
            if (pendingBarcode == null) {
                return;
            }
            barcodeToAssign = pendingBarcode;
            productPicker.launch(new Intent(this, ProductPickerActivity.class));
        });

        // En modo Barras, editar el codigo invalida la lectura anterior (el id resuelto y los avisos).
        etCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!barcodeMode) {
                    return;
                }
                String text = s.toString().trim();
                if (resolvedBarcode != null && !text.equals(resolvedBarcode)) {
                    resolvedBarcode = null;
                    resolvedProductId = null;
                    hideBarcodeInfo();
                    tvProductDescription.setVisibility(View.GONE);
                    tvStockValue.setText("");
                }
                if (pendingBarcode != null && !text.equals(pendingBarcode)) {
                    hideBarcodeBanners();
                }
            }
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
        etQuantity.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addProductCount();
            }
            return false;
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
                    presentationByBarcode.clear();
                    for (ProductPresentationDTO presentation : response.body().getBarcodes()) {
                        if (!TextUtils.isEmpty(presentation.getBarcode())) {
                            presentationByBarcode.put(presentation.getBarcode(), presentation);
                        }
                    }
                    // El backend regresa los conteos en orden de insercion (mas viejo primero);
                    // se invierte para que el ultimo producto agregado aparezca al inicio.
                    productCounts = response.body().getProductsCount();
                    Collections.reverse(productCounts);

                    applyCountSearchFilter();
                    contentScroll.setVisibility(View.VISIBLE);
                    etCode.requestFocus();
                    showKeyboardFor(etCode);
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

        if (barcodeMode) {
            if (code.equals(resolvedBarcode) && resolvedProductId != null) {
                return;
            }
            resolveBarcode(code);
            return;
        }

        // Un id escrito a mano sustituye al producto encontrado por codigo de barras.
        if (!code.equals(barcodeInfoProductId)) {
            hideBarcodeInfo();
        }
        updateStockDisplay(code);
        existingDifference = computeExistingDifference(code, null);
        refreshDifferenceDisplay();
    }

    // ------------------------------------------------------------------
    // Busqueda por codigo de barras
    // ------------------------------------------------------------------

    /** Lectura de la camara: en modo Barras se muestra en el campo; en los otros se resuelve directo al id. */
    private void onScannedBarcode(String code) {
        if (TextUtils.isEmpty(code)) {
            return;
        }
        if (barcodeMode) {
            etCode.setText(code);
            etCode.setSelection(etCode.getText().length());
        }
        resolveBarcode(code);
    }

    /**
     * Busca la presentacion con ese codigo: primero en la lista descargada con
     * el inventario (inmediato) y, si no esta, en el servidor
     * (GET /products/presentations/barcode/{codigo}); 404 = no registrado.
     */
    private void resolveBarcode(String code) {
        hideBarcodeBanners();
        ProductPresentationDTO local = presentationByBarcode.get(code);
        if (local != null) {
            onBarcodeFound(code, local);
            return;
        }

        int generation = ++barcodeLookupGeneration;
        ProductsApi api = ApiClient.createProductsApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());
        api.getPresentationByBarcode(code).enqueue(new Callback<ProductPresentationDTO>() {
            @Override
            public void onResponse(@NonNull Call<ProductPresentationDTO> call,
                                   @NonNull Response<ProductPresentationDTO> response) {
                if (generation != barcodeLookupGeneration || isFinishing()) {
                    return;
                }
                if (response.code() == 401) {
                    handleSessionExpired();
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    presentationByBarcode.put(code, response.body());
                    onBarcodeFound(code, response.body());
                } else if (response.code() == 404) {
                    showUnknownBarcode(code);
                } else {
                    Toast.makeText(CountNormalActivity.this,
                            getString(R.string.count_barcode_lookup_error, ApiErrorUtils.parseErrorMessage(response)),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ProductPresentationDTO> call, @NonNull Throwable t) {
                if (generation != barcodeLookupGeneration || isFinishing()) {
                    return;
                }
                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Toast.makeText(CountNormalActivity.this,
                        getString(R.string.count_barcode_lookup_error, reason), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Si la presentacion del codigo no es la del inventario, se avisa antes de contar. */
    private void onBarcodeFound(String code, ProductPresentationDTO presentation) {
        if (isSamePresentation(presentation.getPresentation(), inventoryPresentation)) {
            applyBarcodeProduct(code, presentation, false);
            return;
        }
        pendingBarcode = code;
        pendingBarcodePresentation = presentation;
        String description = presentation.getDescription() != null ? presentation.getDescription() : "";
        tvBarcodeMismatch.setText(getString(R.string.count_barcode_mismatch, code, presentation.getProductId(),
                description, presentation.getPresentation(), inventoryPresentation));
        btnBarcodeUseAnyway.setText(getString(R.string.count_barcode_use_anyway, presentation.getProductId()));
        barcodeMismatchContainer.setVisibility(View.VISIBLE);
        btnScanBarcode.performHapticFeedback(HapticFeedbackConstants.REJECT);
    }

    /** Carga el producto del codigo leido en el formulario y deja el cursor en Cantidad. */
    private void applyBarcodeProduct(String code, ProductPresentationDTO presentation, boolean otherPresentation) {
        hideBarcodeBanners();
        String productId = presentation.getProductId();
        if (barcodeMode) {
            resolvedBarcode = null;
            resolvedProductId = null;
            etCode.setText(code);
            resolvedBarcode = code;
            resolvedProductId = productId;
        } else {
            etCode.setText(productId);
        }
        etCode.setSelection(etCode.getText().length());
        etCode.setError(null);

        updateStockDisplay(productId);
        existingDifference = computeExistingDifference(productId, null);
        refreshDifferenceDisplay();

        String info = barcodeMode
                ? getString(R.string.count_barcode_found_id, productId, presentation.getPresentation())
                : getString(R.string.count_barcode_found, code, presentation.getPresentation());
        showBarcodeInfo(info, otherPresentation, productId);

        etQuantity.requestFocus();
        showKeyboardFor(etQuantity);
        etCode.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
    }

    private void showUnknownBarcode(String code) {
        pendingBarcode = code;
        pendingBarcodePresentation = null;
        tvBarcodeUnknown.setText(getString(R.string.count_barcode_unknown, code));
        barcodeUnknownContainer.setVisibility(View.VISIBLE);
        btnScanBarcode.performHapticFeedback(HapticFeedbackConstants.REJECT);
    }

    private void discardBarcodeReading() {
        hideBarcodeBanners();
        if (barcodeMode) {
            etCode.setText("");
        }
        etCode.requestFocus();
        showKeyboardFor(etCode);
    }

    private void confirmAssignBarcode(String code, String productId, String description) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.count_barcode_assign_title)
                .setMessage(getString(R.string.count_barcode_assign_message, code, productId,
                        description != null ? description : "", inventoryPresentation))
                .setPositiveButton(R.string.count_barcode_assign_action, (dialog, which) -> assignBarcode(code, productId))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /**
     * POST /products/{id}/presentations/barcode con la presentacion del
     * inventario: crea la presentacion si el producto no la tiene. Al terminar
     * el conteo continua con ese producto.
     */
    private void assignBarcode(String code, String productId) {
        ProductsApi api = ApiClient.createProductsApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());
        api.assignBarcode(productId, new AssignBarcodeRequest(inventoryPresentation, code))
                .enqueue(new Callback<ProductPresentationDTO>() {
                    @Override
                    public void onResponse(@NonNull Call<ProductPresentationDTO> call,
                                           @NonNull Response<ProductPresentationDTO> response) {
                        if (response.code() == 401) {
                            handleSessionExpired();
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null) {
                            presentationByBarcode.put(code, response.body());
                            Toast.makeText(CountNormalActivity.this,
                                    getString(R.string.count_barcode_assigned, productId, inventoryPresentation),
                                    Toast.LENGTH_SHORT).show();
                            applyBarcodeProduct(code, response.body(), false);
                        } else {
                            Toast.makeText(CountNormalActivity.this,
                                    getString(R.string.count_barcode_assign_error, ApiErrorUtils.parseErrorMessage(response)),
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ProductPresentationDTO> call, @NonNull Throwable t) {
                        String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                        Toast.makeText(CountNormalActivity.this,
                                getString(R.string.count_barcode_assign_error, reason), Toast.LENGTH_LONG).show();
                    }
                });
    }

    /** Chip bajo la descripcion: verde si coincide la presentacion, ambar si se conto de otra. */
    private void showBarcodeInfo(String text, boolean otherPresentation, String productId) {
        tvBarcodeInfo.setText(text);
        tvBarcodeInfo.setBackgroundResource(otherPresentation ? R.drawable.bg_chip_accent : R.drawable.bg_chip_good);
        tvBarcodeInfo.setTextColor(ContextCompat.getColor(this,
                otherPresentation ? R.color.md_theme_onTertiaryContainer : R.color.app_onSuccessContainer));
        tvBarcodeInfo.setVisibility(View.VISIBLE);
        barcodeInfoProductId = productId;
    }

    private void hideBarcodeInfo() {
        tvBarcodeInfo.setVisibility(View.GONE);
        barcodeInfoProductId = null;
    }

    private void hideBarcodeBanners() {
        barcodeMismatchContainer.setVisibility(View.GONE);
        barcodeUnknownContainer.setVisibility(View.GONE);
        pendingBarcode = null;
        pendingBarcodePresentation = null;
    }

    /** Mientras la camara lee, el hint del campo lo indica y el icono cambia de color. */
    private void updateScanUi(boolean scanning) {
        if (scanning) {
            etCode.setHint(R.string.count_barcode_scanning_hint);
        } else {
            etCode.setHint(barcodeMode ? getString(R.string.count_barcode_hint) : null);
        }
        int color = scanning
                ? ContextCompat.getColor(this, R.color.md_theme_error)
                : MaterialColors.getColor(btnScanBarcode, androidx.appcompat.R.attr.colorPrimary);
        ImageViewCompat.setImageTintList(btnScanBarcode, ColorStateList.valueOf(color));
    }

    /** Compara presentaciones ignorando mayusculas y espacios ("4 lts" == "4 LTS"). */
    private static boolean isSamePresentation(String a, String b) {
        if (a == null || b == null) {
            return true; // Sin presentacion del inventario no hay contra que comparar.
        }
        return a.trim().replaceAll("\\s+", " ").equalsIgnoreCase(b.trim().replaceAll("\\s+", " "));
    }

    /** Id del producto que se cuenta: en modo Barras el resuelto a partir del codigo. */
    private String currentProductCode() {
        return barcodeMode ? resolvedProductId : etCode.getText().toString().trim();
    }

    /** Cambia el modo del campo Codigo sin limpiar el formulario. */
    private void switchCodeMode(int buttonId) {
        suppressModeReset = true;
        toggleGroup.check(buttonId);
        suppressModeReset = false;
    }

    /**
     * Resultado de ProductPickerActivity: el codigo del envase puede ser
     * ilegible durante el conteo fisico, asi que se permite buscar el
     * producto por nombre y cargar aqui el codigo elegido.
     */
    private void onProductPicked(ActivityResult result) {
        // El selector tambien se usa para elegir a que producto asignar un codigo no registrado.
        String codeToAssign = barcodeToAssign;
        barcodeToAssign = null;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            return;
        }
        String productId = result.getData().getStringExtra(ProductPickerActivity.EXTRA_PRODUCT_ID);
        if (TextUtils.isEmpty(productId)) {
            return;
        }
        if (codeToAssign != null) {
            confirmAssignBarcode(codeToAssign, productId,
                    result.getData().getStringExtra(ProductPickerActivity.EXTRA_PRODUCT_DESCRIPTION));
            return;
        }
        if (barcodeMode) {
            // El selector devuelve un id de producto, no un codigo de barras.
            switchCodeMode(R.id.btnToggleNumber);
        }
        hideBarcodeBanners();
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
        if (TextUtils.isEmpty(etCode.getText().toString().trim())) {
            etCode.setError(getString(R.string.count_error_code_required));
            etCode.requestFocus();
            return;
        }
        String code = currentProductCode();
        if (TextUtils.isEmpty(code)) {
            // Modo Barras con un codigo escrito que aun no se ha buscado (o que no se encontro).
            etCode.setError(getString(R.string.count_barcode_unresolved));
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
        String code = currentProductCode();
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

    /**
     * requestFocus() por si solo no reabre el teclado si ya se habia cerrado
     * (p.ej. al presionar "Listo" en cantidad, o tras recargar la lista
     * despues de agregar/editar/eliminar un conteo). Se pide en el siguiente
     * frame (view.post) para que la vista ya tenga el foco antes de pedirle al
     * sistema que muestre el teclado sobre ella.
     */
    private void showKeyboardFor(View view) {
        view.post(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void resetForm() {
        editingEntry = null;
        updateAddButtonLabel();
        barcodeScan.stop();
        barcodeLookupGeneration++;
        hideBarcodeBanners();
        hideBarcodeInfo();
        resolvedBarcode = null;
        resolvedProductId = null;
        etCode.setText("");
        tvStockValue.setText("");
        tvProductDescription.setVisibility(View.GONE);
        etQuantity.setText("");
        tvDifference.setText("");
        existingDifference = 0f;
        setUnknownProductUiVisible(false);
        etCode.requestFocus();
        showKeyboardFor(etCode);
    }

    @Override
    public void onSelect(ProductCountEntryDTO entry) {
        editingEntry = entry;
        updateAddButtonLabel();
        if (barcodeMode) {
            // El renglon se edita por id de producto.
            switchCodeMode(R.id.btnToggleNumber);
        }
        hideBarcodeBanners();
        hideBarcodeInfo();

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
