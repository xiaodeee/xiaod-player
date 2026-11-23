package com.xiaod.player;

import android.net.Uri;

/**
 * 一首本地歌曲的元数据。
 */
public class Song {

    public final long id;
    public final String title;
    public final String artist;
    public final String album;
    public final String displayName;
    public final long duration;
    public final Uri uri;

    public Song(long id, String title, String artist, String album, String displayName,
                long duration, Uri uri) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.displayName = displayName;
        this.duration = duration;
        this.uri = uri;
    }

    public boolean hasArtist() {
        return !isUnknown(artist, "未知歌手");
    }

    public boolean hasAlbum() {
        return !isUnknown(album, "未知专辑");
    }

    /**
     * “歌手 · 专辑”：未知的一方直接省略，两边都未知时返回空串，界面据此隐藏该行。
     */
    public String infoText() {
        String artistText = hasArtist() ? artist.trim() : "";
        String albumText = hasAlbum() ? album.trim() : "";
        if (!artistText.isEmpty() && !albumText.isEmpty()) {
            return artistText + " · " + albumText;
        }
        if (!artistText.isEmpty()) {
            return artistText;
        }
        return albumText;
    }

    private static boolean isUnknown(String value, String placeholder) {
        if (value == null) {
            return true;
        }
        String text = value.trim();
        return text.isEmpty()
                || placeholder.equals(text)
                || "<unknown>".equalsIgnoreCase(text)
                || "未知".equals(text);
    }
}