package com.trae.batchcrop;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.IntentCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.trae.batchcrop.adapter.ResultImageAdapter;
import com.trae.batchcrop.util.GridSpacingItemDecoration;
import com.trae.batchcrop.util.UiUtils;

import java.util.ArrayList;
import java.util.List;

public class ResultActivity extends AppCompatActivity {

    public static final String EXTRA_OUTPUTS = "extra_outputs";
    public static final String EXTRA_GENERATED = "extra_generated";
    public static final String EXTRA_FAILED = "extra_failed";
    public static final String EXTRA_IMAGES = "extra_images";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiUtils.setupEdgeToEdge(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        UiUtils.applySystemBarInsets(findViewById(R.id.root));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        ArrayList<Uri> outputs = IntentCompat.getParcelableArrayListExtra(getIntent(), EXTRA_OUTPUTS, Uri.class);
        if (outputs == null) {
            outputs = new ArrayList<>();
        }
        int generated = getIntent().getIntExtra(EXTRA_GENERATED, outputs.size());
        int failed = getIntent().getIntExtra(EXTRA_FAILED, 0);
        int images = getIntent().getIntExtra(EXTRA_IMAGES, 0);

        TextView summary = findViewById(R.id.summary);
        TextView detail = findViewById(R.id.detail);
        summary.setText(getString(R.string.result_summary, generated));
        detail.setText(getString(R.string.result_detail, images, failed));

        RecyclerView recyclerView = findViewById(R.id.resultRecycler);
        ResultImageAdapter adapter = new ResultImageAdapter();
        int spacing = Math.round(8 * getResources().getDisplayMetrics().density);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        recyclerView.addItemDecoration(new GridSpacingItemDecoration(3, spacing));
        recyclerView.setAdapter(adapter);
        adapter.submit(outputs);

        List<Uri> finalOutputs = outputs;
        recyclerView.setVisibility(finalOutputs.isEmpty() ? View.GONE : View.VISIBLE);

        MaterialButton doneButton = findViewById(R.id.doneBtn);
        doneButton.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        UiUtils.setLightSystemBars(this, !UiUtils.isNightMode(this));
    }
}
