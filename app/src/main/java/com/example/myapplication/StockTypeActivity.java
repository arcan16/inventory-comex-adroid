package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.myapplication.adapter.StockInventoryAdapter;
import com.example.myapplication.data.ServerPreferences;
import com.example.myapplication.data.SessionPreferences;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.InventoriesApi;
import com.example.myapplication.network.InventoryDTO;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StockTypeActivity extends BaseActivity implements StockInventoryAdapter.OnInventorySelectedListener {

    public static final String EXTRA_TYPE = "extra_type";
    public static final String EXTRA_TYPE_LABEL = "extra_type_label";

    private ServerPreferences serverPreferences;
    private SessionPreferences sessionPreferences;
    private StockInventoryAdapter adapter;

    private String type;
    private String typeLabel;

    private SwipeRefreshLayout swipeRefresh;
    private View progressLoad;
    private View emptyStateContainer;
    private TextView tvEmptyMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_type);

        serverPreferences = new ServerPreferences(this);
        sessionPreferences = new SessionPreferences(this);

        type = getIntent().getStringExtra(EXTRA_TYPE);
        typeLabel = getIntent().getStringExtra(EXTRA_TYPE_LABEL);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setSubtitle(typeLabel);

        swipeRefresh = findViewById(R.id.swipeRefresh);
        progressLoad = findViewById(R.id.progressLoad);
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        RecyclerView recyclerInventories = findViewById(R.id.recyclerInventories);

        adapter = new StockInventoryAdapter(this);
        recyclerInventories.setLayoutManager(new LinearLayoutManager(this));
        recyclerInventories.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(() -> loadInventories(false));
        findViewById(R.id.btnRetry).setOnClickListener(v -> loadInventories(true));

        loadInventories(true);
    }

    private void loadInventories(boolean showFullProgress) {
        if (!serverPreferences.hasServerConfigured() || !sessionPreferences.isLoggedIn()) {
            handleSessionExpired();
            return;
        }

        emptyStateContainer.setVisibility(View.GONE);
        if (showFullProgress) {
            progressLoad.setVisibility(View.VISIBLE);
        }

        InventoriesApi api = ApiClient.createInventoriesApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());

        api.getInventoriesByType(type).enqueue(new Callback<List<InventoryDTO>>() {
            @Override
            public void onResponse(@NonNull Call<List<InventoryDTO>> call, @NonNull Response<List<InventoryDTO>> response) {
                progressLoad.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);

                if (response.code() == 401) {
                    handleSessionExpired();
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    adapter.setItems(response.body());
                    tvEmptyMessage.setText(R.string.stock_type_empty);
                    emptyStateContainer.setVisibility(adapter.isEmpty() ? View.VISIBLE : View.GONE);
                } else if (response.code() == 404) {
                    // El backend responde 404 cuando no hay inventarios con esta presentacion.
                    adapter.setItems(Collections.emptyList());
                    tvEmptyMessage.setText(R.string.stock_type_empty);
                    emptyStateContainer.setVisibility(View.VISIBLE);
                } else {
                    showLoadError();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<InventoryDTO>> call, @NonNull Throwable t) {
                progressLoad.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                showLoadError();
            }
        });
    }

    private void showLoadError() {
        adapter.setItems(Collections.emptyList());
        tvEmptyMessage.setText(R.string.stock_type_load_error);
        emptyStateContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public void onInventorySelected(InventoryDTO inventory) {
        Intent intent = new Intent(this, StockSummaryActivity.class);
        intent.putExtra(StockSummaryActivity.EXTRA_INVENTORY_ID, inventory.getId());
        intent.putExtra(StockSummaryActivity.EXTRA_TYPE_LABEL, typeLabel);
        startActivity(intent);
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
