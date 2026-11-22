package com.xiaod.player;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 根据歌曲名/歌手/时长，从 LRCLIB（免费开放歌词库）自动搜索歌词。
 * 使用同步歌词优先，找不到时退回纯文本歌词。
 */
public final class LyricsFetcher {

    public interface Callback {
        void onLyrics(List<LrcParser.Line> lines, boolean synced);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String USER_AGENT = "XiaoDPlayer/1.0 (Android)";

    private LyricsFetcher() {
    }

    public static void fetch(final Song song, final Callback callback) {
        new Thread(() -> {
            List<LrcParser.Line> result = new ArrayList<>();
            boolean synced = false;
            try {
                String track = cleanTitle(song.title);
                String artist = cleanArtist(song.artist);
                long seconds = Math.round(song.duration / 1000.0);

                String lrc = null;
                String getUrl = "https://lrclib.net/api/get?track_name=" + encode(track)
                        + "&duration=" + seconds
                        + (artist.isEmpty() ? "" : "&artist_name=" + encode(artist));
                String body = request(getUrl);
                if (body != null) {
                    JSONObject json = new JSONObject(body);
                    lrc = firstNonEmpty(json.optString("syncedLyrics", ""),
                            json.optString("plainLyrics", ""));
                }

                if (lrc == null || lrc.trim().isEmpty()) {
                    String searchUrl = "https://lrclib.net/api/search?q=" + encode(track + " " + artist);
                    String arrayBody = request(searchUrl);
                    if (arrayBody != null) {
                        JSONArray array = new JSONArray(arrayBody);
                        String plainFallback = null;
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject item = array.optJSONObject(i);
                            if (item == null) {
                                continue;
                            }
                            String syncedText = item.optString("syncedLyrics", "");
                            if (syncedText != null && !syncedText.trim().isEmpty()) {
                                lrc = syncedText;
                                break;
                            }
                            if (plainFallback == null) {
                                String plainText = item.optString("plainLyrics", "");
                                if (plainText != null && !plainText.trim().isEmpty()) {
                                    plainFallback = plainText;
                                }
                            }
                        }
                        if (lrc == null) {
                            lrc = plainFallback;
                        }
                    }
                }

                if (lrc != null && !lrc.trim().isEmpty()) {
                    result = LrcParser.parse(lrc);
                    synced = !result.isEmpty() && result.get(0).timeMs >= 0;
                    if (result.isEmpty()) {
                        // 纯文本歌词：逐行展示，不带时间轴
                        for (String line : lrc.split("\\r?\\n")) {
                            String text = line.trim();
                            if (!text.isEmpty() && !text.startsWith("[") && !text.startsWith("{")) {
                                result.add(new LrcParser.Line(-1, text));
                            }
                        }
                        synced = false;
                    }
                }
            } catch (Exception ignored) {
                // 网络不可用或歌词库无结果时，界面提示“未找到歌词”
            }

            final List<LrcParser.Line> lines = result;
            final boolean isSynced = synced;
            MAIN.post(() -> callback.onLyrics(lines, isSynced));
        }, "lyrics-fetch").start();
    }

    private static String request(String urlSpec) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlSpec);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            try (InputStream input = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(input, StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                return builder.toString();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static String firstNonEmpty(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        if (second != null && !second.trim().isEmpty()) {
            return second;
        }
        return null;
    }

    private static String cleanTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.replaceAll("(?i)\\.(mp3|flac|m4a|wav|ogg|ape|wma|aac|opus)$", "").trim();
    }

    private static String cleanArtist(String artist) {
        if (artist == null) {
            return "";
        }
        String value = artist.trim();
        if (value.equals("未知歌手") || value.equalsIgnoreCase("<unknown>")) {
            return "";
        }
        // 多人合唱时取第一位歌手，提高命中率
        value = value.split("[/、;&]")[0].trim();
        return value;
    }
}
