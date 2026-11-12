package com.example.simplemusicplayer.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.simplemusicplayer.model.Song;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository helper to scan and load audio tracks from Android MediaStore asynchronously.
 */
public final class MusicLoader {
    private static final String TAG = "MusicLoader";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public interface OnSongsLoadedListener {
        void onSongsLoaded(@NonNull List<Song> songs);
    }

    private MusicLoader() {
        // Utility class, do not instantiate
    }

    /**
     * Scans device MediaStore on a background thread and posts results to main thread.
     */
    public static void loadSongsAsync(Context context, OnSongsLoadedListener listener) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();

        EXECUTOR.execute(() -> {
            List<Song> songs = querySongs(appContext);
            MAIN_HANDLER.post(() -> {
                if (listener != null) {
                    listener.onSongsLoaded(songs);
                }
            });
        });
    }

    /**
     * Synchronously queries and sorts all valid music tracks from MediaStore.
     */
    @NonNull
    public static List<Song> querySongs(Context context) {
        List<Song> songList = new ArrayList<>();
        if (context == null) return songList;

        ContentResolver contentResolver = context.getContentResolver();
        Uri songUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DISPLAY_NAME
        };

        // Filter out ringtones/short notifications (less than 5 seconds)
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND "
                + MediaStore.Audio.Media.DURATION + " >= 5000";
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

        Cursor cursor = null;
        try {
            cursor = contentResolver.query(songUri, projection, selection, null, sortOrder);
            if (cursor != null && cursor.moveToFirst()) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                int displayNameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);

                do {
                    long id = cursor.getLong(idCol);
                    String title = cursor.getString(titleCol);
                    String artist = cursor.getString(artistCol);
                    long duration = cursor.getLong(durationCol);
                    String path = cursor.getString(dataCol);
                    long albumId = cursor.getLong(albumCol);
                    String displayName = displayNameCol != -1 ? cursor.getString(displayNameCol) : null;

                    // Clean title from file display name if metadata title is a technical code (e.g. AUD-..., PTT-...)
                    if (title == null || title.trim().isEmpty() || title.startsWith("AUD-")
                            || title.startsWith("PTT-") || "<unknown>".equalsIgnoreCase(title)) {
                        if (displayName != null && !displayName.trim().isEmpty()) {
                            String cleanName = displayName.replaceFirst("[.][^.]+$", "");
                            if (!cleanName.trim().isEmpty()) {
                                title = cleanName;
                            }
                        }
                    }

                    // Remove technical bracket artifacts if title has trailing artist brackets (e.g. "Nhạc Uống Trà ... [] Tris")
                    if (title != null && title.contains("[]")) {
                        title = title.replaceAll("\\[\\s*\\][^\\[\\]]*$", "").trim();
                    }

                    // Special rule: Name specific WhatsApp voice note "Thủy Chung" (strictly by display name or WhatsApp ID)
                    if ((displayName != null && displayName.equalsIgnoreCase("Thủy Chung.mp3"))
                            || (title != null && title.contains("AUD-20261110-WA0000"))) {
                        title = "Thủy Chung";
                    }

                    songList.add(new Song(id, title, artist, duration, path, albumId));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to query MediaStore for songs", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // Supplemental check: Ensure any songs in public V-Music sync directory or private music directory are loaded
        try {
            File publicVMusicDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "V-Music");
            scanAdditionalFolder(context, publicVMusicDir, songList);

            File appMusicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
            scanAdditionalFolder(context, appMusicDir, songList);
        } catch (Exception e) {
            Log.w(TAG, "Failed to scan supplemental music dirs", e);
        }

        // Sort songs alphabetically with Vietnamese collator support
        try {
            Collator collator = Collator.getInstance(new Locale("vi", "VN"));
            collator.setStrength(Collator.PRIMARY);
            songList.sort((s1, s2) -> collator.compare(s1.getTitle(), s2.getTitle()));
        } catch (Exception e) {
            Log.w(TAG, "Collator sorting failed, fallback to standard sort", e);
            songList.sort((s1, s2) -> s1.getTitle().compareToIgnoreCase(s2.getTitle()));
        }

        return songList;
    }

    private static void scanAdditionalFolder(Context context, File dir, List<Song> songList) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (!f.isFile()) continue;
            String name = f.getName().toLowerCase();
            if (name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".wav")
                    || name.endsWith(".flac") || name.endsWith(".ogg") || name.endsWith(".aac")) {
                boolean exists = false;
                for (Song s : songList) {
                    if (s.getPath() != null && s.getPath().equalsIgnoreCase(f.getAbsolutePath())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    String cleanTitle = f.getName().replaceFirst("[.][^.]+$", "");
                    long duration = 180000;
                    String artist = "V-Music Sync";
                    try {
                        android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
                        mmr.setDataSource(f.getAbsolutePath());
                        String durStr = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                        if (durStr != null) duration = Long.parseLong(durStr);
                        String metaArtist = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST);
                        if (metaArtist != null && !metaArtist.trim().isEmpty() && !metaArtist.equalsIgnoreCase("<unknown>")) {
                            artist = metaArtist.trim();
                        }
                        String metaTitle = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE);
                        if (metaTitle != null && !metaTitle.trim().isEmpty() && !metaTitle.equalsIgnoreCase("<unknown>")) {
                            cleanTitle = metaTitle.trim();
                        }
                        mmr.release();
                    } catch (Exception ignored) {}

                    // Request MediaStore scan so Android system indexes it in background
                    try {
                        android.media.MediaScannerConnection.scanFile(context, new String[]{f.getAbsolutePath()}, null, null);
                    } catch (Exception ignored) {}

                    songList.add(new Song(-Math.abs(f.getAbsolutePath().hashCode()), cleanTitle, artist, duration, f.getAbsolutePath(), -1));
                }
            }
        }
    }
}
