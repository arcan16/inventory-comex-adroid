package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.network.ProductDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * Lista del catalogo de productos (GET /products). Es de solo lectura cuando
 * se construye sin listener (pantalla de catalogo); con un listener, cada
 * renglon se vuelve seleccionable (usado por el buscador de productos del
 * conteo normal).
 */
public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {

    public interface OnProductClickListener {
        void onProductClick(ProductDTO product);
    }

    private final List<ProductDTO> items = new ArrayList<>();
    private final OnProductClickListener listener;

    public ProductAdapter() {
        this(null);
    }

    public ProductAdapter(OnProductClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ProductDTO> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void addItems(List<ProductDTO> newItems) {
        int startPosition = items.size();
        items.addAll(newItems);
        notifyItemRangeInserted(startPosition, newItems.size());
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** Reemplaza en su lugar el producto con el mismo id (tras editarlo con PUT /products/{id}). */
    public void updateItem(ProductDTO updated) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId().equals(updated.getId())) {
                items.set(i, updated);
                notifyItemChanged(i);
                return;
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProductDTO product = items.get(position);
        holder.tvProductId.setText(product.getId());
        holder.tvProductDescription.setText(product.getDescription());
        holder.itemView.setOnClickListener(listener != null ? v -> listener.onProductClick(product) : null);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvProductId;
        final TextView tvProductDescription;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductId = itemView.findViewById(R.id.tvProductId);
            tvProductDescription = itemView.findViewById(R.id.tvProductDescription);
        }
    }
}
