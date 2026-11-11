package com.example.simplemusicplayer;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MusicPlayerManager implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener {
    private static final String TAG = "MusicPlayerManager";

    public enum RepeatMode {
        OFF, ALL, ONE
    }

    public interface PlaybackListener {
        void onSongChanged(Song song);
        void onPlaybackStatusChanged(boolean isPlaying);
        void onProgressChanged(int currentPosition, int duration);
    }

    private static MusicPlayerManager instance;

    private Context appContext;
    private MediaPlayer mediaPlayer;
    private AudioDeviceInfo selectedAudioDevice = null;
    private final List<Song> playlist = new ArrayList<>();
    private final List<Integer> playOrder = new ArrayList<>();
    private int currentOrderIndex = -1;

    private boolean isShuffle = false;
    private RepeatMode repeatMode = RepeatMode.ALL;

    private final List<PlaybackListener> listeners = new ArrayList<>();
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                int cur = mediaPlayer.getCurrentPosition();
                int total = mediaPlayer.getDuration();
                notifyProgress(cur, total);
                progressHandler.postDelayed(this, 500);
            }
        }
    };

    private MusicPlayerManager() {}

    public static synchronized MusicPlayerManager getInstance() {
        if (instance == null) {
            instance = new MusicPlayerManager();
        }
        return instance;
    }

    public void addListener(PlaybackListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            Song currentSong = getCurrentSong();
            if (currentSong != null) {
                listener.onSongChanged(currentSong);
                listener.onPlaybackStatusChanged(isPlaying());
                if (mediaPlayer != null) {
                    listener.onProgressChanged(mediaPlayer.getCurrentPosition(), mediaPlayer.getDuration());
                }
            }
        }
    }

    public void removeListener(PlaybackListener listener) {
        listeners.remove(listener);
    }

    public void setPlaylist(List<Song> songs) {
        playlist.clear();
        if (songs != null) {
            playlist.addAll(songs);
        }
        rebuildPlayOrder();
    }

    private void rebuildPlayOrder() {
        playOrder.clear();
        for (int i = 0; i < playlist.size(); i++) {
            playOrder.add(i);
        }
        if (isShuffle) {
            Collections.shuffle(playOrder, new Random());
        }
    }

    public List<Song> getPlaylist() {
        return new ArrayList<>(playlist);
    }

    public void playSongById(Context context, long id) {
        for (int i = 0; i < playlist.size(); i++) {
            if (playlist.get(i).getId() == id) {
                playSongAtIndex(context, i);
                break;
            }
        }
    }

    public void playSongAtIndex(Context context, int index) {
        if (index < 0 || index >= playlist.size()) return;

        // Find position in playOrder
        for (int i = 0; i < playOrder.size(); i++) {
            if (playOrder.get(i) == index) {
                currentOrderIndex = i;
                break;
            }
        }
        playCurrentOrder(context);
    }

    private void playCurrentOrder(Context context) {
        if (context != null) {
            this.appContext = context.getApplicationContext();
        }
        if (currentOrderIndex < 0 || currentOrderIndex >= playOrder.size()) return;

        int songIndex = playOrder.get(currentOrderIndex);
        if (songIndex < 0 || songIndex >= playlist.size()) return;

        Song song = playlist.get(songIndex);
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.reset();
                mediaPlayer.release();
                mediaPlayer = null;
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            );

            mediaPlayer.setDataSource(context, song.getUri());
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.setOnErrorListener(this);
            mediaPlayer.prepareAsync();

            notifySongChanged(song);

            // Notify Foreground Service
            MusicService.startOrUpdateService(context, song, true);

        } catch (IOException e) {
            Log.e(TAG, "Error playing song: " + song.getTitle(), e);
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (selectedAudioDevice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mp.setPreferredDevice(selectedAudioDevice);
        }
        mp.start();
        notifyStateChanged(true);
        startProgressTracking();
    }

    public void togglePlayPause(Context context) {
        if (mediaPlayer == null) {
            if (!playlist.isEmpty()) {
                currentOrderIndex = 0;
                playCurrentOrder(context);
            }
            return;
        }

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            stopProgressTracking();
            notifyStateChanged(false);
            MusicService.startOrUpdateService(context, getCurrentSong(), false);
        } else {
            mediaPlayer.start();
            startProgressTracking();
            notifyStateChanged(true);
            MusicService.startOrUpdateService(context, getCurrentSong(), true);
        }
    }

    public void playNext(Context context) {
        if (playlist.isEmpty()) return;

        if (repeatMode == RepeatMode.ONE) {
            playCurrentOrder(context);
            return;
        }

        currentOrderIndex++;
        if (currentOrderIndex >= playOrder.size()) {
            if (repeatMode == RepeatMode.ALL) {
                currentOrderIndex = 0;
            } else {
                currentOrderIndex = playOrder.size() - 1;
                return;
            }
        }
        playCurrentOrder(context);
    }

    public void playPrevious(Context context) {
        if (playlist.isEmpty()) return;

        if (mediaPlayer != null && mediaPlayer.getCurrentPosition() > 3000) {
            mediaPlayer.seekTo(0);
            return;
        }

        currentOrderIndex--;
        if (currentOrderIndex < 0) {
            if (repeatMode == RepeatMode.ALL) {
                currentOrderIndex = playOrder.size() - 1;
            } else {
                currentOrderIndex = 0;
                return;
            }
        }
        playCurrentOrder(context);
    }

    public void seekTo(int progressMs) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(progressMs);
        }
    }

    public boolean toggleShuffle() {
        isShuffle = !isShuffle;
        Song curSong = getCurrentSong();
        rebuildPlayOrder();
        if (curSong != null) {
            for (int i = 0; i < playOrder.size(); i++) {
                if (playlist.get(playOrder.get(i)).getId() == curSong.getId()) {
                    currentOrderIndex = i;
                    break;
                }
            }
        }
        return isShuffle;
    }

    public RepeatMode cycleRepeatMode() {
        switch (repeatMode) {
            case OFF:
                repeatMode = RepeatMode.ALL;
                break;
            case ALL:
                repeatMode = RepeatMode.ONE;
                break;
            case ONE:
                repeatMode = RepeatMode.OFF;
                break;
        }
        return repeatMode;
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public boolean isShuffle() {
        return isShuffle;
    }

    public RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public Song getCurrentSong() {
        if (currentOrderIndex >= 0 && currentOrderIndex < playOrder.size()) {
            int songIdx = playOrder.get(currentOrderIndex);
            if (songIdx >= 0 && songIdx < playlist.size()) {
                return playlist.get(songIdx);
            }
        }
        return null;
    }

    public int getCurrentPosition() {
        return mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
    }

    public int getDuration() {
        return mediaPlayer != null ? mediaPlayer.getDuration() : 0;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (repeatMode == RepeatMode.ONE) {
            mp.seekTo(0);
            mp.start();
        } else {
            stopProgressTracking();
            notifyStateChanged(false);

            if (appContext != null && !playOrder.isEmpty()) {
                currentOrderIndex++;
                if (currentOrderIndex >= playOrder.size()) {
                    if (repeatMode == RepeatMode.ALL) {
                        currentOrderIndex = 0;
                    } else {
                        return;
                    }
                }
                playCurrentOrder(appContext);
            }
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
        stopProgressTracking();
        notifyStateChanged(false);
        return true;
    }

    private void startProgressTracking() {
        progressHandler.removeCallbacks(progressRunnable);
        progressHandler.post(progressRunnable);
    }

    private void stopProgressTracking() {
        progressHandler.removeCallbacks(progressRunnable);
    }

    private void notifySongChanged(Song song) {
        for (PlaybackListener listener : new ArrayList<>(listeners)) {
            listener.onSongChanged(song);
        }
    }

    private void notifyStateChanged(boolean isPlaying) {
        for (PlaybackListener listener : new ArrayList<>(listeners)) {
            listener.onPlaybackStatusChanged(isPlaying);
        }
    }

    private void notifyProgress(int cur, int total) {
        for (PlaybackListener listener : new ArrayList<>(listeners)) {
            listener.onProgressChanged(cur, total);
        }
    }

    public boolean setAudioOutputDevice(AudioDeviceInfo device) {
        this.selectedAudioDevice = device;
        if (mediaPlayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return mediaPlayer.setPreferredDevice(device);
        }
        return false;
    }

    public AudioDeviceInfo getSelectedAudioDevice() {
        return selectedAudioDevice;
    }

    public List<AudioDeviceInfo> getAvailableAudioDevices(Context context) {
        List<AudioDeviceInfo> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null) {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                for (AudioDeviceInfo d : devices) {
                    int type = d.getType();
                    if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                        type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                        type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                        type == AudioDeviceInfo.TYPE_LINE_ANALOG) {
                        list.add(d);
                    }
                }
            }
        }
        return list;
    }

    public static String getDeviceTypeName(AudioDeviceInfo device) {
        if (device == null) return "Mặc định (Loa máy)";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            CharSequence name = device.getProductName();
            if (name != null && name.length() > 0 && !name.toString().trim().equalsIgnoreCase("Speaker")) {
                return name.toString();
            }
            switch (device.getType()) {
                case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                    return "Loa ngoài điện thoại";
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                    return "Tai nghe có dây";
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                    return "Tai nghe / Loa Bluetooth";
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                    return "Thiết bị âm thanh USB-C";
                case AudioDeviceInfo.TYPE_LINE_ANALOG:
                    return "Cáp âm thanh AUX";
                default:
                    return "Thiết bị âm thanh ngoài";
            }
        }
        return "Loa thiết bị";
    }

    public static int getDeviceIcon(AudioDeviceInfo device) {
        if (device == null) return R.drawable.ic_speaker;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            switch (device.getType()) {
                case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                    return R.drawable.ic_speaker;
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                    return R.drawable.ic_headphones;
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                    return R.drawable.ic_bluetooth;
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                    return R.drawable.ic_audio_output;
                default:
                    return R.drawable.ic_audio_output;
            }
        }
        return R.drawable.ic_speaker;
    }
}
