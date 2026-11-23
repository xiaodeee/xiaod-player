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
