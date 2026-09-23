package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.network.ReportDTO;
import com.example.myapplication.util.DateFormatUtils;

import java.util.ArrayList;
import java.util.List;

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ViewHolder> {

    public interface OnReportClickListener {
        void onDownloadReport(ReportDTO report);
    }

    private final List<ReportDTO> items = new ArrayList<>();
    private final OnReportClickListener listener;
    private long downloadingInventoryId = -1;

    public ReportAdapter(OnReportClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ReportDTO> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** idInventory del reporte que se esta descargando actualmente (-1 si ninguno), para mostrar su spinner. */
    public void setDownloadingInventoryId(long idInventory) {
        downloadingInventoryId = idInventory;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_report, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ReportDTO report = items.get(position);
        holder.tvDate.setText(DateFormatUtils.toShortSpanishDate(report.getInventoryDate()));
        holder.tvPresentation.setText(report.getPresentation());

        boolean downloading = report.getIdInventory() == downloadingInventoryId;
        holder.progressDownload.setVisibility(downloading ? View.VISIBLE : View.GONE);
        holder.ivDownload.setVisibility(downloading ? View.GONE : View.VISIBLE);

        boolean anyDownloadInProgress = downloadingInventoryId != -1;
        holder.itemView.setEnabled(!anyDownloadInProgress);
        holder.itemView.setOnClickListener(anyDownloadInProgress ? null : v -> listener.onDownloadReport(report));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvDate;
        final TextView tvPresentation;
        final View progressDownload;
        final ImageView ivDownload;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvPresentation = itemView.findViewById(R.id.tvPresentation);
            progressDownload = itemView.findViewById(R.id.progressDownload);
            ivDownload = itemView.findViewById(R.id.ivDownload);
        }
    }
}
