package com.example.myapplication.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.network.InventoryDTO;
import com.example.myapplication.util.DateFormatUtils;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

public class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.ViewHolder> {

    public interface OnInventoryActionListener {
        void onOpenNormalCount(InventoryDTO inventory);

        void onOpenGuidedCount(InventoryDTO inventory);

        void onClose(InventoryDTO inventory);

        void onDelete(InventoryDTO inventory);
    }

    private final List<InventoryDTO> items = new ArrayList<>();
    private final OnInventoryActionListener listener;

    public InventoryAdapter(OnInventoryActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<InventoryDTO> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void removeItem(InventoryDTO inventory) {
        int index = items.indexOf(inventory);
        if (index >= 0) {
            items.remove(index);
            notifyItemRemoved(index);
        }
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_inventory, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InventoryDTO inventory = items.get(position);
        holder.tvPresentation.setText(inventory.getPresentation());
        holder.tvInventoryId.setText("#" + inventory.getId());
        holder.tvDate.setText(DateFormatUtils.toShortSpanishDate(inventory.getDate()));
        bindStatus(holder.tvStatus, inventory.getStatus());

        holder.itemView.setOnClickListener(v -> listener.onOpenNormalCount(inventory));
        holder.btnMore.setOnClickListener(v -> showItemMenu(v, inventory));
    }

    /** Pinta el chip de estado segun el enum InventoryStatus del backend (OPENED, LOCKED, PENDING, CLOSED). */
    private void bindStatus(TextView tvStatus, String status) {
        Context context = tvStatus.getContext();
        if ("CLOSED".equals(status)) {
            tvStatus.setText(R.string.inventories_status_closed);
            tvStatus.setBackgroundResource(R.drawable.bg_chip_neutral);
            tvStatus.setTextColor(MaterialColors.getColor(tvStatus, com.google.android.material.R.attr.colorOnSurfaceVariant));
        } else if ("LOCKED".equals(status)) {
            tvStatus.setText(R.string.inventories_status_locked);
            tvStatus.setBackgroundResource(R.drawable.bg_chip_accent);
            tvStatus.setTextColor(MaterialColors.getColor(tvStatus, com.google.android.material.R.attr.colorOnTertiaryContainer));
        } else if ("PENDING".equals(status)) {
            tvStatus.setText(R.string.inventories_status_pending);
            tvStatus.setBackgroundResource(R.drawable.bg_chip_warn);
            tvStatus.setTextColor(MaterialColors.getColor(tvStatus, com.google.android.material.R.attr.colorOnErrorContainer));
        } else {
            tvStatus.setText(R.string.inventories_status_opened);
            tvStatus.setBackgroundResource(R.drawable.bg_chip_good);
            tvStatus.setTextColor(ContextCompat.getColor(context, R.color.app_onSuccessContainer));
        }
    }

    private void showItemMenu(View anchor, InventoryDTO inventory) {
        PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
        popup.inflate(R.menu.inventory_item_menu);
        popup.getMenu().findItem(R.id.action_close).setVisible(!"CLOSED".equals(inventory.getStatus()));
        popup.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.action_guided_count) {
                listener.onOpenGuidedCount(inventory);
                return true;
            } else if (id == R.id.action_close) {
                listener.onClose(inventory);
                return true;
            } else if (id == R.id.action_delete) {
                listener.onDelete(inventory);
                return true;
            }
            return false;
        });
        popup.show();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvPresentation;
        final TextView tvInventoryId;
        final TextView tvStatus;
        final TextView tvDate;
        final ImageView btnMore;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPresentation = itemView.findViewById(R.id.tvPresentation);
            tvInventoryId = itemView.findViewById(R.id.tvInventoryId);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvDate = itemView.findViewById(R.id.tvDate);
            btnMore = itemView.findViewById(R.id.btnMore);
        }
    }
}
