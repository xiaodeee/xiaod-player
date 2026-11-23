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
}
