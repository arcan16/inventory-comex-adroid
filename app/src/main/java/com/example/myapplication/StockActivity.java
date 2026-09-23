package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;

import com.google.android.material.appbar.MaterialToolbar;

/**
 * Menu de acceso al stock por presentacion (equivalente a TypeStock/SummaryStock
 * del frontend web). Los 4 tipos son valores fijos de "presentacion" usados por
 * el negocio (ver la columna UNIDAD de los CSV de carga, p.ej. GALONES.csv trae
 * "4 LTS"), no un catalogo dinamico.
 */
public class StockActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.tileBuckets).setOnClickListener(v -> openType("19 LTS", getString(R.string.stock_tile_buckets)));
        findViewById(R.id.tileGallons).setOnClickListener(v -> openType("4 LTS", getString(R.string.stock_tile_gallons)));
        findViewById(R.id.tileLiters).setOnClickListener(v -> openType("1 LT", getString(R.string.stock_tile_liters)));
        findViewById(R.id.tilePieces).setOnClickListener(v -> openType("PIEZA", getString(R.string.stock_tile_pieces)));
    }

    private void openType(String type, String typeLabel) {
        Intent intent = new Intent(this, StockTypeActivity.class);
        intent.putExtra(StockTypeActivity.EXTRA_TYPE, type);
        intent.putExtra(StockTypeActivity.EXTRA_TYPE_LABEL, typeLabel);
        startActivity(intent);
    }
}
