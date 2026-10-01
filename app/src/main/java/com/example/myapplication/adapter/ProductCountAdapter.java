package com.example.myapplication.adapter;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.network.ProductCountEntryDTO;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lista de los conteos ya registrados en el backend para este inventario.
 * - Toque corto: carga el renglon en el formulario para editarlo (onSelect).
 * - Mantener presionado 1 s: muestra el detalle del registro (onShowDetail)
 *   sin entrar al flujo de edicion. Mientras se mantiene, el fondo se llena y
 *   un indicador circular marca el avance.
 * - Soltar antes de 1 s (pero despues de un toque) o deslizar el dedo cancela.
 * El icono de bote de basura elimina el renglon (DELETE /productCounts/{id}).
 */
public class ProductCountAdapter extends RecyclerView.Adapter<ProductCountAdapter.ViewHolder> {

    public interface OnProductCountActionListener {
        void onSelect(ProductCountEntryDTO entry);

        void onDelete(ProductCountEntryDTO entry);

        /** Mantener presionado 1 s: detalle de solo lectura (ver CountEntryDetailDialog). */
        void onShowDetail(ProductCountEntryDTO entry);
    }

    /** Hasta este tiempo, levantar el dedo cuenta como toque (editar). */
    private static final long TAP_MAX_MS = 350;
    /** Tiempo que hay que mantener presionado para ver el detalle. */
    private static final long HOLD_MS = 1000;

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
        holder.bind(entry, listener);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        holder.cancelHold();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvProductId;
        final TextView tvQuantity;
        final ImageView ivDelete;
        final View holdFill;
        final CircularProgressIndicator holdProgress;
        private final int touchSlop;

        private ProductCountEntryDTO entry;
        private OnProductCountActionListener listener;
        private ValueAnimator holdAnimator;
        private long pressStart;
        private float downX;
        private float downY;
        /** El gesto en curso ya abrio el detalle: soltar despues no hace nada. */
        private boolean holdCompleted;
        /** Hay un gesto en curso (no cancelado). */
        private boolean tracking;
        /** Id de la accion de accesibilidad "Ver detalle" agregada al renglon. */
        private int detailActionId = View.NO_ID;

        private final Runnable holdRunnable = this::onHoldCompleted;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductId = itemView.findViewById(R.id.tvCountProductId);
            tvQuantity = itemView.findViewById(R.id.tvCountQuantity);
            ivDelete = itemView.findViewById(R.id.ivDeleteCount);
            holdFill = itemView.findViewById(R.id.holdFill);
            holdProgress = itemView.findViewById(R.id.holdProgress);
            touchSlop = ViewConfiguration.get(itemView.getContext()).getScaledTouchSlop();
        }

        @SuppressLint("ClickableViewAccessibility") // El toque corto llama a performClick().
        void bind(ProductCountEntryDTO entry, OnProductCountActionListener listener) {
            this.entry = entry;
            this.listener = listener;
            cancelHold();

            // performClick() (toque corto, o doble toque con TalkBack) = editar.
            itemView.setOnClickListener(v -> listener.onSelect(entry));
            ivDelete.setOnClickListener(v -> listener.onDelete(entry));
            itemView.setOnTouchListener(this::onTouch);

            // Alternativa accesible al gesto de 1 s: accion "Ver detalle" en TalkBack.
            // Se quita la del renglon anterior para no acumularlas al reciclar la vista.
            if (detailActionId != View.NO_ID) {
                ViewCompat.removeAccessibilityAction(itemView, detailActionId);
            }
            detailActionId = ViewCompat.addAccessibilityAction(itemView,
                    itemView.getContext().getString(R.string.count_detail_action),
                    (view, arguments) -> {
                        listener.onShowDetail(entry);
                        return true;
                    });
        }

        private boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pressStart = event.getEventTime();
                    downX = event.getX();
                    downY = event.getY();
                    holdCompleted = false;
                    tracking = true;
                    v.setPressed(true);
                    startHoldProgress();
                    v.postDelayed(holdRunnable, HOLD_MS);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    // Deslizar (p. ej. para hacer scroll) cancela el gesto.
                    if (tracking && Math.hypot(event.getX() - downX, event.getY() - downY) > touchSlop) {
                        cancelHold();
                    }
                    return true;

                case MotionEvent.ACTION_UP: {
                    boolean wasTracking = tracking;
                    long elapsed = event.getEventTime() - pressStart;
                    cancelHold();
                    if (!wasTracking || holdCompleted) {
                        return true;
                    }
                    if (elapsed < TAP_MAX_MS) {
                        v.performClick();
                    } else {
                        // Soltó a medias: ni edita ni abre el detalle; se explica el gesto.
                        Toast.makeText(v.getContext(), R.string.count_hold_hint, Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }

                case MotionEvent.ACTION_CANCEL:
                    cancelHold();
                    return true;

                default:
                    return false;
            }
        }

        private void startHoldProgress() {
            holdProgress.setProgressCompat(0, false);
            holdProgress.setVisibility(View.VISIBLE);
            holdAnimator = ValueAnimator.ofFloat(0f, 1f);
            holdAnimator.setDuration(HOLD_MS);
            holdAnimator.setInterpolator(new LinearInterpolator());
            holdAnimator.addUpdateListener(a -> {
                float fraction = (float) a.getAnimatedValue();
                holdFill.setScaleX(fraction);
                holdProgress.setProgressCompat(Math.round(fraction * 100), false);
            });
            holdAnimator.start();
        }

        private void onHoldCompleted() {
            if (!tracking || entry == null) {
                return;
            }
            holdCompleted = true;
            tracking = false;
            itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            resetHoldVisuals();
            listener.onShowDetail(entry);
        }

        /** Detiene el gesto en curso y deja el renglon en su estado normal. */
        void cancelHold() {
            tracking = false;
            itemView.removeCallbacks(holdRunnable);
            resetHoldVisuals();
        }

        private void resetHoldVisuals() {
            if (holdAnimator != null) {
                holdAnimator.cancel();
                holdAnimator = null;
            }
            itemView.setPressed(false);
            holdFill.setScaleX(0f);
            holdProgress.setVisibility(View.INVISIBLE);
        }
    }
}
