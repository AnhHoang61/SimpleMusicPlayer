package com.example.simplemusicplayer;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MusicAdapter extends RecyclerView.Adapter<MusicAdapter.SongViewHolder> implements Filterable {

    public interface OnSongClickListener {
        void onSongClick(Song song, int originalIndex);
    }

    private final Context context;
    private final List<Song> fullSongList;
    private final List<Song> displayedSongList;
    private final OnSongClickListener clickListener;
    private long currentPlayingSongId = -1;

    public MusicAdapter(Context context, List<Song> songs, OnSongClickListener listener) {
        this.context = context;
        this.fullSongList = new ArrayList<>(songs);
        this.displayedSongList = new ArrayList<>(songs);
        this.clickListener = listener;
    }

    public void updateData(List<Song> songs) {
        this.fullSongList.clear();
        this.fullSongList.addAll(songs);
        this.displayedSongList.clear();
        this.displayedSongList.addAll(songs);
        notifyDataSetChanged();
    }

    public void setCurrentPlayingSongId(long songId) {
        this.currentPlayingSongId = songId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song song = displayedSongList.get(position);
        holder.tvTitle.setText(song.getTitle());
        holder.tvArtist.setText(song.getArtist());
        holder.tvDuration.setText(song.getFormattedDuration());

        boolean isSelected = (song.getId() == currentPlayingSongId);
        if (isSelected) {
            holder.tvTitle.setTextColor(ContextCompat.getColor(context, R.color.primary_accent));
            holder.ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.accent_play));
        } else {
            holder.tvTitle.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            holder.ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.primary_accent));
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                int origIdx = fullSongList.indexOf(song);
                clickListener.onSongClick(song, origIdx >= 0 ? origIdx : position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return displayedSongList.size();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                List<Song> filtered = new ArrayList<>();
                if (constraint == null || constraint.length() == 0) {
                    filtered.addAll(fullSongList);
                } else {
                    String query = constraint.toString().toLowerCase(Locale.getDefault()).trim();
                    for (Song song : fullSongList) {
                        if (song.getTitle().toLowerCase(Locale.getDefault()).contains(query)
                                || song.getArtist().toLowerCase(Locale.getDefault()).contains(query)) {
                            filtered.add(song);
                        }
                    }
                }
                FilterResults results = new FilterResults();
                results.values = filtered;
                results.count = filtered.size();
                return results;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                displayedSongList.clear();
                if (results.values != null) {
                    displayedSongList.addAll((List<Song>) results.values);
                }
                notifyDataSetChanged();
            }
        };
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvArtist, tvDuration;
        ImageView ivIcon;

        SongViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvSongTitle);
            tvArtist = itemView.findViewById(R.id.tvSongArtist);
            tvDuration = itemView.findViewById(R.id.tvSongDuration);
            ivIcon = itemView.findViewById(R.id.ivSongIcon);
        }
    }
}
