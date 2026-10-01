package com.example.myapplication;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
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
import com.example.myapplication.data.InventoryLock;
import com.example.myapplication.data.ServerPreferences;
import com.example.myapplication.data.SessionPreferences;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.ApiErrorUtils;
import com.example.myapplication.network.InventoriesApi;
import com.example.myapplication.network.InventoryDTO;
import com.example.myapplication.network.NewProductCountRequest;
import com.example.myapplication.network.NormalInventoryDataDTO;
import com.example.myapplication.network.ProductCountCreatedDTO;
import com.example.myapplication.network.ProductCountEntryDTO;
import com.example.myapplication.network.ProductCountsApi;
import com.example.myapplication.network.ProductPresentationDTO;
import com.example.myapplication.network.StockItemDTO;
import com.example.myapplication.scanner.BarcodeLookup;
import com.example.myapplication.scanner.BarcodeScanHelper;
import com.example.myapplication.util.DateFormatUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
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
 *
 * Tiene las funciones del conteo normal adaptadas al recorrido: el codigo de
 * barras sirve para verificar el envase del producto actual (o saltar al
 * producto al que pertenece), la lista de conteos se puede filtrar y editar,
 * y el menu permite finalizar el conteo (cerrar el inventario).
 */
public class CountGuidedActivity extends BaseActivity implements ProductCountAdapter.OnProductCountActionListener {

    public static final String EXTRA_INVENTORY_ID = "extra_inventory_id";
    public static final String EXTRA_PRESENTATION = "extra_presentation";
    public static final String EXTRA_DATE = "extra_date";

    /** Debe coincidir en orden con R.array.product_location_labels y con el enum ProductLocation del backend. */
    private static final String[] PLACE_API_VALUES = {"SALES_AREA", "WAREHOUSE", "STORAGE_AREA", "NOTE"};
    private static final int PLACE_DEFAULT_INDEX = 0; // SALES_AREA / "Piso de venta"

    private final ActivityResultLauncher<Intent> productPicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::onProductPicked);

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
    private String currentCountSearch = "";

    /** Renglon de la lista que se esta editando (se reemplaza al guardar) y el paso al que se regresa. */
    private ProductCountEntryDTO editingEntry;
    private int returnIndex = -1;

    // ---- Codigo de barras ----
    private BarcodeLookup barcodeLookup;
    private BarcodeScanHelper barcodeScan;
    /** Lectura en revision (aviso visible). */
    private String pendingBarcode;
    private ProductPresentationDTO pendingBarcodePresentation;
    /** Paso de la secuencia al que pertenece el codigo leido, si es de otro producto del recorrido. */
    private int pendingJumpIndex = -1;
    /** Codigo no registrado que se asignara al producto elegido en ProductPickerActivity. */
    private String barcodeToAssign;

    private View contentScroll;
    private View progressLoad;
    private View errorState;
    private TextView tvErrorMessage;

    private TextView tvGuidedProgress;
    private MaterialCardView cardPrevious;
    private TextView tvPrevId;
    private TextView tvPrevDescription;
    private TextView tvEditingInfo;
    private EditText etBarcode;
    private ImageView btnScanBarcode;
    private FloatingActionButton fabScanBarcode;
    private TextView tvBarcodeInfo;
    private View barcodeMismatchContainer;
    private TextView tvBarcodeMismatch;
    private MaterialButton btnBarcodeUseAnyway;
    private View barcodeUnknownContainer;
    private TextView tvBarcodeUnknown;
    private MaterialButton btnBarcodeAssign;
    private TextView tvCurrentId;
    private TextView tvCurrentDescription;
    private TextView tvCurrentStock;
    private EditText etQuantity;
    private TextView tvCounted;
    private TextView tvDifference;
    private Spinner spinnerPlace;
    private MaterialCardView cardNext;
    private TextView tvNextId;
    private TextView tvNextDescription;
    private MaterialButton btnPrev;
    private MaterialButton btnNext;
    private View progressSubmit;
    private EditText etSearchCount;
    private TextView tvSearchCountResult;
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
        // Marca el inventario en uso (LOCKED) mientras este conteo esta abierto; ver InventoryLock.
        new InventoryLock(this, inventoryId, new InventoryLock.Listener() {
            @Override
            public void onLockedByOther(@NonNull String message) {
                showLockedByOtherDialog(message);
            }

            @Override
            public void onSessionExpired() {
                handleSessionExpired();
            }
        });
        presentation = getIntent().getStringExtra(EXTRA_PRESENTATION);
        dateIso = getIntent().getStringExtra(EXTRA_DATE);

        barcodeLookup = new BarcodeLookup(serverPreferences.getBaseUrl(), sessionPreferences.getToken());
        barcodeScan = new BarcodeScanHelper(this, new BarcodeScanHelper.Listener() {
            @Override
            public void onBarcodeScanned(@NonNull String value) {
                String code = value.trim();
                etBarcode.setText(code);
                etBarcode.setSelection(etBarcode.getText().length());
                resolveBarcode(code);
            }

            @Override
            public void onScanningChanged(boolean scanning) {
                updateScanUi(scanning);
            }
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setSubtitle(presentation + " · " + DateFormatUtils.toShortSpanishDate(dateIso));
        if (toolbar.getOverflowIcon() != null) {
            // El icono de 3 puntos toma colorControlNormal (oscuro); sobre la barra azul va en blanco.
            toolbar.getOverflowIcon().setTint(
                    MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnPrimary));
        }
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_finish_count) {
                confirmFinishCount();
                return true;
            }
            return false;
        });

        contentScroll = findViewById(R.id.contentScroll);
        progressLoad = findViewById(R.id.progressLoad);
        errorState = findViewById(R.id.errorState);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);

        tvGuidedProgress = findViewById(R.id.tvGuidedProgress);
        cardPrevious = findViewById(R.id.cardPrevious);
        tvPrevId = findViewById(R.id.tvPrevId);
        tvPrevDescription = findViewById(R.id.tvPrevDescription);
        tvEditingInfo = findViewById(R.id.tvEditingInfo);
        etBarcode = findViewById(R.id.etBarcode);
        btnScanBarcode = findViewById(R.id.btnScanBarcode);
        fabScanBarcode = findViewById(R.id.fabScanBarcode);
        tvBarcodeInfo = findViewById(R.id.tvBarcodeInfo);
        barcodeMismatchContainer = findViewById(R.id.barcodeMismatchContainer);
        tvBarcodeMismatch = findViewById(R.id.tvBarcodeMismatch);
        btnBarcodeUseAnyway = findViewById(R.id.btnBarcodeUseAnyway);
        barcodeUnknownContainer = findViewById(R.id.barcodeUnknownContainer);
        tvBarcodeUnknown = findViewById(R.id.tvBarcodeUnknown);
        btnBarcodeAssign = findViewById(R.id.btnBarcodeAssign);
        tvCurrentId = findViewById(R.id.tvCurrentId);
        tvCurrentDescription = findViewById(R.id.tvCurrentDescription);
        tvCurrentStock = findViewById(R.id.tvCurrentStock);
        etQuantity = findViewById(R.id.etQuantity);
        tvCounted = findViewById(R.id.tvCounted);
        tvDifference = findViewById(R.id.tvDifference);
        spinnerPlace = findViewById(R.id.spinnerPlace);
        cardNext = findViewById(R.id.cardNext);
        tvNextId = findViewById(R.id.tvNextId);
        tvNextDescription = findViewById(R.id.tvNextDescription);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        progressSubmit = findViewById(R.id.progressSubmit);
        etSearchCount = findViewById(R.id.etSearchCount);
        tvSearchCountResult = findViewById(R.id.tvSearchCountResult);
        recyclerProductCounts = findViewById(R.id.recyclerProductCounts);
        tvCountsEmpty = findViewById(R.id.tvCountsEmpty);

        etSearchCount.setFilters(new InputFilter[]{new InputFilter.AllCaps()});

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

        etQuantity.addTextChangedListener(simpleWatcher(this::refreshDifferenceDisplay));
        etQuantity.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                goToNextOrFinish();
                return true;
            }
            return false;
        });
        etSearchCount.addTextChangedListener(simpleWatcher(() -> {
            currentCountSearch = etSearchCount.getText().toString().trim();
            applyCountSearchFilter();
        }));

        // Codigo de barras: escanear, o escribirlo y presionar Buscar en el teclado.
        btnScanBarcode.setOnClickListener(v -> barcodeScan.toggle());
        fabScanBarcode.setOnClickListener(v -> barcodeScan.toggle());
        etBarcode.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                String code = etBarcode.getText().toString().trim();
                if (!code.isEmpty()) {
                    resolveBarcode(code);
                }
                return true;
            }
            return false;
        });
        etBarcode.addTextChangedListener(simpleWatcher(() -> {
            // Editar el codigo invalida la lectura anterior.
            String text = etBarcode.getText().toString().trim();
            if (pendingBarcode != null && !text.equals(pendingBarcode)) {
                hideBarcodeBanners();
            }
            if (text.isEmpty()) {
                tvBarcodeInfo.setVisibility(View.GONE);
            }
        }));
        findViewById(R.id.btnBarcodeDiscard).setOnClickListener(v -> discardBarcodeReading());
        findViewById(R.id.btnBarcodeUnknownDiscard).setOnClickListener(v -> discardBarcodeReading());
        btnBarcodeUseAnyway.setOnClickListener(v -> onBarcodeUseAnyway());
        btnBarcodeAssign.setOnClickListener(v -> {
            if (pendingBarcode != null) {
                StockItemDTO current = sequence.get(currentIndex);
                confirmAssignBarcode(pendingBarcode, current.getIdProduct(), current.getDescription());
            }
        });
        View btnAssignOther = findViewById(R.id.btnBarcodeAssignOther);
        btnAssignOther.setVisibility(View.VISIBLE);
        btnAssignOther.setOnClickListener(v -> {
            if (pendingBarcode == null) {
                return;
            }
            barcodeToAssign = pendingBarcode;
            productPicker.launch(new Intent(this, ProductPickerActivity.class)
                    .putExtra(ProductPickerActivity.EXTRA_FOCUS_NUMERIC_SEARCH, true));
        });

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
                    barcodeLookup.setKnownPresentations(response.body().getBarcodes());
                    setProductCounts(response.body().getProductsCount());

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
        etQuantity.setError(null);
        resetBarcodeUi();

        boolean editingThisStep = editingEntry != null && editingEntry.getIdProduct() != null
                && current.getIdProduct().equals(editingEntry.getIdProduct().getId());
        if (editingThisStep) {
            etQuantity.setText(String.format(Locale.US, "%.3f", editingEntry.getQuantity()));
            etQuantity.setSelection(etQuantity.getText().length());
            selectPlace(editingEntry.getPlace());
            tvEditingInfo.setText(getString(R.string.count_guided_editing,
                    String.format(Locale.US, "%.3f", editingEntry.getQuantity()), placeLabel(editingEntry.getPlace())));
            tvEditingInfo.setVisibility(View.VISIBLE);
        } else {
            etQuantity.setText("");
            tvEditingInfo.setVisibility(View.GONE);
        }
        refreshDifferenceDisplay();

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

        updateNavButtons();
        etQuantity.requestFocus();
        showKeyboardFor(etQuantity);
    }

    /** Mientras se edita un renglon: Anterior pasa a ser Cancelar y Siguiente pasa a ser Actualizar. */
    private void updateNavButtons() {
        boolean editing = editingEntry != null;
        btnPrev.setText(editing ? R.string.count_cancel : R.string.count_guided_btn_prev);
        btnPrev.setEnabled(!isSaving && (editing || currentIndex > 0));
        btnNext.setEnabled(!isSaving);
        if (isSaving) {
            btnNext.setText(editing ? R.string.count_update_loading : R.string.count_guided_btn_loading);
        } else if (editing) {
            btnNext.setText(R.string.count_update);
        } else {
            btnNext.setText(currentIndex == sequence.size() - 1
                    ? R.string.count_guided_btn_finish
                    : R.string.count_guided_btn_next);
        }
    }

    private void goToPrevious() {
        if (editingEntry != null) {
            finishEditing();
            return;
        }
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
        if (editingEntry == null && (TextUtils.isEmpty(quantityText) || submitted[currentIndex])) {
            advanceOrFinish();
            return;
        }
        if (TextUtils.isEmpty(quantityText)) {
            etQuantity.setError(getString(R.string.count_error_quantity_required));
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

        if (editingEntry != null) {
            updateEditingEntry(quantity);
        } else {
            submitCurrentCount(quantity);
        }
    }

    private void submitCurrentCount(float quantity) {
        StockItemDTO current = sequence.get(currentIndex);
        String place = PLACE_API_VALUES[spinnerPlace.getSelectedItemPosition()];
        int stepIndex = currentIndex;

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
                            submitted[stepIndex] = true;
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

    /**
     * El backend no tiene un endpoint de actualizacion para /productCounts (igual
     * que en el conteo normal): se elimina el renglon original y se crea uno
     * nuevo con los valores capturados. Al terminar se regresa al paso donde
     * estaba el recorrido antes de tocar el renglon.
     */
    private void updateEditingEntry(float quantity) {
        ProductCountEntryDTO original = editingEntry;
        String productId = sequence.get(currentIndex).getIdProduct();
        String place = PLACE_API_VALUES[spinnerPlace.getSelectedItemPosition()];

        setSavingState(true);
        ProductCountsApi api = ApiClient.createProductCountsApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());

        api.deleteProductCount(original.getId()).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.code() == 401) {
                    setSavingState(false);
                    handleSessionExpired();
                    return;
                }
                if (!response.isSuccessful()) {
                    setSavingState(false);
                    Toast.makeText(CountGuidedActivity.this,
                            getString(R.string.count_update_error, ApiErrorUtils.parseErrorMessage(response)),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                api.addProductCount(new NewProductCountRequest(inventoryId, productId, quantity, place))
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
                                    Toast.makeText(CountGuidedActivity.this, R.string.count_update_success,
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    // El renglon original ya no existe: se avisa y la lista refleja el estado real.
                                    Toast.makeText(CountGuidedActivity.this,
                                            getString(R.string.count_update_partial_error,
                                                    ApiErrorUtils.parseErrorMessage(response)),
                                            Toast.LENGTH_LONG).show();
                                }
                                refreshCounts();
                                finishEditing();
                            }

                            @Override
                            public void onFailure(@NonNull Call<ProductCountCreatedDTO> call, @NonNull Throwable t) {
                                setSavingState(false);
                                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                                Toast.makeText(CountGuidedActivity.this,
                                        getString(R.string.count_update_partial_error, reason), Toast.LENGTH_LONG).show();
                                refreshCounts();
                                finishEditing();
                            }
                        });
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                setSavingState(false);
                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Toast.makeText(CountGuidedActivity.this,
                        getString(R.string.count_update_error, reason), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Sale del modo edicion y regresa al paso donde estaba el recorrido. */
    private void finishEditing() {
        editingEntry = null;
        if (returnIndex >= 0 && returnIndex < sequence.size()) {
            currentIndex = returnIndex;
        }
        returnIndex = -1;
        renderStep();
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
        updateNavButtons();
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
                    barcodeLookup.setKnownPresentations(response.body().getBarcodes());
                    setProductCounts(response.body().getProductsCount());
                    refreshDifferenceDisplay();
                }
            }

            @Override
            public void onFailure(@NonNull Call<NormalInventoryDataDTO> call, @NonNull Throwable t) {
                // Se ignora: la lista se queda con los datos anteriores y se reintenta en la siguiente accion.
            }
        });
    }

    /** El backend regresa los conteos del mas viejo al mas nuevo; se invierten para ver primero el ultimo. */
    private void setProductCounts(List<ProductCountEntryDTO> counts) {
        productCounts = new ArrayList<>(counts);
        Collections.reverse(productCounts);
        applyCountSearchFilter();
    }

    /** Filtra la lista de conteos registrados por codigo o descripcion (igual que el conteo normal). */
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
                boolean matchesDescription = description != null
                        && description.toLowerCase(Locale.getDefault()).contains(needle);
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

    /**
     * "Ya contado" = suma de los renglones registrados del producto actual (sin
     * el que se esta editando). Diferencia = ya contado + cantidad escrita - stock.
     */
    private void refreshDifferenceDisplay() {
        if (sequence.isEmpty()) {
            return;
        }
        StockItemDTO current = sequence.get(currentIndex);
        float counted = 0f;
        for (ProductCountEntryDTO entry : productCounts) {
            if (editingEntry != null && entry.getId() == editingEntry.getId()) {
                continue;
            }
            if (entry.getIdProduct() != null && current.getIdProduct().equals(entry.getIdProduct().getId())) {
                counted += entry.getQuantity();
            }
        }
        float typed = 0f;
        String quantityText = etQuantity.getText().toString().trim();
        if (!quantityText.isEmpty()) {
            try {
                typed = Float.parseFloat(quantityText.replace(',', '.'));
            } catch (NumberFormatException ignored) {
                // El usuario todavia esta escribiendo (ej. "1."); se actualiza en el siguiente caracter.
            }
        }
        tvCounted.setText(String.format(Locale.US, "%.3f", counted));
        tvDifference.setText(String.format(Locale.US, "%+.3f", counted + typed - current.getStock()));
    }

    // ------------------------------------------------------------------
    // Codigo de barras: verificar el envase del producto actual
    // ------------------------------------------------------------------

    private void resolveBarcode(String code) {
        hideBarcodeBanners();
        tvBarcodeInfo.setVisibility(View.GONE);
        barcodeLookup.resolve(code, new BarcodeLookup.Callback() {
            @Override
            public void onFound(@NonNull String foundCode, @NonNull ProductPresentationDTO found) {
                if (!isFinishing()) {
                    onBarcodeFound(foundCode, found);
                }
            }

            @Override
            public void onNotFound(@NonNull String notFoundCode) {
                if (!isFinishing()) {
                    showUnknownBarcode(notFoundCode);
                }
            }

            @Override
            public void onError(@NonNull String reason) {
                Toast.makeText(CountGuidedActivity.this,
                        getString(R.string.count_barcode_lookup_error, reason), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onSessionExpired() {
                handleSessionExpired();
            }
        });
    }

    /**
     * - Del producto actual y de la presentacion del inventario: verificado.
     * - Del producto actual pero de otra presentacion: aviso antes de contar.
     * - De otro producto del recorrido: se ofrece ir a ese paso.
     * - De un producto que no esta en el stock: se indica usar el conteo normal.
     */
    private void onBarcodeFound(String code, ProductPresentationDTO found) {
        String productId = found.getProductId();
        boolean samePresentation = BarcodeLookup.isSamePresentation(found.getPresentation(), presentation);
        String description = found.getDescription() != null ? found.getDescription() : "";
        int index = indexInSequence(productId);

        if (index == currentIndex) {
            if (samePresentation) {
                showBarcodeVerified(code, found, false);
                return;
            }
            showBarcodeWarning(code, found, -1,
                    getString(R.string.count_barcode_mismatch, code, productId, description,
                            found.getPresentation(), presentation),
                    getString(R.string.count_barcode_use_anyway, productId));
        } else if (index >= 0) {
            showBarcodeWarning(code, found, index,
                    getString(R.string.count_guided_barcode_other_product, code, productId, description,
                            found.getPresentation(), index + 1, sequence.size()),
                    getString(R.string.count_guided_barcode_go_to, productId));
        } else {
            showBarcodeWarning(code, found, -1,
                    getString(R.string.count_guided_barcode_not_in_stock, code, productId, description),
                    null);
        }
    }

    private void showBarcodeWarning(String code, ProductPresentationDTO found, int jumpIndex,
                                    String message, String actionText) {
        pendingBarcode = code;
        pendingBarcodePresentation = found;
        pendingJumpIndex = jumpIndex;
        tvBarcodeMismatch.setText(message);
        if (actionText != null) {
            btnBarcodeUseAnyway.setText(actionText);
            btnBarcodeUseAnyway.setVisibility(View.VISIBLE);
        } else {
            btnBarcodeUseAnyway.setVisibility(View.GONE);
        }
        barcodeMismatchContainer.setVisibility(View.VISIBLE);
        btnScanBarcode.performHapticFeedback(HapticFeedbackConstants.REJECT);
    }

    /** Boton del aviso ambar: contar de todos modos (otra presentacion) o ir al producto del codigo. */
    private void onBarcodeUseAnyway() {
        String code = pendingBarcode;
        ProductPresentationDTO found = pendingBarcodePresentation;
        int jumpIndex = pendingJumpIndex;
        if (code == null || found == null) {
            return;
        }
        boolean otherPresentation = !BarcodeLookup.isSamePresentation(found.getPresentation(), presentation);
        if (jumpIndex >= 0) {
            if (editingEntry != null) {
                // Saltar a otro producto cancela la edicion en curso.
                editingEntry = null;
                returnIndex = -1;
            }
            currentIndex = jumpIndex;
            renderStep();
            etBarcode.setText(code);
        }
        showBarcodeVerified(code, found, otherPresentation);
    }

    private void showBarcodeVerified(String code, ProductPresentationDTO found, boolean otherPresentation) {
        hideBarcodeBanners();
        tvBarcodeInfo.setText(getString(R.string.count_guided_barcode_verified, code, found.getPresentation()));
        tvBarcodeInfo.setBackgroundResource(otherPresentation ? R.drawable.bg_chip_accent : R.drawable.bg_chip_good);
        tvBarcodeInfo.setTextColor(ContextCompat.getColor(this,
                otherPresentation ? R.color.md_theme_onTertiaryContainer : R.color.app_onSuccessContainer));
        tvBarcodeInfo.setVisibility(View.VISIBLE);
        etQuantity.requestFocus();
        showKeyboardFor(etQuantity);
        btnScanBarcode.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
    }

    private void showUnknownBarcode(String code) {
        pendingBarcode = code;
        pendingBarcodePresentation = null;
        pendingJumpIndex = -1;
        tvBarcodeUnknown.setText(getString(R.string.count_barcode_unknown, code));
        btnBarcodeAssign.setText(getString(R.string.count_guided_barcode_assign_current,
                sequence.get(currentIndex).getIdProduct()));
        barcodeUnknownContainer.setVisibility(View.VISIBLE);
        btnScanBarcode.performHapticFeedback(HapticFeedbackConstants.REJECT);
    }

    private void confirmAssignBarcode(String code, String productId, String description) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.count_barcode_assign_title)
                .setMessage(getString(R.string.count_barcode_assign_message, code, productId,
                        description != null ? description : "", presentation))
                .setPositiveButton(R.string.count_barcode_assign_action, (dialog, which) -> assignBarcode(code, productId))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** Al asignarlo, el codigo se procesa como una lectura normal (verificado, o ir al producto elegido). */
    private void assignBarcode(String code, String productId) {
        barcodeLookup.assign(productId, presentation, code, new BarcodeLookup.Callback() {
            @Override
            public void onFound(@NonNull String assignedCode, @NonNull ProductPresentationDTO found) {
                Toast.makeText(CountGuidedActivity.this,
                        getString(R.string.count_barcode_assigned, productId, presentation), Toast.LENGTH_SHORT).show();
                hideBarcodeBanners();
                onBarcodeFound(assignedCode, found);
            }

            @Override
            public void onNotFound(@NonNull String notFoundCode) {
                // No aplica al asignar.
            }

            @Override
            public void onError(@NonNull String reason) {
                Toast.makeText(CountGuidedActivity.this,
                        getString(R.string.count_barcode_assign_error, reason), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onSessionExpired() {
                handleSessionExpired();
            }
        });
    }

    /** Resultado de ProductPickerActivity: producto al que se asignara un codigo no registrado. */
    private void onProductPicked(ActivityResult result) {
        String code = barcodeToAssign;
        barcodeToAssign = null;
        if (code == null || result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            return;
        }
        String productId = result.getData().getStringExtra(ProductPickerActivity.EXTRA_PRODUCT_ID);
        if (!TextUtils.isEmpty(productId)) {
            confirmAssignBarcode(code, productId,
                    result.getData().getStringExtra(ProductPickerActivity.EXTRA_PRODUCT_DESCRIPTION));
        }
    }

    private void discardBarcodeReading() {
        hideBarcodeBanners();
        etBarcode.setText("");
        etQuantity.requestFocus();
    }

    private void hideBarcodeBanners() {
        barcodeMismatchContainer.setVisibility(View.GONE);
        barcodeUnknownContainer.setVisibility(View.GONE);
        pendingBarcode = null;
        pendingBarcodePresentation = null;
        pendingJumpIndex = -1;
    }

    /** Cada paso empieza sin lectura: se apaga la camara y se descarta cualquier busqueda en curso. */
    private void resetBarcodeUi() {
        barcodeScan.stop();
        barcodeLookup.cancelPending();
        hideBarcodeBanners();
        tvBarcodeInfo.setVisibility(View.GONE);
        etBarcode.setText("");
        etBarcode.setError(null);
    }

    /** Mientras la camara lee, el hint del campo lo indica y los iconos de escaneo cambian de color. */
    private void updateScanUi(boolean scanning) {
        etBarcode.setHint(scanning ? R.string.count_barcode_scanning_hint : R.string.count_guided_barcode_hint);
        int color = scanning
                ? ContextCompat.getColor(this, R.color.md_theme_error)
                : MaterialColors.getColor(btnScanBarcode, androidx.appcompat.R.attr.colorPrimary);
        ImageViewCompat.setImageTintList(btnScanBarcode, ColorStateList.valueOf(color));
        fabScanBarcode.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    private int indexInSequence(String productId) {
        for (int i = 0; i < sequence.size(); i++) {
            if (sequence.get(i).getIdProduct().equals(productId)) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // Finalizar conteo, resumen y lista de conteos
    // ------------------------------------------------------------------

    private void confirmFinishCount() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.count_finish_confirm_title)
                .setMessage(getString(R.string.count_finish_confirm_message, presentation))
                .setPositiveButton(R.string.count_finish_confirm_action, (dialog, which) -> finishCount())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /**
     * Finalizar conteo = cerrar el inventario (PUT /inventories/{id}/close), como
     * en el conteo normal. Regresa a la lista con RESULT_OK para que se recargue.
     */
    private void finishCount() {
        barcodeScan.stop();
        InventoriesApi api = ApiClient.createInventoriesApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());
        api.closeInventory(inventoryId).enqueue(new Callback<InventoryDTO>() {
            @Override
            public void onResponse(@NonNull Call<InventoryDTO> call, @NonNull Response<InventoryDTO> response) {
                if (response.code() == 401) {
                    handleSessionExpired();
                    return;
                }
                if (response.isSuccessful()) {
                    Toast.makeText(CountGuidedActivity.this, R.string.count_finish_success, Toast.LENGTH_SHORT).show();
                    setResult(Activity.RESULT_OK);
                    finish();
                } else {
                    Toast.makeText(CountGuidedActivity.this,
                            getString(R.string.count_finish_error, ApiErrorUtils.parseErrorMessage(response)),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<InventoryDTO> call, @NonNull Throwable t) {
                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Toast.makeText(CountGuidedActivity.this,
                        getString(R.string.count_finish_error, reason), Toast.LENGTH_LONG).show();
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

    /**
     * Tocar un renglon de la lista lo abre para editarlo en el paso de su
     * producto; al guardar o cancelar se regresa al paso donde se iba.
     */
    @Override
    public void onSelect(ProductCountEntryDTO entry) {
        if (isSaving) {
            return;
        }
        String code = entry.getIdProduct() != null ? entry.getIdProduct().getId() : "";
        int index = indexInSequence(code);
        if (index < 0) {
            Toast.makeText(this, getString(R.string.count_guided_not_in_sequence, code), Toast.LENGTH_LONG).show();
            return;
        }
        if (editingEntry == null) {
            returnIndex = currentIndex;
        }
        editingEntry = entry;
        currentIndex = index;
        renderStep();
    }

    /** Mantener presionado 1 s un renglon: detalle de solo lectura, sin entrar a la edicion. */
    @Override
    public void onShowDetail(ProductCountEntryDTO entry) {
        CountEntryDetailDialog.show(this, entry);
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
                    if (editingEntry != null && editingEntry.getId() == entry.getId()) {
                        finishEditing();
                    }
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

    private void selectPlace(String place) {
        for (int i = 0; i < PLACE_API_VALUES.length; i++) {
            if (PLACE_API_VALUES[i].equals(place)) {
                spinnerPlace.setSelection(i);
                return;
            }
        }
    }

    private String placeLabel(String place) {
        String[] labels = getResources().getStringArray(R.array.product_location_labels);
        for (int i = 0; i < PLACE_API_VALUES.length && i < labels.length; i++) {
            if (PLACE_API_VALUES[i].equals(place)) {
                return labels[i];
            }
        }
        return place != null ? place : "";
    }

    /** Ver CountNormalActivity#showKeyboardFor: se pide en el siguiente frame para que la vista ya tenga el foco. */
    private void showKeyboardFor(View view) {
        view.post(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private static TextWatcher simpleWatcher(Runnable onChanged) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onChanged.run();
            }
        };
    }

    private void showError(String message) {
        tvErrorMessage.setText(message);
        errorState.setVisibility(View.VISIBLE);
        contentScroll.setVisibility(View.GONE);
    }

    /** Otro usuario esta usando el inventario: se avisa quien y se regresa a la lista. */
    private void showLockedByOtherDialog(String message) {
        if (isFinishing()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.count_locked_by_other_title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.action_accept, (dialog, which) -> finish())
                .show();
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
