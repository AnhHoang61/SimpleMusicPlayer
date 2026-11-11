package com.example.simplemusicplayer;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements MusicPlayerManager.PlaybackListener {

    private RecyclerView rvSongs;
    private MusicAdapter musicAdapter;
    private final List<Song> songList = new ArrayList<>();

    private TextView tvSongCount;
    private ImageView btnRefresh;
    private EditText etSearch;

    private LinearLayout layoutEmptyState;
    private TextView tvEmptyTitle, tvEmptyMessage;
    private MaterialButton btnGrantPermission;

    // Mini Player
    private LinearLayout miniPlayerLayout;
    private TextView tvMiniTitle, tvMiniArtist;
    private ImageView btnMiniPlayPause, btnMiniNext;

    // BottomSheet Full Player components
    private BottomSheetDialog playerBottomSheet;
    private View viewVinylDisc;
    private ObjectAnimator discAnimator;
    private TextView tvSheetTitle, tvSheetArtist, tvCurrentTime, tvTotalTime;
    private SeekBar seekBarPlayer;
    private ImageView btnSheetPlayPause, btnSheetPrevious, btnSheetNext, btnShuffle, btnRepeat;
    private boolean isUserSeeking = false;

    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MusicPlayerManager playerManager;

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean audioGranted = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Boolean audio = result.get(Manifest.permission.READ_MEDIA_AUDIO);
                    audioGranted = audio != null && audio;
                } else {
                    Boolean storage = result.get(Manifest.permission.READ_EXTERNAL_STORAGE);
                    audioGranted = storage != null && storage;
                }

                if (audioGranted) {
                    loadSongs();
                } else {
                    showPermissionDeniedView();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        playerManager = MusicPlayerManager.getInstance();

        initViews();
        setupRecyclerView();
        setupListeners();
        checkPermissionsAndLoadSongs();
    }

    private void initViews() {
        rvSongs = findViewById(R.id.rvSongs);
        tvSongCount = findViewById(R.id.tvSongCount);
        btnRefresh = findViewById(R.id.btnRefresh);
        etSearch = findViewById(R.id.etSearch);

        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        btnGrantPermission = findViewById(R.id.btnGrantPermission);

        miniPlayerLayout = findViewById(R.id.miniPlayerLayout);
        tvMiniTitle = findViewById(R.id.tvMiniTitle);
        tvMiniArtist = findViewById(R.id.tvMiniArtist);
        btnMiniPlayPause = findViewById(R.id.btnMiniPlayPause);
        btnMiniNext = findViewById(R.id.btnMiniNext);
    }

    private void setupRecyclerView() {
        musicAdapter = new MusicAdapter(this, songList, (song, originalIndex) -> {
            playerManager.setPlaylist(songList);
            playerManager.playSongAtIndex(MainActivity.this, originalIndex);
        });
        rvSongs.setLayoutManager(new LinearLayoutManager(this));
        rvSongs.setAdapter(musicAdapter);
    }

    private void setupListeners() {
        btnRefresh.setOnClickListener(v -> checkPermissionsAndLoadSongs());

        btnGrantPermission.setOnClickListener(v -> checkPermissionsAndLoadSongs());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (musicAdapter != null) {
                    musicAdapter.getFilter().filter(s);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Mini player clicks
        miniPlayerLayout.setOnClickListener(v -> showFullPlayerDialog());

        btnMiniPlayPause.setOnClickListener(v -> playerManager.togglePlayPause(MainActivity.this));

        btnMiniNext.setOnClickListener(v -> playerManager.playNext(MainActivity.this));
    }

    private void checkPermissionsAndLoadSongs() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (!permissions.isEmpty()) {
            requestPermissionsLauncher.launch(permissions.toArray(new String[0]));
        } else {
            loadSongs();
        }
    }

    private void loadSongs() {
        layoutEmptyState.setVisibility(View.GONE);
        rvSongs.setVisibility(View.VISIBLE);

        backgroundExecutor.execute(() -> {
            List<Song> fetchedSongs = new ArrayList<>();
            ContentResolver contentResolver = getContentResolver();
            Uri songUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

            String[] projection = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.ALBUM_ID
            };

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

                    do {
                        long id = cursor.getLong(idCol);
                        String title = cursor.getString(titleCol);
                        String artist = cursor.getString(artistCol);
                        long duration = cursor.getLong(durationCol);
                        String path = cursor.getString(dataCol);
                        long albumId = cursor.getLong(albumCol);

                        fetchedSongs.add(new Song(id, title, artist, duration, path, albumId));
                    } while (cursor.moveToNext());
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) cursor.close();
            }

            mainHandler.post(() -> {
                songList.clear();
                songList.addAll(fetchedSongs);
                musicAdapter.updateData(songList);
                playerManager.setPlaylist(songList);

                tvSongCount.setText(String.format(Locale.getDefault(), "%d bài hát", songList.size()));

                if (songList.isEmpty()) {
                    showNoSongsView();
                } else {
                    layoutEmptyState.setVisibility(View.GONE);
                    rvSongs.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void showPermissionDeniedView() {
        rvSongs.setVisibility(View.GONE);
        layoutEmptyState.setVisibility(View.VISIBLE);
        tvEmptyTitle.setText("Chưa được cấp quyền");
        tvEmptyMessage.setText(R.string.permission_required);
        btnGrantPermission.setVisibility(View.VISIBLE);
    }

    private void showNoSongsView() {
        rvSongs.setVisibility(View.GONE);
        layoutEmptyState.setVisibility(View.VISIBLE);
        tvEmptyTitle.setText(R.string.no_songs_found);
        tvEmptyMessage.setText("Hãy sao chép các file bài hát .mp3 vào bộ nhớ điện thoại để bắt đầu nghe nhạc.");
        btnGrantPermission.setVisibility(View.GONE);
    }

    private void showFullPlayerDialog() {
        if (playerBottomSheet == null) {
            playerBottomSheet = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
            View view = getLayoutInflater().inflate(R.layout.bottom_sheet_player, null);
            playerBottomSheet.setContentView(view);

            viewVinylDisc = view.findViewById(R.id.viewVinylDisc);
            tvSheetTitle = view.findViewById(R.id.tvSheetTitle);
            tvSheetArtist = view.findViewById(R.id.tvSheetArtist);
            tvCurrentTime = view.findViewById(R.id.tvCurrentTime);
            tvTotalTime = view.findViewById(R.id.tvTotalTime);
            seekBarPlayer = view.findViewById(R.id.seekBarPlayer);

            btnSheetPlayPause = view.findViewById(R.id.btnPlayPauseLarge);
            btnSheetPrevious = view.findViewById(R.id.btnPrevious);
            btnSheetNext = view.findViewById(R.id.btnNext);
            btnShuffle = view.findViewById(R.id.btnShuffle);
            btnRepeat = view.findViewById(R.id.btnRepeat);

            // Vinyl rotation animator
            discAnimator = ObjectAnimator.ofFloat(viewVinylDisc, "rotation", 0f, 360f);
            discAnimator.setDuration(12000);
            discAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            discAnimator.setInterpolator(new LinearInterpolator());

            view.findViewById(R.id.btnCollapse).setOnClickListener(v -> playerBottomSheet.dismiss());

            btnSheetPlayPause.setOnClickListener(v -> playerManager.togglePlayPause(this));
            btnSheetNext.setOnClickListener(v -> playerManager.playNext(this));
            btnSheetPrevious.setOnClickListener(v -> playerManager.playPrevious(this));

            btnShuffle.setOnClickListener(v -> {
                boolean active = playerManager.toggleShuffle();
                updateShuffleButton(active);
                Toast.makeText(this, active ? "Đã bật phát ngẫu nhiên" : "Đã tắt phát ngẫu nhiên", Toast.LENGTH_SHORT).show();
            });

            btnRepeat.setOnClickListener(v -> {
                MusicPlayerManager.RepeatMode mode = playerManager.cycleRepeatMode();
                updateRepeatButton(mode);
            });

            seekBarPlayer.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        tvCurrentTime.setText(formatTime(progress));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    isUserSeeking = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    isUserSeeking = false;
                    playerManager.seekTo(seekBar.getProgress());
                }
            });
        }

        updateDialogData(playerManager.getCurrentSong());
        updatePlayPauseIcons(playerManager.isPlaying());
        updateShuffleButton(playerManager.isShuffle());
        updateRepeatButton(playerManager.getRepeatMode());

        playerBottomSheet.show();
    }

    private void updateShuffleButton(boolean active) {
        if (btnShuffle != null) {
            int color = active ? ContextCompat.getColor(this, R.color.primary_accent)
                    : ContextCompat.getColor(this, R.color.text_muted);
            btnShuffle.setColorFilter(color);
        }
    }

    private void updateRepeatButton(MusicPlayerManager.RepeatMode mode) {
        if (btnRepeat == null) return;
        switch (mode) {
            case OFF:
                btnRepeat.setImageResource(R.drawable.ic_repeat);
                btnRepeat.setColorFilter(ContextCompat.getColor(this, R.color.text_muted));
                break;
            case ALL:
                btnRepeat.setImageResource(R.drawable.ic_repeat);
                btnRepeat.setColorFilter(ContextCompat.getColor(this, R.color.primary_accent));
                break;
            case ONE:
                btnRepeat.setImageResource(R.drawable.ic_repeat_one);
                btnRepeat.setColorFilter(ContextCompat.getColor(this, R.color.primary_accent));
                break;
        }
    }

    private void updateDialogData(Song song) {
        if (song != null && tvSheetTitle != null) {
            tvSheetTitle.setText(song.getTitle());
            tvSheetArtist.setText(song.getArtist());
            tvTotalTime.setText(song.getFormattedDuration());
            seekBarPlayer.setMax((int) song.getDuration());
        }
    }

    private void updatePlayPauseIcons(boolean isPlaying) {
        int icon = isPlaying ? R.drawable.ic_pause : R.drawable.ic_play;
        btnMiniPlayPause.setImageResource(icon);

        if (btnSheetPlayPause != null) {
            btnSheetPlayPause.setImageResource(icon);
        }

        if (discAnimator != null) {
            if (isPlaying) {
                if (discAnimator.isPaused()) {
                    discAnimator.resume();
                } else if (!discAnimator.isRunning()) {
                    discAnimator.start();
                }
            } else {
                if (discAnimator.isRunning()) {
                    discAnimator.pause();
                }
            }
        }
    }

    @Override
    public void onSongChanged(Song song) {
        if (song == null) return;
        miniPlayerLayout.setVisibility(View.VISIBLE);
        tvMiniTitle.setText(song.getTitle());
        tvMiniArtist.setText(song.getArtist());

        musicAdapter.setCurrentPlayingSongId(song.getId());
        updateDialogData(song);
    }

    @Override
    public void onPlaybackStatusChanged(boolean isPlaying) {
        updatePlayPauseIcons(isPlaying);
    }

    @Override
    public void onProgressChanged(int currentPosition, int duration) {
        if (!isUserSeeking && seekBarPlayer != null) {
            seekBarPlayer.setMax(duration);
            seekBarPlayer.setProgress(currentPosition);
            tvCurrentTime.setText(formatTime(currentPosition));
            tvTotalTime.setText(formatTime(duration));
        }
    }

    private static String formatTime(int millis) {
        long totalSecs = millis / 1000;
        long minutes = totalSecs / 60;
        long seconds = totalSecs % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onStart() {
        super.onStart();
        playerManager.addListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        playerManager.removeListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (discAnimator != null) {
            discAnimator.cancel();
        }
        backgroundExecutor.shutdown();
    }
}
