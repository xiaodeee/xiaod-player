package com.xiaod.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LRC 歌词解析器，支持 [mm:ss]、[mm:ss.xx]、[mm:ss:xx] 以及一行多个时间标签。
 */
public final class LrcParser {

    public static class Line {
        public final long timeMs;
        public final String text;

        public Line(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    private static final Pattern TIME_TAG =
            Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");

    private LrcParser() {
    }

    public static List<Line> parse(String lrc) {
        List<Line> lines = new ArrayList<>();
        if (lrc == null || lrc.trim().isEmpty()) {
            return lines;
        }

        for (String row : lrc.split("\\r?\\n")) {
            Matcher matcher = TIME_TAG.matcher(row);
            List<Long> times = new ArrayList<>();
            int textStart = 0;
            while (matcher.find()) {
                long time = toMillis(matcher.group(1), matcher.group(2), matcher.group(3));
                times.add(time);
                textStart = matcher.end();
            }
            if (times.isEmpty()) {
                continue;
            }
            String text = row.substring(textStart).trim();
            if (text.isEmpty()) {
                continue;
            }
            for (long time : times) {
                lines.add(new Line(time, text));
            }
        }

        Collections.sort(lines, (a, b) -> Long.compare(a.timeMs, b.timeMs));
        return lines;
    }

    /**
     * 返回当前播放时间对应的歌词行下标，没有则返回 -1。
     */
    public static int indexAt(List<Line> lines, long positionMs) {
        if (lines == null || lines.isEmpty()) {
            return -1;
        }
        int low = 0;
        int high = lines.size() - 1;
        int result = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).timeMs <= positionMs) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
    }

    private static long toMillis(String minutes, String seconds, String fraction) {
        long m = Long.parseLong(minutes);
        long s = Long.parseLong(seconds);
        long ms = 0;
        if (fraction != null && !fraction.isEmpty()) {
            if (fraction.length() == 1) {
                ms = Long.parseLong(fraction) * 100;
            } else if (fraction.length() == 2) {
                ms = Long.parseLong(fraction) * 10;
            } else {
                ms = Long.parseLong(fraction.substring(0, 3));
            }
        }
        return m * 60_000 + s * 1_000 + ms;
    }
}
