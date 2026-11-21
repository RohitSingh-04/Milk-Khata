package com.example.milk_khata.ui.history; // Update to your package

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.milk_khata.R;
import com.example.milk_khata.data.AppDatabase;
import com.example.milk_khata.data.Slip;
import com.example.milk_khata.databinding.FragmentHistoryBinding;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.OutputStream;
import android.widget.Toast;

public class HistoryFragment extends Fragment {

    private FragmentHistoryBinding binding;
    private SlipAdapter adapter;
    private List<Slip> allSlips = new ArrayList<>();

    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private Date filterStartDate = null;
    private Date filterEndDate = null;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHistoryBinding.inflate(inflater, container, false);

        setupRecyclerView();
        setupDateFilters();
        loadDataFromDatabase();

        binding.fabDeleteAll.setOnClickListener(v -> {
            if (allSlips.isEmpty()) {
                Toast.makeText(getContext(), "No data to delete", Toast.LENGTH_SHORT).show();
            } else {
                confirmDeleteAll();
            }
        });

        return binding.getRoot();
    }

    private void setupRecyclerView() {
        binding.recyclerHistory.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new SlipAdapter(new SlipAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Slip slip) {
                showDetailDialog(slip);
            }

            @Override
            public void onDeleteClick(Slip slip) {
                confirmDeletion(slip);
            }
        });

        binding.recyclerHistory.setAdapter(adapter);
    }

    private void loadDataFromDatabase() {
        Executors.newSingleThreadExecutor().execute(() -> {
            allSlips = AppDatabase.getDatabase(requireContext()).slipDao().getAllSlips();
            requireActivity().runOnUiThread(this::applyFilterAndCalculateTotals);
        });
    }

    private void applyFilterAndCalculateTotals() {
        List<Slip> filteredList = new ArrayList<>();
        double totalAmt = 0.0;
        double totalQty = 0.0;

        for (Slip slip : allSlips) {
            boolean matchesDate = true;
            try {
                if (slip.date != null) {
                    Date slipDate = sdf.parse(slip.date.trim());
                    if (slipDate != null) {
                        if (filterStartDate != null && slipDate.before(filterStartDate)) matchesDate = false;
                        if (filterEndDate != null && slipDate.after(filterEndDate)) matchesDate = false;
                    }
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }

            if (matchesDate) {
                filteredList.add(slip);
                totalAmt += slip.amount;
                totalQty += slip.qty;
            }
        }

        // Calculate Duplicate Dates (Regex Cleaning)
        java.util.HashMap<String, Integer> dateCounts = new java.util.HashMap<>();

        // First pass: Count dates by stripping ALL invisible OCR characters
        for (Slip slip : filteredList) {
            if (slip.date != null) {
                // This forces "20/11/2026[invisible_garbage]" to become pure "20/11/2026"
                String cleanDate = slip.date.replaceAll("[^0-9/]", "");
                dateCounts.put(cleanDate, dateCounts.getOrDefault(cleanDate, 0) + 1);
            }
        }

        // Second pass: Add to duplicate set
        java.util.Set<String> duplicateDates = new java.util.HashSet<>();
        for (java.util.Map.Entry<String, Integer> entry : dateCounts.entrySet()) {
            if (entry.getValue() > 1) {
                duplicateDates.add(entry.getKey());
            }
        }

        if (filteredList.isEmpty()) {
            binding.tvNoData.setVisibility(View.VISIBLE);
            binding.recyclerHistory.setVisibility(View.GONE);
        } else {
            binding.tvNoData.setVisibility(View.GONE);
            binding.recyclerHistory.setVisibility(View.VISIBLE);
        }

        // --- NEW: Sort the list chronologically by date ---
        java.util.Collections.sort(filteredList, (s1, s2) -> {
            try {
                // Safely clean and parse both dates
                java.util.Date d1 = sdf.parse(s1.date.replaceAll("[^0-9/]", ""));
                java.util.Date d2 = sdf.parse(s2.date.replaceAll("[^0-9/]", ""));

                if (d1 != null && d2 != null) {
                    // d2.compareTo(d1) puts the LATEST dates at the top.
                    // (Change to d1.compareTo(d2) if you want OLDEST dates at the top).
                    return d2.compareTo(d1);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0; // If parsing fails, leave them in their current order
        });
        java.util.Collections.reverse(filteredList);
        adapter.setSlips(filteredList, duplicateDates);

        // Safely format the raw decimal numbers first
        String formattedQty = String.format(java.util.Locale.getDefault(), "%.2f", totalQty);
        String formattedAmt = String.format(java.util.Locale.getDefault(), "%.2f", totalAmt);

        // Then apply the formatted numbers to the text views
        binding.tvTotalQty.setText("Total Qty: " + formattedQty);
        binding.tvTotalAmount.setText("Total Amt: ₹" + formattedAmt);
    }

    private void setupDateFilters() {
        binding.btnStartDate.setOnClickListener(v -> showDatePicker(true));
        binding.btnEndDate.setOnClickListener(v -> showDatePicker(false));
        binding.btnClearFilter.setOnClickListener(v -> {
            filterStartDate = null;
            filterEndDate = null;
            binding.btnStartDate.setText("Start Date");
            binding.btnEndDate.setText("End Date");
            applyFilterAndCalculateTotals();
        });
    }

    private void showDatePicker(boolean isStart) {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
            if (isStart) {
                // Set to exactly the first millisecond of the day
                calendar.set(year, month, dayOfMonth, 0, 0, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                filterStartDate = calendar.getTime();
                binding.btnStartDate.setText(sdf.format(filterStartDate));
            } else {
                // Set to exactly the last millisecond of the day
                calendar.set(year, month, dayOfMonth, 23, 59, 59);
                calendar.set(Calendar.MILLISECOND, 999);
                filterEndDate = calendar.getTime();
                binding.btnEndDate.setText(sdf.format(filterEndDate));
            }
            applyFilterAndCalculateTotals();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showDetailDialog(Slip slip) {
        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_slip_detail);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        ImageView ivImage = dialog.findViewById(R.id.iv_dialog_image);
        if (slip.imagePath != null) {
            File imgFile = new File(slip.imagePath);
            if (imgFile.exists()) {
                ivImage.setImageURI(Uri.fromFile(imgFile));
            }
        }

        ((TextView) dialog.findViewById(R.id.tv_dialog_date)).setText("Date: " + slip.date);
        ((TextView) dialog.findViewById(R.id.tv_dialog_qty)).setText("Quantity: " + slip.qty + "L");
        ((TextView) dialog.findViewById(R.id.tv_dialog_rate)).setText("Rate: ₹" + slip.rate);
        ((TextView) dialog.findViewById(R.id.tv_dialog_amount)).setText("Amount: ₹" + slip.amount);
        ((TextView) dialog.findViewById(R.id.tv_dialog_incentive)).setText("Incentive: ₹" + slip.incentive);

        dialog.findViewById(R.id.btn_dialog_close).setOnClickListener(v -> dialog.dismiss());

        // New Save to Gallery Button
        dialog.findViewById(R.id.btn_dialog_save_gallery).setOnClickListener(v -> {
            if (slip.imagePath != null) {
                saveImageToPublicGallery(slip.imagePath);
            }
        });

        dialog.show();
    }
    private void confirmDeletion(Slip slip) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Slip")
                .setMessage("Are you sure you want to delete this slip? This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteSlip(slip))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteSlip(Slip slip) {
        Executors.newSingleThreadExecutor().execute(() -> {
            // 1. Delete from Database
            AppDatabase.getDatabase(requireContext()).slipDao().delete(slip);

            // 2. Delete the physical image file from internal storage to free up memory
            if (slip.imagePath != null) {
                File imgFile = new File(slip.imagePath);
                if (imgFile.exists()) {
                    imgFile.delete();
                }
            }

            // 3. Reload the list and recalculate totals on the main thread
            requireActivity().runOnUiThread(this::loadDataFromDatabase);
        });
    }

    private void saveImageToPublicGallery(String internalImagePath) {
        try {
            File file = new File(internalImagePath);
            if (!file.exists()) return;

            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());

            // Prepare MediaStore entry
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "MilkSlip_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            // Saves it to a dedicated folder in the Gallery
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MilkKhata");

            // Insert into MediaStore and write the file
            Uri uri = requireContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                OutputStream outputStream = requireContext().getContentResolver().openOutputStream(uri);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                outputStream.close();
                Toast.makeText(getContext(), "Image saved to Gallery!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Failed to save image", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDeleteAll() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete All Slips")
                .setMessage("Are you sure you want to delete ALL slips? This will permanently erase all data and internal images.")
                .setPositiveButton("Delete All", (dialog, which) -> deleteAllSlips())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteAllSlips() {
        Executors.newSingleThreadExecutor().execute(() -> {

            // 1. Delete all physical image files from internal storage to free up memory
            for (Slip slip : allSlips) {
                if (slip.imagePath != null) {
                    File imgFile = new File(slip.imagePath);
                    if (imgFile.exists()) {
                        imgFile.delete();
                    }
                }
            }

            // 2. Clear the database table using the DAO
            AppDatabase.getDatabase(requireContext()).slipDao().deleteAllSlips();

            // 3. Reload the UI on the main thread
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(getContext(), "All data cleared", Toast.LENGTH_SHORT).show();
                loadDataFromDatabase();
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}