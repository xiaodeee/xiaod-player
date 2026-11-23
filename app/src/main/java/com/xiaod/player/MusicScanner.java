package com.xiaod.player;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 扫描系统媒体库中的本地音乐，只保留 MP3。只有用户点击“扫描”时才会调用。
 */
public final class MusicScanner {

    private MusicScanner() {
    }

    public static List<Song> scan(Context context) {
        List<Song> songs = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();

        Uri collection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.MIME_TYPE
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

        try (Cursor cursor = resolver.query(collection, projection, selection, null, sortOrder)) {
            if (cursor == null) {
                return songs;
            }

            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            int mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                long duration = cursor.getLong(durationCol);
                if (duration <= 0) {
                    continue;
                }

                String displayName = cursor.getString(nameCol);
                String mime = mimeCol >= 0 ? cursor.getString(mimeCol) : null;
                if (!isMp3(displayName, mime)) {
                    continue;
                }

                String title = cursor.getString(titleCol);
                if (title == null || title.trim().isEmpty()) {
                    title = stripExtension(displayName);
                }

                String artist = cursor.getString(artistCol);
                if (artist == null || artist.trim().isEmpty() || "<unknown>".equalsIgnoreCase(artist.trim())) {
                    artist = context.getString(R.string.unknown_artist);
                }

                String album = cursor.getString(albumCol);
                if (album == null || album.trim().isEmpty() || "<unknown>".equalsIgnoreCase(album.trim())) {
                    album = context.getString(R.string.unknown_album);
                }

                Uri uri = ContentUris.withAppendedId(collection, id);
                String fileName = displayName == null ? title : displayName;
                songs.add(new Song(id, title, artist, album, fileName, duration, uri));
            }
        } catch (Exception ignored) {
            // 某些机型媒体库查询可能抛出异常，返回已扫描到的结果
        }

        return songs;
    }

    /**
     * 文件名以 .mp3 结尾，或 MIME 为 audio/mpeg，即视为 MP3。
     */
    private static boolean isMp3(String displayName, String mimeType) {
        if (displayName != null && displayName.toLowerCase(Locale.ROOT).endsWith(".mp3")) {
            return true;
        }
        return mimeType != null && mimeType.equalsIgnoreCase("audio/mpeg");
    }

    private static String stripExtension(String name) {
        if (name == null || name.isEmpty()) {
            return "未知歌曲";
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
