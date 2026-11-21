package com.example.milk_khata.ui.analysis;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.milk_khata.R;
import com.example.milk_khata.data.AppDatabase;
import com.example.milk_khata.data.Slip;
import com.example.milk_khata.databinding.FragmentAnalysisBinding;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.concurrent.Executors;

import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileWriter;

public class AnalysisFragment extends Fragment {

    private FragmentAnalysisBinding binding;
    private List<Slip> allSlips = new ArrayList<>();
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    private Date filterStartDate = null;
    private Date filterEndDate = null;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAnalysisBinding.inflate(inflater, container, false);

        Executors.newSingleThreadExecutor().execute(() -> {
            allSlips = AppDatabase.getDatabase(requireContext()).slipDao().getAllSlips();
        });

        binding.cardRate.setOnClickListener(v -> showChartDialog("RATE"));
        binding.cardAmount.setOnClickListener(v -> showChartDialog("AMOUNT"));
        binding.cardQty.setOnClickListener(v -> showChartDialog("QTY"));
        // New Frequency Click Listener
        binding.cardFreq.setOnClickListener(v -> showChartDialog("FREQ"));

        binding.cardInfo.setOnClickListener(v -> showInfoDialog());

        return binding.getRoot();
    }

    private void showInfoDialog() {
        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_info);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        // Export CSV Logic
        dialog.findViewById(R.id.btn_export_csv).setOnClickListener(v -> {
            if (allSlips.isEmpty()) {
                Toast.makeText(getContext(), "No data to export", Toast.LENGTH_SHORT).show();
                return;
            }
            exportDatabaseToCSV();
        });

        dialog.findViewById(R.id.btn_close_info).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void exportDatabaseToCSV() {
        try {
            // 1. Create a temporary file in the cache directory
            File exportDir = new File(requireContext().getCacheDir(), "");
            if (!exportDir.exists()) exportDir.mkdirs();

            File file = new File(exportDir, "Dairy_Export.csv");
            file.createNewFile();
            FileWriter csvWriter = new FileWriter(file);

            // 2. Write the CSV Header
            csvWriter.append("ID,Date,Quantity (L),Rate (Rs),Amount (Rs),Incentive (Rs)\n");

            // 3. Write all rows from the database
            for (Slip slip : allSlips) {
                csvWriter.append(String.valueOf(slip.id)).append(",");

                // Sanitize fields to prevent commas from breaking the CSV layout
                String cleanDate = slip.date != null ? slip.date.replace(",", "") : "";
                csvWriter.append(cleanDate).append(",");

                csvWriter.append(String.valueOf(slip.qty)).append(",");
                csvWriter.append(String.valueOf(slip.rate)).append(",");
                csvWriter.append(String.valueOf(slip.amount)).append(",");
                csvWriter.append(String.valueOf(slip.incentive)).append("\n");
            }

            csvWriter.flush();
            csvWriter.close();

            // 4. Fire the Share Intent using FileProvider
            Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", file);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Milk Khata Export");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share CSV file via..."));

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Failed to export CSV: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showChartDialog(String chartMode) {
        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_chart);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        filterStartDate = null;
        filterEndDate = null;

        TextView tvTitle = dialog.findViewById(R.id.tv_chart_title);
        BarChart barChart = dialog.findViewById(R.id.bar_chart);
        LineChart lineChart = dialog.findViewById(R.id.line_chart);
        Button btnStart = dialog.findViewById(R.id.btn_chart_start_date);
        Button btnEnd = dialog.findViewById(R.id.btn_chart_end_date);
        Button btnClear = dialog.findViewById(R.id.btn_chart_clear);

        // Dynamic Title Setup
        if (chartMode.equals("AMOUNT")) tvTitle.setText("Amount Per Day");
        else if (chartMode.equals("RATE")) tvTitle.setText("Average Rate Per Day");
        else if (chartMode.equals("QTY")) tvTitle.setText("Total Quantity Per Day");
        else if (chartMode.equals("FREQ")) tvTitle.setText("Slip Frequency Per Day"); // New Title

        // Toggle UI based on mode
        if (chartMode.equals("AMOUNT")) {
            barChart.setVisibility(View.GONE);
            lineChart.setVisibility(View.VISIBLE);
        } else {
            barChart.setVisibility(View.VISIBLE);
            lineChart.setVisibility(View.GONE);
        }

        Runnable refreshChart = () -> renderChart(chartMode, barChart, lineChart);

        btnStart.setOnClickListener(v -> showDatePicker(true, btnStart, refreshChart));
        btnEnd.setOnClickListener(v -> showDatePicker(false, btnEnd, refreshChart));
        btnClear.setOnClickListener(v -> {
            filterStartDate = null;
            filterEndDate = null;
            btnStart.setText("Start Date");
            btnEnd.setText("End Date");
            refreshChart.run();
        });

        refreshChart.run();

        dialog.findViewById(R.id.btn_close_chart).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void renderChart(String mode, BarChart barChart, LineChart lineChart) {
        TreeMap<Date, List<Slip>> dailyData = new TreeMap<>();

        for (Slip slip : allSlips) {
            if (slip.date != null) {
                try {
                    Date slipDate = sdf.parse(slip.date.replaceAll("[^0-9/]", ""));
                    if (slipDate != null) {
                        if (filterStartDate != null && slipDate.before(filterStartDate)) continue;
                        if (filterEndDate != null && slipDate.after(filterEndDate)) continue;

                        if (!dailyData.containsKey(slipDate)) dailyData.put(slipDate, new ArrayList<>());
                        dailyData.get(slipDate).add(slip);
                    }
                } catch (Exception ignored) {}
            }
        }

        List<BarEntry> barEntries = new ArrayList<>();
        List<Entry> lineEntries = new ArrayList<>();
        List<String> xLabels = new ArrayList<>();

        int index = 0;
        for (Date date : dailyData.keySet()) {
            List<Slip> daySlips = dailyData.get(date);
            double totalAmt = 0;
            double totalQty = 0;

            for (Slip s : daySlips) {
                totalAmt += s.amount;
                totalQty += s.qty;
            }

            float yValue = 0f;
            if (mode.equals("AMOUNT")) yValue = (float) totalAmt;
            else if (mode.equals("QTY")) yValue = (float) totalQty;
            else if (mode.equals("RATE")) yValue = totalQty > 0 ? (float) (totalAmt / totalQty) : 0f;
            else if (mode.equals("FREQ")) yValue = (float) daySlips.size(); // New Math: Just count the array size

            if (mode.equals("AMOUNT")) {
                lineEntries.add(new Entry(index, yValue));
            } else {
                barEntries.add(new BarEntry(index, yValue));
            }

            xLabels.add(sdf.format(date));
            index++;
        }

        // Detect Dark Mode for axes colors
        int nightModeFlags = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        int textColor = (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) ? Color.WHITE : Color.BLACK;

        if (mode.equals("AMOUNT")) {
            LineDataSet dataSet = new LineDataSet(lineEntries, "Total Amount (₹)");
            dataSet.setColor(Color.parseColor("#4CAF50"));
            dataSet.setLineWidth(3f);
            dataSet.setCircleColor(Color.parseColor("#4CAF50"));
            dataSet.setValueTextSize(12f);
            dataSet.setValueTextColor(textColor);

            lineChart.setData(new LineData(dataSet));
            setupXAxis(lineChart.getXAxis(), xLabels, textColor);
            lineChart.getAxisLeft().setTextColor(textColor);
            lineChart.getAxisRight().setEnabled(false);
            lineChart.getLegend().setTextColor(textColor);
            lineChart.getDescription().setEnabled(false);
            lineChart.invalidate();
        } else {
            String label = mode.equals("RATE") ? "Avg Rate (₹)" : mode.equals("QTY") ? "Total Qty (L)" : "Total Slips";
            BarDataSet dataSet = new BarDataSet(barEntries, label);

            // Give Frequency a unique color (Orange)
            dataSet.setColor(Color.parseColor(mode.equals("FREQ") ? "#FF9800" : "#2196F3"));
            dataSet.setValueTextSize(12f);
            dataSet.setValueTextColor(textColor);

            barChart.setData(new BarData(dataSet));
            setupXAxis(barChart.getXAxis(), xLabels, textColor);
            barChart.getAxisLeft().setTextColor(textColor);
            barChart.getAxisRight().setEnabled(false);
            barChart.getLegend().setTextColor(textColor);
            barChart.getDescription().setEnabled(false);
            barChart.invalidate();
        }
    }

    private void setupXAxis(XAxis xAxis, List<String> labels, int textColor) {
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(-45f);
        xAxis.setTextColor(textColor);
    }

    private void showDatePicker(boolean isStart, Button btn, Runnable onDateSelected) {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
            if (isStart) {
                // Set to exactly the first millisecond of the day
                calendar.set(year, month, dayOfMonth, 0, 0, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                filterStartDate = calendar.getTime();
                btn.setText(sdf.format(filterStartDate));
            } else {
                // Set to exactly the last millisecond of the day
                calendar.set(year, month, dayOfMonth, 23, 59, 59);
                calendar.set(Calendar.MILLISECOND, 999);
                filterEndDate = calendar.getTime();
                btn.setText(sdf.format(filterEndDate));
            }
            onDateSelected.run();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}