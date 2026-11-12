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
import com.example.simplemusicplayer.model.ThemedAlbum;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying Themed Albums with emoji badge, song count, play and options actions.
 */
public class ThemedAlbumAdapter extends RecyclerView.Adapter<ThemedAlbumAdapter.AlbumViewHolder> {

    public interface OnAlbumActionListener {
        void onAlbumClick(ThemedAlbum album);
        void onPlayAlbum(ThemedAlbum album);
        void onAlbumOptions(ThemedAlbum album, View anchorView);
    }

    private final Context context;
    private final List<ThemedAlbum> albumList = new ArrayList<>();
    private final OnAlbumActionListener listener;

    public ThemedAlbumAdapter(Context context, List<ThemedAlbum> albums, OnAlbumActionListener listener) {
        this.context = context;
        if (albums != null) {
            this.albumList.addAll(albums);
        }
        this.listener = listener;
    }

    public void updateData(List<ThemedAlbum> albums) {
        this.albumList.clear();
        if (albums != null) {
            this.albumList.addAll(albums);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AlbumViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_themed_album, parent, false);
        return new AlbumViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlbumViewHolder holder, int position) {
        ThemedAlbum album = albumList.get(position);
        holder.tvEmoji.setText(album.getEmoji());
        holder.tvTitle.setText(album.getName());
        holder.tvSubtitle.setText(String.format(Locale.getDefault(), "%d khúc nhạc", album.getSongCount()));

        holder.itemView.setOnClickListener(v -> {
            v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(70).withEndAction(() -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                if (listener != null) listener.onAlbumClick(album);
            }).start();
        });

        holder.btnPlay.setOnClickListener(v -> {
            v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(70).withEndAction(() -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                if (listener != null) listener.onPlayAlbum(album);
            }).start();
        });

        holder.btnOptions.setOnClickListener(v -> {
            v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(70).withEndAction(() -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                if (listener != null) listener.onAlbumOptions(album, v);
            }).start();
        });
    }

    @Override
    public int getItemCount() {
        return albumList.size();
    }

    static class AlbumViewHolder extends RecyclerView.ViewHolder {
        final TextView tvEmoji;
        final TextView tvTitle;
        final TextView tvSubtitle;
        final ImageView btnPlay;
        final ImageView btnOptions;

        AlbumViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEmoji = itemView.findViewById(R.id.tvAlbumEmoji);
            tvTitle = itemView.findViewById(R.id.tvAlbumTitle);
            tvSubtitle = itemView.findViewById(R.id.tvAlbumSubtitle);
            btnPlay = itemView.findViewById(R.id.btnPlayAlbum);
            btnOptions = itemView.findViewById(R.id.btnAlbumOptions);
        }
    }
}
