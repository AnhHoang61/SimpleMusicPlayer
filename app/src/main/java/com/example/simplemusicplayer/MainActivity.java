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
import android.content.Context;
import android.content.Intent;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

    // Tab buttons & containers
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
        setupTabs();
        setupPlayerControls();
        setupRecyclerView();
        setupListeners();
        checkPermissionsAndLoadSongs();
    }

    private void initViews() {
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

        // Disc rotation animator
        discAnimator = ObjectAnimator.ofFloat(viewVinylDisc, "rotation", 0f, 360f);
        discAnimator.setDuration(12000);
        discAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        discAnimator.setInterpolator(new LinearInterpolator());
    }

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

    private void setupRecyclerView() {
        musicAdapter = new MusicAdapter(this, songList, (song, originalIndex) -> {
            playerManager.setPlaylist(songList);
            playerManager.playSongAtIndex(MainActivity.this, originalIndex);
            switchTab(true); // Jump to full player
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

        miniPlayerLayout.setOnClickListener(v -> switchTab(true));
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
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DISPLAY_NAME
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
                    int displayNameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);

                    do {
                        long id = cursor.getLong(idCol);
                        String title = cursor.getString(titleCol);
                        String artist = cursor.getString(artistCol);
                        long duration = cursor.getLong(durationCol);
                        String path = cursor.getString(dataCol);
                        long albumId = cursor.getLong(albumCol);
                        String displayName = displayNameCol != -1 ? cursor.getString(displayNameCol) : null;

                        // Tối ưu tên hiển thị: Nếu title dạng mã WhatsApp (AUD-..., PTT-...) hoặc rỗng thì lấy theo tên file
                        if (title == null || title.trim().isEmpty() || title.startsWith("AUD-") || title.startsWith("PTT-") || "<unknown>".equalsIgnoreCase(title)) {
                            if (displayName != null && !displayName.trim().isEmpty()) {
                                String cleanName = displayName.replaceFirst("[.][^.]+$", "");
                                if (!cleanName.trim().isEmpty()) {
                                    title = cleanName;
                                }
                            }
                        }

                        // Đổi tên bài hát 59 phút / file Thủy Chung thành đúng "Thủy Chung" theo yêu cầu người dùng
                        if ((displayName != null && displayName.contains("Thủy Chung"))
                                || (duration >= 3500000 && duration <= 3650000)
                                || (title != null && title.contains("AUD-20261110-WA0000"))) {
                            title = "Thủy Chung";
                        }

                        fetchedSongs.add(new Song(id, title, artist, duration, path, albumId));
                    } while (cursor.moveToNext());
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) cursor.close();
            }

            // Sắp xếp danh sách theo tên bài hát hiển thị (hỗ trợ tiếng Việt)
            try {
                java.text.Collator collator = java.text.Collator.getInstance(new Locale("vi", "VN"));
                collator.setStrength(java.text.Collator.PRIMARY);
                fetchedSongs.sort((s1, s2) -> collator.compare(s1.getTitle(), s2.getTitle()));
            } catch (Exception ignored) {}

            mainHandler.post(() -> {
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

    private void updatePlayerMetadata(Song song) {
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

    @Override
    public void onSongChanged(Song song) {
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

        List<AudioDeviceInfo> availableDevices = playerManager.getAvailableAudioDevices(this);
        AudioDeviceInfo selected = playerManager.getSelectedAudioDevice();

        // 1. Add "System Default / Auto" option
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

        // 2. Add each hardware device (Loa máy, Bluetooth, Tai nghe...)
        for (AudioDeviceInfo dev : availableDevices) {
            View item = LayoutInflater.from(this).inflate(R.layout.item_audio_device, layoutDeviceList, false);
            ImageView ivIcon = item.findViewById(R.id.ivDeviceIcon);
            TextView tvTitle = item.findViewById(R.id.tvDeviceTitle);
            TextView tvSub = item.findViewById(R.id.tvDeviceSubtitle);
            ImageView ivCheck = item.findViewById(R.id.ivCheckmark);

            ivIcon.setImageResource(MusicPlayerManager.getDeviceIcon(dev));
            tvTitle.setText(MusicPlayerManager.getDeviceTypeName(dev));

            String typeStr = "Đang kết nối";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (dev.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) typeStr = "Loa tích hợp sẵn";
                else if (dev.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) typeStr = "Tai nghe/Loa Bluetooth không dây";
                else if (dev.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET) typeStr = "Cổng cắm tai nghe 3.5mm";
                else if (dev.getType() == AudioDeviceInfo.TYPE_USB_HEADSET) typeStr = "Cổng USB-C / DAC âm thanh";
            }
            tvSub.setText(typeStr);

            boolean isCurrent = (selected != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && selected.getId() == dev.getId());
            ivCheck.setVisibility(isCurrent ? View.VISIBLE : View.GONE);

            item.setOnClickListener(v -> {
                boolean success = playerManager.setAudioOutputDevice(dev);
                updateCurrentAudioOutput();
                Toast.makeText(this, "Đã chuyển đầu ra: " + MusicPlayerManager.getDeviceTypeName(dev), Toast.LENGTH_SHORT).show();
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
            tvOutputDeviceName.setText(MusicPlayerManager.getDeviceTypeName(selected));
            ivOutputIcon.setImageResource(MusicPlayerManager.getDeviceIcon(selected));
        } else {
            List<AudioDeviceInfo> list = playerManager.getAvailableAudioDevices(this);
            AudioDeviceInfo active = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                for (AudioDeviceInfo d : list) {
                    if (d.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        d.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        d.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
                        active = d;
                        break;
                    }
                }
            }
            if (active != null) {
                tvOutputDeviceName.setText(MusicPlayerManager.getDeviceTypeName(active));
                ivOutputIcon.setImageResource(MusicPlayerManager.getDeviceIcon(active));
            } else {
                tvOutputDeviceName.setText("Loa ngoài điện thoại");
                ivOutputIcon.setImageResource(R.drawable.ic_speaker);
            }
        }
    }

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
        backgroundExecutor.shutdown();
    }
}