package com.trae.batchcrop;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.trae.batchcrop.util.BatchCropper;
import com.trae.batchcrop.util.ImageUtils;
import com.trae.batchcrop.util.UiUtils;
import com.trae.batchcrop.view.CropEditorView;

import java.util.ArrayList;

/**
 * 在参考图上逐张框出每个裁剪区域，然后套用到全部图片。
 */
public class CropActivity extends AppCompatActivity {

    public static final String EXTRA_URIS = "extra_uris";
    public static final String EXTRA_COUNT = "extra_count";

    private static final int REQUEST_STORAGE = 21;
    private static final int PREVIEW_MAX_DIM = 2048;
    private static final String STATE_REGIONS = "state_regions";
    private static final String STATE_CURRENT = "state_current";

    private final ArrayList<RectF> regions = new ArrayList<>();
    private ArrayList<Uri> sources = new ArrayList<>();
    private int regionCount = 1;
    private int currentIndex = 0;
    private int sourceWidth;
    private int sourceHeight;
    private boolean syncingChips;

    private CropEditorView editor;
    private TextView hintView;
    private TextView sizeBadge;
    private ProgressBar loading;
    private ChipGroup chipGroup;
    private MaterialButton prevButton;
    private MaterialButton nextButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiUtils.setupEdgeToEdge(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);
        UiUtils.applySystemBarInsets(findViewById(R.id.root));
        UiUtils.setLightSystemBars(this, false);

        ArrayList<Uri> received = IntentCompat.getParcelableArrayListExtra(getIntent(), EXTRA_URIS, Uri.class);
        if (received != null) {
            sources = received;
        }
        regionCount = Math.max(1, Math.min(9, getIntent().getIntExtra(EXTRA_COUNT, 1)));
        for (int i = 0; i < regionCount; i++) {
            regions.add(null);
        }
        if (savedInstanceState != null) {
            ArrayList<RectF> savedRegions = savedInstanceState.getParcelableArrayList(STATE_REGIONS);
            if (savedRegions != null && savedRegions.size() == regionCount) {
                for (int i = 0; i < regionCount; i++) {
                    regions.set(i, savedRegions.get(i));
                }
            }
            currentIndex = savedInstanceState.getInt(STATE_CURRENT, 0);
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        editor = findViewById(R.id.editor);
        hintView = findViewById(R.id.hint);
        sizeBadge = findViewById(R.id.sizeBadge);
        loading = findViewById(R.id.loading);
        chipGroup = findViewById(R.id.chipGroup);
        prevButton = findViewById(R.id.prevBtn);
        nextButton = findViewById(R.id.nextBtn);

        editor.setOnCropChangeListener(this::updateSizeBadge);

        prevButton.setOnClickListener(v -> {
            if (currentIndex > 0) {
                goToRegion(currentIndex - 1);
            }
        });
        nextButton.setOnClickListener(v -> {
            if (currentIndex < regionCount - 1) {
                goToRegion(currentIndex + 1);
            } else {
                startBatchFlow();
            }
        });

        buildChips();
        loadPreview();
    }

    private void buildChips() {
        chipGroup.removeAllViews();
        chipGroup.setVisibility(regionCount > 1 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < regionCount; i++) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_region_chip, chipGroup, false);
            chip.setText(String.valueOf(i + 1));
            final int index = i;
            chip.setOnClickListener(v -> {
                if (!syncingChips) {
                    goToRegion(index);
                }
            });
            chipGroup.addView(chip);
            if (i == currentIndex) {
                chip.setChecked(true);
            }
        }
    }

    private void loadPreview() {
        if (sources.isEmpty()) {
            Toast.makeText(this, R.string.no_images, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        loading.setVisibility(View.VISIBLE);
        final Uri uri = sources.get(0);
        new Thread(() -> {
            Bitmap bitmap = null;
            int[] size = null;
            try {
                bitmap = ImageUtils.decodeSampled(getApplicationContext(), uri, PREVIEW_MAX_DIM);
                size = ImageUtils.readRotatedSize(getApplicationContext(), uri);
            } catch (Exception ignored) {
                // 下方统一提示
            }
            final Bitmap preview = bitmap;
            final int[] dimension = size;
            runOnUiThread(() -> {
                loading.setVisibility(View.GONE);
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (preview == null) {
                    Toast.makeText(this, R.string.decode_failed, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                sourceWidth = dimension != null ? dimension[0] : preview.getWidth();
                sourceHeight = dimension != null ? dimension[1] : preview.getHeight();
                editor.setBitmap(preview, regions.get(currentIndex));
                updateUi();
            });
        }).start();
    }

    private void goToRegion(int index) {
        if (index == currentIndex) {
            syncChipSelection();
            return;
        }
        saveCurrentRegion();
        currentIndex = index;
        editor.setNormalizedRegion(regions.get(currentIndex));
        updateUi();
    }

    private void saveCurrentRegion() {
        RectF normalized = editor.getNormalizedRegion();
        if (normalized != null) {
            regions.set(currentIndex, normalized);
        }
    }

    private void updateUi() {
        hintView.setText(getString(R.string.crop_hint, currentIndex + 1, regionCount));
        prevButton.setEnabled(currentIndex > 0);
        nextButton.setText(currentIndex == regionCount - 1 ? R.string.start_crop : R.string.next_region);
        editor.setLabel(String.valueOf(currentIndex + 1));
        syncChipSelection();
        RectF region = regions.get(currentIndex);
        if (region != null) {
            updateSizeBadge(region);
        }
    }

    private void syncChipSelection() {
        syncingChips = true;
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            View child = chipGroup.getChildAt(i);
            if (child instanceof Chip) {
                ((Chip) child).setChecked(i == currentIndex);
            }
        }
        syncingChips = false;
    }

    private void updateSizeBadge(RectF normalized) {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }
        int width = Math.round(normalized.width() * sourceWidth);
        int height = Math.round(normalized.height() * sourceHeight);
        sizeBadge.setText(getString(R.string.size_badge, width, height));
    }

    private void startBatchFlow() {
        saveCurrentRegion();
        for (int i = 0; i < regions.size(); i++) {
            if (regions.get(i) == null) {
                regions.set(i, new RectF(0.1f, 0.1f, 0.9f, 0.9f));
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
            return;
        }
        runBatch();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (editor != null) {
            saveCurrentRegion();
        }
        ArrayList<RectF> copy = new ArrayList<>();
        for (RectF region : regions) {
            copy.add(region == null ? null : new RectF(region));
        }
        outState.putParcelableArrayList(STATE_REGIONS, copy);
        outState.putInt(STATE_CURRENT, currentIndex);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                runBatch();
            } else {
                Toast.makeText(this, R.string.storage_denied, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void runBatch() {
        View content = getLayoutInflater().inflate(R.layout.dialog_progress, null);
        TextView detail = content.findViewById(R.id.progressDetail);
        detail.setText(getString(R.string.cropping_detail, 0, sources.size()));
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .setCancelable(false)
                .create();
        dialog.show();

        BatchCropper.run(getApplicationContext(), sources, regions, new BatchCropper.Listener() {
            @Override
            public void onProgress(int done, int total, String currentName) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                detail.setText(getString(R.string.cropping_detail, done, total) + "  " + currentName);
            }

            @Override
            public void onComplete(BatchCropper.Result result) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
                Intent intent = new Intent(CropActivity.this, ResultActivity.class);
                intent.putParcelableArrayListExtra(ResultActivity.EXTRA_OUTPUTS, result.outputUris);
                intent.putExtra(ResultActivity.EXTRA_GENERATED, result.generated);
                intent.putExtra(ResultActivity.EXTRA_FAILED, result.failed);
                intent.putExtra(ResultActivity.EXTRA_IMAGES, result.processedImages);
                startActivity(intent);
                finish();
            }
        });
    }
}
