package com.trae.batchcrop;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.trae.batchcrop.adapter.SelectedImageAdapter;
import com.trae.batchcrop.util.GridSpacingItemDecoration;
import com.trae.batchcrop.util.UiUtils;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 9;
    private static final String STATE_URIS = "state_uris";
    private static final String STATE_COUNT = "state_count";

    private final ArrayList<Uri> selectedUris = new ArrayList<>();

    private SelectedImageAdapter adapter;
    private RecyclerView recyclerView;
    private TextView countBadge;
    private View emptyHint;
    private TextView countValue;
    private TextView clearButton;
    private MaterialButton minusButton;
    private MaterialButton plusButton;
    private MaterialButton nextButton;

    private int cropCount = 1;
    private ActivityResultLauncher<PickVisualMediaRequest> pickLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiUtils.setupEdgeToEdge(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        UiUtils.applySystemBarInsets(findViewById(R.id.root));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.app_name);

        countBadge = findViewById(R.id.countBadge);
        emptyHint = findViewById(R.id.emptyHint);
        countValue = findViewById(R.id.countValue);
        clearButton = findViewById(R.id.clearBtn);
        minusButton = findViewById(R.id.minusBtn);
        plusButton = findViewById(R.id.plusBtn);
        nextButton = findViewById(R.id.nextBtn);
        recyclerView = findViewById(R.id.imageRecycler);

        adapter = new SelectedImageAdapter(position -> {
            if (position >= 0 && position < selectedUris.size()) {
                selectedUris.remove(position);
                refreshSelection();
            }
        });
        int spacing = Math.round(8 * getResources().getDisplayMetrics().density);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        recyclerView.addItemDecoration(new GridSpacingItemDecoration(3, spacing));
        recyclerView.setAdapter(adapter);

        pickLauncher = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(), uris -> {
                    if (uris == null) {
                        return;
                    }
                    for (Uri uri : uris) {
                        if (uri != null && !selectedUris.contains(uri)) {
                            selectedUris.add(uri);
                        }
                    }
                    refreshSelection();
                });

        findViewById(R.id.pickBtn).setOnClickListener(v -> pickLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));

        clearButton.setOnClickListener(v -> {
            selectedUris.clear();
            refreshSelection();
        });

        minusButton.setOnClickListener(v -> updateCount(cropCount - 1));
        plusButton.setOnClickListener(v -> updateCount(cropCount + 1));

        nextButton.setOnClickListener(v -> {
            if (selectedUris.isEmpty()) {
                Toast.makeText(this, R.string.no_images, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, CropActivity.class);
            intent.putParcelableArrayListExtra(CropActivity.EXTRA_URIS, selectedUris);
            intent.putExtra(CropActivity.EXTRA_COUNT, cropCount);
            startActivity(intent);
        });

        if (savedInstanceState != null) {
            ArrayList<Uri> restored = savedInstanceState.getParcelableArrayList(STATE_URIS);
            if (restored != null) {
                selectedUris.addAll(restored);
            }
            cropCount = savedInstanceState.getInt(STATE_COUNT, 1);
        }

        refreshSelection();
        updateCount(cropCount);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(STATE_URIS, selectedUris);
        outState.putInt(STATE_COUNT, cropCount);
    }

    @Override
    protected void onResume() {
        super.onResume();
        UiUtils.setLightSystemBars(this, !UiUtils.isNightMode(this));
    }

    private void updateCount(int value) {
        cropCount = Math.max(MIN_COUNT, Math.min(MAX_COUNT, value));
        countValue.setText(String.valueOf(cropCount));
        minusButton.setEnabled(cropCount > MIN_COUNT);
        plusButton.setEnabled(cropCount < MAX_COUNT);
    }

    private void refreshSelection() {
        adapter.submit(selectedUris);
        int count = selectedUris.size();
        countBadge.setText(getString(R.string.image_count, count));
        boolean empty = count == 0;
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyHint.setVisibility(empty ? View.VISIBLE : View.GONE);
        clearButton.setVisibility(empty ? View.GONE : View.VISIBLE);
        nextButton.setEnabled(!empty);
    }
}
