package com.xiaod.player;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自动搜索歌词。
 *
 * 本地文件常见命名为 “歌手－歌名.mp3”，因此先从文件名/标题中拆出歌手与歌名，
 * 再按 “歌名 + 歌手” 结构化搜索：
 *   1. QQ 音乐（中文曲库准，返回带时间轴的 LRC，按本地时长挑版本）
 *   2. LRCLIB（免费开放歌词库，覆盖外语歌）
 *
 * 最后按 “同步歌词优先、标题/歌手匹配度、时长接近度” 选最佳结果。
 */
public final class LyricsFetcher {

    public interface Callback {
        void onLyrics(List<LrcParser.Line> lines, boolean synced);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String UA = "XiaoDPlayer/1.2 (Android)";
    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Safari/537.36";
    private static final String QQ_REFERER = "https://y.qq.com/portal/player.html";
    private static final int TIMEOUT_MS = 6000;
    private static final int MAX_QUERIES = 6;

    private static final Pattern FILE_EXT =
            Pattern.compile("(?i)\\.(mp3|flac|m4a|wav|ogg|ape|wma|aac|opus)$");
    private static final Pattern TRACK_NUMBER =
            Pattern.compile("^\\s*\\d{1,3}\\s*[.、\\-－—–]\\s*");
    private static final Pattern FULL_WIDTH_SEP = Pattern.compile("[－—–]");
    private static final Pattern SPACED_DASH = Pattern.compile("\\s-\\s");

    private LyricsFetcher() {
    }

    public static void fetch(final Song song, final Callback callback) {
        new Thread(() -> {
            List<LrcParser.Line> lines = new ArrayList<>();
            boolean synced = false;

            try {
                List<Query> queries = buildQueries(song);
                Result best = null;

                // 1) QQ 音乐：按“歌手 + 歌名 + 时长”搜索，中文命中率最高
                for (Query query : queries) {
                    Result result = searchQq(query, song.duration);
                    if (result != null && result.isBetterThan(best)) {
                        best = result;
                    }
                    if (best != null && best.synced && best.score >= 70) {
                        break;
                    }
                }

                // 2) 没拿到同步歌词时，再用 LRCLIB 兜底（外语歌覆盖较好）
                if (best == null || !best.synced) {
                    for (Query query : queries) {
                        Result result = searchLrclib(query, song.duration);
                        if (result != null && result.isBetterThan(best)) {
                            best = result;
                        }
                        if (best != null && best.synced && best.score >= 80) {
                            break;
                        }
                    }
                }

                if (best != null && best.lyric != null && !best.lyric.trim().isEmpty()) {
                    lines = LrcParser.parse(best.lyric);
                    synced = !lines.isEmpty() && lines.get(0).timeMs >= 0;
                    if (lines.isEmpty()) {
                        // 纯文本歌词：逐行展示，不带时间轴
                        for (String row : best.lyric.split("\\r?\\n")) {
                            String text = row.trim();
                            if (!text.isEmpty() && !text.startsWith("[") && !text.startsWith("{")) {
                                lines.add(new LrcParser.Line(-1, text));
                            }
                        }
                        synced = false;
                    }
                }
            } catch (Exception ignored) {
                // 网络不可用或歌词源均无结果
            }

            final List<LrcParser.Line> resultLines = lines;
            final boolean syncedResult = synced;
            MAIN.post(() -> callback.onLyrics(resultLines, syncedResult));
        }, "lyrics-fetch").start();
    }

    // ---------------------------------------------------------------- 查询构造

    private static List<Query> buildQueries(Song song) {
        List<Query> queries = new ArrayList<>();

        String tagArtist = cleanArtist(song.artist);
        String tagTitle = stripExt(song.title);
        String fileName = song.displayName != null ? song.displayName : song.title;
        String baseName = stripTrackNumber(stripExt(fileName));

        // 文件名 “歌手－歌名” 通常最可信
        String[] fromFile = splitArtistTitle(baseName);
        if (fromFile != null) {
            addQuery(queries, fromFile[0], fromFile[1]);
            String first = firstArtist(fromFile[0]);
            if (first != null) {
                addQuery(queries, first, fromFile[1]);
            }
        }

        // 内嵌标签
        if (!tagArtist.isEmpty()) {
            addQuery(queries, tagArtist, tagTitle);
        }

        // 标题本身可能也是 “歌手－歌名”
        String[] fromTitle = splitArtistTitle(tagTitle);
        if (fromTitle != null) {
            addQuery(queries, fromTitle[0], fromTitle[1]);
        }

        // 只按歌名兜底
        String titleOnly = fromFile != null ? fromFile[1] : tagTitle;
        addQuery(queries, "", titleOnly);
        addQuery(queries, "", tagTitle);

        return queries;
    }

    private static void addQuery(List<Query> queries, String artist, String title) {
        if (queries.size() >= MAX_QUERIES) {
            return;
        }
        String cleanT = title == null ? "" : title.trim();
        if (cleanT.isEmpty()) {
            return;
        }
        Query query = new Query(artist == null ? "" : artist.trim(), cleanT);
        if (!queries.contains(query)) {
            queries.add(query);
        }
    }

    /**
     * 把 “歌手－歌名” 拆成 [歌手, 歌名]。常见分隔符：全角横线、—、–、空格 - 空格、｜、|、半角 -。
     * 半角 “-” 取最后一次出现，尽量避免拆坏 “Jay-Z” 之类的名字。
     */
    private static String[] splitArtistTitle(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        int index = -1;
        int length = 1;

        Matcher full = FULL_WIDTH_SEP.matcher(value);
        if (full.find()) {
            index = full.start();
            length = full.end() - full.start();
        }
        if (index < 0) {
            Matcher spaced = SPACED_DASH.matcher(value);
            if (spaced.find()) {
                index = spaced.start();
                length = spaced.end() - spaced.start();
            }
        }
        if (index < 0) {
            index = value.indexOf('｜');
        }
        if (index < 0) {
            index = value.indexOf('|');
        }
        if (index < 0) {
            index = value.lastIndexOf('-');
        }
        if (index <= 0) {
            return null;
        }

        String left = value.substring(0, index).trim();
        String right = value.substring(index + length).trim();
        if (left.isEmpty() || right.isEmpty()) {
            return null;
        }
        if (normalize(left).length() > 30) {
            return null;
        }
        return new String[]{left, right};
    }

    private static String firstArtist(String artists) {
        if (artists == null) {
            return null;
        }
        for (String part : artists.split("[/、,&，]")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- QQ 音乐

    private static Result searchQq(Query query, long durationMs) throws Exception {
        if (query.title.isEmpty()) {
            return null;
        }
        String keyword = (query.artist + " " + query.title).trim();
        String searchUrl = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?format=json&n=8&p=1&w="
                + encode(keyword);
        String body = request(searchUrl, BROWSER_UA, QQ_REFERER);
        if (body == null) {
            // 偶发 503/限流，重试一次
            body = request(searchUrl, BROWSER_UA, QQ_REFERER);
        }
        if (body == null) {
            return null;
        }

        JSONObject root = new JSONObject(body);
        JSONObject data = root.optJSONObject("data");
        JSONObject songBlock = data != null ? data.optJSONObject("song") : null;
        JSONArray songs = songBlock != null ? songBlock.optJSONArray("list") : null;
        if (songs == null) {
            return null;
        }

        long durationSec = Math.round(durationMs / 1000.0);
        String bestMid = null;
        int bestScore = -1;

        for (int i = 0; i < songs.length(); i++) {
            JSONObject song = songs.optJSONObject(i);
            if (song == null) {
                continue;
            }
            String mid = song.optString("songmid", "");
            if (mid.isEmpty()) {
                continue;
            }
            int score = matchScore(song.optString("songname"), joinSingers(song.optJSONArray("singer")),
                    query.title, query.artist);
            long interval = song.optLong("interval", 0);
            if (interval > 0) {
                score += durationScore(Math.abs(interval - durationSec));
            }
            if (score > bestScore) {
                bestScore = score;
                bestMid = mid;
            }
        }

        if (bestMid == null || bestScore <= 0) {
            return null;
        }

        String lyricUrl = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid="
                + encode(bestMid) + "&format=json&nobase64=0&g_tk=5381";
        String lyricBody = request(lyricUrl, BROWSER_UA, QQ_REFERER);
        if (lyricBody == null) {
            lyricBody = request(lyricUrl, BROWSER_UA, QQ_REFERER);
        }
        if (lyricBody == null) {
            return null;
        }

        JSONObject lyricJson = new JSONObject(lyricBody);
        if (lyricJson.optInt("retcode", 0) != 0) {
            return null;
        }
        String base64 = lyricJson.optString("lyric", "");
        if (base64.isEmpty()) {
            return null;
        }
        String lyric;
        try {
            lyric = new String(Base64.decode(base64, Base64.DEFAULT), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
        if (lyric.trim().isEmpty() || isPlaceholderLyric(lyric)) {
            return null;
        }

        boolean synced = !LrcParser.parse(lyric).isEmpty();
        return new Result(lyric, synced, bestScore);
    }

    private static String joinSingers(JSONArray singers) {
        if (singers == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < singers.length(); i++) {
            JSONObject singer = singers.optJSONObject(i);
            if (singer == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('/');
            }
            builder.append(singer.optString("name", ""));
        }
        return builder.toString();
    }

    /**
     * 过滤“纯音乐，请欣赏”之类的占位歌词，避免显示成歌词。
     */
    private static boolean isPlaceholderLyric(String lyric) {
        if (!lyric.replaceAll("\\s", "").contains("纯音乐")) {
            return false;
        }
        return LrcParser.parse(lyric).size() <= 4;
    }

    // ---------------------------------------------------------------- LRCLIB

    private static Result searchLrclib(Query query, long durationMs) throws Exception {
        if (query.title.isEmpty()) {
            return null;
        }
        StringBuilder url = new StringBuilder("https://lrclib.net/api/search?track_name=")
                .append(encode(query.title));
        if (!query.artist.isEmpty()) {
            url.append("&artist_name=").append(encode(query.artist));
        }
        String body = request(url.toString(), UA, null);
        if (body == null) {
            return null;
        }

        JSONArray array = new JSONArray(body);
        long durationSec = Math.round(durationMs / 1000.0);
        Result best = null;

        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String syncedText = item.optString("syncedLyrics", "");
            String plainText = item.optString("plainLyrics", "");
            boolean hasSynced = syncedText != null && !syncedText.trim().isEmpty();
            String lyric = hasSynced ? syncedText : plainText;
            if (lyric == null || lyric.trim().isEmpty()) {
                continue;
            }

            int score = matchScore(item.optString("trackName"), item.optString("artistName"),
                    query.title, query.artist);
            score += durationScore(Math.abs(Math.round(item.optDouble("duration", 0)) - durationSec));
            if (score <= 0) {
                continue;
            }

            Result candidate = new Result(lyric, hasSynced, score);
            if (candidate.isBetterThan(best)) {
                best = candidate;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- 匹配打分

    private static int matchScore(String candidateTitle, String candidateArtist,
                                  String wantTitle, String wantArtist) {
        int score = 0;
        String candTitle = normalize(candidateTitle);
        String wantedTitle = normalize(wantTitle);
        if (!candTitle.isEmpty() && !wantedTitle.isEmpty()) {
            if (candTitle.equals(wantedTitle)) {
                score += 40;
            } else if (candTitle.contains(wantedTitle) || wantedTitle.contains(candTitle)) {
                score += 25;
            }
        }
        String candArtist = normalize(candidateArtist);
        String wantedArtist = normalize(wantArtist);
        if (!candArtist.isEmpty() && !wantedArtist.isEmpty()) {
            if (candArtist.contains(wantedArtist) || wantedArtist.contains(candArtist)) {
                score += 25;
            }
        }
        return score;
    }

    private static int durationScore(long diffSeconds) {
        if (diffSeconds <= 3) {
            return 20;
        }
        if (diffSeconds <= 10) {
            return 10;
        }
        if (diffSeconds <= 30) {
            return 0;
        }
        return -15;
    }

    // ---------------------------------------------------------------- 工具方法

    private static String request(String urlSpec, String userAgent, String referer) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlSpec);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", userAgent);
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            if (referer != null) {
                connection.setRequestProperty("Referer", referer);
            }
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
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return "";
        }
    }

    private static String stripExt(String value) {
        if (value == null) {
            return "";
        }
        return FILE_EXT.matcher(value).replaceAll("").trim();
    }

    private static String stripTrackNumber(String value) {
        return TRACK_NUMBER.matcher(value).replaceFirst("").trim();
    }

    private static String cleanArtist(String artist) {
        if (artist == null) {
            return "";
        }
        String value = artist.trim();
        if (value.equals("未知歌手") || value.equalsIgnoreCase("<unknown>")) {
            return "";
        }
        return value;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String text = value.toLowerCase(Locale.ROOT);
        text = text.replaceAll("[\\s\\u3000]", "");
        text = text.replaceAll("[（）()\\[\\]【】《》「」『』<>·•,，.。!！?？:：;；'\"“”‘’_\\-—–－~\\\\/｜|]", "");
        text = text.replace("feat", "");
        return text;
    }

    private static class Query {
        final String artist;
        final String title;

        Query(String artist, String title) {
            this.artist = artist == null ? "" : artist;
            this.title = title == null ? "" : title;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Query)) {
                return false;
            }
            Query other = (Query) o;
            return artist.equalsIgnoreCase(other.artist) && title.equalsIgnoreCase(other.title);
        }

        @Override
        public int hashCode() {
            return artist.toLowerCase(Locale.ROOT).hashCode() * 31
                    + title.toLowerCase(Locale.ROOT).hashCode();
        }
    }

    private static class Result {
        final String lyric;
        final boolean synced;
        final int score;

        Result(String lyric, boolean synced, int score) {
            this.lyric = lyric;
            this.synced = synced;
            this.score = score;
        }

        boolean isBetterThan(Result other) {
            if (other == null) {
                return true;
            }
            if (synced != other.synced) {
                return synced;
            }
            return score > other.score;
        }
    }
}
