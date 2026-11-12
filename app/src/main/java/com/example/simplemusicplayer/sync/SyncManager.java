package com.example.simplemusicplayer.sync;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.simplemusicplayer.R;
import com.example.simplemusicplayer.model.Song;
import com.example.simplemusicplayer.util.MusicLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Singleton managing Wi-Fi local network auto-discovery, song catalog diffing,
 * background downloads, and MediaStore scanning.
 */
public class SyncManager {
    private static final String TAG = "SyncManager";
    private static final String PREFS_NAME = "v_music_sync_prefs";
    private static final String KEY_AUTO_SYNC = "auto_sync_enabled";
    private static final String KEY_LAST_SERVER_IP = "last_server_ip";
    private static final String KEY_LAST_SYNC_COUNT = "last_sync_count";
    private static final String KEY_LAST_SYNC_TIME = "last_sync_time";

    private static final int UDP_DISCOVERY_PORT = 8888;
    private static final int DEFAULT_HTTP_PORT = 8899;
    private static final String NOTIFICATION_CHANNEL_ID = "v_music_sync_channel";
    private static final int NOTIFICATION_ID = 2002;

    public enum SyncStatus {
        IDLE,
        SEARCHING_SERVER,
        SERVER_CONNECTED,
        DOWNLOADING,
        COMPLETED,
        ERROR
    }

    public interface SyncListener {
        void onSyncStatusChanged(SyncStatus status, @Nullable String message);
        void onSyncProgress(int current, int total, String songName);
        void onSyncCompleted(int syncedCount);
    }

    private static volatile SyncManager instance;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<SyncListener> listeners = new CopyOnWriteArrayList<>();

    private SyncStatus currentStatus = SyncStatus.IDLE;
    private String currentStatusMessage = "Sẵn sàng đồng bộ";
    private boolean isSyncInProgress = false;
    private boolean isNetworkCallbackRegistered = false;

    private SyncManager() {}

    public static synchronized SyncManager getInstance() {
        if (instance == null) {
            instance = new SyncManager();
        }
        return instance;
    }

    public void addListener(SyncListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            listener.onSyncStatusChanged(currentStatus, currentStatusMessage);
        }
    }

    public void removeListener(SyncListener listener) {
        listeners.remove(listener);
    }

    public SyncStatus getCurrentStatus() {
        return currentStatus;
    }

    public String getCurrentStatusMessage() {
        return currentStatusMessage;
    }

    public boolean isAutoSyncEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_AUTO_SYNC, true);
    }

    public void setAutoSyncEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply();
    }

    public String getLastServerIp(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_SERVER_IP, null);
    }

    public String getLastSyncSummary(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int count = prefs.getInt(KEY_LAST_SYNC_COUNT, 0);
        long time = prefs.getLong(KEY_LAST_SYNC_TIME, 0);
        if (time == 0) return "Chưa có lượt đồng bộ nào";

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm - dd/MM", java.util.Locale.getDefault());
        return String.format(java.util.Locale.getDefault(), "Đã đồng bộ %d bài lúc %s", count, sdf.format(new java.util.Date(time)));
    }

    /**
     * Registers Wi-Fi network listener to trigger auto-sync whenever the phone joins Wi-Fi.
     */
    public void setupAutoSyncOnWifi(Context context) {
        if (isNetworkCallbackRegistered || context == null) return;
        Context appContext = context.getApplicationContext();

        try {
            ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkRequest request = new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .build();

                cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        Log.d(TAG, "Wi-Fi connected! Checking auto-sync...");
                        mainHandler.postDelayed(() -> {
                            if (isAutoSyncEnabled(appContext)) {
                                startSync(appContext, true);
                            }
                        }, 3000);
                    }
                });
                isNetworkCallbackRegistered = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to register network callback", e);
        }
    }

    /**
     * Starts synchronization (discovering PC server, diffing tracks, downloading new songs).
     *
     * @param context Application context
     * @param isAutoTriggered true if triggered by Wi-Fi join event
     */
    public void startSync(Context context, boolean isAutoTriggered) {
        if (isSyncInProgress) {
            Log.d(TAG, "Sync already in progress, skipping request.");
            return;
        }

        isSyncInProgress = true;
        Context appContext = context.getApplicationContext();
        updateStatus(SyncStatus.SEARCHING_SERVER, "Đang quét tìm máy tính trong mạng Wi-Fi...");

        executor.execute(() -> {
            WifiManager.MulticastLock multicastLock = null;
            try {
                // Acquire multicast lock for reliable UDP broadcast
                WifiManager wm = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    multicastLock = wm.createMulticastLock("vmusic_sync_lock");
                    multicastLock.setReferenceCounted(true);
                    multicastLock.acquire();
                }

                // 1. Discover PC server via UDP
                String serverIp = discoverServerIp(appContext);
                int serverPort = DEFAULT_HTTP_PORT;

                if (serverIp == null) {
                    // Try fallback to last known IP
                    String lastIp = getLastServerIp(appContext);
                    if (lastIp != null && testHttpConnection(lastIp, serverPort)) {
                        serverIp = lastIp;
                    }
                }

                if (serverIp == null) {
                    updateStatus(SyncStatus.ERROR, "Không tìm thấy máy tính trong mạng Wi-Fi");
                    isSyncInProgress = false;
                    return;
                }

                // Save successful IP
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putString(KEY_LAST_SERVER_IP, serverIp).apply();

                updateStatus(SyncStatus.SERVER_CONNECTED, "Đã kết nối với máy tính (" + serverIp + ")");

                // 2. Query PC song catalog
                List<RemoteSong> remoteSongs = fetchRemoteCatalog(serverIp, serverPort);
                if (remoteSongs.isEmpty()) {
                    updateStatus(SyncStatus.COMPLETED, "Máy tính chưa có bài hát nào để đồng bộ");
                    isSyncInProgress = false;
                    return;
                }

                // 3. Diff against local songs
                List<Song> localSongs = MusicLoader.querySongs(appContext);
                Set<String> localSongKeys = new HashSet<>();
                Set<String> localSongFilenames = new HashSet<>();
                for (Song s : localSongs) {
                    localSongKeys.add(normalizeSongKey(s.getTitle()));
                    if (s.getPath() != null) {
                        localSongFilenames.add(new File(s.getPath()).getName().toLowerCase());
                    }
                }

                List<RemoteSong> songsToDownload = new ArrayList<>();
                for (RemoteSong rs : remoteSongs) {
                    boolean filenameExists = localSongFilenames.contains(rs.filename.toLowerCase());
                    boolean titleExists = localSongKeys.contains(normalizeSongKey(rs.title));

                    if (!filenameExists && !titleExists) {
                        songsToDownload.add(rs);
                    }
                }

                if (songsToDownload.isEmpty()) {
                    updateStatus(SyncStatus.COMPLETED, "Toàn bộ nhạc trên máy tính đã được đồng bộ đầy đủ!");
                    isSyncInProgress = false;
                    notifyCompleted(0);
                    return;
                }

                // 4. Download missing songs
                updateStatus(SyncStatus.DOWNLOADING, "Bắt đầu tải " + songsToDownload.size() + " khúc nhạc mới...");
                int successCount = 0;
                List<String> downloadedTitles = new ArrayList<>();

                for (int i = 0; i < songsToDownload.size(); i++) {
                    RemoteSong song = songsToDownload.get(i);
                    final int currentIndex = i + 1;
                    final int total = songsToDownload.size();

                    notifyProgress(currentIndex, total, song.title);

                    boolean downloaded = downloadSongToDevice(appContext, serverIp, serverPort, song);
                    if (downloaded) {
                        successCount++;
                        downloadedTitles.add(song.title);
                    }
                }

                // 5. Report sync to PC & Send Android Notification
                sendSyncReport(serverIp, serverPort, successCount, downloadedTitles);
                showSyncNotification(appContext, successCount);

                // Update prefs
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putInt(KEY_LAST_SYNC_COUNT, successCount)
                        .putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
                        .apply();

                updateStatus(SyncStatus.COMPLETED, "Đã đồng bộ thành công " + successCount + " khúc nhạc mới!");
                notifyCompleted(successCount);

            } catch (Exception e) {
                Log.e(TAG, "Sync failed with exception", e);
                updateStatus(SyncStatus.ERROR, "Lỗi đồng bộ: " + e.getMessage());
            } finally {
                if (multicastLock != null && multicastLock.isHeld()) {
                    try { multicastLock.release(); } catch (Exception ignored) {}
                }
                isSyncInProgress = false;
            }
        });
    }

    @Nullable
    private String discoverServerIp(Context context) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setSoTimeout(2500);

            byte[] pingData = "VMUSIC_DISCOVERY_PING".getBytes(StandardCharsets.UTF_8);
            DatagramPacket pingPacket = new DatagramPacket(
                    pingData, pingData.length,
                    InetAddress.getByName("255.255.255.255"), UDP_DISCOVERY_PORT
            );

            socket.send(pingPacket);

            byte[] recvBuf = new byte[1024];
            DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);

            socket.receive(recvPacket);
            String response = new String(recvPacket.getData(), 0, recvPacket.getLength());
            Log.d(TAG, "Discovery response from " + recvPacket.getAddress().getHostAddress() + ": " + response);

            if (response.contains("vmusic-sync") || response.contains("vmusic-beacon")) {
                return recvPacket.getAddress().getHostAddress();
            }
        } catch (Exception e) {
            Log.w(TAG, "UDP Discovery probe timed out or failed: " + e.getMessage());
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
        return null;
    }

    private boolean testHttpConnection(String ip, int port) {
        try {
            URL url = new URL("http://" + ip + ":" + port + "/api/status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);
            conn.setRequestMethod("GET");
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private List<RemoteSong> fetchRemoteCatalog(String ip, int port) {
        List<RemoteSong> list = new ArrayList<>();
        try {
            URL url = new URL("http://" + ip + ":" + port + "/api/songs");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject root = new JSONObject(sb.toString());
                JSONArray songs = root.optJSONArray("songs");
                if (songs != null) {
                    for (int i = 0; i < songs.length(); i++) {
                        JSONObject obj = songs.getJSONObject(i);
                        list.add(new RemoteSong(
                                obj.optString("id"),
                                obj.optString("filename"),
                                obj.optString("title"),
                                obj.optLong("size")
                        ));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch songs catalog from PC", e);
        }
        return list;
    }

    private boolean downloadSongToDevice(Context context, String ip, int port, RemoteSong song) {
        String filename = song.filename;
        String encoded;
        try {
            encoded = URLEncoder.encode(filename, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            encoded = filename;
        }

        HttpURLConnection conn = null;
        try {
            URL url = new URL("http://" + ip + ":" + port + "/api/download/" + encoded);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(30000);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) {
                Log.e(TAG, "Download failed for " + filename + " HTTP code: " + conn.getResponseCode());
                return false;
            }

            ContentResolver resolver = context.getContentResolver();
            String mimeType = getMimeType(filename);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Audio.Media.DISPLAY_NAME, filename);
                values.put(MediaStore.Audio.Media.TITLE, song.title);
                values.put(MediaStore.Audio.Media.MIME_TYPE, mimeType);
                values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/V-Music");
                values.put(MediaStore.Audio.Media.IS_PENDING, 1);

                Uri collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                Uri itemUri = null;
                try {
                    itemUri = resolver.insert(collection, values);
                } catch (Exception e) {
                    Log.w(TAG, "MediaStore insert exception: " + e.getMessage());
                }

                if (itemUri != null) {
                    try (InputStream in = conn.getInputStream();
                         OutputStream out = resolver.openOutputStream(itemUri)) {
                        if (out != null) {
                            byte[] buffer = new byte[16384];
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                            }
                            out.flush();

                            values.clear();
                            values.put(MediaStore.Audio.Media.IS_PENDING, 0);
                            resolver.update(itemUri, values, null, null);

                            // Immediately notify MediaScanner so system indexes tags & duration
                            try {
                                File pubFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "V-Music/" + filename);
                                if (pubFile.exists()) {
                                    MediaScannerConnection.scanFile(context, new String[]{pubFile.getAbsolutePath()}, null, null);
                                }
                            } catch (Exception ignored) {}

                            Log.d(TAG, "Successfully downloaded to MediaStore: " + filename);
                            return true;
                        }
                    } catch (Exception e) {
                        try { resolver.delete(itemUri, null, null); } catch (Exception ignored) {}
                        Log.e(TAG, "Error writing to MediaStore stream: " + e.getMessage(), e);
                    }
                }
            }

            // Fallback for pre-Q or if MediaStore insert was null
            File appMusicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
            if (appMusicDir != null) {
                if (!appMusicDir.exists()) appMusicDir.mkdirs();
                File destFile = new File(appMusicDir, filename);
                File tempFile = new File(destFile.getAbsolutePath() + ".tmp");

                try (InputStream in = conn.getInputStream();
                     OutputStream out = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[16384];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                }

                if (tempFile.length() > 0) {
                    if (destFile.exists()) destFile.delete();
                    boolean renamed = tempFile.renameTo(destFile);
                    if (renamed) {
                        MediaScannerConnection.scanFile(context, new String[]{destFile.getAbsolutePath()}, null, null);
                        Log.d(TAG, "Successfully downloaded to app music dir: " + filename);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Download error for " + filename, e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return false;
    }

    private static String getMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        return "audio/*";
    }

    private void sendSyncReport(String ip, int port, int count, List<String> downloadedTitles) {
        try {
            URL url = new URL("http://" + ip + ":" + port + "/api/sync-report");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            JSONObject report = new JSONObject();
            report.put("deviceName", Build.MANUFACTURER + " " + Build.MODEL);
            report.put("syncedCount", count);
            JSONArray arr = new JSONArray(downloadedTitles);
            report.put("downloadedSongs", arr);

            byte[] bytes = report.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
                os.flush();
            }
            conn.getResponseCode();
        } catch (Exception e) {
            Log.w(TAG, "Failed to send sync report to PC: " + e.getMessage());
        }
    }

    private File getTargetMusicDir(Context context) {
        File publicMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        File vMusicDir = new File(publicMusic, "V-Music");
        if (!vMusicDir.exists()) {
            boolean created = vMusicDir.mkdirs();
            if (!created) {
                return context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
            }
        }
        return vMusicDir;
    }

    private String normalizeSongKey(String str) {
        if (str == null) return "";
        return str.toLowerCase().replaceAll("[^a-zA-Z0-9\\u00C0-\\u1EF9]", "");
    }

    private void showSyncNotification(Context context, int count) {
        if (count <= 0) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "V-Music Đồng bộ Wi-Fi",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Thông báo tự động đồng bộ nhạc từ máy tính");
            nm.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_wifi_sync)
                .setContentTitle("Đồng bộ nhạc hoàn tất")
                .setContentText(String.format(java.util.Locale.getDefault(), "Đã tự động tải %d khúc nhạc mới từ máy tính!", count))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        nm.notify(NOTIFICATION_ID, builder.build());
    }

    private void updateStatus(SyncStatus status, String message) {
        this.currentStatus = status;
        this.currentStatusMessage = message;
        mainHandler.post(() -> {
            for (SyncListener l : listeners) {
                l.onSyncStatusChanged(status, message);
            }
        });
    }

    private void notifyProgress(int cur, int total, String songName) {
        mainHandler.post(() -> {
            for (SyncListener l : listeners) {
                l.onSyncProgress(cur, total, songName);
            }
        });
    }

    private void notifyCompleted(int count) {
        mainHandler.post(() -> {
            for (SyncListener l : listeners) {
                l.onSyncCompleted(count);
            }
        });
    }

    private static class RemoteSong {
        final String id;
        final String filename;
        final String title;
        final long size;

        RemoteSong(String id, String filename, String title, long size) {
            this.id = id;
            this.filename = filename;
            this.title = title;
            this.size = size;
        }
    }
}
