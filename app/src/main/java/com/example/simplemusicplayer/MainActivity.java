package com.example.simplemusicplayer;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.simplemusicplayer.adapter.MusicAdapter;
import com.example.simplemusicplayer.model.Song;
import com.example.simplemusicplayer.player.MusicPlayerManager;
import com.example.simplemusicplayer.sync.SyncManager;
import com.example.simplemusicplayer.util.AudioDeviceHelper;
import com.example.simplemusicplayer.util.MusicLoader;
import com.example.simplemusicplayer.util.TimeUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Main activity of V-Music ("Bushi no Hibiki" Samurai Edition).
 * Handles user interface navigation, full player controls, song library, and audio output routing.
 */
public class MainActivity extends AppCompatActivity implements MusicPlayerManager.PlaybackListener {

    // Tab buttons & layout containers
    private ImageView btnOpenSync;
    private TextView btnTabPlayer, btnTabLibrary;
    private ScrollView layoutPlayerView;
    private LinearLayout layoutLibraryView;

    // Full Samurai Player components (Main Screen)
    private ImageView viewVinylDisc;
    private ObjectAnimator discAnimator;
    private TextView tvPlayerBadge;
    private TextView tvSheetTitle, tvSheetArtist, tvCurrentTime, tvTotalTime;
    private SeekBar seekBarPlayer;
    private ImageView btnPlayPauseLarge, btnPrevious, btnNext, btnShuffle, btnRepeat;
    private boolean isUserSeeking = false;

    // Audio Output Routing components
    private LinearLayout btnAudioOutput;
    private ImageView ivOutputIcon;
    private TextView tvOutputDeviceName;
    private AudioDeviceCallback audioDeviceCallback;

    // Song Library components
    private RecyclerView rvSongs;
    private MusicAdapter musicAdapter;
    private final List<Song> songList = new ArrayList<>();
    private TextView tvSongCount;
    private ImageView btnRefresh;
    private EditText etSearch;

    private LinearLayout layoutEmptyState;
    private TextView tvEmptyTitle, tvEmptyMessage;
    private MaterialButton btnGrantPermission;

    // Mini Player (Visible when browsing Library)
    private LinearLayout miniPlayerLayout;
    private TextView tvMiniTitle, tvMiniArtist;
    private ImageView btnMiniPlayPause, btnMiniNext;

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
        setupTabs();
        setupPlayerControls();
        setupRecyclerView();
        setupSearchAndActions();
        setupSyncFeature();
        checkPermissionsAndLoadSongs();
    }

    // ==========================================
    // View Initialization
    // ==========================================

    private void initViews() {
        btnOpenSync = findViewById(R.id.btnOpenSync);
        btnTabPlayer = findViewById(R.id.btnTabPlayer);
        btnTabLibrary = findViewById(R.id.btnTabLibrary);
        layoutPlayerView = findViewById(R.id.layoutPlayerView);
        layoutLibraryView = findViewById(R.id.layoutLibraryView);

        // Player elements
        viewVinylDisc = findViewById(R.id.viewVinylDisc);
        tvPlayerBadge = findViewById(R.id.tvPlayerBadge);
        tvSheetTitle = findViewById(R.id.tvSheetTitle);
        tvSheetArtist = findViewById(R.id.tvSheetArtist);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        seekBarPlayer = findViewById(R.id.seekBarPlayer);
        btnPlayPauseLarge = findViewById(R.id.btnPlayPauseLarge);
        btnPrevious = findViewById(R.id.btnPrevious);
        btnNext = findViewById(R.id.btnNext);
        btnShuffle = findViewById(R.id.btnShuffle);
        btnRepeat = findViewById(R.id.btnRepeat);

        btnAudioOutput = findViewById(R.id.btnAudioOutput);
        ivOutputIcon = findViewById(R.id.ivOutputIcon);
        tvOutputDeviceName = findViewById(R.id.tvOutputDeviceName);

        // Library elements
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

        // Disc rotation animation
        discAnimator = ObjectAnimator.ofFloat(viewVinylDisc, "rotation", 0f, 360f);
        discAnimator.setDuration(12000);
        discAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        discAnimator.setInterpolator(new LinearInterpolator());
    }

    // ==========================================
    // Tab Navigation
    // ==========================================

    private void setupTabs() {
        btnTabPlayer.setOnClickListener(v -> switchTab(true));
        btnTabLibrary.setOnClickListener(v -> switchTab(false));
    }

    private void switchTab(boolean showPlayer) {
        if (showPlayer) {
            layoutPlayerView.setVisibility(View.VISIBLE);
            layoutLibraryView.setVisibility(View.GONE);
            btnTabPlayer.setBackgroundResource(R.drawable.bg_tab_active);
            btnTabPlayer.setTextColor(ContextCompat.getColor(this, R.color.white));
            btnTabLibrary.setBackgroundResource(R.drawable.bg_tab_inactive);
            btnTabLibrary.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        } else {
            layoutPlayerView.setVisibility(View.GONE);
            layoutLibraryView.setVisibility(View.VISIBLE);
            btnTabLibrary.setBackgroundResource(R.drawable.bg_tab_active);
            btnTabLibrary.setTextColor(ContextCompat.getColor(this, R.color.white));
            btnTabPlayer.setBackgroundResource(R.drawable.bg_tab_inactive);
            btnTabPlayer.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    // ==========================================
    // Player Controls & SeekBar
    // ==========================================

    private void setupPlayerControls() {
        btnPlayPauseLarge.setOnClickListener(v -> playerManager.togglePlayPause(MainActivity.this));
        btnPrevious.setOnClickListener(v -> playerManager.playPrevious(MainActivity.this));
        btnNext.setOnClickListener(v -> playerManager.playNext(MainActivity.this));

        btnShuffle.setOnClickListener(v -> {
            boolean active = playerManager.toggleShuffle();
            updateShuffleButton(active);
            Toast.makeText(this, active ? "Đã bật phát ngẫu nhiên (乱)" : "Đã tắt phát ngẫu nhiên", Toast.LENGTH_SHORT).show();
        });

        btnRepeat.setOnClickListener(v -> {
            MusicPlayerManager.RepeatMode mode = playerManager.cycleRepeatMode();
            updateRepeatButton(mode);
        });

        btnAudioOutput.setOnClickListener(v -> showAudioOutputDialog());

        seekBarPlayer.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvCurrentTime.setText(TimeUtils.formatDuration(progress));
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

    // ==========================================
    // Song Library & Search
    // ==========================================

    private void setupRecyclerView() {
        musicAdapter = new MusicAdapter(this, songList, (song, originalIndex) -> {
            playerManager.setPlaylist(songList);
            playerManager.playSongAtIndex(MainActivity.this, originalIndex);
            switchTab(true);
        });
        rvSongs.setLayoutManager(new LinearLayoutManager(this));
        rvSongs.setAdapter(musicAdapter);
    }

    private void setupSearchAndActions() {
        btnOpenSync.setOnClickListener(v -> showWifiSyncDialog());
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

        miniPlayerLayout.setOnClickListener(v -> switchTab(true));
        btnMiniPlayPause.setOnClickListener(v -> playerManager.togglePlayPause(MainActivity.this));
        btnMiniNext.setOnClickListener(v -> playerManager.playNext(MainActivity.this));
    }

    // ==========================================
    // Permissions & MediaStore Loading
    // ==========================================

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
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

        MusicLoader.loadSongsAsync(this, fetchedSongs -> {
            songList.clear();
            songList.addAll(fetchedSongs);
            musicAdapter.updateData(songList);
            playerManager.setPlaylist(songList);

            tvSongCount.setText(String.format(Locale.getDefault(), "%d khúc nhạc", songList.size()));

            if (songList.isEmpty()) {
                showNoSongsView();
            } else {
                layoutEmptyState.setVisibility(View.GONE);
                rvSongs.setVisibility(View.VISIBLE);

                if (playerManager.getCurrentSong() == null && !songList.isEmpty()) {
                    updatePlayerMetadata(songList.get(0));
                }
            }
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

    // ==========================================
    // Wi-Fi Auto-Sync Feature
    // ==========================================

    private void setupSyncFeature() {
        SyncManager.getInstance().setupAutoSyncOnWifi(this);
        SyncManager.getInstance().addListener(new SyncManager.SyncListener() {
            @Override
            public void onSyncStatusChanged(SyncManager.SyncStatus status, @Nullable String message) {}

            @Override
            public void onSyncProgress(int current, int total, String songName) {}

            @Override
            public void onSyncCompleted(int syncedCount) {
                if (syncedCount > 0) {
                    mainHandler.post(() -> {
                        Toast.makeText(MainActivity.this, "Đã đồng bộ thành công " + syncedCount + " bài hát mới!", Toast.LENGTH_LONG).show();
                        loadSongs();
                    });
                }
            }
        });
    }

    private void showWifiSyncDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_wifi_sync, null);
        dialog.setContentView(view);

        TextView tvTitle = view.findViewById(R.id.tvSyncStatusTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvSyncStatusSubtitle);
        LinearLayout layoutProgress = view.findViewById(R.id.layoutSyncProgress);
        ProgressBar pbSync = view.findViewById(R.id.pbSync);
        TextView tvProgressDetail = view.findViewById(R.id.tvSyncProgressDetail);
        androidx.appcompat.widget.SwitchCompat switchAuto = view.findViewById(R.id.switchAutoSync);
        MaterialButton btnTrigger = view.findViewById(R.id.btnTriggerSync);

        tvTitle.setText(SyncManager.getInstance().getCurrentStatusMessage());
        tvSubtitle.setText(SyncManager.getInstance().getLastSyncSummary(this));
        switchAuto.setChecked(SyncManager.getInstance().isAutoSyncEnabled(this));
        switchAuto.setOnCheckedChangeListener((btn, isChecked) -> {
            SyncManager.getInstance().setAutoSyncEnabled(this, isChecked);
        });

        view.findViewById(R.id.btnSyncClose).setOnClickListener(v -> dialog.dismiss());

        SyncManager.SyncListener dialogListener = new SyncManager.SyncListener() {
            @Override
            public void onSyncStatusChanged(SyncManager.SyncStatus status, @Nullable String message) {
                tvTitle.setText(message != null ? message : "");
                if (status == SyncManager.SyncStatus.DOWNLOADING) {
                    layoutProgress.setVisibility(View.VISIBLE);
                    btnTrigger.setEnabled(false);
                } else if (status == SyncManager.SyncStatus.COMPLETED || status == SyncManager.SyncStatus.ERROR) {
                    layoutProgress.setVisibility(View.GONE);
                    btnTrigger.setEnabled(true);
                    tvSubtitle.setText(SyncManager.getInstance().getLastSyncSummary(MainActivity.this));
                }
            }

            @Override
            public void onSyncProgress(int current, int total, String songName) {
                layoutProgress.setVisibility(View.VISIBLE);
                btnTrigger.setEnabled(false);
                pbSync.setMax(total);
                pbSync.setProgress(current);
                tvProgressDetail.setText(String.format(Locale.getDefault(), "Đang tải (%d/%d): %s", current, total, songName));
            }

            @Override
            public void onSyncCompleted(int syncedCount) {
                layoutProgress.setVisibility(View.GONE);
                btnTrigger.setEnabled(true);
                tvSubtitle.setText(SyncManager.getInstance().getLastSyncSummary(MainActivity.this));
            }
        };

        SyncManager.getInstance().addListener(dialogListener);
        dialog.setOnDismissListener(d -> SyncManager.getInstance().removeListener(dialogListener));

        btnTrigger.setOnClickListener(v -> {
            SyncManager.getInstance().startSync(this, false);
        });

        dialog.show();
    }

    // ==========================================
    // Audio Output Switcher & Dialog
    // ==========================================

    private void showAudioOutputDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_audio_output, null);
        dialog.setContentView(view);

        view.findViewById(R.id.btnAudioClose).setOnClickListener(v -> dialog.dismiss());

        view.findViewById(R.id.btnOpenBluetooth).setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Không thể mở cài đặt Bluetooth", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });

        LinearLayout layoutDeviceList = view.findViewById(R.id.layoutDeviceList);
        layoutDeviceList.removeAllViews();

        List<AudioDeviceInfo> availableDevices = AudioDeviceHelper.getAvailableAudioDevices(this);
        AudioDeviceInfo selected = playerManager.getSelectedAudioDevice();

        // 1. "System Default / Auto" option
        View defaultItem = LayoutInflater.from(this).inflate(R.layout.item_audio_device, layoutDeviceList, false);
        ((ImageView) defaultItem.findViewById(R.id.ivDeviceIcon)).setImageResource(R.drawable.ic_audio_output);
        ((TextView) defaultItem.findViewById(R.id.tvDeviceTitle)).setText("Tự động theo hệ thống");
        ((TextView) defaultItem.findViewById(R.id.tvDeviceSubtitle)).setText("Theo kết nối tai nghe / loa hiện hành");
        ImageView defCheck = defaultItem.findViewById(R.id.ivCheckmark);
        defCheck.setVisibility(selected == null ? View.VISIBLE : View.GONE);
        defaultItem.setOnClickListener(v -> {
            playerManager.setAudioOutputDevice(null);
            updateCurrentAudioOutput();
            Toast.makeText(this, "Đã chọn: Tự động theo hệ thống", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        layoutDeviceList.addView(defaultItem);

        // 2. Hardware devices list
        for (AudioDeviceInfo dev : availableDevices) {
            View item = LayoutInflater.from(this).inflate(R.layout.item_audio_device, layoutDeviceList, false);
            ImageView ivIcon = item.findViewById(R.id.ivDeviceIcon);
            TextView tvTitle = item.findViewById(R.id.tvDeviceTitle);
            TextView tvSub = item.findViewById(R.id.tvDeviceSubtitle);
            ImageView ivCheck = item.findViewById(R.id.ivCheckmark);

            ivIcon.setImageResource(AudioDeviceHelper.getDeviceIcon(dev));
            tvTitle.setText(AudioDeviceHelper.getDeviceTypeName(dev));
            tvSub.setText(AudioDeviceHelper.getDeviceDescription(dev));

            boolean isCurrent = (selected != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && selected.getId() == dev.getId());
            ivCheck.setVisibility(isCurrent ? View.VISIBLE : View.GONE);

            item.setOnClickListener(v -> {
                playerManager.setAudioOutputDevice(dev);
                updateCurrentAudioOutput();
                Toast.makeText(this, "Đã chuyển đầu ra: " + AudioDeviceHelper.getDeviceTypeName(dev), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });

            layoutDeviceList.addView(item);
        }

        dialog.show();
    }

    private void updateCurrentAudioOutput() {
        if (tvOutputDeviceName == null || ivOutputIcon == null) return;
        AudioDeviceInfo selected = playerManager.getSelectedAudioDevice();
        if (selected != null) {
            tvOutputDeviceName.setText(AudioDeviceHelper.getDeviceTypeName(selected));
            ivOutputIcon.setImageResource(AudioDeviceHelper.getDeviceIcon(selected));
        } else {
            List<AudioDeviceInfo> list = AudioDeviceHelper.getAvailableAudioDevices(this);
            AudioDeviceInfo active = AudioDeviceHelper.getActiveConnectedDevice(this, list);
            if (active != null) {
                tvOutputDeviceName.setText(AudioDeviceHelper.getDeviceTypeName(active));
                ivOutputIcon.setImageResource(AudioDeviceHelper.getDeviceIcon(active));
            } else {
                tvOutputDeviceName.setText("Loa ngoài điện thoại");
                ivOutputIcon.setImageResource(R.drawable.ic_speaker);
            }
        }
    }

    // ==========================================
    // UI Player Metadata & Button Sync
    // ==========================================

    private void updatePlayerMetadata(@Nullable Song song) {
        if (song == null) return;
        tvSheetTitle.setText(song.getTitle());
        tvSheetArtist.setText(String.format("⚔️ %s", song.getArtist()));
        tvTotalTime.setText(song.getFormattedDuration());
        seekBarPlayer.setMax((int) song.getDuration());

        tvMiniTitle.setText(song.getTitle());
        tvMiniArtist.setText(song.getArtist());
    }

    private void updatePlayPauseIcons(boolean isPlaying) {
        int icon = isPlaying ? R.drawable.ic_pause : R.drawable.ic_play;
        btnPlayPauseLarge.setImageResource(icon);
        btnMiniPlayPause.setImageResource(icon);

        if (isPlaying) {
            tvPlayerBadge.setText("ĐANG PHÁT NHẠC");
            if (discAnimator.isPaused()) {
                discAnimator.resume();
            } else if (!discAnimator.isRunning()) {
                discAnimator.start();
            }
        } else {
            tvPlayerBadge.setText("TẠM DỪNG");
            if (discAnimator.isRunning()) {
                discAnimator.pause();
            }
        }
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
                btnRepeat.setColorFilter(ContextCompat.getColor(this, R.color.accent_gold));
                break;
        }
    }

    // ==========================================
    // PlaybackListener Callbacks
    // ==========================================

    @Override
    public void onSongChanged(@Nullable Song song) {
        if (song == null) return;
        updatePlayerMetadata(song);
        miniPlayerLayout.setVisibility(View.VISIBLE);
        musicAdapter.setCurrentPlayingSongId(song.getId());
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
            tvCurrentTime.setText(TimeUtils.formatDuration(currentPosition));
            tvTotalTime.setText(TimeUtils.formatDuration(duration));
        }
    }

    // ==========================================
    // Activity Lifecycle
    // ==========================================

    @Override
    protected void onStart() {
        super.onStart();
        playerManager.addListener(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                audioDeviceCallback = new AudioDeviceCallback() {
                    @Override
                    public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                        runOnUiThread(() -> updateCurrentAudioOutput());
                    }

                    @Override
                    public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                        runOnUiThread(() -> updateCurrentAudioOutput());
                    }
                };
                am.registerAudioDeviceCallback(audioDeviceCallback, mainHandler);
            }
        }
        updateCurrentAudioOutput();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (SyncManager.getInstance().isAutoSyncEnabled(this)) {
            SyncManager.getInstance().startSync(this, true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        playerManager.removeListener(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.unregisterAudioDeviceCallback(audioDeviceCallback);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (discAnimator != null) {
            discAnimator.cancel();
        }
    }
}
