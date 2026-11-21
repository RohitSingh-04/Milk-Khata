package com.example.milk_khata.ui.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import com.example.milk_khata.R;
import com.example.milk_khata.data.Slip;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import android.content.Context;

public class SlipAdapter extends RecyclerView.Adapter<SlipAdapter.SlipViewHolder> {

    private java.util.List<Slip> slips = new java.util.ArrayList<>();
    private java.util.Set<String> duplicateDates = new java.util.HashSet<>(); // Back to String
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Slip slip);
        void onDeleteClick(Slip slip);
    }

    public SlipAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    // Updated setter to accept both the list and the set of duplicate dates
    public void setSlips(java.util.List<Slip> slips, java.util.Set<String> duplicateDates) {
        this.slips = slips;
        this.duplicateDates = duplicateDates;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SlipViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_slip, parent, false);
        return new SlipViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SlipViewHolder holder, int position) {
        Slip slip = slips.get(position);

        // Display the raw date on screen
        holder.tvDate.setText(slip.date);
        holder.tvQtyRate.setText("Qty: " + slip.qty + "L | Rate: " + slip.rate);
        holder.tvAmount.setText("₹" + slip.amount);

        // Detect if the phone is currently in Dark Mode
        Context context = holder.itemView.getContext();
        int nightModeFlags = context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkMode = (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES);

        // PERFECTLY clean the date to match the Fragment using Regex
        String cleanDate = slip.date != null ? slip.date.replaceAll("[^0-9/]", "") : "";

        // 3. Force Apply Color using raw integers
        if (duplicateDates != null && duplicateDates.contains(cleanDate)) {
            // Dark Mode: Dark Mustard | Light Mode: Light Yellow
            int yellowColor = android.graphics.Color.parseColor(isDarkMode ? "#827717" : "#FFF59D");
            holder.cardView.setCardBackgroundColor(yellowColor);
        } else {
            // Dark Mode: Dark Grey | Light Mode: White
            int normalColor = android.graphics.Color.parseColor(isDarkMode ? "#2C2C2C" : "#FFFFFF");
            holder.cardView.setCardBackgroundColor(normalColor);
        }

        // Hide delete button by default
        holder.btnDelete.setVisibility(View.GONE);

        // Single, unified Click Listener
        holder.itemView.setOnClickListener(v -> {
            holder.btnDelete.setVisibility(View.GONE);

            listener.onItemClick(slip);
        });

        // Long Click Listener
        holder.itemView.setOnLongClickListener(v -> {
            holder.btnDelete.setVisibility(View.VISIBLE);
            return true;
        });

        // Delete Button Click
        holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(slip));
    }

    @Override
    public int getItemCount() {
        return slips.size();
    }

    static class SlipViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView tvDate, tvQtyRate, tvAmount;
        ImageButton btnDelete;

        public SlipViewHolder(@NonNull View itemView) {
            super(itemView);
            // Grab the exact ID we just added to the XML
            cardView = itemView.findViewById(R.id.card_view_slip);
            tvDate = itemView.findViewById(R.id.tv_row_date);
            tvQtyRate = itemView.findViewById(R.id.tv_row_qty_rate);
            tvAmount = itemView.findViewById(R.id.tv_row_amount);
            btnDelete = itemView.findViewById(R.id.btn_delete_row);
        }
    }
}