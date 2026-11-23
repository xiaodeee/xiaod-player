package com.xiaod.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

/**
 * 系统悬浮窗：显示当前歌曲名 + 暂停/继续 + 下一首，可拖动。
 * 播放依然使用进程内单例 PlayerManager，悬浮窗只是它的第二个监听者。
 */
public class FloatingPlayerService extends Service implements PlayerManager.Listener {

    private static final String CHANNEL_ID = "xiaod_floating";
    private static final int NOTIFICATION_ID = 1201;
    private static volatile boolean running;

    public static boolean isRunning() {
        return running;
    }

    private WindowManager windowManager;
    private View rootView;
    private WindowManager.LayoutParams params;
    private PlayerManager player;

    private TextView tvTitle;
    private ImageButton btnPlay;

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        player = PlayerManager.getInstance(this);
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        startForeground(NOTIFICATION_ID, buildNotification(currentTitle()));
        showOverlay();
        player.addListener(this);
        Song song = player.currentSong();
        if (song != null) {
            onSongChanged(song, player.getIndex());
        }
        onPlayStateChanged(player.isPlaying());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showOverlay() {
        if (rootView != null || windowManager == null) {
            return;
        }
        rootView = LayoutInflater.from(this).inflate(R.layout.view_floating_player, null);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(12);
        params.y = dp(96);

        tvTitle = rootView.findViewById(R.id.tv_float_title);
        btnPlay = rootView.findViewById(R.id.btn_float_play);
        ImageButton btnNext = rootView.findViewById(R.id.btn_float_next);
        TextView btnClose = rootView.findViewById(R.id.btn_float_close);

        btnPlay.setOnClickListener(v -> player.playPause());
        btnNext.setOnClickListener(v -> player.next());
        btnClose.setOnClickListener(v -> stopSelf());

        setupDrag();

        try {
            windowManager.addView(rootView, params);
        } catch (Exception e) {
            // 没有悬浮窗权限或厂商限制
            stopSelf();
        }
    }

    /**
     * 按住卡片拖动；移动距离小于阈值视为点击。
     */
    private void setupDrag() {
        View dragArea = rootView.findViewById(R.id.tv_float_title);
        dragArea.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private int startX;
            private int startY;
            private boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = params.x;
                        startY = params.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) {
                            moved = true;
                        }
                        if (moved) {
                            params.x = startX + (int) dx;
                            params.y = startY + (int) dy;
                            if (windowManager != null && rootView != null) {
                                windowManager.updateViewLayout(rootView, params);
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!moved) {
                            view.performClick();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    @Override
    public void onSongChanged(Song song, int index) {
        if (tvTitle != null) {
            tvTitle.setText(song.title);
        }
        updateNotification(song.title);
    }

    @Override
    public void onPlayStateChanged(boolean playing) {
        if (btnPlay != null) {
            btnPlay.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
            btnPlay.setContentDescription(playing ? "暂停" : "继续播放");
        }
    }

    @Override
    public void onProgress(long position, long duration) {
        // 悬浮窗不需要进度
    }

    @Override
    public void onError(String message) {
        // 错误交给主界面提示
    }

    private String currentTitle() {
        Song song = player != null ? player.currentSong() : null;
        return song == null ? "小d播放器 · 悬浮播放" : song.title;
    }

    private Notification buildNotification(String title) {
        createChannel();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText("悬浮播放中")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String title) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(title));
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "悬浮播放",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("悬浮窗播放控制正在运行");
        manager.createNotificationChannel(channel);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        running = false;
        if (player != null) {
            player.removeListener(this);
        }
        if (windowManager != null && rootView != null) {
            try {
                windowManager.removeView(rootView);
            } catch (Exception ignored) {
            }
        }
        rootView = null;
        stopForeground(true);
        super.onDestroy();
    }
}
