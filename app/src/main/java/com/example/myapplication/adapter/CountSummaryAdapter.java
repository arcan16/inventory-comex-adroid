package com.example.myapplication.adapter;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.network.CountsDifferenceDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Resumen del conteo fisico: stock esperado, total contado y diferencia por producto. */
public class CountSummaryAdapter extends RecyclerView.Adapter<CountSummaryAdapter.ViewHolder> {

    private static final float DIFFERENCE_EPSILON = 0.0005f;

    private final List<CountsDifferenceDTO> items = new ArrayList<>();

    public void setItems(List<CountsDifferenceDTO> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void addItems(List<CountsDifferenceDTO> moreItems) {
        int startPosition = items.size();
        items.addAll(moreItems);
        notifyItemRangeInserted(startPosition, moreItems.size());
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_summary_count, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CountsDifferenceDTO entry = items.get(position);
        Context context = holder.itemView.getContext();

        holder.tvId.setText(entry.getIdProduct());
        holder.tvStock.setText(String.format(Locale.US, "%.3f", entry.getStock()));
        holder.tvCount.setText(String.format(Locale.US, "%.3f", entry.getSum()));

        boolean matches = Math.abs(entry.getDifference()) < DIFFERENCE_EPSILON;
        holder.tvDifference.setText(String.format(Locale.US, "%+.3f", entry.getDifference()));
        holder.tvDifference.setCompoundDrawablesWithIntrinsicBounds(
                matches ? R.drawable.ic_check : R.drawable.ic_alert, 0, 0, 0);

        int textColor;
        if (matches) {
            holder.tvDifference.setBackgroundResource(R.drawable.bg_chip_good);
            textColor = ContextCompat.getColor(context, R.color.app_onSuccessContainer);
        } else {
            holder.tvDifference.setBackgroundResource(R.drawable.bg_chip_warn);
            textColor = resolveThemeColor(context, com.google.android.material.R.attr.colorOnErrorContainer);
        }
        holder.tvDifference.setTextColor(textColor);
        holder.tvDifference.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(textColor));
    }

    private int resolveThemeColor(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvId;
        final TextView tvStock;
        final TextView tvCount;
        final TextView tvDifference;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvId = itemView.findViewById(R.id.tvSummaryId);
            tvStock = itemView.findViewById(R.id.tvSummaryStock);
            tvCount = itemView.findViewById(R.id.tvSummaryCount);
            tvDifference = itemView.findViewById(R.id.tvSummaryDifference);
        }
    }
}
