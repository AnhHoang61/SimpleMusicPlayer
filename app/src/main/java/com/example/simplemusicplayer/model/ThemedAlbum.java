package com.example.simplemusicplayer.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Model representing a custom user-created Themed Album (Album theo chủ đề).
 */
public class ThemedAlbum implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private String name;
    private String emoji;
    private final long createdAt;
    private final Set<Long> songIds = new HashSet<>();

    public ThemedAlbum(String id, String name, String emoji, long createdAt) {
        this.id = id;
        this.name = (name != null && !name.trim().isEmpty()) ? name.trim() : "Album không tên";
        this.emoji = (emoji != null && !emoji.trim().isEmpty()) ? emoji.trim() : "💽";
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            this.name = name.trim();
        }
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        if (emoji != null && !emoji.trim().isEmpty()) {
            this.emoji = emoji.trim();
        }
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Set<Long> getSongIds() {
        return Collections.unmodifiableSet(songIds);
    }

    public int getSongCount() {
        return songIds.size();
    }

    public boolean containsSong(long songId) {
        return songIds.contains(songId);
    }

    public boolean addSong(long songId) {
        return songIds.add(songId);
    }

    public boolean removeSong(long songId) {
        return songIds.remove(songId);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("name", name);
        obj.put("emoji", emoji);
        obj.put("createdAt", createdAt);

        JSONArray arr = new JSONArray();
        for (Long sId : songIds) {
            arr.put(sId);
        }
        obj.put("songIds", arr);
        return obj;
    }

    public static ThemedAlbum fromJson(JSONObject obj) {
        if (obj == null) return null;
        try {
            String id = obj.getString("id");
            String name = obj.getString("name");
            String emoji = obj.optString("emoji", "💽");
            long createdAt = obj.optLong("createdAt", System.currentTimeMillis());

            ThemedAlbum album = new ThemedAlbum(id, name, emoji, createdAt);
            JSONArray arr = obj.optJSONArray("songIds");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    album.addSong(arr.getLong(i));
                }
            }
            return album;
        } catch (JSONException e) {
            return null;
        }
    }
}
