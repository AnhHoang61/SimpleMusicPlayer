package com.example.simplemusicplayer.model;

import android.content.ContentUris;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.NonNull;

import com.example.simplemusicplayer.util.TimeUtils;

import java.io.Serializable;
import java.util.Objects;

/**
 * Model class representing an audio track.
 */
public class Song implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long id;
    private String title;
    private final String artist;
    private final long duration;
    private final String path;
    private final long albumId;

    public Song(long id, String title, String artist, long duration, String path, long albumId) {
        this.id = id;
        this.title = (title != null && !title.trim().isEmpty()) ? title.trim() : "Bài hát không tên";
        this.artist = (artist != null && !artist.trim().isEmpty() && !artist.equalsIgnoreCase("<unknown>"))
                ? artist.trim()
                : "Nghệ sĩ chưa rõ";
        this.duration = duration;
        this.path = path;
        this.albumId = albumId;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String newTitle) {
        if (newTitle != null && !newTitle.trim().isEmpty()) {
            this.title = newTitle.trim();
        }
    }

    public String getArtist() {
        return artist;
    }

    public long getDuration() {
        return duration;
    }

    public String getPath() {
        return path;
    }

    public long getAlbumId() {
        return albumId;
    }

    public String getParentFolderName() {
        if (path == null || path.trim().isEmpty()) return "Thư mục chung";
        try {
            java.io.File file = new java.io.File(path);
            java.io.File parent = file.getParentFile();
            if (parent != null && parent.getName() != null && !parent.getName().isEmpty()) {
                return parent.getName();
            }
        } catch (Exception ignored) {}
        return "Bộ nhớ máy";
    }

    public String getParentFolderPath() {
        if (path == null || path.trim().isEmpty()) return "";
        try {
            java.io.File file = new java.io.File(path);
            java.io.File parent = file.getParentFile();
            if (parent != null) {
                return parent.getAbsolutePath();
            }
        } catch (Exception ignored) {}
        return "";
    }

    public Uri getUri() {
        if (id > 0) {
            return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
        }
        if (path != null) {
            return Uri.fromFile(new java.io.File(path));
        }
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
    }

    public String getFormattedDuration() {
        return TimeUtils.formatDuration(duration);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Song song = (Song) o;
        return id == song.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @NonNull
    @Override
    public String toString() {
        return "Song{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                ", duration=" + duration +
                '}';
    }
}
