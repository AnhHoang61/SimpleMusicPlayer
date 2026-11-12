package com.example.simplemusicplayer.manager;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.simplemusicplayer.model.Song;
import com.example.simplemusicplayer.model.ThemedAlbum;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Singleton repository managing custom user Themed Albums, song title overrides,
 * favorite bookmarks, and folder groupings.
 */
public class ThemedAlbumManager {
    private static final String TAG = "ThemedAlbumManager";
    private static final String PREF_NAME = "v_music_theme_albums";
    private static final String KEY_ALBUMS = "albums_json";
    private static final String KEY_FAVORITES = "favorite_song_ids";
    private static final String PREF_TITLES = "v_music_custom_titles";

    private static volatile ThemedAlbumManager instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final SharedPreferences titlePrefs;

    private final List<ThemedAlbum> albums = new ArrayList<>();
    private final Set<Long> favoriteSongIds = new HashSet<>();

    private ThemedAlbumManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.titlePrefs = appContext.getSharedPreferences(PREF_TITLES, Context.MODE_PRIVATE);
        loadAlbums();
        loadFavorites();
    }

    public static synchronized ThemedAlbumManager getInstance(Context context) {
        if (instance == null) {
            instance = new ThemedAlbumManager(context);
        }
        return instance;
    }

    // ==========================================
    // Albums Management
    // ==========================================

    private void loadAlbums() {
        albums.clear();
        String json = prefs.getString(KEY_ALBUMS, null);
        if (json != null && !json.trim().isEmpty()) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    ThemedAlbum a = ThemedAlbum.fromJson(arr.getJSONObject(i));
                    if (a != null) {
                        albums.add(a);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse albums JSON", e);
            }
        }

        // Seed default albums on first launch
        if (albums.isEmpty()) {
            albums.add(new ThemedAlbum("default_samurai", "Samurai Kiếm Đạo", "⚔️", System.currentTimeMillis()));
            albums.add(new ThemedAlbum("default_chill", "Nhạc Đêm Khuya", "🌙", System.currentTimeMillis()));
            albums.add(new ThemedAlbum("default_energy", "Động Lực & Luyện Tập", "🔥", System.currentTimeMillis()));
            saveAlbums();
        }
    }

    private synchronized void saveAlbums() {
        try {
            JSONArray arr = new JSONArray();
            for (ThemedAlbum a : albums) {
                arr.put(a.toJson());
            }
            prefs.edit().putString(KEY_ALBUMS, arr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save albums JSON", e);
        }
    }

    public synchronized List<ThemedAlbum> getAlbums() {
        return new ArrayList<>(albums);
    }

    @Nullable
    public synchronized ThemedAlbum getAlbumById(String id) {
        for (ThemedAlbum a : albums) {
            if (a.getId().equals(id)) return a;
        }
        return null;
    }

    public synchronized ThemedAlbum createAlbum(String name, String emoji) {
        String id = "album_" + System.currentTimeMillis();
        ThemedAlbum album = new ThemedAlbum(id, name, emoji, System.currentTimeMillis());
        albums.add(album);
        saveAlbums();
        return album;
    }

    public synchronized boolean renameAlbum(String albumId, String newName) {
        ThemedAlbum a = getAlbumById(albumId);
        if (a != null) {
            a.setName(newName);
            saveAlbums();
            return true;
        }
        return false;
    }

    public synchronized boolean deleteAlbum(String albumId) {
        ThemedAlbum a = getAlbumById(albumId);
        if (a != null) {
            albums.remove(a);
            saveAlbums();
            return true;
        }
        return false;
    }

    public synchronized boolean addSongToAlbum(String albumId, long songId) {
        ThemedAlbum a = getAlbumById(albumId);
        if (a != null) {
            boolean added = a.addSong(songId);
            if (added) {
                saveAlbums();
            }
            return added;
        }
        return false;
    }

    public synchronized boolean removeSongFromAlbum(String albumId, long songId) {
        ThemedAlbum a = getAlbumById(albumId);
        if (a != null) {
            boolean removed = a.removeSong(songId);
            if (removed) {
                saveAlbums();
            }
            return removed;
        }
        return false;
    }

    public synchronized List<Song> getSongsInAlbum(String albumId, List<Song> allSongs) {
        ThemedAlbum a = getAlbumById(albumId);
        List<Song> result = new ArrayList<>();
        if (a == null || allSongs == null) return result;

        for (Song s : allSongs) {
            if (a.containsSong(s.getId())) {
                result.add(s);
            }
        }
        return result;
    }

    public synchronized List<ThemedAlbum> getAlbumsContainingSong(long songId) {
        List<ThemedAlbum> result = new ArrayList<>();
        for (ThemedAlbum a : albums) {
            if (a.containsSong(songId)) {
                result.add(a);
            }
        }
        return result;
    }

    // ==========================================
    // Favorites Management
    // ==========================================

    private void loadFavorites() {
        favoriteSongIds.clear();
        Set<String> set = prefs.getStringSet(KEY_FAVORITES, null);
        if (set != null) {
            for (String s : set) {
                try {
                    favoriteSongIds.add(Long.parseLong(s));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private synchronized void saveFavorites() {
        Set<String> set = new HashSet<>();
        for (Long id : favoriteSongIds) {
            set.add(String.valueOf(id));
        }
        prefs.edit().putStringSet(KEY_FAVORITES, set).apply();
    }

    public synchronized boolean isFavorite(long songId) {
        return favoriteSongIds.contains(songId);
    }

    public synchronized boolean toggleFavorite(long songId) {
        boolean nowFavorite;
        if (favoriteSongIds.contains(songId)) {
            favoriteSongIds.remove(songId);
            nowFavorite = false;
        } else {
            favoriteSongIds.add(songId);
            nowFavorite = true;
        }
        saveFavorites();
        return nowFavorite;
    }

    public synchronized List<Song> getFavoriteSongs(List<Song> allSongs) {
        List<Song> result = new ArrayList<>();
        if (allSongs == null) return result;
        for (Song s : allSongs) {
            if (favoriteSongIds.contains(s.getId())) {
                result.add(s);
            }
        }
        return result;
    }

    // ==========================================
    // Custom Song Titles (Persistent Override)
    // ==========================================

    public void setCustomTitle(long songId, @NonNull String newTitle) {
        titlePrefs.edit().putString("title_" + songId, newTitle.trim()).apply();
    }

    @Nullable
    public String getCustomTitle(long songId) {
        return titlePrefs.getString("title_" + songId, null);
    }

    public void applyCustomTitles(@Nullable List<Song> songs) {
        if (songs == null) return;
        for (Song s : songs) {
            String custom = getCustomTitle(s.getId());
            if (custom != null && !custom.trim().isEmpty()) {
                s.setTitle(custom);
            }
        }
    }

    // ==========================================
    // Folder Grouping
    // ==========================================

    public static class FolderGroup {
        public final String folderName;
        public final String folderPath;
        public final List<Song> songs = new ArrayList<>();

        public FolderGroup(String folderName, String folderPath) {
            this.folderName = folderName;
            this.folderPath = folderPath;
        }

        public int getSongCount() {
            return songs.size();
        }
    }

    public List<FolderGroup> groupSongsByFolder(@Nullable List<Song> songs) {
        Map<String, FolderGroup> map = new LinkedHashMap<>();
        if (songs == null) return new ArrayList<>();

        for (Song s : songs) {
            String folderName = s.getParentFolderName();
            String folderPath = s.getParentFolderPath();
            FolderGroup group = map.get(folderName);
            if (group == null) {
                group = new FolderGroup(folderName, folderPath);
                map.put(folderName, group);
            }
            group.songs.add(s);
        }

        List<FolderGroup> list = new ArrayList<>(map.values());
        list.sort((g1, g2) -> g2.getSongCount() - g1.getSongCount()); // Sort largest folder first
        return list;
    }

    // ==========================================
    // Song Deletion Support
    // ==========================================

    public boolean deleteSong(Context context, Song song) {
        if (song == null) return false;
        long sId = song.getId();

        // 1. Remove from all albums
        for (ThemedAlbum a : albums) {
            a.removeSong(sId);
        }
        saveAlbums();

        // 2. Remove from favorites
        favoriteSongIds.remove(sId);
        saveFavorites();

        // 3. Remove custom title
        titlePrefs.edit().remove("title_" + sId).apply();

        // 4. Delete file from storage if accessible
        boolean fileDeleted = false;
        if (song.getPath() != null) {
            try {
                File file = new File(song.getPath());
                if (file.exists()) {
                    fileDeleted = file.delete();
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to delete file directly: " + song.getPath(), e);
            }
        }

        // 5. Try MediaStore delete if positive id
        if (sId > 0 && context != null) {
            try {
                ContentResolver cr = context.getContentResolver();
                Uri uri = song.getUri();
                cr.delete(uri, null, null);
            } catch (Exception e) {
                Log.w(TAG, "ContentResolver delete caught: " + e.getMessage());
            }
        }

        return true;
    }
}
