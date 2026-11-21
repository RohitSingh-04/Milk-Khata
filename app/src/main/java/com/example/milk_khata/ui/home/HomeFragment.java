package com.example.milk_khata.ui.home; // Ensure this matches your package name

import static android.app.Activity.RESULT_OK;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.milk_khata.databinding.FragmentHomeBinding;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.core.content.ContextCompat;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;

import java.util.concurrent.Executors;
import com.example.milk_khata.data.AppDatabase;
import com.example.milk_khata.data.Slip;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private TextRecognizer textRecognizer;
    private GmsDocumentScanner documentScanner;

    private Bitmap currentSlipBitmap;

    // Smart Document Scanner Launcher
    private final ActivityResultLauncher<IntentSenderRequest> scannerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    GmsDocumentScanningResult scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.getData());
                    if (scanningResult != null && scanningResult.getPages() != null && !scanningResult.getPages().isEmpty()) {

                        // Get the cropped image URI from the scanner
                        Uri croppedImageUri = scanningResult.getPages().get(0).getImageUri();
                        processImageWithMLKit(croppedImageUri);
                    }
                }
            });

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        setupLiveValidation();

        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        // Configure the Scanner (Allows Camera and Gallery imports, returns a clean JPEG)
        GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
                .build();

        documentScanner = GmsDocumentScanning.getClient(options);

        View.OnClickListener launchScanner = v -> {
            documentScanner.getStartScanIntent(requireActivity())
                    .addOnSuccessListener(intentSender ->
                            scannerLauncher.launch(new IntentSenderRequest.Builder(intentSender).build()))
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(), "Scanner failed to start", Toast.LENGTH_SHORT).show());
        };

        binding.btnCamera.setOnClickListener(launchScanner);
        binding.btnGallery.setOnClickListener(launchScanner);

        binding.btnDiscard.setOnClickListener(v -> clearFields());

        // Call the new centralized validation method
        binding.btnSave.setOnClickListener(v -> validateAndSave());

        return root;
    }

    private void validateAndSave() {
        // 1. Clear any previous errors
        binding.etDate.setError(null);
        binding.etAmount.setError(null);

        // 2. Validate Image Presence
        if (currentSlipBitmap == null) {
            Toast.makeText(getContext(), "Please scan an image first", Toast.LENGTH_SHORT).show();
            return;
        }

        // 3. Validate Date Format (Strict dd/MM/yyyy)
        String dateStr = binding.etDate.getText().toString().trim();
        if (!dateStr.matches("\\d{2}/\\d{2}/\\d{4}")) {
            // This natively pops up the red warning icon inside the EditText
            binding.etDate.setError("Format must be exactly dd/MM/yyyy");
            binding.etDate.requestFocus();
            return; // Abort save
        }

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
        sdf.setLenient(false); // Prevents invalid calendar dates
        try {
            sdf.parse(dateStr);
        } catch (java.text.ParseException e) {
            binding.etDate.setError("Invalid calendar date");
            binding.etDate.requestFocus();
            return; // Abort save
        }

        // 4. Validate Math (Qty * Rate == Amount) and Empty Fields
        try {
            String qtyStr = binding.etQty.getText().toString().trim();
            String rateStr = binding.etRate.getText().toString().trim();
            String amtStr = binding.etAmount.getText().toString().trim();
            String incentiveStr = binding.etIncentive.getText().toString().trim();

            if (qtyStr.isEmpty() || rateStr.isEmpty() || amtStr.isEmpty() || incentiveStr.isEmpty()) {
                Toast.makeText(getContext(), "All fields are required", Toast.LENGTH_SHORT).show();
                return; // Abort save
            }

            double q = Double.parseDouble(qtyStr);
            double r = Double.parseDouble(rateStr);
            double a = Double.parseDouble(amtStr);

            // Calculate expected amount
            double expectedAmount = q * r;

            // Use a 0.10 tolerance to allow for standard currency rounding
            if (Math.abs(expectedAmount - a) > 0.10) {
                binding.etAmount.setError("Math Error: Qty * Rate does not equal this Amount");
                binding.etAmount.requestFocus();
                Toast.makeText(getContext(), "Please fix the Amount before saving", Toast.LENGTH_SHORT).show();
                return; // Abort save
            }
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Invalid numbers entered", Toast.LENGTH_SHORT).show();
            return; // Abort save
        }

        // 5. If all validations pass, proceed to save to database!
        saveSlipToDatabase();
    }

    private void saveSlipToDatabase() {
        String date = binding.etDate.getText().toString().trim();
        double qty = Double.parseDouble(binding.etQty.getText().toString().trim());
        double rate = Double.parseDouble(binding.etRate.getText().toString().trim());
        double amount = Double.parseDouble(binding.etAmount.getText().toString().trim());
        double incentive = Double.parseDouble(binding.etIncentive.getText().toString().trim());

        Executors.newSingleThreadExecutor().execute(() -> {
            String internalPath = saveImageToInternalStorage(currentSlipBitmap);
            Slip newSlip = new Slip(date, qty, rate, amount, incentive, internalPath);
            AppDatabase.getDatabase(requireContext()).slipDao().insert(newSlip);

            requireActivity().runOnUiThread(() -> {
                Toast.makeText(getContext(), "Slip Saved Successfully!", Toast.LENGTH_SHORT).show();
                clearFields();
                binding.ivSlipPreview.setImageDrawable(null);
                currentSlipBitmap = null;
            });
        });
    }

    private void processImageWithMLKit(Uri imageUri) {
        clearFields();
        try {
            // 1. Convert the URI to a raw Bitmap
            InputStream imageStream = requireContext().getContentResolver().openInputStream(imageUri);
            Bitmap rawBitmap = BitmapFactory.decodeStream(imageStream);

            // 2. Run custom contrast/brightness filter to optimize for OCR
            Bitmap sharpenBitmap = applySharpening(rawBitmap);
            Bitmap enhancedBitmap = enhanceBitmapForOCR(sharpenBitmap);

            // for saving the image
            currentSlipBitmap = enhancedBitmap;
            // make the image visible
            binding.ivSlipPreview.setVisibility(View.VISIBLE);

            // 3. Show the enhanced image on screen
            binding.ivSlipPreview.setImageBitmap(enhancedBitmap);

            // 4. Feed the enhanced Bitmap directly to ML Kit
            InputImage image = InputImage.fromBitmap(enhancedBitmap, 0);

            textRecognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        String rawText = visionText.getText();
                        Log.d("MLKitText", rawText);
                        extractDataFromText(rawText);
                    })
                    .addOnFailureListener(e -> {
                        Log.e("MLKit", "Text recognition failed", e);
                        Toast.makeText(getContext(), "Text recognition failed", Toast.LENGTH_SHORT).show();
                    });

        } catch (Exception e) {
            Log.e("MLKit", "Failed to process image", e);
            Toast.makeText(getContext(), "Error loading image", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap applySharpening(Bitmap src) {
        int width = src.getWidth();
        int height = src.getHeight();
        Bitmap result = Bitmap.createBitmap(width, height, src.getConfig());

        // Extract raw pixels for massive performance gain over getPixel/setPixel
        int[] srcPixels = new int[width * height];
        int[] destPixels = new int[width * height];
        src.getPixels(srcPixels, 0, width, 0, 0, width, height);

        // Copy borders to destination so we don't have to calculate edges
        System.arraycopy(srcPixels, 0, destPixels, 0, srcPixels.length);

        // Fast 3x3 Sharpening Kernel
        for (int y = 1; y < height - 1; y++) {
            int offset = y * width;
            for (int x = 1; x < width - 1; x++) {
                int i = offset + x;

                // We grab just the Blue channel (& 0xFF) for speed.
                int center = (srcPixels[i] & 0xFF);
                int top    = (srcPixels[i - width] & 0xFF);
                int bottom = (srcPixels[i + width] & 0xFF);
                int left   = (srcPixels[i - 1] & 0xFF);
                int right  = (srcPixels[i + 1] & 0xFF);

                // Apply Kernel math: (5 * Center) - Top - Bottom - Left - Right
                int pixelValue = (5 * center) - top - bottom - left - right;

                // Clamp between 0 (Black) and 255 (White)
                pixelValue = Math.max(0, Math.min(pixelValue, 255));

                // Reconstruct the grayscale pixel (Alpha=255, R=G=B=pixelValue)
                destPixels[i] = (0xFF000000) | (pixelValue << 16) | (pixelValue << 8) | pixelValue;
            }
        }

        result.setPixels(destPixels, 0, width, 0, 0, width, height);
        return result;
    }

    private Bitmap enhanceBitmapForOCR(Bitmap original) {
        Bitmap enhanced = Bitmap.createBitmap(original.getWidth(), original.getHeight(), original.getConfig());
        Canvas canvas = new Canvas(enhanced);
        Paint paint = new Paint();

        // Increase contrast by 3x and flat brightness by 5
        float contrast = 3f;
        float brightness = 5f;

        ColorMatrix colorMatrix = new ColorMatrix(new float[]{
                contrast, 0, 0, 0, brightness,
                0, contrast, 0, 0, brightness,
                0, 0, contrast, 0, brightness,
                0, 0, 0, 1, 0
        });

        // Convert to grayscale for even better OCR text isolation
        colorMatrix.setSaturation(0);

        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        canvas.drawBitmap(original, 0, 0, paint);

        return enhanced;
    }

    private void extractDataFromText(String rawText) {
        // 1. PRE-CLEANING: Fix common OCR artifacts before Regex sees them
        // Fixes commas next to decimals, and replaces letter 'O'/'o' with zero
        String cleanText = rawText.replace(",.", ".")
                .replace(".,", ".")
                .replace("O", "0")
                .replace("o", "0");

        android.util.Log.d("OCR_DEBUG", "Cleaned Text:\n" + cleanText);

        // 2. REGEX EXTRACTION
        Pattern datePattern = Pattern.compile("(?i)Date.*?(\\d{2}/\\d{2}/\\d{2,4})");
        Pattern qtyPattern = Pattern.compile("(?i)QTY.*?([0-9]+\\.[0-9]+)");
        Pattern ratePattern = Pattern.compile("(?i)Rate.*?([0-9]+\\.[0-9]+)");
        Pattern amountPattern = Pattern.compile("(?i)Amount.*?([0-9]+\\.[0-9]+)");
        Pattern incentivePattern = Pattern.compile("(?i)Incentive.*?([0-9]+\\.[0-9]+)");

        Matcher dateMatcher = datePattern.matcher(cleanText);
        if (dateMatcher.find()) binding.etDate.setText(dateMatcher.group(1));

        Matcher qtyMatcher = qtyPattern.matcher(cleanText);
        if (qtyMatcher.find()) binding.etQty.setText(qtyMatcher.group(1));

        Matcher rateMatcher = ratePattern.matcher(cleanText);
        if (rateMatcher.find()) binding.etRate.setText(rateMatcher.group(1));

        Matcher amountMatcher = amountPattern.matcher(cleanText);
        if (amountMatcher.find()) binding.etAmount.setText(amountMatcher.group(1));

        Matcher incentiveMatcher = incentivePattern.matcher(cleanText);
        if (incentiveMatcher.find()) binding.etIncentive.setText(incentiveMatcher.group(1));

        // 3. SMART MATH FALLBACK
        // If ML Kit scrambles the columns and misses a value, calculate it automatically
        try {
            String qtyStr = binding.etQty.getText().toString();
            String rateStr = binding.etRate.getText().toString();
            String amtStr = binding.etAmount.getText().toString();

            double q = qtyStr.isEmpty() ? 0 : Double.parseDouble(qtyStr);
            double r = rateStr.isEmpty() ? 0 : Double.parseDouble(rateStr);
            double a = amtStr.isEmpty() ? 0 : Double.parseDouble(amtStr);

            // If Rate is missing (0.00) but we have Qty and Amount
            if (r == 0 && q > 0 && a > 0) {
                double calculatedRate = a / q;
                binding.etRate.setText(String.format(java.util.Locale.getDefault(), "%.2f", calculatedRate));
            }
            // If Amount is missing (0.00) but we have Qty and Rate
            else if (a == 0 && q > 0 && r > 0) {
                double calculatedAmount = q * r;
                binding.etAmount.setText(String.format(java.util.Locale.getDefault(), "%.2f", calculatedAmount));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        validateMath();
    }

    private void validateMath() {
        try {
            String qtyStr = binding.etQty.getText().toString();
            String rateStr = binding.etRate.getText().toString();
            String amountStr = binding.etAmount.getText().toString();

            if (qtyStr.isEmpty() || rateStr.isEmpty() || amountStr.isEmpty()) {
                setWarning(false); // Don't show warnings if fields are empty
                return;
            }

            double qty = Double.parseDouble(qtyStr);
            double rate = Double.parseDouble(rateStr);
            double amount = Double.parseDouble(amountStr);

            double calculatedAmount = qty * rate;

            // Check if the difference is greater than 0.1 (handles rounding differences)
            boolean isMismatch = Math.abs(calculatedAmount - amount) > 0.1;
            setWarning(isMismatch);

        } catch (NumberFormatException e) {
            setWarning(false); // Failsafe if the text isn't a valid number yet
        }
    }

    private void setWarning(boolean show) {
        Drawable warningIcon = null;
        if (show) {
            warningIcon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_dialog_alert);
            if (warningIcon != null) {
                warningIcon.setBounds(0, 0, warningIcon.getIntrinsicWidth(), warningIcon.getIntrinsicHeight());
                warningIcon.setTint(Color.parseColor("#FFB300")); // Amber/Yellow color
            }
        }

        // Places the icon on the right side of the text fields
        binding.etQty.setCompoundDrawables(null, null, warningIcon, null);
        binding.etRate.setCompoundDrawables(null, null, warningIcon, null);
        binding.etAmount.setCompoundDrawables(null, null, warningIcon, null);
    }

    private void setupLiveValidation() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                validateMath();
            }
        };

        binding.etQty.addTextChangedListener(watcher);
        binding.etRate.addTextChangedListener(watcher);
        binding.etAmount.addTextChangedListener(watcher);
    }

    private String saveImageToInternalStorage(Bitmap bitmap) {
        if (bitmap == null) return null;

        // Creates a private folder named "slip_images" that is hidden from the gallery
        File directory = requireContext().getDir("slip_images", Context.MODE_PRIVATE);
        File imagePath = new File(directory, "slip_" + System.currentTimeMillis() + ".jpg");

        try (FileOutputStream fos = new FileOutputStream(imagePath)) {
            // Compress and save the image to the private folder
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            return imagePath.getAbsolutePath();
        } catch (Exception e) {
            Log.e("Storage", "Error saving image", e);
            return null;
        }
    }
    private void clearFields() {
        binding.etDate.setText("");
        binding.etQty.setText("");
        binding.etRate.setText("");
        binding.etAmount.setText("");
        binding.etIncentive.setText("");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}