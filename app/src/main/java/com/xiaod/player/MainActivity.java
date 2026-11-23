package com.xiaod.player;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements PlayerManager.Listener {

    private final List<Song> songs = new ArrayList<>();

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = true;
                for (Boolean value : result.values()) {
                    if (value == null || !value) {
                        granted = false;
                    }
                }
                if (granted) {
                    startScan();
                } else {
                    toast("没有读取音频的权限，无法扫描本地音乐");
                }
            });

    private PlayerManager player;
    private SongAdapter songAdapter;
    private LyricsAdapter lyricsAdapter;

    private RecyclerView rvSongs;
    private RecyclerView rvLyrics;
    private TextView tvScan;
    private TextView tvOverlay;
    private TextView tvNowTitle;
    private TextView tvNowArtist;
    private TextView tvCurrent;
    private TextView tvTotal;
    private TextView tvMode;
    private TextView tvListTitle;
    private TextView tvLyricsStatus;
    private TextView tvLyricsToggle;
    private ImageButton btnPlay;
    private SeekBar seekBar;

    private boolean dragging;
    private boolean lyricsExpanded;
    private boolean userCollapsedLyrics;
    private boolean lyricsSynced;
    private int lyricsSeq;
    private long lastPosition;
    private boolean pendingOverlayStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        player = PlayerManager.getInstance(this);
        bindViews();
        setupLists();
        setupControls();
        player.setListener(this);
        updateModeLabel();
        updateOverlayLabel(FloatingPlayerService.isRunning());
        tvNowTitle.setText(R.string.app_name);
        tvNowArtist.setText("还没有歌曲，点右上角“扫描本地音乐”");
        tvNowArtist.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateOverlayLabel(FloatingPlayerService.isRunning());
        if (pendingOverlayStart) {
            pendingOverlayStart = false;
            if (Settings.canDrawOverlays(this)) {
                startOverlayService();
            } else {
                toast("未获得悬浮窗权限，悬浮窗没有打开");
            }
        }
    }

    private void bindViews() {
        rvSongs = findViewById(R.id.rv_songs);
        rvLyrics = findViewById(R.id.rv_lyrics);
        tvScan = findViewById(R.id.tv_scan);
        tvOverlay = findViewById(R.id.tv_overlay);
        tvNowTitle = findViewById(R.id.tv_now_title);
        tvNowArtist = findViewById(R.id.tv_now_artist);
        tvCurrent = findViewById(R.id.tv_time_current);
        tvTotal = findViewById(R.id.tv_time_total);
        tvMode = findViewById(R.id.tv_mode);
        tvListTitle = findViewById(R.id.tv_list_title);
        tvLyricsStatus = findViewById(R.id.tv_lyrics_status);
        tvLyricsToggle = findViewById(R.id.tv_lyrics_toggle);
        btnPlay = findViewById(R.id.btn_play);
        seekBar = findViewById(R.id.seek_bar);
    }

    private void setupLists() {
        rvSongs.setLayoutManager(new LinearLayoutManager(this));
        songAdapter = new SongAdapter(position -> {
            player.setPlaylist(songs);
            player.playAt(position);
        });
        rvSongs.setAdapter(songAdapter);
        rvLyrics.setLayoutManager(new LinearLayoutManager(this));
        lyricsAdapter = new LyricsAdapter();
        rvLyrics.setAdapter(lyricsAdapter);
        setupSwipeToDelete();
    }

    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback callback =
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
                    @Override
                    public boolean onMove(@NonNull RecyclerView recyclerView,
                                          @NonNull RecyclerView.ViewHolder viewHolder,
                                          @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                        int position = viewHolder.getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            removeSongAt(position);
                        }
                    }

                    @Override
                    public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView recyclerView,
                                            @NonNull RecyclerView.ViewHolder viewHolder,
                                            float dX, float dY, int actionState, boolean isCurrentlyActive) {
                        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                            View item = viewHolder.itemView;
                            Paint background = new Paint();
                            background.setColor(ContextCompat.getColor(MainActivity.this, R.color.danger));
                            if (dX > 0) {
                                canvas.drawRect(item.getLeft(), item.getTop(),
                                        item.getLeft() + dX, item.getBottom(), background);
                            } else {
                                canvas.drawRect(item.getRight() + dX, item.getTop(),
                                        item.getRight(), item.getBottom(), background);
                            }
                            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                            textPaint.setColor(Color.WHITE);
                            textPaint.setTextSize(dp(15));
                            textPaint.setTextAlign(dX > 0 ? Paint.Align.LEFT : Paint.Align.RIGHT);
                            float margin = dp(24);
                            float x = dX > 0 ? item.getLeft() + margin : item.getRight() - margin;
                            float y = item.getTop() + item.getHeight() / 2f + dp(5);
                            canvas.drawText("删除", x, y, textPaint);
                        }
                        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                    }
                };
        new ItemTouchHelper(callback).attachToRecyclerView(rvSongs);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void removeSongAt(int position) {
        if (position < 0 || position >= songs.size()) {
            return;
        }
        Song removed = songs.remove(position);
        songAdapter.removeItem(position);
        tvListTitle.setText("本地音乐 (" + songs.size() + ")");
        player.removeAt(position);
        songAdapter.setCurrentIndex(player.getIndex());
        toast("已移除《" + removed.title + "》");
        if (songs.isEmpty()) {
            resetEmptyUi();
        }
    }

    private void resetEmptyUi() {
        tvNowTitle.setText(R.string.app_name);
        tvNowArtist.setVisibility(View.VISIBLE);
        tvNowArtist.setText("播放列表已清空，点右上角重新扫描");
        tvCurrent.setText("00:00");
        tvTotal.setText("00:00");
        seekBar.setProgress(0);
        lyricsSynced = false;
        lyricsSeq++;
        lyricsAdapter.submit(new ArrayList<>());
        tvLyricsStatus.setText("点击展开");
        setLyricsExpanded(false);
        btnPlay.setImageResource(R.drawable.ic_play);
    }

    private void setupControls() {
        tvScan.setOnClickListener(v -> {
            if (hasAudioPermission()) {
                startScan();
            } else {
                permissionLauncher.launch(requiredPermissions());
            }
        });
        tvOverlay.setOnClickListener(v -> toggleOverlay());
        tvMode.setOnClickListener(v -> {
            PlayerManager.Mode mode = player.cycleMode();
            updateModeLabel();
            toast(modeLabel(mode));
        });
        btnPlay.setOnClickListener(v -> {
            if (ensurePlaylist()) {
                player.playPause();
            }
        });
        findViewById(R.id.btn_next).setOnClickListener(v -> {
            if (ensurePlaylist()) {
                player.next();
            }
        });
        findViewById(R.id.lyrics_header).setOnClickListener(v -> setLyricsExpanded(!lyricsExpanded));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvCurrent.setText(SongAdapter.formatDuration(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                dragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                dragging = false;
                player.seekTo(bar.getProgress());
            }
        });
    }

    private void toggleOverlay() {
        if (FloatingPlayerService.isRunning()) {
            stopService(new Intent(this, FloatingPlayerService.class));
            updateOverlayLabel(false);
            toast("已关闭悬浮窗");
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            pendingOverlayStart = true;
            toast("请允许“显示在其他应用上层”，返回后悬浮窗会自动打开");
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            }
            return;
        }
        startOverlayService();
    }

    private void startOverlayService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1102);
            }
        }
        if (!songs.isEmpty()) {
            player.setPlaylist(songs);
            songAdapter.setCurrentIndex(player.getIndex());
        }
        ContextCompat.startForegroundService(this,
                new Intent(this, FloatingPlayerService.class));
        updateOverlayLabel(true);
        toast("悬浮窗已打开，可拖动到任意位置");
    }

    private void updateOverlayLabel(boolean running) {
        if (tvOverlay == null) {
            return;
        }
        tvOverlay.setText(running ? "关闭悬浮窗" : "悬浮窗");
    }

    private boolean ensurePlaylist() {
        if (!player.getPlaylist().isEmpty()) {
            return true;
        }
        if (songs.isEmpty()) {
            toast("请先点右上角扫描本地音乐");
            return false;
        }
        player.setPlaylist(songs);
        songAdapter.setCurrentIndex(player.getIndex());
        return true;
    }

    private void startScan() {
        if (isFinishing()) {
            return;
        }
        tvScan.setEnabled(false);
        tvScan.setText("扫描中…");
        new Thread(() -> {
            final List<Song> scanned = MusicScanner.scan(getApplicationContext());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                tvScan.setEnabled(true);
                tvScan.setText(R.string.scan_local);
                songs.clear();
                songs.addAll(scanned);
                songAdapter.submit(songs);
                tvListTitle.setText("本地音乐 (" + songs.size() + ")");
                player.setPlaylist(songs);
                songAdapter.setCurrentIndex(player.getIndex());
                if (songs.isEmpty()) {
                    toast("没有扫描到 MP3 音乐，请确认手机里有 .mp3 文件");
                } else {
                    toast("扫描完成，共 " + songs.size() + " 首 MP3");
                }
            });
        }, "music-scan").start();
    }

    private String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{Manifest.permission.READ_MEDIA_AUDIO};
        }
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    private boolean hasAudioPermission() {
        for (String permission : requiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void updateModeLabel() {
        tvMode.setText(modeLabel(player.getMode()));
    }

    private String modeLabel(PlayerManager.Mode mode) {
        if (mode == PlayerManager.Mode.SHUFFLE) {
            return "随机播放";
        }
        if (mode == PlayerManager.Mode.LOOP_ONE) {
            return "单曲循环";
        }
        return "列表循环";
    }

    private void setLyricsExpanded(boolean expanded) {
        lyricsExpanded = expanded;
        rvLyrics.setVisibility(expanded ? View.VISIBLE : View.GONE);
        tvLyricsToggle.setText(expanded ? "收起 ▴" : "展开 ▾");
        userCollapsedLyrics = !expanded;
        LinearLayout.LayoutParams lyricParams = (LinearLayout.LayoutParams) rvLyrics.getLayoutParams();
        LinearLayout.LayoutParams listParams = (LinearLayout.LayoutParams) rvSongs.getLayoutParams();
        lyricParams.height = 0;
        lyricParams.weight = expanded ? 1.5f : 0f;
        listParams.weight = 1.2f;
        rvLyrics.setLayoutParams(lyricParams);
        rvSongs.setLayoutParams(listParams);
        if (expanded) {
            rvLyrics.post(() -> scrollToCurrentLyric(false));
        }
    }

    private void loadLyrics(final Song song, final int seq) {
        LyricsFetcher.fetch(song, (lines, synced) -> {
            if (seq != lyricsSeq || isFinishing()) {
                return;
            }
            lyricsSynced = synced;
            if (lines.isEmpty()) {
                lyricsAdapter.submit(new ArrayList<>());
                tvLyricsStatus.setText(song.title + " · 未找到歌词");
                return;
            }
            lyricsAdapter.submit(lines);
            tvLyricsStatus.setText(synced
                    ? song.title + " · 歌词同步中"
                    : song.title + " · 纯文本歌词");
            updateLyricHighlight(lastPosition);
            if (!userCollapsedLyrics) {
                setLyricsExpanded(true);
            }
        });
    }

    private void updateLyricHighlight(long positionMs) {
        if (!lyricsSynced || lyricsAdapter == null || lyricsAdapter.isEmpty()) {
            return;
        }
        int index = lyricsAdapter.findIndex(positionMs);
        if (index == lyricsAdapter.getHighlighted()) {
            return;
        }
        lyricsAdapter.setHighlighted(index);
        if (lyricsExpanded && index >= 0) {
            scrollToCurrentLyric(true);
        }
    }

    private void scrollToCurrentLyric(boolean smooth) {
        int index = lyricsAdapter.getHighlighted();
        if (index < 0 || !lyricsExpanded) {
            return;
        }
        LinearLayoutManager manager = (LinearLayoutManager) rvLyrics.getLayoutManager();
        if (manager == null) {
            return;
        }
        if (smooth) {
            LinearSmoothScroller scroller = new LinearSmoothScroller(this) {
                @Override
                protected int getVerticalSnapPreference() {
                    return SNAP_TO_START;
                }

                @Override
                public int calculateDtToFit(int viewStart, int viewEnd,
                                            int boxStart, int boxEnd, int snapPreference) {
                    int viewCenter = viewStart + (viewEnd - viewStart) / 2;
                    int boxCenter = boxStart + (boxEnd - boxStart) / 2;
                    return boxCenter - viewCenter;
                }

                @Override
                protected float calculateSpeedPerPixel(DisplayMetrics metrics) {
                    return 55f / metrics.densityDpi;
                }

                @Override
                protected int calculateTimeForScrolling(int dx) {
                    return Math.min(380, super.calculateTimeForScrolling(dx));
                }
            };
            scroller.setTargetPosition(index);
            manager.startSmoothScroll(scroller);
        } else {
            manager.scrollToPositionWithOffset(index,
                    (int) Math.max(0, rvLyrics.getHeight() / 2f - dp(26)));
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSongChanged(Song song, int index) {
        tvNowTitle.setText(song.title);
        String info = song.infoText();
        if (info.isEmpty()) {
            tvNowArtist.setVisibility(View.GONE);
        } else {
            tvNowArtist.setVisibility(View.VISIBLE);
            tvNowArtist.setText(info);
        }
        songAdapter.setCurrentIndex(index);
        lyricsSynced = false;
        lyricsAdapter.submit(new ArrayList<>());
        tvLyricsStatus.setText("正在搜索歌词…");
        int seq = ++lyricsSeq;
        loadLyrics(song, seq);
    }

    @Override
    public void onPlayStateChanged(boolean playing) {
        btnPlay.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        btnPlay.setContentDescription(playing ? "暂停" : "播放");
    }

    @Override
    public void onProgress(long position, long duration) {
        lastPosition = position;
        if (!dragging) {
            if (duration > 0) {
                seekBar.setMax((int) Math.min(Integer.MAX_VALUE, duration));
                seekBar.setProgress((int) Math.min(position, duration));
            }
            tvCurrent.setText(SongAdapter.formatDuration(position));
            tvTotal.setText(SongAdapter.formatDuration(duration));
        }
        updateLyricHighlight(position);
    }

    @Override
    public void onError(String message) {
        toast(message);
    }

    @Override
    protected void onDestroy() {
        player.removeListener(this);
        super.onDestroy();
        if (isFinishing() && !FloatingPlayerService.isRunning()) {
            player.release();
        }
    }
}
