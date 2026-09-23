package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myapplication.data.SessionPreferences;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class HomeActivity extends BaseActivity {

    private SessionPreferences sessionPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        sessionPreferences = new SessionPreferences(this);

        findViewById(R.id.tileInventories).setOnClickListener(v ->
                startActivity(new Intent(this, InventoriesActivity.class)));
        findViewById(R.id.tileReports).setOnClickListener(v ->
                startActivity(new Intent(this, ReportsActivity.class)));
        findViewById(R.id.tileProducts).setOnClickListener(v ->
                startActivity(new Intent(this, ProductsActivity.class)));
        findViewById(R.id.tileStock).setOnClickListener(v ->
                startActivity(new Intent(this, StockActivity.class)));

        findViewById(R.id.btnLogout).setOnClickListener(v -> confirmLogout());

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        moveBottomInsetToNavBar(bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_reports) {
                startActivity(new Intent(this, ReportsActivity.class));
                return true;
            } else if (id == R.id.nav_products) {
                startActivity(new Intent(this, ProductsActivity.class));
                return true;
            } else if (id == R.id.nav_stock) {
                startActivity(new Intent(this, StockActivity.class));
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Home no hace ninguna llamada de red por si sola, asi que es la unica
        // pantalla que no dispara el chequeo de sesion "de rebote" al fallar
        // un request con 401. Se valida aqui explicitamente, tanto al entrar
        // como al volver del segundo plano.
        if (!sessionPreferences.isLoggedIn()) {
            handleSessionExpired();
        }
    }

    private void handleSessionExpired() {
        Toast.makeText(this, R.string.inventories_session_expired, Toast.LENGTH_LONG).show();
        sessionPreferences.clearSession();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * BaseActivity le pone el padding inferior del gesture bar al contenedor
     * raiz completo, lo que empuja el BottomNavigationView hacia arriba y deja
     * un hueco del color de fondo debajo de el. Aqui se mueve ese padding del
     * contenedor raiz hacia el propio BottomNavigationView para que quede
     * pegado al borde inferior real de la pantalla.
     */
    private void moveBottomInsetToNavBar(View bottomNav) {
        View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            bottomNav.setPadding(bottomNav.getPaddingLeft(), bottomNav.getPaddingTop(),
                    bottomNav.getPaddingRight(), systemBars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(content);
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.home_logout_dialog_title)
                .setMessage(R.string.home_logout_dialog_message)
                .setPositiveButton(R.string.home_logout_dialog_confirm, (dialog, which) -> logout())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void logout() {
        sessionPreferences.clearSession();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
