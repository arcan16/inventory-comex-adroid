package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.myapplication.data.ServerPreferences;
import com.example.myapplication.data.SessionPreferences;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.ApiErrorUtils;
import com.example.myapplication.network.UpdateUserRequest;
import com.example.myapplication.network.UserDTO;
import com.example.myapplication.network.UsersApi;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Pantalla de "Mi cuenta": muestra los datos del usuario autenticado
 * (GET /users/me) y permite modificar usuario, correo y/o contraseña
 * (PUT /users). Distinta de UsersActivity (pendiente, pantalla admin para
 * administrar OTROS usuarios).
 */
public class AccountActivity extends BaseActivity {

    private ServerPreferences serverPreferences;
    private SessionPreferences sessionPreferences;

    private long userId;
    private String originalUsername;

    private ScrollView contentScroll;
    private View progressLoad;
    private View errorState;
    private TextView tvErrorMessage;

    private EditText etUsername;
    private EditText etEmail;
    private EditText etNewPassword;
    private EditText etConfirmPassword;
    private TextView tvAccountError;
    private MaterialButton btnSave;
    private View progressSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        serverPreferences = new ServerPreferences(this);
        sessionPreferences = new SessionPreferences(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        contentScroll = findViewById(R.id.contentScroll);
        progressLoad = findViewById(R.id.progressLoad);
        errorState = findViewById(R.id.errorState);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);

        etUsername = findViewById(R.id.etUsername);
        etEmail = findViewById(R.id.etEmail);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        tvAccountError = findViewById(R.id.tvAccountError);
        btnSave = findViewById(R.id.btnSave);
        progressSave = findViewById(R.id.progressSave);

        findViewById(R.id.btnRetryLoad).setOnClickListener(v -> loadAccount());
        btnSave.setOnClickListener(v -> saveChanges());

        loadAccount();
    }

    private void loadAccount() {
        if (!serverPreferences.hasServerConfigured() || !sessionPreferences.isLoggedIn()) {
            handleSessionExpired();
            return;
        }

        progressLoad.setVisibility(View.VISIBLE);
        errorState.setVisibility(View.GONE);
        contentScroll.setVisibility(View.GONE);

        UsersApi api = ApiClient.createUsersApi(serverPreferences.getBaseUrl(), sessionPreferences.getToken());

        api.getCurrentUser().enqueue(new Callback<UserDTO>() {
            @Override
            public void onResponse(@NonNull Call<UserDTO> call, @NonNull Response<UserDTO> response) {
                progressLoad.setVisibility(View.GONE);

                if (response.code() == 401) {
                    handleSessionExpired();
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    userId = response.body().getId();
                    originalUsername = response.body().getUsuario();
                    etUsername.setText(originalUsername);
                    etEmail.setText(response.body().getEmail());
                    contentScroll.setVisibility(View.VISIBLE);
                } else {
                    showLoadError();
                }
            }

            @Override
            public void onFailure(@NonNull Call<UserDTO> call, @NonNull Throwable t) {
                progressLoad.setVisibility(View.GONE);
                showLoadError();
            }
        });
    }

    private void showLoadError() {
        tvErrorMessage.setText(R.string.account_load_error);
        errorState.setVisibility(View.VISIBLE);
        contentScroll.setVisibility(View.GONE);
    }

    private void saveChanges() {
        hideAccountError();

        String usuario = etUsername.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String newPassword = etNewPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        if (TextUtils.isEmpty(usuario)) {
            etUsername.setError(getString(R.string.account_error_username_required));
            etUsername.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(email)) {
            etEmail.setError(getString(R.string.account_error_email_required));
            etEmail.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError(getString(R.string.account_error_email_invalid));
            etEmail.requestFocus();
            return;
        }

        boolean changingPassword = !TextUtils.isEmpty(newPassword) || !TextUtils.isEmpty(confirmPassword);
        if (changingPassword) {
            if (newPassword.length() < 6) {
                etNewPassword.setError(getString(R.string.account_error_password_short));
                etNewPassword.requestFocus();
                return;
            }
            if (!newPassword.equals(confirmPassword)) {
                etConfirmPassword.setError(getString(R.string.account_error_password_mismatch));
                etConfirmPassword.requestFocus();
                return;
            }
        }

        setSaveLoadingState(true);

        UsersApi api = ApiClient.createUsersApi(serverPreferences.getBaseUrl(), sessionPreferences.getToken());
        UpdateUserRequest request = new UpdateUserRequest(
                userId, usuario, changingPassword ? newPassword : null, email);

        api.updateUser(request).enqueue(new Callback<UserDTO>() {
            @Override
            public void onResponse(@NonNull Call<UserDTO> call, @NonNull Response<UserDTO> response) {
                setSaveLoadingState(false);

                if (response.code() == 401) {
                    handleSessionExpired();
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    onSaveSuccess(response.body());
                } else {
                    showAccountError(ApiErrorUtils.parseErrorMessage(response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<UserDTO> call, @NonNull Throwable t) {
                setSaveLoadingState(false);
                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                showAccountError(reason);
            }
        });
    }

    /**
     * El JWT actual quedo firmado con el username viejo (JwtUtils.generateToken
     * lo usa como "sub"); si el usuario cambio, el backend ya no puede resolver
     * ese token en las siguientes peticiones (JwtAuthorizationFilter busca por
     * username), asi que hay que forzar un login nuevo en vez de seguir con la
     * sesion actual.
     */
    private void onSaveSuccess(UserDTO updated) {
        etNewPassword.setText("");
        etConfirmPassword.setText("");

        boolean usernameChanged = !updated.getUsuario().equals(originalUsername);
        if (usernameChanged) {
            Toast.makeText(this, getString(R.string.account_username_changed_message, updated.getUsuario()),
                    Toast.LENGTH_LONG).show();
            sessionPreferences.clearSession();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        originalUsername = updated.getUsuario();
        Toast.makeText(this, R.string.account_save_success, Toast.LENGTH_SHORT).show();
    }

    private void setSaveLoadingState(boolean loading) {
        btnSave.setEnabled(!loading);
        btnSave.setText(loading ? R.string.account_save_loading : R.string.account_save);
        progressSave.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showAccountError(String message) {
        tvAccountError.setText(getString(R.string.account_save_error, message));
        tvAccountError.setVisibility(View.VISIBLE);
    }

    private void hideAccountError() {
        tvAccountError.setVisibility(View.GONE);
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
