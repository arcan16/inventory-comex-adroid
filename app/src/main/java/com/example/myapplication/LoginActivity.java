package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.myapplication.data.ServerPreferences;
import com.example.myapplication.data.SessionPreferences;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.LoginApi;
import com.example.myapplication.network.LoginRequest;
import com.example.myapplication.network.LoginResponse;
import com.google.android.material.button.MaterialButton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends BaseActivity {

    private ServerPreferences serverPreferences;
    private SessionPreferences sessionPreferences;

    private TextView tvServerFooter;
    private EditText etUsername;
    private EditText etPassword;
    private TextView tvLoginError;
    private MaterialButton btnLogin;
    private ProgressBar progressLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        serverPreferences = new ServerPreferences(this);
        sessionPreferences = new SessionPreferences(this);

        if (sessionPreferences.isLoggedIn()) {
            // Ya hay una sesion activa, no tiene caso volver a mostrar el login.
            goToHome();
            return;
        }

        setContentView(R.layout.activity_login);

        tvServerFooter = findViewById(R.id.tvServerFooter);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        tvLoginError = findViewById(R.id.tvLoginError);
        btnLogin = findViewById(R.id.btnLogin);
        progressLogin = findViewById(R.id.progressLogin);

        findViewById(R.id.serverFooter).setOnClickListener(v -> openServerConfig());
        btnLogin.setOnClickListener(v -> attemptLogin());

        if (!serverPreferences.hasServerConfigured()) {
            // Primer uso: sin servidor configurado no hay a donde mandar el login,
            // asi que se pide configurarlo antes de mostrar el formulario.
            openServerConfig();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tvServerFooter != null) {
            updateServerFooter();
        }
    }

    private void openServerConfig() {
        startActivity(new Intent(this, ServerConfigActivity.class));
    }

    private void goToHome() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    private void updateServerFooter() {
        if (serverPreferences.hasServerConfigured()) {
            String hostPort = serverPreferences.getHost() + ":" + serverPreferences.getPort();
            tvServerFooter.setText(getString(R.string.login_server_footer_configured, hostPort));
        } else {
            tvServerFooter.setText(R.string.login_server_footer_unconfigured);
        }
    }

    private void attemptLogin() {
        hideLoginError();

        String usuario = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (TextUtils.isEmpty(usuario)) {
            etUsername.setError(getString(R.string.login_error_username_required));
            etUsername.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError(getString(R.string.login_error_password_required));
            etPassword.requestFocus();
            return;
        }
        if (!serverPreferences.hasServerConfigured()) {
            Toast.makeText(this, R.string.login_error_server_required, Toast.LENGTH_SHORT).show();
            openServerConfig();
            return;
        }

        setLoadingState(true);

        LoginApi loginApi = ApiClient.createLoginApi(serverPreferences.getBaseUrl());
        loginApi.login(new LoginRequest(usuario, password)).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(@NonNull Call<LoginResponse> call, @NonNull Response<LoginResponse> response) {
                setLoadingState(false);

                if (response.isSuccessful() && response.body() != null && response.body().getToken() != null) {
                    sessionPreferences.saveSession(response.body().getToken(), response.body().getUsername());
                    goToHome();
                } else {
                    showLoginError(getString(R.string.login_error_invalid_credentials));
                }
            }

            @Override
            public void onFailure(@NonNull Call<LoginResponse> call, @NonNull Throwable t) {
                setLoadingState(false);
                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                showLoginError(getString(R.string.login_error_network, reason));
            }
        });
    }

    private void setLoadingState(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? R.string.login_loading : R.string.login_submit);
        progressLogin.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showLoginError(String message) {
        tvLoginError.setText(message);
        tvLoginError.setVisibility(View.VISIBLE);
    }

    private void hideLoginError() {
        tvLoginError.setVisibility(View.GONE);
    }
}
