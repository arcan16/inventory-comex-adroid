package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.myapplication.adapter.InventoryAdapter;
import com.example.myapplication.data.ServerPreferences;
import com.example.myapplication.data.SessionPreferences;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.InventoriesApi;
import com.example.myapplication.network.InventoryDTO;
import com.example.myapplication.network.PageResponse;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InventoriesActivity extends BaseActivity implements InventoryAdapter.OnInventoryActionListener {

    private static final int PAGE_SIZE = 50;

    private ServerPreferences serverPreferences;
    private SessionPreferences sessionPreferences;
    private InventoryAdapter adapter;

    private SwipeRefreshLayout swipeRefresh;
    private View progressInventories;
    private View emptyStateContainer;
    private TextView tvEmptyMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventories);

        serverPreferences = new ServerPreferences(this);
        sessionPreferences = new SessionPreferences(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        swipeRefresh = findViewById(R.id.swipeRefresh);
        progressInventories = findViewById(R.id.progressInventories);
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        RecyclerView recyclerView = findViewById(R.id.recyclerInventories);
        FloatingActionButton fab = findViewById(R.id.fabAddInventory);

        adapter = new InventoryAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(() -> loadInventories(false));
        findViewById(R.id.btnRetry).setOnClickListener(v -> loadInventories(true));
        fab.setOnClickListener(v -> startActivity(new Intent(this, InventoryUploadActivity.class)));

        loadInventories(true);
    }

    private void loadInventories(boolean showFullProgress) {
        if (!serverPreferences.hasServerConfigured() || !sessionPreferences.isLoggedIn()) {
            redirectToLogin();
            return;
        }

        hideEmptyState();
        if (showFullProgress) {
            progressInventories.setVisibility(View.VISIBLE);
        }

        InventoriesApi api = ApiClient.createInventoriesApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());

        api.getInventories(0, PAGE_SIZE).enqueue(new Callback<PageResponse<InventoryDTO>>() {
            @Override
            public void onResponse(@NonNull Call<PageResponse<InventoryDTO>> call,
                                    @NonNull Response<PageResponse<InventoryDTO>> response) {
                progressInventories.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);

                if (response.code() == 401) {
                    handleSessionExpired();
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    adapter.setItems(response.body().getContent());
                    if (adapter.isEmpty()) {
                        showEmptyState(getString(R.string.inventories_empty));
                    }
                } else {
                    showEmptyState(getString(R.string.inventories_load_error));
                }
            }

            @Override
            public void onFailure(@NonNull Call<PageResponse<InventoryDTO>> call, @NonNull Throwable t) {
                progressInventories.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                showEmptyState(getString(R.string.inventories_load_error));
                Toast.makeText(InventoriesActivity.this,
                        t.getMessage() != null ? t.getMessage() : getString(R.string.inventories_load_error),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEmptyState(String message) {
        tvEmptyMessage.setText(message);
        emptyStateContainer.setVisibility(View.VISIBLE);
    }

    private void hideEmptyState() {
        emptyStateContainer.setVisibility(View.GONE);
    }

    private void handleSessionExpired() {
        Toast.makeText(this, R.string.inventories_session_expired, Toast.LENGTH_LONG).show();
        sessionPreferences.clearSession();
        redirectToLogin();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onOpenNormalCount(InventoryDTO inventory) {
        if ("LOCKED".equals(inventory.getStatus())) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.inventories_locked_title)
                    .setMessage(getString(R.string.inventories_locked_message, inventory.getPresentation()))
                    .setPositiveButton(R.string.action_accept, null)
                    .show();
            return;
        }

        if ("CLOSED".equals(inventory.getStatus())) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.inventories_closed_open_title)
                    .setMessage(getString(R.string.inventories_closed_open_message, inventory.getPresentation()))
                    .setPositiveButton(R.string.inventories_reopen_confirm, (dialog, which) -> reopenInventory(inventory))
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
            return;
        }

        openNormalCount(inventory);
    }

    private void openNormalCount(InventoryDTO inventory) {
        Intent intent = new Intent(this, CountNormalActivity.class);
        intent.putExtra(CountNormalActivity.EXTRA_INVENTORY_ID, inventory.getId());
        intent.putExtra(CountNormalActivity.EXTRA_PRESENTATION, inventory.getPresentation());
        intent.putExtra(CountNormalActivity.EXTRA_DATE, inventory.getDate());
        startActivity(intent);
    }

    private void reopenInventory(InventoryDTO inventory) {
        InventoriesApi api = ApiClient.createInventoriesApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());

        api.reopenInventory(inventory.getId()).enqueue(new Callback<InventoryDTO>() {
            @Override
            public void onResponse(@NonNull Call<InventoryDTO> call, @NonNull Response<InventoryDTO> response) {
                if (response.code() == 401) {
                    handleSessionExpired();
                    return;
                }
                if (response.isSuccessful()) {
                    Toast.makeText(InventoriesActivity.this, R.string.inventories_reopened_toast, Toast.LENGTH_SHORT).show();
                    loadInventories(false);
                    openNormalCount(inventory);
                } else {
                    Toast.makeText(InventoriesActivity.this,
                            getString(R.string.inventories_reopen_error, "HTTP " + response.code()),
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<InventoryDTO> call, @NonNull Throwable t) {
                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Toast.makeText(InventoriesActivity.this,
                        getString(R.string.inventories_reopen_error, reason),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onOpenGuidedCount(InventoryDTO inventory) {
        Intent intent = new Intent(this, CountGuidedActivity.class);
        intent.putExtra(CountGuidedActivity.EXTRA_INVENTORY_ID, inventory.getId());
        intent.putExtra(CountGuidedActivity.EXTRA_PRESENTATION, inventory.getPresentation());
        intent.putExtra(CountGuidedActivity.EXTRA_DATE, inventory.getDate());
        startActivity(intent);
    }

    @Override
    public void onClose(InventoryDTO inventory) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.inventories_close_confirm_title)
                .setMessage(getString(R.string.inventories_close_confirm_message, inventory.getPresentation()))
                .setPositiveButton(R.string.inventories_menu_close, (dialog, which) -> closeInventory(inventory))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void closeInventory(InventoryDTO inventory) {
        InventoriesApi api = ApiClient.createInventoriesApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());

        api.closeInventory(inventory.getId()).enqueue(new Callback<InventoryDTO>() {
            @Override
            public void onResponse(@NonNull Call<InventoryDTO> call, @NonNull Response<InventoryDTO> response) {
                if (response.code() == 401) {
                    handleSessionExpired();
                    return;
                }
                if (response.isSuccessful()) {
                    Toast.makeText(InventoriesActivity.this, R.string.inventories_closed_toast, Toast.LENGTH_SHORT).show();
                    loadInventories(false);
                } else {
                    Toast.makeText(InventoriesActivity.this,
                            getString(R.string.inventories_close_error, "HTTP " + response.code()),
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<InventoryDTO> call, @NonNull Throwable t) {
                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Toast.makeText(InventoriesActivity.this,
                        getString(R.string.inventories_close_error, reason),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDelete(InventoryDTO inventory) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.inventories_delete_confirm_title)
                .setMessage(getString(R.string.inventories_delete_confirm_message, inventory.getPresentation()))
                .setPositiveButton(R.string.inventories_menu_delete, (dialog, which) -> deleteInventory(inventory))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void deleteInventory(InventoryDTO inventory) {
        InventoriesApi api = ApiClient.createInventoriesApi(
                serverPreferences.getBaseUrl(), sessionPreferences.getToken());

        api.deleteInventory(inventory.getId()).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.code() == 401) {
                    handleSessionExpired();
                    return;
                }
                if (response.isSuccessful()) {
                    adapter.removeItem(inventory);
                    Toast.makeText(InventoriesActivity.this, R.string.inventories_deleted_toast, Toast.LENGTH_SHORT).show();
                    if (adapter.isEmpty()) {
                        showEmptyState(getString(R.string.inventories_empty));
                    }
                } else {
                    Toast.makeText(InventoriesActivity.this,
                            getString(R.string.inventories_delete_error, "HTTP " + response.code()),
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Toast.makeText(InventoriesActivity.this,
                        getString(R.string.inventories_delete_error, reason),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}
