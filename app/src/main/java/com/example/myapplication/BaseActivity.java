package com.example.myapplication;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Activity base para toda la app: en Android 15+ (targetSdk 35+) el sistema
 * fuerza el modo edge-to-edge sin importar lo que haga la app, por lo que la
 * barra de estado y la de navegacion pueden tapar el contenido si nadie
 * reserva ese espacio. Aqui se habilita edge-to-edge explicitamente en todas
 * las versiones (para tener un solo comportamiento consistente) y se aplica
 * el padding de los system bars al contenido de cada pantalla.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        applySystemBarsPadding();
    }

    private void applySystemBarsPadding() {
        View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}
