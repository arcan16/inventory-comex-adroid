package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.network.ProductCountEntryDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lista de los conteos ya registrados en el backend para este inventario.
 * Tocar un renglon carga su informacion en el formulario para editarla; el
 * icono de bote de basura elimina el renglon (DELETE /productCounts/{id}).
 */
public class ProductCountAdapter extends RecyclerView.Adapter<ProductCountAdapter.ViewHolder> {

    public interface OnProductCountActionListener {
        void onSelect(ProductCountEntryDTO entry);

        void onDelete(ProductCountEntryDTO entry);
    }

    private final List<ProductCountEntryDTO> items = new ArrayList<>();
    private final OnProductCountActionListener listener;

    public ProductCountAdapter(OnProductCountActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ProductCountEntryDTO> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product_count, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProductCountEntryDTO entry = items.get(position);
        String productId = entry.getIdProduct() != null ? entry.getIdProduct().getId() : "";
        holder.tvProductId.setText(productId);
        holder.tvQuantity.setText(String.format(Locale.US, "%.3f", entry.getQuantity()));
        holder.itemView.setOnClickListener(v -> listener.onSelect(entry));
        holder.ivDelete.setOnClickListener(v -> listener.onDelete(entry));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvProductId;
        final TextView tvQuantity;
        final ImageView ivDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductId = itemView.findViewById(R.id.tvCountProductId);
            tvQuantity = itemView.findViewById(R.id.tvCountQuantity);
            ivDelete = itemView.findViewById(R.id.ivDeleteCount);
        }
    }
}
