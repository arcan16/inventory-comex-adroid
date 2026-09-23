package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.myapplication.adapter.ProductAdapter;
import com.example.myapplication.data.ServerPreferences;
import com.example.myapplication.data.SessionPreferences;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.PageResponse;
import com.example.myapplication.network.ProductDTO;
import com.example.myapplication.network.ProductsApi;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.Collections;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProductsActivity extends BaseActivity {

    private static final int PAGE_SIZE = 20;
    /** Cuantos renglones antes del final se dispara la siguiente pagina. */
    private static final int LOAD_MORE_THRESHOLD = 4;
    private static final long SEARCH_DEBOUNCE_MS = 350;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Runnable searchRunnable = () -> fetchPage(0, false);

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

        adapter = new ProductAdapter();
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

    private void handleSessionExpired() {
        Toast.makeText(this, R.string.inventories_session_expired, Toast.LENGTH_LONG).show();
        sessionPreferences.clearSession();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
