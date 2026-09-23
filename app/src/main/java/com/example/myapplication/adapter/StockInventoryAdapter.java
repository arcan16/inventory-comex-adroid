package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.network.InventoryDTO;
import com.example.myapplication.util.DateFormatUtils;

import java.util.ArrayList;
import java.util.List;

/** Lista de inventarios que comparten una presentacion (GET /inventories/allByType/{type}). */
public class StockInventoryAdapter extends RecyclerView.Adapter<StockInventoryAdapter.ViewHolder> {

    public interface OnInventorySelectedListener {
        void onInventorySelected(InventoryDTO inventory);
    }

    private final List<InventoryDTO> items = new ArrayList<>();
    private final OnInventorySelectedListener listener;

    public StockInventoryAdapter(OnInventorySelectedListener listener) {
        this.listener = listener;
    }

    public void setItems(List<InventoryDTO> newItems) {
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
                .inflate(R.layout.item_stock_inventory, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InventoryDTO inventory = items.get(position);
        holder.tvInventoryId.setText("#" + inventory.getId());
        holder.tvDate.setText(DateFormatUtils.toShortSpanishDate(inventory.getDate()));
        holder.tvPresentation.setText(inventory.getPresentation());
        holder.itemView.setOnClickListener(v -> listener.onInventorySelected(inventory));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvInventoryId;
        final TextView tvDate;
        final TextView tvPresentation;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvInventoryId = itemView.findViewById(R.id.tvInventoryId);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvPresentation = itemView.findViewById(R.id.tvPresentation);
        }
    }
}
