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
 *
 * Con decorFitsSystemWindows en false (lo que activa EdgeToEdge.enable), el
 * teclado tambien pasa a dibujarse por encima del contenido en vez de
 * encogerlo, asi que ademas de systemBars() se pide el inset de ime(): al
 * combinarlos, el padding inferior crece mientras el teclado esta visible y
 * los ScrollView/RecyclerView de cada pantalla tienen espacio real para
 * desplazarse por encima de el en lugar de quedar tapados.
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
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }
}
