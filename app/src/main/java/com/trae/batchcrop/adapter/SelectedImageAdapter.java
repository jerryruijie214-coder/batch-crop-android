package com.trae.batchcrop.adapter;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.trae.batchcrop.R;
import com.trae.batchcrop.util.ThumbnailLoader;

import java.util.ArrayList;
import java.util.List;

public class SelectedImageAdapter extends RecyclerView.Adapter<SelectedImageAdapter.ViewHolder> {

    public interface OnRemoveListener {
        void onRemove(int position);
    }

    private final List<Uri> items = new ArrayList<>();
    private final OnRemoveListener removeListener;

    public SelectedImageAdapter(OnRemoveListener removeListener) {
        this.removeListener = removeListener;
    }

    public void submit(List<Uri> uris) {
        items.clear();
        items.addAll(uris);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_selected_image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Uri uri = items.get(position);
        ThumbnailLoader.load(holder.image.getContext(), uri, 400, holder.image);
        holder.remove.setOnClickListener(v -> {
            int current = holder.getBindingAdapterPosition();
            if (current != RecyclerView.NO_POSITION && removeListener != null) {
                removeListener.onRemove(current);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final ImageButton remove;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.thumb);
            remove = itemView.findViewById(R.id.removeBtn);
        }
    }
}
