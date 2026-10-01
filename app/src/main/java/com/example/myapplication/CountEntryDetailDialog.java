package com.example.myapplication;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.myapplication.network.ProductCountEntryDTO;

import java.util.Locale;

/**
 * Detalle de solo lectura de un renglon de conteo (Id, nombre, cantidad y
 * ubicacion), que se abre al mantener presionado 1 s un renglon de la lista en
 * el conteo normal o el guiado. No entra al flujo de edicion. Todos los datos
 * vienen en el renglon, asi que no consulta al servidor.
 */
public final class CountEntryDetailDialog {

    /** Mismo orden que R.array.product_location_labels y el enum ProductLocation del backend. */
    private static final String[] PLACE_API_VALUES = {"SALES_AREA", "WAREHOUSE", "STORAGE_AREA", "NOTE"};

    private CountEntryDetailDialog() {
    }

    public static void show(Context context, ProductCountEntryDTO entry) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_count_entry_detail, null);
        String productId = entry.getIdProduct() != null ? entry.getIdProduct().getId() : "";
        String description = entry.getIdProduct() != null ? entry.getIdProduct().getDescription() : null;

        ((TextView) view.findViewById(R.id.tvDetailRecord))
                .setText(context.getString(R.string.count_detail_record, entry.getId()));
        ((TextView) view.findViewById(R.id.tvDetailName))
                .setText(description != null && !description.isEmpty() ? description : productId);
        ((TextView) view.findViewById(R.id.tvDetailId))
                .setText(context.getString(R.string.count_detail_id, productId));
        ((TextView) view.findViewById(R.id.tvDetailQuantity))
                .setText(String.format(Locale.US, "%.3f", entry.getQuantity()));
        ((TextView) view.findViewById(R.id.tvDetailPlace))
                .setText(placeLabel(context, entry.getPlace()));

        new AlertDialog.Builder(context)
                .setView(view)
                .setPositiveButton(R.string.count_detail_close, null)
                .show();
    }

    private static String placeLabel(Context context, String place) {
        String[] labels = context.getResources().getStringArray(R.array.product_location_labels);
        for (int i = 0; i < PLACE_API_VALUES.length && i < labels.length; i++) {
            if (PLACE_API_VALUES[i].equals(place)) {
                return labels[i];
            }
        }
        return place != null ? place : "";
    }
}
