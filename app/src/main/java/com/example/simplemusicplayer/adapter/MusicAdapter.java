package com.example.simplemusicplayer.adapter;

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
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.simplemusicplayer.R;
import com.example.simplemusicplayer.model.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for displaying the list of songs with live search filtering,
 * item selection highlighting, and 3-dot options menu for Rename, Delete, and Themed Albums.
 */
public class MusicAdapter extends RecyclerView.Adapter<MusicAdapter.SongViewHolder> implements Filterable {

    public interface OnSongClickListener {
        void onSongClick(Song song, int originalIndex);
    }

    public interface OnSongOptionsClickListener {
        void onSongOptionsClick(Song song, View anchorView);
    }

    public interface OnFilterResultsListener {
        void onFilterResults(int count, boolean isFiltering);
    }

    private final Context context;
    private final List<Song> fullSongList;
    private final List<Song> displayedSongList;
    private final OnSongClickListener clickListener;
    private OnSongOptionsClickListener optionsListener;
    private OnFilterResultsListener filterResultsListener;
    private long currentPlayingSongId = -1;

    public MusicAdapter(Context context, List<Song> songs, OnSongClickListener listener) {
        this.context = context;
        this.fullSongList = new ArrayList<>(songs != null ? songs : new ArrayList<>());
        this.displayedSongList = new ArrayList<>(this.fullSongList);
        this.clickListener = listener;
    }

    public void setOnSongOptionsClickListener(@Nullable OnSongOptionsClickListener listener) {
        this.optionsListener = listener;
    }

    public void setOnFilterResultsListener(@Nullable OnFilterResultsListener listener) {
        this.filterResultsListener = listener;
    }

    public void updateData(List<Song> songs) {
        this.fullSongList.clear();
        if (songs != null) {
            this.fullSongList.addAll(songs);
        }
        this.displayedSongList.clear();
        this.displayedSongList.addAll(this.fullSongList);
        notifyDataSetChanged();
    }

    public void updateSong(Song song) {
        if (song == null) return;
        for (int i = 0; i < fullSongList.size(); i++) {
            if (fullSongList.get(i).getId() == song.getId()) {
                fullSongList.set(i, song);
                break;
            }
        }
        for (int i = 0; i < displayedSongList.size(); i++) {
            if (displayedSongList.get(i).getId() == song.getId()) {
                displayedSongList.set(i, song);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void removeSong(Song song) {
        if (song == null) return;
        fullSongList.removeIf(s -> s.getId() == song.getId());
        displayedSongList.removeIf(s -> s.getId() == song.getId());
        notifyDataSetChanged();
    }

    public void setCurrentPlayingSongId(long songId) {
        if (this.currentPlayingSongId != songId) {
            this.currentPlayingSongId = songId;
            notifyDataSetChanged();
        }
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
            holder.ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.primary_accent));
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.surface_card_selected));
        } else {
            holder.tvTitle.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            holder.ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.accent_gold));
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }

        holder.itemView.setOnClickListener(v -> {
            v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(70).withEndAction(() -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                if (clickListener != null) {
                    int origIdx = fullSongList.indexOf(song);
                    clickListener.onSongClick(song, origIdx >= 0 ? origIdx : position);
                }
            }).start();
        });

        holder.btnOptions.setOnClickListener(v -> {
            v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(70).withEndAction(() -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                if (optionsListener != null) {
                    optionsListener.onSongOptionsClick(song, v);
                }
            }).start();
        });
    }

    @Override
    public int getItemCount() {
        return displayedSongList.size();
    }

    /**
     * Strips Vietnamese accents/diacritics and converts to lowercase for forgiving search.
     */
    public static String normalizeSearchText(String str) {
        if (str == null) return "";
        String nfd = java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD);
        String unaccented = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(nfd).replaceAll("");
        unaccented = unaccented.replace('đ', 'd').replace('Đ', 'D');
        return unaccented.toLowerCase(Locale.getDefault()).trim();
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
                    String queryRaw = constraint.toString().toLowerCase(Locale.getDefault()).trim();
                    String queryNorm = normalizeSearchText(constraint.toString());

                    for (Song song : fullSongList) {
                        String titleRaw = song.getTitle().toLowerCase(Locale.getDefault());
                        String artistRaw = song.getArtist().toLowerCase(Locale.getDefault());
                        String titleNorm = normalizeSearchText(song.getTitle());
                        String artistNorm = normalizeSearchText(song.getArtist());

                        if (titleRaw.contains(queryRaw) || artistRaw.contains(queryRaw)
                                || titleNorm.contains(queryNorm) || artistNorm.contains(queryNorm)) {
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
                if (filterResultsListener != null) {
                    filterResultsListener.onFilterResults(displayedSongList.size(), constraint != null && constraint.length() > 0);
                }
            }
        };
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvArtist;
        final TextView tvDuration;
        final ImageView ivIcon;
        final ImageView btnOptions;

        SongViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvSongTitle);
            tvArtist = itemView.findViewById(R.id.tvSongArtist);
            tvDuration = itemView.findViewById(R.id.tvSongDuration);
            ivIcon = itemView.findViewById(R.id.ivSongIcon);
            btnOptions = itemView.findViewById(R.id.btnSongOptions);
        }
    }
}
