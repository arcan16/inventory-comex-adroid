package com.example.myapplication;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.myapplication.adapter.ProductAdapter;
import com.example.myapplication.data.ServerPreferences;
import com.example.myapplication.data.SessionPreferences;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.ApiErrorUtils;
import com.example.myapplication.network.PageResponse;
import com.example.myapplication.network.ProductDTO;
import com.example.myapplication.network.ProductPresentationDTO;
import com.example.myapplication.network.ProductsApi;
import com.example.myapplication.network.UpdateProductPresentationRequest;
import com.example.myapplication.network.UpdateProductRequest;
import com.example.myapplication.scanner.BarcodeScanHelper;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProductsActivity extends BaseActivity implements ProductAdapter.OnProductClickListener {

    private static final int PAGE_SIZE = 20;
    /** Cuantos renglones antes del final se dispara la siguiente pagina. */
    private static final int LOAD_MORE_THRESHOLD = 4;
    private static final long SEARCH_DEBOUNCE_MS = 350;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Runnable searchRunnable = () -> fetchPage(0, false);

    private BarcodeScanHelper barcodeScan;
    /** Campo del dialogo abierto que recibira el codigo leido por la camara. */
    private EditText pendingBarcodeTarget;

    private ServerPreferences serverPreferences;
    private SessionPreferences sessionPreferences;
    private ProductAdapter adapter;

    private int currentPage = 0;
    private int totalPages = 1;
    private boolean isLoadingPage;
    private String currentQuery;

    private EditText etSearch;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerProducts;
    private View progressLoad;
    private View progressLoadMore;
    private View emptyStateContainer;
    private TextView tvEmptyMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_products);

        serverPreferences = new ServerPreferences(this);
        sessionPreferences = new SessionPreferences(this);
        barcodeScan = new BarcodeScanHelper(this, this::onBarcodeScanned);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etSearch = findViewById(R.id.etSearch);
        etSearch.setFilters(new InputFilter[]{new InputFilter.AllCaps()});
        swipeRefresh = findViewById(R.id.swipeRefresh);
        recyclerProducts = findViewById(R.id.recyclerProducts);
        progressLoad = findViewById(R.id.progressLoad);
        progressLoadMore = findViewById(R.id.progressLoadMore);
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);

        adapter = new ProductAdapter(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerProducts.setLayoutManager(layoutManager);
        recyclerProducts.setAdapter(adapter);
        recyclerProducts.addOnScrollListener(new RecyclerView.OnScrollListener() {
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

        swipeRefresh.setOnRefreshListener(() -> fetchPage(0, false));
        findViewById(R.id.btnRetry).setOnClickListener(v -> fetchPage(0, false));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s.toString().trim();
                searchHandler.removeCallbacks(searchRunnable);
                searchHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                searchHandler.removeCallbacks(searchRunnable);
                fetchPage(0, false);
                return true;
            }
            return false;
        });

        fetchPage(0, false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchHandler.removeCallbacksAndMessages(null);
    }

    private void loadNextPage() {
        if (isLoadingPage || currentPage + 1 >= totalPages) {
            return;
        }
        fetchPage(currentPage + 1, true);
    }

    private void fetchPage(int page, boolean appending) {
        if (!serverPreferences.hasServerConfigured() || !sessionPreferences.isLoggedIn()) {
            handleSessionExpired();
            return;
        }

        isLoadingPage = true;
        if (appending) {
            progressLoadMore.setVisibility(View.VISIBLE);
        } else {
            progressLoad.setVisibility(View.VISIBLE);
            emptyStateContainer.setVisibility(View.GONE);
        }

        ProductsApi api = ApiClient.createProductsApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());
        String query = TextUtils.isEmpty(currentQuery) ? null : currentQuery;

        api.getProducts(page, PAGE_SIZE, query).enqueue(new Callback<PageResponse<ProductDTO>>() {
            @Override
            public void onResponse(@NonNull Call<PageResponse<ProductDTO>> call,
                                    @NonNull Response<PageResponse<ProductDTO>> response) {
                isLoadingPage = false;
                progressLoad.setVisibility(View.GONE);
                progressLoadMore.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);

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
                    if (adapter.isEmpty()) {
                        tvEmptyMessage.setText(R.string.products_empty);
                        emptyStateContainer.setVisibility(View.VISIBLE);
                    } else {
                        emptyStateContainer.setVisibility(View.GONE);
                    }
                } else if (!appending) {
                    adapter.setItems(Collections.emptyList());
                    tvEmptyMessage.setText(R.string.products_load_error);
                    emptyStateContainer.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(ProductsActivity.this, R.string.products_load_more_error, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<PageResponse<ProductDTO>> call, @NonNull Throwable t) {
                isLoadingPage = false;
                progressLoad.setVisibility(View.GONE);
                progressLoadMore.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);

                if (appending) {
                    Toast.makeText(ProductsActivity.this, R.string.products_load_more_error, Toast.LENGTH_SHORT).show();
                } else {
                    adapter.setItems(Collections.emptyList());
                    tvEmptyMessage.setText(R.string.products_load_error);
                    emptyStateContainer.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    /**
     * Toca un renglon del catalogo: como un producto puede tener varias
     * presentaciones (cada una con su codigo de barras), primero se muestran
     * las registradas (GET /products/{id}/presentations) para elegir cual editar.
     */
    @Override
    public void onProductClick(ProductDTO product) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_select_presentation, null);
        TextView tvId = view.findViewById(R.id.tvPickerProductId);
        TextView tvDescription = view.findViewById(R.id.tvPickerDescription);
        View progress = view.findViewById(R.id.progressPresentations);
        TextView tvMessage = view.findViewById(R.id.tvPresentationsMessage);
        LinearLayout container = view.findViewById(R.id.presentationsContainer);

        tvId.setText(product.getId());
        tvDescription.setText(product.getDescription());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.product_presentation_pick_title)
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                // Solo se muestra si el producto no tiene presentaciones (ver abajo).
                .setNeutralButton(R.string.product_edit_description_only,
                        (d, which) -> showEditProductDialog(product, null))
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE));
        dialog.show();

        ProductsApi api = ApiClient.createProductsApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());
        api.getPresentations(product.getId()).enqueue(new Callback<List<ProductPresentationDTO>>() {
            @Override
            public void onResponse(@NonNull Call<List<ProductPresentationDTO>> call,
                                   @NonNull Response<List<ProductPresentationDTO>> response) {
                if (!dialog.isShowing()) {
                    return;
                }
                progress.setVisibility(View.GONE);
                if (response.code() == 401) {
                    dialog.dismiss();
                    handleSessionExpired();
                    return;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    tvMessage.setText(getString(R.string.product_presentations_load_error,
                            ApiErrorUtils.parseErrorMessage(response)));
                    tvMessage.setVisibility(View.VISIBLE);
                    return;
                }

                List<ProductPresentationDTO> presentations = response.body();
                if (presentations.isEmpty()) {
                    tvMessage.setText(R.string.product_presentations_empty);
                    tvMessage.setVisibility(View.VISIBLE);
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.VISIBLE);
                    return;
                }

                LayoutInflater inflater = LayoutInflater.from(ProductsActivity.this);
                for (ProductPresentationDTO presentation : presentations) {
                    View row = inflater.inflate(R.layout.item_product_presentation, container, false);
                    TextView tvLabel = row.findViewById(R.id.tvPresentationLabel);
                    TextView tvBarcode = row.findViewById(R.id.tvPresentationBarcode);
                    tvLabel.setText(presentation.getPresentation());
                    tvBarcode.setText(TextUtils.isEmpty(presentation.getBarcode())
                            ? getString(R.string.product_barcode_hint) : presentation.getBarcode());
                    row.setOnClickListener(v -> {
                        dialog.dismiss();
                        showEditProductDialog(product, presentation);
                    });
                    container.addView(row);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<ProductPresentationDTO>> call, @NonNull Throwable t) {
                if (!dialog.isShowing()) {
                    return;
                }
                progress.setVisibility(View.GONE);
                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                tvMessage.setText(getString(R.string.product_presentations_load_error, reason));
                tvMessage.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * Dialogo Editar producto. Con presentacion muestra id, presentacion,
     * codigo de barras y descripcion tal como estan en la base de datos, y
     * guarda con PUT /products/{id}/presentations/{presentationId}. Sin
     * presentacion (producto sin ninguna registrada) solo permite editar la
     * descripcion con PUT /products/{id}.
     */
    private void showEditProductDialog(ProductDTO product, @Nullable ProductPresentationDTO presentation) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_product, null);
        TextView tvId = view.findViewById(R.id.tvDialogProductId);
        TextView tvPresentation = view.findViewById(R.id.tvDialogPresentation);
        EditText etBarcode = view.findViewById(R.id.etDialogBarcode);
        EditText etDescription = view.findViewById(R.id.etDialogDescription);
        View btnScanBarcode = view.findViewById(R.id.btnScanBarcode);

        etDescription.setFilters(new InputFilter[]{new InputFilter.AllCaps()});
        tvId.setText(product.getId());
        if (presentation != null) {
            tvPresentation.setText(presentation.getPresentation());
            etBarcode.setText(presentation.getBarcode());
            etDescription.setText(presentation.getDescription());
        } else {
            view.findViewById(R.id.fieldPresentation).setVisibility(View.GONE);
            view.findViewById(R.id.fieldBarcode).setVisibility(View.GONE);
            etDescription.setText(product.getDescription());
        }
        etDescription.setSelection(etDescription.getText().length());
        btnScanBarcode.setOnClickListener(v -> toggleBarcodeScan(etBarcode));

        // Boton "Quitar codigo": un campo vacio por si solo conserva el codigo
        // guardado, asi que quitarlo se marca explicitamente y se aplica al
        // guardar (Cancelar lo deshace). Escribir o escanear otro codigo lo anula.
        boolean[] clearBarcodeRequested = {false};
        View btnClearBarcode = view.findViewById(R.id.btnClearBarcode);
        btnClearBarcode.setVisibility(etBarcode.length() > 0 ? View.VISIBLE : View.GONE);
        etBarcode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                btnClearBarcode.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (s.length() > 0 && clearBarcodeRequested[0]) {
                    clearBarcodeRequested[0] = false;
                    etBarcode.setHint(R.string.product_barcode_hint);
                }
            }
        });
        btnClearBarcode.setOnClickListener(v -> {
            stopBarcodeScan();
            etBarcode.setText("");
            if (presentation != null && !TextUtils.isEmpty(presentation.getBarcode())) {
                clearBarcodeRequested[0] = true;
                etBarcode.setHint(R.string.product_barcode_clear_pending);
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.product_edit_title)
                .setView(view)
                .setPositiveButton(R.string.product_edit_save, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        // Si el dialogo se cierra mientras se escanea, se apaga la camara.
        dialog.setOnDismissListener(d -> stopBarcodeScan());

        // Se sobreescribe el listener del boton positivo despues de show() para poder
        // validar y esperar la respuesta del servidor sin que el dialogo se cierre solo.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String description = etDescription.getText().toString().trim();
            if (TextUtils.isEmpty(description)) {
                etDescription.setError(getString(R.string.product_edit_error_description_required));
                etDescription.requestFocus();
                return;
            }
            ProductsApi api = ApiClient.createProductsApi(
                    serverPreferences.getBaseUrl(), sessionPreferences.getToken());
            if (presentation != null) {
                String barcode = etBarcode.getText().toString().trim();
                saveEdit(api.updatePresentation(product.getId(), presentation.getId(),
                        new UpdateProductPresentationRequest(description, barcode,
                                barcode.isEmpty() && clearBarcodeRequested[0])), dialog);
            } else {
                saveEdit(api.updateProduct(product.getId(), new UpdateProductRequest(description)), dialog);
            }
        }));

        dialog.show();
    }

    /**
     * Boton de escaneo del dialogo: enciende la camara trasera en segundo plano
     * (sin vista previa ni cambio de pantalla) y escribe el primer codigo leido
     * en el campo. Si ya estaba escaneando, un segundo toque lo cancela.
     */
    private void toggleBarcodeScan(EditText target) {
        if (barcodeScan.isRunning()) {
            stopBarcodeScan();
            return;
        }
        pendingBarcodeTarget = target;
        barcodeScan.start();
    }

    private void onBarcodeScanned(String value) {
        EditText target = pendingBarcodeTarget;
        pendingBarcodeTarget = null;
        if (target == null) {
            return;
        }
        target.setText(value);
        target.setSelection(target.getText().length());
        target.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
    }

    private void stopBarcodeScan() {
        barcodeScan.stop();
        pendingBarcodeTarget = null;
    }

    /**
     * Envia el PUT del dialogo Editar producto (de presentacion o solo de
     * descripcion) y refleja la nueva descripcion en la lista al terminar.
     */
    private <T> void saveEdit(Call<T> call, AlertDialog dialog) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);

        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(@NonNull Call<T> call, @NonNull Response<T> response) {
                if (response.code() == 401) {
                    dialog.dismiss();
                    handleSessionExpired();
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    adapter.updateItem(toProduct(response.body()));
                    Toast.makeText(ProductsActivity.this, R.string.product_edit_success, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                } else {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                    Toast.makeText(ProductsActivity.this,
                            getString(R.string.product_edit_error, ApiErrorUtils.parseErrorMessage(response)),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<T> call, @NonNull Throwable t) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Toast.makeText(ProductsActivity.this,
                        getString(R.string.product_edit_error, reason), Toast.LENGTH_LONG).show();
            }
        });
    }

    private static ProductDTO toProduct(Object body) {
        if (body instanceof ProductPresentationDTO) {
            ProductPresentationDTO presentation = (ProductPresentationDTO) body;
            return new ProductDTO(presentation.getProductId(), presentation.getDescription());
        }
        return (ProductDTO) body;
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
