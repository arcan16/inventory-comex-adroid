package com.example.myapplication;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.data.ServerPreferences;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.HealthApi;
import com.example.myapplication.network.HealthResponse;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ServerConfigActivity extends BaseActivity {

    private ServerPreferences serverPreferences;

    private EditText etHost;
    private EditText etPort;
    private TextView tvResolvedUrl;
    private MaterialButton btnTestConnection;
    private ProgressBar progressTest;
    private TextView tvConnectionStatus;
    private View recentNetworksSection;
    private LinearLayout recentNetworksContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_config);

        serverPreferences = new ServerPreferences(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        etHost = findViewById(R.id.etHost);
        etPort = findViewById(R.id.etPort);
        tvResolvedUrl = findViewById(R.id.tvResolvedUrl);
        btnTestConnection = findViewById(R.id.btnTestConnection);
        progressTest = findViewById(R.id.progressTest);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        recentNetworksSection = findViewById(R.id.recentNetworksSection);
        recentNetworksContainer = findViewById(R.id.recentNetworksContainer);
        MaterialButton btnSave = findViewById(R.id.btnSave);

        if (serverPreferences.hasServerConfigured()) {
            etHost.setText(serverPreferences.getHost());
            etPort.setText(serverPreferences.getPort());
        }

        TextWatcher urlUpdater = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateResolvedUrl();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        etHost.addTextChangedListener(urlUpdater);
        etPort.addTextChangedListener(urlUpdater);
        updateResolvedUrl();

        renderRecentServers();

        btnTestConnection.setOnClickListener(v -> testConnection());
        btnSave.setOnClickListener(v -> saveAndContinue());
    }

    private void updateResolvedUrl() {
        String host = etHost.getText().toString().trim();
        if (TextUtils.isEmpty(host)) {
            tvResolvedUrl.setText(R.string.server_config_url_empty);
        } else {
            tvResolvedUrl.setText(ServerPreferences.buildBaseUrl(host, etPort.getText().toString()) + "actuator/health");
        }
    }

    private void renderRecentServers() {
        recentNetworksContainer.removeAllViews();
        List<String> recent = serverPreferences.getRecentServers();
        recentNetworksSection.setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (String entry : recent) {
            View row = inflater.inflate(R.layout.item_recent_server, recentNetworksContainer, false);
            TextView label = row.findViewById(R.id.tvRecentServer);
            label.setText(entry);
            row.setOnClickListener(v -> applyRecentServer(entry));
            recentNetworksContainer.addView(row);
        }
    }

    private void applyRecentServer(String hostPort) {
        int separatorIndex = hostPort.lastIndexOf(':');
        if (separatorIndex <= 0) {
            return;
        }
        etHost.setText(hostPort.substring(0, separatorIndex));
        etPort.setText(hostPort.substring(separatorIndex + 1));
        etHost.setSelection(etHost.getText().length());
        tvConnectionStatus.setVisibility(View.GONE);
    }

    private void testConnection() {
        String host = etHost.getText().toString().trim();
        if (TextUtils.isEmpty(host)) {
            etHost.setError(getString(R.string.server_config_error_ip_required));
            etHost.requestFocus();
            return;
        }

        String baseUrl = ServerPreferences.buildBaseUrl(host, etPort.getText().toString());
        HealthApi healthApi = ApiClient.createHealthApi(baseUrl);

        setTestingState(true);
        long startedAt = System.currentTimeMillis();

        healthApi.checkHealth().enqueue(new Callback<HealthResponse>() {
            @Override
            public void onResponse(@NonNull Call<HealthResponse> call, @NonNull Response<HealthResponse> response) {
                long elapsedMs = System.currentTimeMillis() - startedAt;
                setTestingState(false);

                boolean isUp = response.isSuccessful()
                        && response.body() != null
                        && response.body().isUp();

                if (isUp) {
                    showConnectionStatus(true, getString(R.string.server_config_status_ok, elapsedMs));
                } else {
                    showConnectionStatus(false, getString(R.string.server_config_status_bad_response, response.code()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<HealthResponse> call, @NonNull Throwable t) {
                setTestingState(false);
                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                showConnectionStatus(false, getString(R.string.server_config_status_unreachable, reason));
            }
        });
    }

    private void setTestingState(boolean testing) {
        btnTestConnection.setEnabled(!testing);
        btnTestConnection.setText(testing ? R.string.server_config_testing : R.string.server_config_test);
        progressTest.setVisibility(testing ? View.VISIBLE : View.GONE);
        if (testing) {
            tvConnectionStatus.setVisibility(View.GONE);
        }
    }

    private void showConnectionStatus(boolean success, String message) {
        tvConnectionStatus.setText(message);
        tvConnectionStatus.setBackgroundResource(success ? R.drawable.bg_chip_good : R.drawable.bg_chip_warn);
        int textColor = success
                ? getColor(R.color.app_onSuccessContainer)
                : fetchThemeColor(com.google.android.material.R.attr.colorOnErrorContainer);
        tvConnectionStatus.setTextColor(textColor);
        tvConnectionStatus.setVisibility(View.VISIBLE);
    }

    private int fetchThemeColor(int attr) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    private void saveAndContinue() {
        String host = etHost.getText().toString().trim();
        if (TextUtils.isEmpty(host)) {
            etHost.setError(getString(R.string.server_config_error_ip_required));
            etHost.requestFocus();
            return;
        }

        serverPreferences.saveServer(host, etPort.getText().toString());
        Toast.makeText(this, R.string.server_config_saved_toast, Toast.LENGTH_SHORT).show();
        finish();
    }
}
