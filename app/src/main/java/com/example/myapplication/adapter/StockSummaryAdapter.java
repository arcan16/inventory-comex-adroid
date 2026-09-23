package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.network.StockItemDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Existencias de un inventario (GET /stock/{idInventory}), filtrables por descripcion. */
public class StockSummaryAdapter extends RecyclerView.Adapter<StockSummaryAdapter.ViewHolder> {

    private final List<StockItemDTO> items = new ArrayList<>();

    public void setItems(List<StockItemDTO> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_stock_summary, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StockItemDTO item = items.get(position);
        holder.tvProductId.setText(item.getIdProduct());
        holder.tvProductDescription.setText(item.getDescription());
        holder.tvStock.setText(String.format(Locale.US, "%.3f", item.getStock()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvProductId;
        final TextView tvProductDescription;
        final TextView tvStock;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductId = itemView.findViewById(R.id.tvProductId);
            tvProductDescription = itemView.findViewById(R.id.tvProductDescription);
            tvStock = itemView.findViewById(R.id.tvStock);
        }
    }
}
