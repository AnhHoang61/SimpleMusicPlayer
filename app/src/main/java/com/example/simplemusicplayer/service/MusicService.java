package com.example.simplemusicplayer.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.IntentCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;

import com.example.simplemusicplayer.MainActivity;
import com.example.simplemusicplayer.R;
import com.example.simplemusicplayer.model.Song;
import com.example.simplemusicplayer.player.MusicPlayerManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Foreground MediaBrowserServiceCompat providing background audio playback,
 * lockscreen & notification media controls, and Android Auto integration.
 */
public class MusicService extends MediaBrowserServiceCompat {

    public static final String CHANNEL_ID = "v_music_playback_channel";
    public static final int NOTIFICATION_ID = 1001;
    public static final String MEDIA_ROOT_ID = "v_music_media_root";

    public static final String ACTION_UPDATE = "ACTION_UPDATE";
    public static final String ACTION_TOGGLE = "ACTION_TOGGLE";
    public static final String ACTION_NEXT = "ACTION_NEXT";
    public static final String ACTION_PREV = "ACTION_PREV";
    public static final String EXTRA_SONG = "EXTRA_SONG";
    public static final String EXTRA_IS_PLAYING = "EXTRA_IS_PLAYING";

    private MediaSessionCompat mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        setupMediaSession();
    }

    private void setupMediaSession() {
        mediaSession = new MediaSessionCompat(this, "VMusicSession");
        setSessionToken(mediaSession.getSessionToken());

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                MusicPlayerManager.getInstance().togglePlayPause(MusicService.this);
            }

            @Override
            public void onPause() {
                MusicPlayerManager.getInstance().togglePlayPause(MusicService.this);
            }

            @Override
            public void onSkipToNext() {
                MusicPlayerManager.getInstance().playNext(MusicService.this);
            }

            @Override
            public void onSkipToPrevious() {
                MusicPlayerManager.getInstance().playPrevious(MusicService.this);
            }

            @Override
            public void onSeekTo(long pos) {
                MusicPlayerManager.getInstance().seekTo((int) pos);
            }

            @Override
            public void onPlayFromMediaId(String mediaId, Bundle extras) {
                try {
                    long id = Long.parseLong(mediaId);
                    MusicPlayerManager.getInstance().playSongById(MusicService.this, id);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        mediaSession.setActive(true);
    }

    public static void startOrUpdateService(Context context, Song song, boolean isPlaying) {
        if (song == null || context == null) return;
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(ACTION_UPDATE);
        intent.putExtra(EXTRA_SONG, song);
        intent.putExtra(EXTRA_IS_PLAYING, isPlaying);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MediaButtonReceiver.handleIntent(mediaSession, intent);

        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_TOGGLE.equals(action)) {
                MusicPlayerManager.getInstance().togglePlayPause(this);
            } else if (ACTION_NEXT.equals(action)) {
                MusicPlayerManager.getInstance().playNext(this);
            } else if (ACTION_PREV.equals(action)) {
                MusicPlayerManager.getInstance().playPrevious(this);
            } else if (ACTION_UPDATE.equals(action)) {
                Song song = IntentCompat.getSerializableExtra(intent, EXTRA_SONG, Song.class);
                boolean isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false);
                if (song != null) {
                    updateMediaSession(song, isPlaying);
                    showNotification(song, isPlaying);
                }
            }
        }
        return START_NOT_STICKY;
    }

    private void updateMediaSession(Song song, boolean isPlaying) {
        if (mediaSession == null) return;

        MediaMetadataCompat.Builder metaBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, String.valueOf(song.getId()))
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.getTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.getArtist())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "V-Music")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.getDuration());

        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_app_logo);
        if (icon != null) {
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, icon);
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, icon);
        }
        mediaSession.setMetadata(metaBuilder.build());

        long actions = PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_SEEK_TO |
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID;

        int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        long position = MusicPlayerManager.getInstance().getCurrentPosition();

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, position, 1.0f);

        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private void showNotification(Song song, boolean isPlaying) {
        int pendingFlag = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlag |= PendingIntent.FLAG_IMMUTABLE;
        }

        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, pendingFlag);

        Intent prevIntent = new Intent(this, MusicService.class).setAction(ACTION_PREV);
        PendingIntent prevPendingIntent = PendingIntent.getService(this, 1, prevIntent, pendingFlag);

        Intent toggleIntent = new Intent(this, MusicService.class).setAction(ACTION_TOGGLE);
        PendingIntent togglePendingIntent = PendingIntent.getService(this, 2, toggleIntent, pendingFlag);

        Intent nextIntent = new Intent(this, MusicService.class).setAction(ACTION_NEXT);
        PendingIntent nextPendingIntent = PendingIntent.getService(this, 3, nextIntent, pendingFlag);

        int playPauseIcon = isPlaying ? R.drawable.ic_pause : R.drawable.ic_play;
        String playPauseTitle = isPlaying ? "Tạm dừng" : "Phát";

        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_app_logo);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note)
                .setLargeIcon(largeIcon)
                .setContentTitle(song.getTitle())
                .setContentText(song.getArtist())
                .setSubText("V-Music")
                .setContentIntent(contentPendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(isPlaying)
                .addAction(R.drawable.ic_skip_previous, "Trước", prevPendingIntent)
                .addAction(playPauseIcon, playPauseTitle, togglePendingIntent)
                .addAction(R.drawable.ic_skip_next, "Sau", nextPendingIntent)
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));

        Notification notification = builder.build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Trình phát V-Music",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Điều khiển nhạc đang phát và Android Auto");
            channel.setShowBadge(false);
            channel.setSound(null, null);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return new BrowserRoot(MEDIA_ROOT_ID, null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentMediaId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
        List<Song> songs = MusicPlayerManager.getInstance().getPlaylist();

        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_app_logo);

        for (Song song : songs) {
            MediaDescriptionCompat.Builder descBuilder = new MediaDescriptionCompat.Builder()
                    .setMediaId(String.valueOf(song.getId()))
                    .setTitle(song.getTitle())
                    .setSubtitle(song.getArtist())
                    .setDescription("V-Music");

            if (icon != null) {
                descBuilder.setIconBitmap(icon);
            }

            mediaItems.add(new MediaBrowserCompat.MediaItem(descBuilder.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
        }

        result.sendResult(mediaItems);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
    }
}
