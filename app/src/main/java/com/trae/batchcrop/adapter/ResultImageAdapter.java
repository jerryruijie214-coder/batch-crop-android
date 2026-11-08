package com.trae.batchcrop.adapter;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.trae.batchcrop.R;
import com.trae.batchcrop.util.ThumbnailLoader;

import java.util.ArrayList;
import java.util.List;

public class ResultImageAdapter extends RecyclerView.Adapter<ResultImageAdapter.ViewHolder> {

    private final List<Uri> items = new ArrayList<>();

    public void submit(List<Uri> uris) {
        items.clear();
        items.addAll(uris);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_result_image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ThumbnailLoader.load(holder.image.getContext(), items.get(position), 400, holder.image);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView image;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.thumb);
        }
    }
}
