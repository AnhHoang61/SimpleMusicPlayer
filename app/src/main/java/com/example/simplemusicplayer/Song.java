package com.example.simplemusicplayer;

import android.content.ContentUris;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.Serializable;
import java.util.Locale;

public class Song implements Serializable {
    private final long id;
    private final String title;
    private final String artist;
    private final long duration;
    private final String path;
    private final long albumId;

    public Song(long id, String title, String artist, long duration, String path, long albumId) {
        this.id = id;
        this.title = (title != null && !title.trim().isEmpty()) ? title : "Bài hát không tên";
        this.artist = (artist != null && !artist.trim().isEmpty() && !artist.equals("<unknown>")) ? artist : "Nghệ sĩ chưa rõ";
        this.duration = duration;
        this.path = path;
        this.albumId = albumId;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public long getDuration() { return duration; }
    public String getPath() { return path; }
    public long getAlbumId() { return albumId; }

    public Uri getUri() {
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
    }

    public String getFormattedDuration() {
        long totalSecs = duration / 1000;
        long minutes = totalSecs / 60;
        long seconds = totalSecs % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}
