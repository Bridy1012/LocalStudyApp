package com.localstudy.app.util;

import com.localstudy.app.data.Course;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimetableTextParser {
    private static final Pattern DAY = Pattern.compile("(?:周|星期)([一二三四五六日天1-7])");
    private static final Pattern PERIODS = Pattern.compile(
            "(?:第)?\\s*(\\d+)\\s*[-~—至到]\\s*(\\d+)\\s*(?:节|大节)?"
    );
    private static final Pattern WEEK_RANGE = Pattern.compile(
            "(?:第)?\\s*(\\d+)\\s*[-~—至到]\\s*(\\d+)\\s*周"
    );
    private static final Pattern WEEK_LIST = Pattern.compile("(?:第)?\\s*([\\d,，\\s]+)\\s*周");

    private TimetableTextParser() {
    }

    public static List<Course> parse(String text) {
        List<Course> result = new ArrayList<>();
        if (text == null) return result;
        int color = 0;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw == null ? "" : raw.trim();
            if (line.length() < 4) continue;
            Matcher dayMatcher = DAY.matcher(line);
            Matcher periodMatcher = PERIODS.matcher(line);
            if (!dayMatcher.find() || !periodMatcher.find()) continue;

            int day = parseDay(dayMatcher.group(1));
            int start = safeInt(periodMatcher.group(1), 1);
            int end = Math.max(start, safeInt(periodMatcher.group(2), start));
            if (day < 1) continue;

            Set<Integer> weeks = new HashSet<>();
            Matcher range = WEEK_RANGE.matcher(line);
            if (range.find()) {
                int from = safeInt(range.group(1), 1);
                int to = safeInt(range.group(2), from);
                for (int week = Math.min(from, to); week <= Math.max(from, to); week++) {
                    weeks.add(week);
                }
            } else {
                Matcher list = WEEK_LIST.matcher(line);
                if (list.find()) {
                    for (String token : list.group(1).split("[,，\\s]+")) {
                        int week = safeInt(token, 0);
                        if (week > 0) weeks.add(week);
                    }
                }
            }
            if (weeks.isEmpty()) continue;

            String name = line.substring(0, dayMatcher.start()).trim()
                    .replaceAll("^[|,，\\t]+|[|,，\\t]+$", "");
            if (name.isEmpty()) continue;
            String rest = line.substring(periodMatcher.end()).trim();
            rest = WEEK_RANGE.matcher(rest).replaceAll("").trim();
            rest = WEEK_LIST.matcher(rest).replaceAll("").trim();
            String[] restParts = rest.split("[|,，\\t]+");
            String classroom = "";
            String teacher = "";
            if (restParts.length > 0) classroom = restParts[0].trim();
            if (restParts.length > 1) teacher = restParts[1].trim();

            Course course = new Course();
            course.name = name;
            course.dayOfWeek = day;
            course.startPeriod = start;
            course.endPeriod = end;
            course.weeks = TimetableUtils.formatWeeks(weeks);
            course.classroom = classroom;
            course.teacher = teacher;
            course.note = "";
            course.color = Ui.chartColor(color++);
            course.createdAt = DateUtils.nowText();
            result.add(course);
        }
        return result;
    }

    private static int parseDay(String value) {
        switch (value) {
            case "一": case "1": return 1;
            case "二": case "2": return 2;
            case "三": case "3": return 3;
            case "四": case "4": return 4;
            case "五": case "5": return 5;
            case "六": case "6": return 6;
            case "日": case "天": case "7": return 7;
            default: return 0;
        }
    }

    private static int safeInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
