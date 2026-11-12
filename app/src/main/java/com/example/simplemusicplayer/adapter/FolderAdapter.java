package com.example.simplemusicplayer.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.simplemusicplayer.R;
import com.example.simplemusicplayer.manager.ThemedAlbumManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying Device Storage Folders grouping audio files.
 */
public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.FolderViewHolder> {

    public interface OnFolderClickListener {
        void onFolderClick(ThemedAlbumManager.FolderGroup group);
        void onPlayFolder(ThemedAlbumManager.FolderGroup group);
    }

    private final Context context;
    private final List<ThemedAlbumManager.FolderGroup> folderList = new ArrayList<>();
    private final OnFolderClickListener listener;

    public FolderAdapter(Context context, List<ThemedAlbumManager.FolderGroup> groups, OnFolderClickListener listener) {
        this.context = context;
        if (groups != null) {
            this.folderList.addAll(groups);
        }
        this.listener = listener;
    }

    public void updateData(List<ThemedAlbumManager.FolderGroup> groups) {
        this.folderList.clear();
        if (groups != null) {
            this.folderList.addAll(groups);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_folder, parent, false);
        return new FolderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
        ThemedAlbumManager.FolderGroup group = folderList.get(position);
        holder.tvName.setText(group.folderName);
        holder.tvDetail.setText(String.format(Locale.getDefault(), "%s · %d khúc nhạc", group.folderPath, group.getSongCount()));

        holder.itemView.setOnClickListener(v -> {
            v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(70).withEndAction(() -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                if (listener != null) listener.onFolderClick(group);
            }).start();
        });

        holder.btnPlay.setOnClickListener(v -> {
            v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(70).withEndAction(() -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                if (listener != null) listener.onPlayFolder(group);
            }).start();
        });
    }

    @Override
    public int getItemCount() {
        return folderList.size();
    }

    static class FolderViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvDetail;
        final ImageView btnPlay;

        FolderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvFolderName);
            tvDetail = itemView.findViewById(R.id.tvFolderDetail);
            btnPlay = itemView.findViewById(R.id.btnPlayFolder);
        }
    }
}
