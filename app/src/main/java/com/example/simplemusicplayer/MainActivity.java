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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.simplemusicplayer.view.VinylWaveVisualizerView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.simplemusicplayer.adapter.FolderAdapter;
import com.example.simplemusicplayer.adapter.MusicAdapter;
import com.example.simplemusicplayer.adapter.ThemedAlbumAdapter;
import com.example.simplemusicplayer.manager.ThemedAlbumManager;
import com.example.simplemusicplayer.model.Song;
import com.example.simplemusicplayer.model.ThemedAlbum;
import com.example.simplemusicplayer.player.MusicPlayerManager;
import com.example.simplemusicplayer.service.MusicService;
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
 * Handles user interface navigation, full player controls, song library, folder browsing,
 * themed albums, and audio output routing.
 */
public class MainActivity extends AppCompatActivity implements MusicPlayerManager.PlaybackListener {

    // Tab buttons & layout containers
    private ImageView btnOpenSync;
    private TextView btnTabPlayer, btnTabLibrary;
    private ScrollView layoutPlayerView;
    private LinearLayout layoutLibraryView;

    // Full Samurai Player components (Main Screen)
    private ImageView viewVinylDisc;
    private View viewTsubaGlow;
    private FrameLayout layoutDiscContainer;
    private VinylWaveVisualizerView viewVinylWaveVisualizer;
    private ObjectAnimator discAnimator;
    private ObjectAnimator tsubaGlowScaleXAnimator;
    private ObjectAnimator tsubaGlowScaleYAnimator;
    private ObjectAnimator tsubaGlowAlphaAnimator;
    private TextView tvPlayerBadge, tvPlayerSourceBadge;
    private TextView tvSheetTitle, tvSheetArtist, tvCurrentTime, tvTotalTime;
    private SeekBar seekBarPlayer;
    private ImageView btnPlayPauseLarge, btnPrevious, btnNext, btnShuffle, btnRepeat;
    private boolean isUserSeeking = false;

    // Player Quick Actions (Utilizes the bottom space)
    private ImageView btnPlayerFavorite, btnPlayerAddToAlbum, btnPlayerRename, btnPlayerDelete, btnPlayerQueue;

    // Audio Output Routing components
    private LinearLayout btnAudioOutput;
    private ImageView ivOutputIcon;
    private TextView tvOutputDeviceName;
    private AudioDeviceCallback audioDeviceCallback;

    // Library Sub Navigation
    private TextView btnSubTabAll, btnSubTabFolders, btnSubTabAlbums;
    private LinearLayout layoutAllSongsContainer, layoutFoldersContainer, layoutAlbumsContainer;
    private int currentSubTab = 0; // 0: All, 1: Folders, 2: Albums

    // Song Library components (Sub-tab 0)
    private RecyclerView rvSongs;
    private MusicAdapter musicAdapter;
    private final List<Song> songList = new ArrayList<>();
    private TextView tvSongCount;
    private ImageView btnRefresh;
    private ImageView btnSearchClear;
    private EditText etSearch;

    private LinearLayout layoutEmptyState;
    private TextView tvEmptyTitle, tvEmptyMessage;
    private MaterialButton btnGrantPermission;

    // Device Folders components (Sub-tab 1)
    private TextView tvFoldersCount;
    private RecyclerView rvFolders;
    private FolderAdapter folderAdapter;
    private final List<ThemedAlbumManager.FolderGroup> folderGroupList = new ArrayList<>();

    private LinearLayout layoutFolderDetailHeader;
    private ImageView btnFolderBack, btnPlayFolderAll;
    private TextView tvCurrentFolderName, tvCurrentFolderPath;
    private RecyclerView rvFolderSongs;
    private MusicAdapter folderSongsAdapter;
    private ThemedAlbumManager.FolderGroup currentViewingFolder = null;

    // Themed Albums components (Sub-tab 2)
    private View btnCreateAlbumTop;
    private RecyclerView rvAlbums;
    private ThemedAlbumAdapter themedAlbumAdapter;
    private final List<ThemedAlbum> albumList = new ArrayList<>();

    private LinearLayout layoutAlbumDetailHeader;
    private ImageView btnAlbumBack, btnAddSongsToAlbum, btnPlayAlbumAll;
    private TextView tvCurrentAlbumEmoji, tvCurrentAlbumTitle, tvCurrentAlbumCount;
    private RecyclerView rvAlbumSongs;
    private MusicAdapter albumSongsAdapter;
    private ThemedAlbum currentViewingAlbum = null;

    // Mini Player (Visible when browsing Library)
    private LinearLayout miniPlayerLayout;
    private TextView tvMiniTitle, tvMiniArtist;
    private ImageView btnMiniPlayPause, btnMiniNext;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MusicPlayerManager playerManager;
    private ThemedAlbumManager albumManager;

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
        albumManager = ThemedAlbumManager.getInstance(this);

        initViews();
        setupTabs();
        setupSubTabs();
        setupPlayerControls();
        setupPlayerQuickActions();
        setupRecyclerViews();
        setupSearchAndActions();
        setupSyncFeature();
        setupBackPressHandling();
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
        layoutDiscContainer = findViewById(R.id.layoutDiscContainer);
        viewVinylWaveVisualizer = findViewById(R.id.viewVinylWaveVisualizer);
        viewVinylDisc = findViewById(R.id.viewVinylDisc);
        viewTsubaGlow = findViewById(R.id.viewTsubaGlow);
        tvPlayerBadge = findViewById(R.id.tvPlayerBadge);
        tvPlayerSourceBadge = findViewById(R.id.tvPlayerSourceBadge);
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

        // Player Quick Actions
        btnPlayerFavorite = findViewById(R.id.btnPlayerFavorite);
        btnPlayerAddToAlbum = findViewById(R.id.btnPlayerAddToAlbum);
        btnPlayerRename = findViewById(R.id.btnPlayerRename);
        btnPlayerDelete = findViewById(R.id.btnPlayerDelete);
        btnPlayerQueue = findViewById(R.id.btnPlayerQueue);

        btnAudioOutput = findViewById(R.id.btnAudioOutput);
        ivOutputIcon = findViewById(R.id.ivOutputIcon);
        tvOutputDeviceName = findViewById(R.id.tvOutputDeviceName);

        // Library Sub-tabs
        btnSubTabAll = findViewById(R.id.btnSubTabAll);
        btnSubTabFolders = findViewById(R.id.btnSubTabFolders);
        btnSubTabAlbums = findViewById(R.id.btnSubTabAlbums);
        layoutAllSongsContainer = findViewById(R.id.layoutAllSongsContainer);
        layoutFoldersContainer = findViewById(R.id.layoutFoldersContainer);
        layoutAlbumsContainer = findViewById(R.id.layoutAlbumsContainer);

        // All Songs
        rvSongs = findViewById(R.id.rvSongs);
        tvSongCount = findViewById(R.id.tvSongCount);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnSearchClear = findViewById(R.id.btnSearchClear);
        etSearch = findViewById(R.id.etSearch);

        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        btnGrantPermission = findViewById(R.id.btnGrantPermission);

        // Folders
        tvFoldersCount = findViewById(R.id.tvFoldersCount);
        rvFolders = findViewById(R.id.rvFolders);
        layoutFolderDetailHeader = findViewById(R.id.layoutFolderDetailHeader);
        btnFolderBack = findViewById(R.id.btnFolderBack);
        btnPlayFolderAll = findViewById(R.id.btnPlayFolderAll);
        tvCurrentFolderName = findViewById(R.id.tvCurrentFolderName);
        tvCurrentFolderPath = findViewById(R.id.tvCurrentFolderPath);
        rvFolderSongs = findViewById(R.id.rvFolderSongs);

        // Albums
        btnCreateAlbumTop = findViewById(R.id.btnCreateAlbumTop);
        rvAlbums = findViewById(R.id.rvAlbums);
        layoutAlbumDetailHeader = findViewById(R.id.layoutAlbumDetailHeader);
        btnAlbumBack = findViewById(R.id.btnAlbumBack);
        btnAddSongsToAlbum = findViewById(R.id.btnAddSongsToAlbum);
        btnPlayAlbumAll = findViewById(R.id.btnPlayAlbumAll);
        tvCurrentAlbumEmoji = findViewById(R.id.tvCurrentAlbumEmoji);
        tvCurrentAlbumTitle = findViewById(R.id.tvCurrentAlbumTitle);
        tvCurrentAlbumCount = findViewById(R.id.tvCurrentAlbumCount);
        rvAlbumSongs = findViewById(R.id.rvAlbumSongs);

        // Mini player
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

        setupTsubaGlowAnimation();
    }

    private void setupTsubaGlowAnimation() {
        if (viewTsubaGlow == null) return;
        viewTsubaGlow.setAlpha(0.35f);

        tsubaGlowScaleXAnimator = ObjectAnimator.ofFloat(viewTsubaGlow, "scaleX", 0.94f, 1.06f);
        tsubaGlowScaleXAnimator.setDuration(1800);
        tsubaGlowScaleXAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        tsubaGlowScaleXAnimator.setRepeatMode(ObjectAnimator.REVERSE);
        tsubaGlowScaleXAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        tsubaGlowScaleYAnimator = ObjectAnimator.ofFloat(viewTsubaGlow, "scaleY", 0.94f, 1.06f);
        tsubaGlowScaleYAnimator.setDuration(1800);
        tsubaGlowScaleYAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        tsubaGlowScaleYAnimator.setRepeatMode(ObjectAnimator.REVERSE);
        tsubaGlowScaleYAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        tsubaGlowAlphaAnimator = ObjectAnimator.ofFloat(viewTsubaGlow, "alpha", 0.35f, 0.85f);
        tsubaGlowAlphaAnimator.setDuration(1800);
        tsubaGlowAlphaAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        tsubaGlowAlphaAnimator.setRepeatMode(ObjectAnimator.REVERSE);
        tsubaGlowAlphaAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
    }

    private void startTsubaGlow() {
        if (tsubaGlowScaleXAnimator != null && !tsubaGlowScaleXAnimator.isRunning()) {
            tsubaGlowScaleXAnimator.start();
            tsubaGlowScaleYAnimator.start();
            tsubaGlowAlphaAnimator.start();
        }
    }

    private void pauseTsubaGlow() {
        if (tsubaGlowScaleXAnimator != null && tsubaGlowScaleXAnimator.isRunning()) {
            tsubaGlowScaleXAnimator.pause();
            tsubaGlowScaleYAnimator.pause();
            tsubaGlowAlphaAnimator.pause();
            if (viewTsubaGlow != null) {
                viewTsubaGlow.animate().alpha(0.25f).scaleX(1.0f).scaleY(1.0f).setDuration(400).start();
            }
        }
    }

    private void animateBounceView(View view, Runnable onEndAction) {
        if (view == null) return;
        view.animate()
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(90)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(180)
                            .setInterpolator(new OvershootInterpolator(2.2f))
                            .withEndAction(onEndAction != null ? onEndAction : () -> {})
                            .start();
                })
                .start();
    }

    private void animateSlideNudge(View view, float translationX, Runnable onEndAction) {
        if (view == null) return;
        view.animate()
                .translationX(translationX)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(80)
                .withEndAction(() -> {
                    view.animate()
                            .translationX(0f)
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(180)
                            .setInterpolator(new OvershootInterpolator(2.0f))
                            .withEndAction(onEndAction != null ? onEndAction : () -> {})
                            .start();
                })
                .start();
    }

    private void animateFavoriteHeart(ImageView btn, boolean isFavorite) {
        if (btn == null) return;
        btn.animate()
                .scaleX(0.6f)
                .scaleY(0.6f)
                .setDuration(90)
                .withEndAction(() -> {
                    btn.setImageResource(isFavorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
                    btn.animate()
                            .scaleX(1.35f)
                            .scaleY(1.35f)
                            .setDuration(160)
                            .setInterpolator(new OvershootInterpolator(3.0f))
                            .withEndAction(() -> {
                                btn.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                            })
                            .start();
                })
                .start();
    }

    // ==========================================
    // Back Press Handling
    // ==========================================

    private void setupBackPressHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (layoutLibraryView.getVisibility() == View.VISIBLE) {
                    if (currentSubTab == 1 && currentViewingFolder != null) {
                        closeFolderDetail();
                        return;
                    }
                    if (currentSubTab == 2 && currentViewingAlbum != null) {
                        closeAlbumDetail();
                        return;
                    }
                }
                if (layoutPlayerView.getVisibility() == View.VISIBLE && !songList.isEmpty()) {
                    switchTab(false);
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
    }

    // ==========================================
    // Tab & Sub-Tab Navigation
    // ==========================================

    private void setupTabs() {
        btnTabPlayer.setOnClickListener(v -> switchTab(true));
        btnTabLibrary.setOnClickListener(v -> switchTab(false));
    }

    private void switchTab(boolean showPlayer) {
        if (showPlayer) {
            if (layoutPlayerView.getVisibility() == View.VISIBLE) return;

            layoutPlayerView.setAlpha(0f);
            layoutPlayerView.setTranslationY(40f);
            layoutPlayerView.setVisibility(View.VISIBLE);
            layoutPlayerView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(240)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

            layoutLibraryView.animate()
                    .alpha(0f)
                    .setDuration(160)
                    .withEndAction(() -> layoutLibraryView.setVisibility(View.GONE))
                    .start();

            btnTabPlayer.setBackgroundResource(R.drawable.bg_tab_active);
            btnTabPlayer.setTextColor(ContextCompat.getColor(this, R.color.white));
            btnTabLibrary.setBackgroundResource(R.drawable.bg_tab_inactive);
            btnTabLibrary.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        } else {
            if (layoutLibraryView.getVisibility() == View.VISIBLE) return;

            layoutLibraryView.setAlpha(0f);
            layoutLibraryView.setTranslationY(40f);
            layoutLibraryView.setVisibility(View.VISIBLE);
            layoutLibraryView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(240)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

            layoutPlayerView.animate()
                    .alpha(0f)
                    .setDuration(160)
                    .withEndAction(() -> layoutPlayerView.setVisibility(View.GONE))
                    .start();

            btnTabLibrary.setBackgroundResource(R.drawable.bg_tab_active);
            btnTabLibrary.setTextColor(ContextCompat.getColor(this, R.color.white));
            btnTabPlayer.setBackgroundResource(R.drawable.bg_tab_inactive);
            btnTabPlayer.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

            // Animate Mini Player slide-up
            if (playerManager.getCurrentSong() != null && miniPlayerLayout != null) {
                miniPlayerLayout.setTranslationY(60f);
                miniPlayerLayout.setAlpha(0f);
                miniPlayerLayout.animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setDuration(280)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            }
        }
    }

    private void setupSubTabs() {
        btnSubTabAll.setOnClickListener(v -> switchSubTab(0));
        btnSubTabFolders.setOnClickListener(v -> switchSubTab(1));
        btnSubTabAlbums.setOnClickListener(v -> switchSubTab(2));
    }

    private void switchSubTab(int index) {
        if (currentSubTab == index && (layoutAllSongsContainer.getVisibility() == View.VISIBLE || layoutFoldersContainer.getVisibility() == View.VISIBLE || layoutAlbumsContainer.getVisibility() == View.VISIBLE)) {
            return;
        }
        currentSubTab = index;

        btnSubTabAll.setBackgroundResource(index == 0 ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        btnSubTabAll.setTextColor(ContextCompat.getColor(this, index == 0 ? R.color.white : R.color.text_secondary));

        btnSubTabFolders.setBackgroundResource(index == 1 ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        btnSubTabFolders.setTextColor(ContextCompat.getColor(this, index == 1 ? R.color.white : R.color.text_secondary));

        btnSubTabAlbums.setBackgroundResource(index == 2 ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        btnSubTabAlbums.setTextColor(ContextCompat.getColor(this, index == 2 ? R.color.white : R.color.text_secondary));

        View targetContainer = (index == 0) ? layoutAllSongsContainer : (index == 1 ? layoutFoldersContainer : layoutAlbumsContainer);
        View[] otherContainers = (index == 0) ? new View[]{layoutFoldersContainer, layoutAlbumsContainer}
                : (index == 1 ? new View[]{layoutAllSongsContainer, layoutAlbumsContainer}
                : new View[]{layoutAllSongsContainer, layoutFoldersContainer});

        for (View other : otherContainers) {
            other.setVisibility(View.GONE);
        }

        targetContainer.setAlpha(0f);
        targetContainer.setTranslationY(24f);
        targetContainer.setVisibility(View.VISIBLE);
        targetContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        if (index == 1) {
            refreshFoldersList();
        } else if (index == 2) {
            refreshAlbumsList();
        }

        if (etSearch != null) {
            applyCurrentSearchFilter(etSearch.getText());
        }
    }

    // ==========================================
    // Player Controls & SeekBar
    // ==========================================

    private void setupPlayerControls() {
        if (layoutDiscContainer != null) {
            layoutDiscContainer.setOnClickListener(v -> {
                animateBounceView(viewVinylDisc, null);
                if (viewVinylWaveVisualizer != null) {
                    int style = viewVinylWaveVisualizer.cycleWaveStyle();
                    String styleName = (style == 0) ? "Đầy đủ: Sóng âm & Thanh phổ (全)"
                            : (style == 1 ? "Vòng sóng âm lan tỏa (波)" : "Thanh phổ sóng Neon (光)");
                    Toast.makeText(this, "🌊 Kiểu sóng nhạc: " + styleName, Toast.LENGTH_SHORT).show();
                }
            });
        }

        btnPlayPauseLarge.setOnClickListener(v -> animateBounceView(v, () -> playerManager.togglePlayPause(MainActivity.this)));
        btnPrevious.setOnClickListener(v -> animateSlideNudge(v, -16f, () -> playerManager.playPrevious(MainActivity.this)));
        btnNext.setOnClickListener(v -> animateSlideNudge(v, 16f, () -> playerManager.playNext(MainActivity.this)));

        btnShuffle.setOnClickListener(v -> animateBounceView(v, () -> {
            boolean active = playerManager.toggleShuffle();
            updateShuffleButton(active);
            Toast.makeText(this, active ? "Đã bật phát ngẫu nhiên (乱)" : "Đã tắt phát ngẫu nhiên", Toast.LENGTH_SHORT).show();
        }));

        btnRepeat.setOnClickListener(v -> animateBounceView(v, () -> {
            MusicPlayerManager.RepeatMode mode = playerManager.cycleRepeatMode();
            updateRepeatButton(mode);
        }));

        btnAudioOutput.setOnClickListener(v -> animateBounceView(v, this::showAudioOutputDialog));

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
    // Player Quick Actions (Fixes Bottom Space)
    // ==========================================

    private void setupPlayerQuickActions() {
        // 1. Toggle Favorite
        btnPlayerFavorite.setOnClickListener(v -> {
            Song current = playerManager.getCurrentSong();
            if (current != null) {
                boolean fav = albumManager.toggleFavorite(current.getId());
                animateFavoriteHeart(btnPlayerFavorite, fav);
                Toast.makeText(this, fav ? "❤️ Đã thêm vào khúc nhạc Yêu thích" : "Đã bỏ khỏi Yêu thích", Toast.LENGTH_SHORT).show();
            }
        });

        // 2. Add to Themed Album
        btnPlayerAddToAlbum.setOnClickListener(v -> animateBounceView(v, () -> {
            Song current = playerManager.getCurrentSong();
            if (current != null) {
                showAddToAlbumBottomSheet(current);
            }
        }));

        // 3. Rename Song
        btnPlayerRename.setOnClickListener(v -> animateBounceView(v, () -> {
            Song current = playerManager.getCurrentSong();
            if (current != null) {
                showRenameSongDialog(current);
            }
        }));

        // 4. Delete Song
        btnPlayerDelete.setOnClickListener(v -> animateBounceView(v, () -> {
            Song current = playerManager.getCurrentSong();
            if (current != null) {
                showDeleteSongDialog(current, null);
            }
        }));

        // 5. Jump to Queue / Library
        btnPlayerQueue.setOnClickListener(v -> animateBounceView(v, () -> switchTab(false)));
    }

    private void updatePlayerFavoriteButton(boolean isFavorite) {
        if (btnPlayerFavorite == null) return;
        btnPlayerFavorite.setImageResource(isFavorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
    }

    // ==========================================
    // RecyclerViews Setup
    // ==========================================

    private void setupRecyclerViews() {
        // 1. All Songs Adapter
        musicAdapter = new MusicAdapter(this, songList, (song, originalIndex) -> {
            playerManager.setPlaylist(songList);
            playerManager.playSongAtIndex(MainActivity.this, originalIndex);
            updatePlayerSourceBadge("🎵 Tất cả khúc nhạc");
            switchTab(true);
        });
        musicAdapter.setOnSongOptionsClickListener((song, anchor) -> showSongOptionsBottomSheet(song, null));
        musicAdapter.setOnFilterResultsListener((count, isFiltering) -> {
            if (isFiltering) {
                tvSongCount.setText(String.format(Locale.getDefault(), "Tìm thấy %d khúc nhạc", count));
            } else {
                tvSongCount.setText(String.format(Locale.getDefault(), "%d khúc nhạc", songList.size()));
            }
        });
        rvSongs.setLayoutManager(new LinearLayoutManager(this));
        rvSongs.setAdapter(musicAdapter);

        // 2. Folders Adapter
        folderAdapter = new FolderAdapter(this, folderGroupList, new FolderAdapter.OnFolderClickListener() {
            @Override
            public void onFolderClick(ThemedAlbumManager.FolderGroup group) {
                openFolderDetail(group);
            }

            @Override
            public void onPlayFolder(ThemedAlbumManager.FolderGroup group) {
                playFolderSongs(group);
            }
        });
        rvFolders.setLayoutManager(new LinearLayoutManager(this));
        rvFolders.setAdapter(folderAdapter);

        // Folder Songs Adapter
        folderSongsAdapter = new MusicAdapter(this, new ArrayList<>(), (song, originalIndex) -> {
            if (currentViewingFolder != null) {
                playerManager.setPlaylist(currentViewingFolder.songs);
                playerManager.playSongAtIndex(MainActivity.this, originalIndex);
                updatePlayerSourceBadge("📁 " + currentViewingFolder.folderName);
                switchTab(true);
            }
        });
        folderSongsAdapter.setOnSongOptionsClickListener((song, anchor) -> showSongOptionsBottomSheet(song, null));
        folderSongsAdapter.setOnFilterResultsListener((count, isFiltering) -> {
            if (currentViewingFolder != null) {
                if (isFiltering) {
                    tvCurrentFolderPath.setText(String.format(Locale.getDefault(), "%s · Tìm thấy %d khúc nhạc", currentViewingFolder.folderPath, count));
                } else {
                    tvCurrentFolderPath.setText(String.format(Locale.getDefault(), "%s · %d khúc nhạc", currentViewingFolder.folderPath, currentViewingFolder.getSongCount()));
                }
            }
        });
        rvFolderSongs.setLayoutManager(new LinearLayoutManager(this));
        rvFolderSongs.setAdapter(folderSongsAdapter);

        // 3. Themed Albums Adapter
        themedAlbumAdapter = new ThemedAlbumAdapter(this, albumList, new ThemedAlbumAdapter.OnAlbumActionListener() {
            @Override
            public void onAlbumClick(ThemedAlbum album) {
                openAlbumDetail(album);
            }

            @Override
            public void onPlayAlbum(ThemedAlbum album) {
                playAlbumSongs(album);
            }

            @Override
            public void onAlbumOptions(ThemedAlbum album, View anchorView) {
                showAlbumOptionsMenu(album);
            }
        });
        rvAlbums.setLayoutManager(new LinearLayoutManager(this));
        rvAlbums.setAdapter(themedAlbumAdapter);

        // Album Songs Adapter
        albumSongsAdapter = new MusicAdapter(this, new ArrayList<>(), (song, originalIndex) -> {
            if (currentViewingAlbum != null) {
                List<Song> songs = albumManager.getSongsInAlbum(currentViewingAlbum.getId(), songList);
                playerManager.setPlaylist(songs);
                playerManager.playSongAtIndex(MainActivity.this, originalIndex);
                updatePlayerSourceBadge(currentViewingAlbum.getEmoji() + " " + currentViewingAlbum.getName());
                switchTab(true);
            }
        });
        albumSongsAdapter.setOnSongOptionsClickListener((song, anchor) -> showSongOptionsBottomSheet(song, currentViewingAlbum));
        albumSongsAdapter.setOnFilterResultsListener((count, isFiltering) -> {
            if (currentViewingAlbum != null) {
                if (isFiltering) {
                    tvCurrentAlbumCount.setText(String.format(Locale.getDefault(), "Tìm thấy %d khúc nhạc", count));
                } else {
                    List<Song> songsInAlbum = albumManager.getSongsInAlbum(currentViewingAlbum.getId(), songList);
                    tvCurrentAlbumCount.setText(String.format(Locale.getDefault(), "%d khúc nhạc", songsInAlbum.size()));
                }
            }
        });
        rvAlbumSongs.setLayoutManager(new LinearLayoutManager(this));
        rvAlbumSongs.setAdapter(albumSongsAdapter);
    }

    private void updatePlayerSourceBadge(String badgeText) {
        if (tvPlayerSourceBadge != null && badgeText != null) {
            tvPlayerSourceBadge.setText(badgeText);
        }
    }

    // ==========================================
    // Folder Details Navigation
    // ==========================================

    private void openFolderDetail(ThemedAlbumManager.FolderGroup group) {
        currentViewingFolder = group;
        tvCurrentFolderName.setText(group.folderName);
        tvCurrentFolderPath.setText(String.format(Locale.getDefault(), "%s · %d khúc nhạc", group.folderPath, group.getSongCount()));
        folderSongsAdapter.updateData(group.songs);
        if (etSearch != null && etSearch.getText().length() > 0) {
            folderSongsAdapter.getFilter().filter(etSearch.getText());
        }

        // Smooth horizontal slide-out of folders and slide-in of folder songs
        rvFolders.animate()
                .alpha(0f)
                .translationX(-50f)
                .setDuration(160)
                .withEndAction(() -> {
                    rvFolders.setVisibility(View.GONE);
                    rvFolders.setTranslationX(0f);

                    layoutFolderDetailHeader.setAlpha(0f);
                    layoutFolderDetailHeader.setTranslationX(50f);
                    layoutFolderDetailHeader.setVisibility(View.VISIBLE);

                    rvFolderSongs.setAlpha(0f);
                    rvFolderSongs.setTranslationX(50f);
                    rvFolderSongs.setVisibility(View.VISIBLE);

                    tvFoldersCount.setVisibility(View.GONE);

                    layoutFolderDetailHeader.animate().alpha(1f).translationX(0f).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
                    rvFolderSongs.animate().alpha(1f).translationX(0f).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
                }).start();
    }

    private void closeFolderDetail() {
        currentViewingFolder = null;

        layoutFolderDetailHeader.animate().alpha(0f).translationX(50f).setDuration(150).start();
        rvFolderSongs.animate().alpha(0f).translationX(50f).setDuration(150).withEndAction(() -> {
            layoutFolderDetailHeader.setVisibility(View.GONE);
            rvFolderSongs.setVisibility(View.GONE);

            rvFolders.setAlpha(0f);
            rvFolders.setTranslationX(-50f);
            rvFolders.setVisibility(View.VISIBLE);
            tvFoldersCount.setVisibility(View.VISIBLE);

            rvFolders.animate().alpha(1f).translationX(0f).setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
            refreshFoldersList();
        }).start();
    }

    private void playFolderSongs(ThemedAlbumManager.FolderGroup group) {
        if (group == null || group.songs.isEmpty()) return;
        playerManager.setPlaylist(group.songs);
        playerManager.playSongAtIndex(this, 0);
        updatePlayerSourceBadge("📁 " + group.folderName);
        switchTab(true);
    }

    private void refreshFoldersList() {
        folderGroupList.clear();
        folderGroupList.addAll(albumManager.groupSongsByFolder(songList));
        folderAdapter.updateData(folderGroupList);
        tvFoldersCount.setText(String.format(Locale.getDefault(), "%d thư mục chứa nhạc", folderGroupList.size()));

        if (currentViewingFolder != null) {
            ThemedAlbumManager.FolderGroup updatedGroup = null;
            for (ThemedAlbumManager.FolderGroup g : folderGroupList) {
                if (g.folderPath.equalsIgnoreCase(currentViewingFolder.folderPath)
                        || g.folderName.equalsIgnoreCase(currentViewingFolder.folderName)) {
                    updatedGroup = g;
                    break;
                }
            }
            if (updatedGroup != null) {
                currentViewingFolder = updatedGroup;
                tvCurrentFolderPath.setText(String.format(Locale.getDefault(), "%s · %d khúc nhạc", currentViewingFolder.folderPath, currentViewingFolder.getSongCount()));
                folderSongsAdapter.updateData(currentViewingFolder.songs);
                if (etSearch != null && etSearch.getText().length() > 0) {
                    folderSongsAdapter.getFilter().filter(etSearch.getText());
                }
            }
        }
    }

    // ==========================================
    // Themed Album Navigation
    // ==========================================

    private void openAlbumDetail(ThemedAlbum album) {
        currentViewingAlbum = album;
        tvCurrentAlbumEmoji.setText(album.getEmoji());
        tvCurrentAlbumTitle.setText(album.getName());

        List<Song> songsInAlbum = albumManager.getSongsInAlbum(album.getId(), songList);
        tvCurrentAlbumCount.setText(String.format(Locale.getDefault(), "%d khúc nhạc", songsInAlbum.size()));
        albumSongsAdapter.updateData(songsInAlbum);
        if (etSearch != null && etSearch.getText().length() > 0) {
            albumSongsAdapter.getFilter().filter(etSearch.getText());
        }

        // Smooth horizontal slide-out of albums and slide-in of album songs
        btnCreateAlbumTop.setVisibility(View.GONE);
        rvAlbums.animate()
                .alpha(0f)
                .translationX(-50f)
                .setDuration(160)
                .withEndAction(() -> {
                    rvAlbums.setVisibility(View.GONE);
                    rvAlbums.setTranslationX(0f);

                    layoutAlbumDetailHeader.setAlpha(0f);
                    layoutAlbumDetailHeader.setTranslationX(50f);
                    layoutAlbumDetailHeader.setVisibility(View.VISIBLE);

                    rvAlbumSongs.setAlpha(0f);
                    rvAlbumSongs.setTranslationX(50f);
                    rvAlbumSongs.setVisibility(View.VISIBLE);

                    layoutAlbumDetailHeader.animate().alpha(1f).translationX(0f).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
                    rvAlbumSongs.animate().alpha(1f).translationX(0f).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
                }).start();
    }

    private void closeAlbumDetail() {
        currentViewingAlbum = null;

        layoutAlbumDetailHeader.animate().alpha(0f).translationX(50f).setDuration(150).start();
        rvAlbumSongs.animate().alpha(0f).translationX(50f).setDuration(150).withEndAction(() -> {
            layoutAlbumDetailHeader.setVisibility(View.GONE);
            rvAlbumSongs.setVisibility(View.GONE);
            btnCreateAlbumTop.setVisibility(View.VISIBLE);

            rvAlbums.setAlpha(0f);
            rvAlbums.setTranslationX(-50f);
            rvAlbums.setVisibility(View.VISIBLE);

            rvAlbums.animate().alpha(1f).translationX(0f).setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
            refreshAlbumsList();
        }).start();
    }

    private void playAlbumSongs(ThemedAlbum album) {
        if (album == null) return;
        List<Song> songs = albumManager.getSongsInAlbum(album.getId(), songList);
        if (songs.isEmpty()) {
            Toast.makeText(this, "Album chưa có bài hát nào! Hãy thêm bài hát vào album.", Toast.LENGTH_SHORT).show();
            return;
        }
        playerManager.setPlaylist(songs);
        playerManager.playSongAtIndex(this, 0);
        updatePlayerSourceBadge(album.getEmoji() + " " + album.getName());
        switchTab(true);
    }

    private void refreshAlbumsList() {
        albumList.clear();
        albumList.addAll(albumManager.getAlbums());
        themedAlbumAdapter.updateData(albumList);

        if (currentViewingAlbum != null) {
            List<Song> songsInAlbum = albumManager.getSongsInAlbum(currentViewingAlbum.getId(), songList);
            tvCurrentAlbumCount.setText(String.format(Locale.getDefault(), "%d khúc nhạc", songsInAlbum.size()));
            albumSongsAdapter.updateData(songsInAlbum);
            if (etSearch != null && etSearch.getText().length() > 0) {
                albumSongsAdapter.getFilter().filter(etSearch.getText());
            }
        }
    }

    // ==========================================
    // Search & Actions Setup
    // ==========================================

    private void setupSearchAndActions() {
        btnOpenSync.setOnClickListener(v -> animateBounceView(v, this::showWifiSyncDialog));
        btnRefresh.setOnClickListener(v -> animateBounceView(v, this::checkPermissionsAndLoadSongs));
        btnGrantPermission.setOnClickListener(v -> animateBounceView(v, this::checkPermissionsAndLoadSongs));

        // Sub-navigation Folder actions
        btnFolderBack.setOnClickListener(v -> animateSlideNudge(v, -12f, this::closeFolderDetail));
        btnPlayFolderAll.setOnClickListener(v -> animateBounceView(v, () -> {
            if (currentViewingFolder != null) playFolderSongs(currentViewingFolder);
        }));

        // Sub-navigation Album actions
        btnAlbumBack.setOnClickListener(v -> animateSlideNudge(v, -12f, this::closeAlbumDetail));
        btnPlayAlbumAll.setOnClickListener(v -> animateBounceView(v, () -> {
            if (currentViewingAlbum != null) playAlbumSongs(currentViewingAlbum);
        }));
        btnAddSongsToAlbum.setOnClickListener(v -> animateBounceView(v, () -> {
            if (currentViewingAlbum != null) showAddSongsToAlbumPicker(currentViewingAlbum);
        }));
        btnCreateAlbumTop.setOnClickListener(v -> animateBounceView(v, () -> showCreateAlbumDialog(null)));

        btnSearchClear.setOnClickListener(v -> {
            etSearch.setText("");
            btnSearchClear.setVisibility(View.GONE);
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean hasText = (s != null && s.length() > 0);
                btnSearchClear.setVisibility(hasText ? View.VISIBLE : View.GONE);
                applyCurrentSearchFilter(s);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        miniPlayerLayout.setOnClickListener(v -> switchTab(true));
        btnMiniPlayPause.setOnClickListener(v -> animateBounceView(v, () -> playerManager.togglePlayPause(MainActivity.this)));
        btnMiniNext.setOnClickListener(v -> animateSlideNudge(v, 14f, () -> playerManager.playNext(MainActivity.this)));
    }

    private void applyCurrentSearchFilter(@Nullable CharSequence s) {
        if (currentSubTab == 0) {
            if (musicAdapter != null) musicAdapter.getFilter().filter(s);
        } else if (currentSubTab == 1) {
            if (currentViewingFolder != null && folderSongsAdapter != null) {
                folderSongsAdapter.getFilter().filter(s);
            }
        } else if (currentSubTab == 2) {
            if (currentViewingAlbum != null && albumSongsAdapter != null) {
                albumSongsAdapter.getFilter().filter(s);
            }
        }
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
            // Apply persistent user custom song titles
            albumManager.applyCustomTitles(fetchedSongs);

            songList.clear();
            songList.addAll(fetchedSongs);
            musicAdapter.updateData(songList);
            playerManager.setPlaylist(songList);

            tvSongCount.setText(String.format(Locale.getDefault(), "%d khúc nhạc", songList.size()));

            refreshFoldersList();
            refreshAlbumsList();

            if (etSearch != null && etSearch.getText().length() > 0) {
                applyCurrentSearchFilter(etSearch.getText());
            }

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
    // Song Options BottomSheet (Rename, Delete, Album, Favorite)
    // ==========================================

    private void showSongOptionsBottomSheet(@NonNull Song song, @Nullable ThemedAlbum albumContext) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_song_options, null);
        dialog.setContentView(view);

        TextView tvTitle = view.findViewById(R.id.tvOptionSongTitle);
        TextView tvArtist = view.findViewById(R.id.tvOptionSongArtist);
        ImageView ivFav = view.findViewById(R.id.ivOptionFavorite);
        TextView tvFav = view.findViewById(R.id.tvOptionFavorite);

        tvTitle.setText(song.getTitle());
        tvArtist.setText(String.format("%s · %s", song.getArtist(), song.getFormattedDuration()));

        boolean isFav = albumManager.isFavorite(song.getId());
        ivFav.setImageResource(isFav ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        tvFav.setText(isFav ? "Đã yêu thích (Bỏ yêu thích)" : "Thêm vào Yêu thích");

        // Action 1: Play now
        view.findViewById(R.id.btnOptionPlay).setOnClickListener(v -> {
            playerManager.setPlaylist(songList);
            playerManager.playSongById(MainActivity.this, song.getId());
            switchTab(true);
            dialog.dismiss();
        });

        // Action 2: Add to Themed Album
        view.findViewById(R.id.btnOptionAddToAlbum).setOnClickListener(v -> {
            dialog.dismiss();
            showAddToAlbumBottomSheet(song);
        });

        // Action 3: Favorite
        view.findViewById(R.id.btnOptionFavorite).setOnClickListener(v -> {
            boolean fav = albumManager.toggleFavorite(song.getId());
            Song current = playerManager.getCurrentSong();
            if (current != null && current.getId() == song.getId()) {
                updatePlayerFavoriteButton(fav);
            }
            Toast.makeText(this, fav ? "❤️ Đã thêm vào khúc nhạc Yêu thích" : "Đã bỏ khỏi Yêu thích", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        // Action 4: Rename
        view.findViewById(R.id.btnOptionRename).setOnClickListener(v -> {
            dialog.dismiss();
            showRenameSongDialog(song);
        });

        // Action 5: Delete
        view.findViewById(R.id.btnOptionDelete).setOnClickListener(v -> {
            dialog.dismiss();
            showDeleteSongDialog(song, albumContext);
        });

        dialog.show();
    }

    // ==========================================
    // Rename Song Dialog (Đổi Tên Nhạc)
    // ==========================================

    private void showRenameSongDialog(@NonNull Song song) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_rename_song, null);
        dialog.setContentView(view);

        EditText etTitle = view.findViewById(R.id.etDialogSongTitle);
        etTitle.setText(song.getTitle());
        etTitle.setSelection(etTitle.getText().length());

        view.findViewById(R.id.btnDialogRenameCancel).setOnClickListener(v -> dialog.dismiss());

        view.findViewById(R.id.btnDialogRenameSave).setOnClickListener(v -> {
            String newTitle = etTitle.getText().toString().trim();
            if (newTitle.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập tên bài hát!", Toast.LENGTH_SHORT).show();
                return;
            }

            song.setTitle(newTitle);
            albumManager.setCustomTitle(song.getId(), newTitle);

            // Update in UI adapters
            musicAdapter.updateSong(song);
            if (folderSongsAdapter != null) folderSongsAdapter.updateSong(song);
            if (albumSongsAdapter != null) albumSongsAdapter.updateSong(song);

            // If currently playing, update player title and service notification
            Song current = playerManager.getCurrentSong();
            if (current != null && current.getId() == song.getId()) {
                current.setTitle(newTitle);
                updatePlayerMetadata(current);
                MusicService.startOrUpdateService(this, current, playerManager.isPlaying());
            }

            Toast.makeText(this, "Đã đổi tên khúc nhạc thành công!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    // ==========================================
    // Delete Song Dialog (Xóa Nhạc)
    // ==========================================

    private void showDeleteSongDialog(@NonNull Song song, @Nullable ThemedAlbum albumContext) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialogTheme);
        builder.setTitle("Xóa khúc nhạc");

        if (albumContext != null) {
            builder.setMessage("Bạn muốn xóa bài hát \"" + song.getTitle() + "\" khỏi Album chủ đề này hay xóa hoàn toàn khỏi thiết bị?");
            builder.setPositiveButton("Xóa khỏi Album", (d, which) -> {
                albumManager.removeSongFromAlbum(albumContext.getId(), song.getId());
                openAlbumDetail(albumContext);
                refreshAlbumsList();
                Toast.makeText(this, "Đã xóa bài hát khỏi Album!", Toast.LENGTH_SHORT).show();
            });
            builder.setNegativeButton("Xóa khỏi máy", (d, which) -> performDeleteSongFromDevice(song));
            builder.setNeutralButton("Hủy", null);
        } else {
            builder.setMessage("Bạn có chắc chắn muốn xóa bài hát \"" + song.getTitle() + "\" khỏi thiết bị?");
            builder.setPositiveButton("Xóa vĩnh viễn", (d, which) -> performDeleteSongFromDevice(song));
            builder.setNegativeButton("Hủy", null);
        }

        builder.show();
    }

    private void performDeleteSongFromDevice(@NonNull Song song) {
        albumManager.deleteSong(this, song);

        // If currently playing, skip to next or stop
        Song current = playerManager.getCurrentSong();
        if (current != null && current.getId() == song.getId()) {
            playerManager.playNext(this);
        }

        songList.removeIf(s -> s.getId() == song.getId());
        musicAdapter.removeSong(song);
        if (folderSongsAdapter != null) folderSongsAdapter.removeSong(song);
        if (albumSongsAdapter != null) albumSongsAdapter.removeSong(song);

        tvSongCount.setText(String.format(Locale.getDefault(), "%d khúc nhạc", songList.size()));
        refreshFoldersList();
        refreshAlbumsList();

        Toast.makeText(this, "Đã xóa khúc nhạc thành công!", Toast.LENGTH_SHORT).show();
    }

    // ==========================================
    // Create / Edit Themed Album Dialog
    // ==========================================

    private void showCreateAlbumDialog(@Nullable ThemedAlbum albumToEdit) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_create_album, null);
        dialog.setContentView(view);

        TextView tvHeading = view.findViewById(R.id.tvDialogAlbumHeading);
        EditText etName = view.findViewById(R.id.etDialogAlbumName);
        LinearLayout layoutEmojis = view.findViewById(R.id.layoutEmojiSelector);

        final String[] selectedEmoji = {albumToEdit != null ? albumToEdit.getEmoji() : "⚔️"};

        if (albumToEdit != null) {
            tvHeading.setText("Đổi Tên Album Chủ Đề");
            etName.setText(albumToEdit.getName());
            etName.setSelection(etName.getText().length());
        }

        // Highlight selected emoji
        for (int i = 0; i < layoutEmojis.getChildCount(); i++) {
            View child = layoutEmojis.getChildAt(i);
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                String tag = (String) tv.getTag();
                if (selectedEmoji[0].equals(tag)) {
                    tv.setBackgroundResource(R.drawable.bg_tab_active);
                } else {
                    tv.setBackgroundResource(R.drawable.bg_pill_search);
                }

                tv.setOnClickListener(v -> {
                    selectedEmoji[0] = tag;
                    for (int j = 0; j < layoutEmojis.getChildCount(); j++) {
                        View other = layoutEmojis.getChildAt(j);
                        other.setBackgroundResource(other == tv ? R.drawable.bg_tab_active : R.drawable.bg_pill_search);
                    }
                });
            }
        }

        view.findViewById(R.id.btnDialogAlbumCancel).setOnClickListener(v -> dialog.dismiss());

        view.findViewById(R.id.btnDialogAlbumSave).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập tên Album!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (albumToEdit != null) {
                albumToEdit.setName(name);
                albumToEdit.setEmoji(selectedEmoji[0]);
                albumManager.renameAlbum(albumToEdit.getId(), name);
                refreshAlbumsList();
                if (currentViewingAlbum != null && currentViewingAlbum.getId().equals(albumToEdit.getId())) {
                    openAlbumDetail(albumToEdit);
                }
                Toast.makeText(this, "Đã cập nhật Album thành công!", Toast.LENGTH_SHORT).show();
            } else {
                albumManager.createAlbum(name, selectedEmoji[0]);
                refreshAlbumsList();
                Toast.makeText(this, "Đã tạo Album chủ đề mới thành công!", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    // ==========================================
    // Add Song To Themed Album BottomSheet
    // ==========================================

    private void showAddToAlbumBottomSheet(@NonNull Song song) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_add_to_album, null);
        dialog.setContentView(view);

        view.findViewById(R.id.btnAddAlbumClose).setOnClickListener(v -> dialog.dismiss());

        view.findViewById(R.id.btnCreateNewAlbumFromSheet).setOnClickListener(v -> {
            dialog.dismiss();
            showCreateAlbumDialog(null);
        });

        LinearLayout layoutPicker = view.findViewById(R.id.layoutAlbumPickerList);
        layoutPicker.removeAllViews();

        List<ThemedAlbum> allAlbums = albumManager.getAlbums();
        for (ThemedAlbum a : allAlbums) {
            View item = LayoutInflater.from(this).inflate(R.layout.item_themed_album, layoutPicker, false);
            TextView tvEmoji = item.findViewById(R.id.tvAlbumEmoji);
            TextView tvTitle = item.findViewById(R.id.tvAlbumTitle);
            TextView tvSub = item.findViewById(R.id.tvAlbumSubtitle);
            ImageView btnPlay = item.findViewById(R.id.btnPlayAlbum);
            ImageView btnOpts = item.findViewById(R.id.btnAlbumOptions);

            tvEmoji.setText(a.getEmoji());
            tvTitle.setText(a.getName());
            boolean alreadyIn = a.containsSong(song.getId());
            tvSub.setText(alreadyIn ? "✓ Đã có trong album này" : String.format(Locale.getDefault(), "%d khúc nhạc", a.getSongCount()));
            tvSub.setTextColor(ContextCompat.getColor(this, alreadyIn ? R.color.primary_accent : R.color.accent_gold));

            btnPlay.setVisibility(View.GONE);
            btnOpts.setVisibility(View.GONE);

            item.setOnClickListener(v -> {
                if (alreadyIn) {
                    albumManager.removeSongFromAlbum(a.getId(), song.getId());
                    Toast.makeText(this, "Đã xóa khỏi album: " + a.getName(), Toast.LENGTH_SHORT).show();
                } else {
                    albumManager.addSongToAlbum(a.getId(), song.getId());
                    Toast.makeText(this, "Đã thêm vào album: " + a.getName(), Toast.LENGTH_SHORT).show();
                }
                refreshAlbumsList();
                if (currentViewingAlbum != null && currentViewingAlbum.getId().equals(a.getId())) {
                    openAlbumDetail(a);
                }
                dialog.dismiss();
            });

            layoutPicker.addView(item);
        }

        dialog.show();
    }

    // ==========================================
    // Add Multiple Songs Picker To Album
    // ==========================================

    private void showAddSongsToAlbumPicker(@NonNull ThemedAlbum album) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialogTheme);
        builder.setTitle(album.getEmoji() + " Thêm khúc nhạc vào " + album.getName());

        String[] songNames = new String[songList.size()];
        boolean[] checked = new boolean[songList.size()];

        for (int i = 0; i < songList.size(); i++) {
            Song s = songList.get(i);
            songNames[i] = s.getTitle() + " (" + s.getArtist() + ")";
            checked[i] = album.containsSong(s.getId());
        }

        builder.setMultiChoiceItems(songNames, checked, (d, which, isChecked) -> {
            Song s = songList.get(which);
            if (isChecked) {
                albumManager.addSongToAlbum(album.getId(), s.getId());
            } else {
                albumManager.removeSongFromAlbum(album.getId(), s.getId());
            }
        });

        builder.setPositiveButton("Xong", (d, which) -> {
            openAlbumDetail(album);
            refreshAlbumsList();
            Toast.makeText(this, "Đã cập nhật khúc nhạc trong album!", Toast.LENGTH_SHORT).show();
        });

        builder.show();
    }

    private void showAlbumOptionsMenu(@NonNull ThemedAlbum album) {
        String[] options = {"▶ Phát album này", "✏️ Đổi tên & Biểu tượng", "🗑️ Xóa Album chủ đề"};
        new AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setTitle(album.getEmoji() + " " + album.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        playAlbumSongs(album);
                    } else if (which == 1) {
                        showCreateAlbumDialog(album);
                    } else if (which == 2) {
                        new AlertDialog.Builder(this, R.style.CustomDialogTheme)
                                .setTitle("Xóa Album")
                                .setMessage("Bạn có chắc muốn xóa Album \"" + album.getName() + "\"? Các bài hát vẫn được giữ nguyên trong máy.")
                                .setPositiveButton("Xóa Album", (d, w) -> {
                                    albumManager.deleteAlbum(album.getId());
                                    if (currentViewingAlbum != null && currentViewingAlbum.getId().equals(album.getId())) {
                                        closeAlbumDetail();
                                    } else {
                                        refreshAlbumsList();
                                    }
                                    Toast.makeText(this, "Đã xóa Album chủ đề!", Toast.LENGTH_SHORT).show();
                                })
                                .setNegativeButton("Hủy", null)
                                .show();
                    }
                })
                .show();
    }

    // ==========================================
    // Wi-Fi Auto-Sync Feature
    // ==========================================

    private ObjectAnimator syncIconAnimator;

    private void startSyncIconAnimation() {
        if (btnOpenSync == null) return;
        if (syncIconAnimator == null) {
            syncIconAnimator = ObjectAnimator.ofFloat(btnOpenSync, "rotation", 0f, 360f);
            syncIconAnimator.setDuration(1200);
            syncIconAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            syncIconAnimator.setInterpolator(new LinearInterpolator());
        }
        if (!syncIconAnimator.isRunning()) {
            syncIconAnimator.start();
            btnOpenSync.setColorFilter(ContextCompat.getColor(this, R.color.primary_accent));
        }
    }

    private void stopSyncIconAnimation() {
        if (syncIconAnimator != null && syncIconAnimator.isRunning()) {
            syncIconAnimator.cancel();
            btnOpenSync.setRotation(0f);
            btnOpenSync.setColorFilter(ContextCompat.getColor(this, R.color.accent_gold));
        }
    }

    private void setupSyncFeature() {
        SyncManager.getInstance().setupAutoSyncOnWifi(this);
        SyncManager.getInstance().startRealtimeMonitoring(this);
        SyncManager.getInstance().addListener(new SyncManager.SyncListener() {
            @Override
            public void onSyncStatusChanged(SyncManager.SyncStatus status, @Nullable String message) {
                mainHandler.post(() -> {
                    if (status == SyncManager.SyncStatus.DOWNLOADING) {
                        startSyncIconAnimation();
                    } else if (status == SyncManager.SyncStatus.COMPLETED || status == SyncManager.SyncStatus.ERROR || status == SyncManager.SyncStatus.IDLE) {
                        stopSyncIconAnimation();
                    }
                });
            }

            @Override
            public void onSyncProgress(int current, int total, String songName) {
                mainHandler.post(() -> startSyncIconAnimation());
            }

            @Override
            public void onSyncCompleted(int syncedCount) {
                mainHandler.post(() -> {
                    stopSyncIconAnimation();
                    if (syncedCount > 0) {
                        Toast.makeText(MainActivity.this, "Đã tự động đồng bộ " + syncedCount + " khúc nhạc mới từ máy tính!", Toast.LENGTH_LONG).show();
                    }
                    loadSongs();
                });
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
                loadSongs();
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

        // Smooth subtle slide & fade for song title and artist change
        if (tvSheetTitle != null && tvSheetArtist != null) {
            String newTitle = song.getTitle();
            String newArtist = String.format("⚔️ %s", song.getArtist());

            if (!newTitle.equals(tvSheetTitle.getText().toString())) {
                tvSheetTitle.animate().alpha(0f).translationY(-6f).setDuration(120).withEndAction(() -> {
                    tvSheetTitle.setText(newTitle);
                    tvSheetTitle.setTranslationY(6f);
                    tvSheetTitle.animate().alpha(1f).translationY(0f).setDuration(160).setInterpolator(new DecelerateInterpolator()).start();
                }).start();

                tvSheetArtist.animate().alpha(0f).translationY(-6f).setDuration(120).withEndAction(() -> {
                    tvSheetArtist.setText(newArtist);
                    tvSheetArtist.setTranslationY(6f);
                    tvSheetArtist.animate().alpha(1f).translationY(0f).setDuration(160).setInterpolator(new DecelerateInterpolator()).start();
                }).start();
            } else {
                tvSheetTitle.setText(newTitle);
                tvSheetArtist.setText(newArtist);
            }
        }

        tvTotalTime.setText(song.getFormattedDuration());
        seekBarPlayer.setMax((int) song.getDuration());

        tvMiniTitle.setText(song.getTitle());
        tvMiniArtist.setText(song.getArtist());

        updatePlayerFavoriteButton(albumManager.isFavorite(song.getId()));
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
            startTsubaGlow();
            if (viewVinylWaveVisualizer != null) {
                viewVinylWaveVisualizer.setAudioSessionId(playerManager.getAudioSessionId());
                viewVinylWaveVisualizer.start();
            }
        } else {
            tvPlayerBadge.setText("TẠM DỪNG");
            if (discAnimator.isRunning()) {
                discAnimator.pause();
            }
            pauseTsubaGlow();
            if (viewVinylWaveVisualizer != null) {
                viewVinylWaveVisualizer.pause();
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
        if (folderSongsAdapter != null) folderSongsAdapter.setCurrentPlayingSongId(song.getId());
        if (albumSongsAdapter != null) albumSongsAdapter.setCurrentPlayingSongId(song.getId());

        if (viewVinylWaveVisualizer != null) {
            viewVinylWaveVisualizer.setAudioSessionId(playerManager.getAudioSessionId());
            if (playerManager.isPlaying()) {
                viewVinylWaveVisualizer.start();
            }
        }
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

    private final Runnable fastSyncRunnable = new Runnable() {
        @Override
        public void run() {
            if (SyncManager.getInstance().isAutoSyncEnabled(MainActivity.this)) {
                SyncManager.getInstance().checkFastCatalogUpdate(MainActivity.this);
            }
            mainHandler.postDelayed(this, 6000);
        }
    };

    private final android.content.BroadcastReceiver syncBroadcastReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && "com.example.simplemusicplayer.ACTION_SYNC".equals(intent.getAction())) {
                Log.i("MainActivity", "Received ACTION_SYNC broadcast, triggering sync!");
                SyncManager.getInstance().startSync(MainActivity.this, false);
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        SyncManager.getInstance().startRealtimeMonitoring(this);
        if (SyncManager.getInstance().isAutoSyncEnabled(this)) {
            SyncManager.getInstance().startSync(this, true);
        }
        mainHandler.removeCallbacks(fastSyncRunnable);
        mainHandler.postDelayed(fastSyncRunnable, 4000);

        try {
            android.content.IntentFilter filter = new android.content.IntentFilter("com.example.simplemusicplayer.ACTION_SYNC");
            registerReceiver(syncBroadcastReceiver, filter);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(fastSyncRunnable);
        try {
            unregisterReceiver(syncBroadcastReceiver);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null) {
            String action = intent.getAction();
            if ("com.example.simplemusicplayer.ACTION_SYNC".equals(action) || intent.getBooleanExtra("trigger_sync", false)) {
                SyncManager.getInstance().startSync(this, false);
            }
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
        if (tsubaGlowScaleXAnimator != null) tsubaGlowScaleXAnimator.cancel();
        if (tsubaGlowScaleYAnimator != null) tsubaGlowScaleYAnimator.cancel();
        if (tsubaGlowAlphaAnimator != null) tsubaGlowAlphaAnimator.cancel();
        if (viewVinylWaveVisualizer != null) {
            viewVinylWaveVisualizer.pause();
        }
    }
}
