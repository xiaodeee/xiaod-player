package com.xiaod.player;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 全局唯一的播放控制器：负责播放/暂停、上下曲、三种播放模式与进度回调。
 */
public class PlayerManager {

    public enum Mode {
        /** 列表循环 */
        LOOP_LIST,
        /** 单曲循环 */
        LOOP_ONE,
        /** 随机播放 */
        SHUFFLE
    }

    public interface Listener {
        void onSongChanged(Song song, int index);

        void onPlayStateChanged(boolean playing);

        void onProgress(long position, long duration);

        void onError(String message);
    }

    private static PlayerManager sInstance;

    private final Context appContext;
    private final List<Song> playlist = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private MediaPlayer player;
    private Listener listener;
    private int index = -1;
    private Mode mode = Mode.LOOP_LIST;
    private boolean preparing;

    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            if (player != null && listener != null) {
                try {
                    listener.onProgress(player.getCurrentPosition(), player.getDuration());
                } catch (IllegalStateException ignored) {
                    // 播放器正在切换状态，忽略这一帧
                }
                handler.postDelayed(this, 500);
            }
        }
    };

    private PlayerManager(Context context) {
        appContext = context.getApplicationContext();
    }

    public static synchronized PlayerManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PlayerManager(context);
        }
        return sInstance;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        if (listener == null) {
            return;
        }
        Song current = currentSong();
        if (current != null) {
            listener.onSongChanged(current, index);
        }
        listener.onPlayStateChanged(isPlaying());
        if (player != null) {
            try {
                listener.onProgress(player.getCurrentPosition(), player.getDuration());
            } catch (IllegalStateException ignored) {
                // 尚未 prepare 完成
            }
        }
    }

    public List<Song> getPlaylist() {
        return playlist;
    }

    /**
     * 更新播放列表，若正在播放的歌仍在新列表中则保持播放不中断。
     */
    public void setPlaylist(List<Song> songs) {
        Song current = currentSong();
        playlist.clear();
        if (songs != null) {
            playlist.addAll(songs);
        }

        if (current == null) {
            index = -1;
            return;
        }

        int found = -1;
        for (int i = 0; i < playlist.size(); i++) {
            if (playlist.get(i).uri.equals(current.uri)) {
                found = i;
                break;
            }
        }
        if (found >= 0) {
            index = found;
        } else {
            stopInternal();
            index = -1;
            if (listener != null) {
                listener.onPlayStateChanged(false);
            }
        }
    }

    public int getIndex() {
        return index;
    }

    public Song currentSong() {
        if (index >= 0 && index < playlist.size()) {
            return playlist.get(index);
        }
        return null;
    }

    public long getPosition() {
        if (player == null || preparing) {
            return 0;
        }
        try {
            return player.getCurrentPosition();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public long getDuration() {
        if (player == null || preparing) {
            return 0;
        }
        try {
            return player.getDuration();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public boolean isPlaying() {
        if (player == null || preparing) {
            return false;
        }
        try {
            return player.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public void playAt(int position) {
        if (position < 0 || position >= playlist.size()) {
            return;
        }
        index = position;
        startCurrent();
    }

    private void startCurrent() {
        Song song = currentSong();
        if (song == null) {
            return;
        }

        stopInternal();
        preparing = true;

        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());

        player.setOnPreparedListener(mp -> {
            preparing = false;
            mp.start();
            notifyPlaying(true);
            startTicker();
        });
        player.setOnCompletionListener(mp -> handleCompletion());
        player.setOnErrorListener((mp, what, extra) -> {
            preparing = false;
            notifyPlaying(false);
            notifyError("播放失败（错误码 " + what + "）");
            return true;
        });

        try {
            player.setDataSource(appContext, song.uri);
            player.prepareAsync();
            // 先通知界面换歌，歌词页可以立即开始搜索
            if (listener != null) {
                listener.onSongChanged(song, index);
            }
            notifyPlaying(false);
        } catch (Exception e) {
            stopInternal();
            notifyPlaying(false);
            notifyError("无法打开这首歌，文件可能已损坏或被移动");
        }
    }

    public void playPause() {
        if (playlist.isEmpty()) {
            return;
        }
        if (index < 0) {
            playAt(0);
            return;
        }
        if (player == null) {
            startCurrent();
            return;
        }
        if (isPlaying()) {
            try {
                player.pause();
            } catch (IllegalStateException ignored) {
            }
            notifyPlaying(false);
            handler.removeCallbacks(progressTicker);
        } else if (!preparing) {
            try {
                player.start();
                notifyPlaying(true);
                startTicker();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    public void next() {
        if (playlist.isEmpty()) {
            return;
        }
        if (playlist.size() == 1) {
            playAt(0);
            return;
        }
        if (mode == Mode.SHUFFLE) {
            playAt(randomOther());
        } else {
            playAt(index + 1 >= playlist.size() ? 0 : index + 1);
        }
    }

    public void prev() {
        if (playlist.isEmpty()) {
            return;
        }
        // 播放超过 3 秒时，上一首按钮先回到本条开头
        if (getPosition() > 3000) {
            seekTo(0);
            return;
        }
        if (playlist.size() == 1) {
            playAt(0);
            return;
        }
        int target = index - 1;
        if (target < 0) {
            target = playlist.size() - 1;
        }
        playAt(target);
    }

    public void seekTo(int positionMs) {
        if (player == null || preparing) {
            return;
        }
        try {
            int duration = player.getDuration();
            int target = Math.max(0, Math.min(positionMs, duration));
            player.seekTo(target);
        } catch (IllegalStateException ignored) {
        }
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Mode cycleMode() {
        if (mode == Mode.LOOP_LIST) {
            mode = Mode.SHUFFLE;
        } else if (mode == Mode.SHUFFLE) {
            mode = Mode.LOOP_ONE;
        } else {
            mode = Mode.LOOP_LIST;
        }
        return mode;
    }

    private void handleCompletion() {
        if (playlist.isEmpty()) {
            return;
        }
        if (mode == Mode.LOOP_ONE) {
            if (player != null) {
                try {
                    player.seekTo(0);
                    player.start();
                    notifyPlaying(true);
                    startTicker();
                } catch (IllegalStateException ignored) {
                }
            }
            return;
        }
        if (mode == Mode.SHUFFLE) {
            playAt(randomOther());
            return;
        }
        playAt(index + 1 >= playlist.size() ? 0 : index + 1);
    }

    private int randomOther() {
        if (playlist.size() <= 1) {
            return 0;
        }
        int target = index;
        while (target == index) {
            target = random.nextInt(playlist.size());
        }
        return target;
    }

    private void startTicker() {
        handler.removeCallbacks(progressTicker);
        handler.post(progressTicker);
    }

    private void stopInternal() {
        handler.removeCallbacks(progressTicker);
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        preparing = false;
    }

    public void release() {
        stopInternal();
        listener = null;
        playlist.clear();
        index = -1;
        sInstance = null;
    }

    private void notifyPlaying(boolean playing) {
        if (listener != null) {
            listener.onPlayStateChanged(playing);
        }
    }

    private void notifyError(String message) {
        if (listener != null) {
            listener.onError(message);
        }
    }
}
