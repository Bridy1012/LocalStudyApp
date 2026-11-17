package com.localstudy.app.util;

import com.localstudy.app.data.RepeatTask;
import com.localstudy.app.data.StudyRecord;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DateUtils {
    public static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    public static final DateTimeFormatter ISO_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public static final Locale CN = Locale.SIMPLIFIED_CHINESE;

    private DateUtils() {
    }

    public static String today() {
        return LocalDate.now().format(ISO_DATE);
    }

    public static String nowText() {
        return LocalDateTime.now().format(ISO_TIME);
    }

    public static boolean isValidDate(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            LocalDate.parse(value, ISO_DATE);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static long daysBetween(String from, String to) {
        try {
            return ChronoUnit.DAYS.between(
                    LocalDate.parse(from, ISO_DATE),
                    LocalDate.parse(to, ISO_DATE)
            );
        } catch (Exception ignored) {
            return 0;
        }
    }

    public static String friendlyDate(String iso) {
        if (!isValidDate(iso)) {
            return "未排期";
        }
        LocalDate date = LocalDate.parse(iso, ISO_DATE);
        LocalDate today = LocalDate.now();
        if (date.equals(today)) {
            return "今天";
        }
        if (date.equals(today.plusDays(1))) {
            return "明天";
        }
        if (date.equals(today.minusDays(1))) {
            return "昨天";
        }
        return date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
    }

    public static String fullDateCn() {
        LocalDate date = LocalDate.now();
        return (date.getMonthValue()) + "月" + date.getDayOfMonth() + "日 "
                + date.getDayOfWeek().getDisplayName(TextStyle.FULL, CN);
    }

    public static String duration(long seconds) {
        seconds = Math.max(0, seconds);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.CHINA, "%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(Locale.CHINA, "%02d:%02d", minutes, secs);
    }

    public static String durationHuman(long seconds) {
        seconds = Math.max(0, seconds);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0 && minutes > 0) {
            return hours + "小时" + minutes + "分";
        }
        if (hours > 0) {
            return hours + "小时";
        }
        if (minutes > 0) {
            return minutes + "分钟";
        }
        return seconds > 0 ? "不足1分钟" : "0分钟";
    }

    public static boolean repeatOccursOn(RepeatTask task, LocalDate date) {
        if (task == null || task.repeatType == null) {
            return false;
        }
        switch (task.repeatType) {
            case "daily":
                return true;
            case "weekly": {
                Set<Integer> days = new HashSet<>();
                for (String item : task.repeatValue.split(",")) {
                    try {
                        days.add(Integer.parseInt(item.trim()));
                    } catch (Exception ignored) {
                    }
                }
                return days.contains(date.getDayOfWeek().getValue());
            }
            case "monthly": {
                for (String item : task.repeatValue.split(",")) {
                    try {
                        if (Integer.parseInt(item.trim()) == date.getDayOfMonth()) {
                            return true;
                        }
                    } catch (Exception ignored) {
                    }
                }
                int desired = lastMonthlyDay(task.repeatValue);
                return desired > 0 && date.getDayOfMonth() == YearMonth.from(date).lengthOfMonth()
                        && desired > YearMonth.from(date).lengthOfMonth();
            }
            default:
                return false;
        }
    }

    private static int lastMonthlyDay(String value) {
        int last = -1;
        for (String item : value.split(",")) {
            try {
                last = Math.max(last, Integer.parseInt(item.trim()));
            } catch (Exception ignored) {
            }
        }
        return last;
    }

    public static int consecutiveDays(List<StudyRecord> records) {
        Set<LocalDate> active = new HashSet<>();
        for (StudyRecord record : records) {
            if (record.durationSec <= 0 || record.startTime == null || record.startTime.length() < 10) {
                continue;
            }
            try {
                active.add(LocalDate.parse(record.startTime.substring(0, 10), ISO_DATE));
            } catch (Exception ignored) {
            }
        }
        LocalDate cursor = LocalDate.now();
        if (!active.contains(cursor)) {
            cursor = cursor.minusDays(1);
        }
        int count = 0;
        while (active.contains(cursor)) {
            count++;
            cursor = cursor.minusDays(1);
        }
        return count;
    }

    public static long totalForRange(List<StudyRecord> records, LocalDate start, LocalDate end) {
        long total = 0;
        for (StudyRecord record : records) {
            LocalDate day = parseRecordDate(record);
            if (day != null && !day.isBefore(start) && !day.isAfter(end)) {
                total += Math.max(0, record.durationSec);
            }
        }
        return total;
    }

    public static LocalDate parseRecordDate(StudyRecord record) {
        if (record == null || record.startTime == null || record.startTime.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(record.startTime.substring(0, 10), ISO_DATE);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static LocalDate startOfWeek(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static long recordSecondsOn(List<StudyRecord> records, LocalDate day) {
        long total = 0;
        for (StudyRecord record : records) {
            if (day.equals(parseRecordDate(record))) {
                total += Math.max(0, record.durationSec);
            }
        }
        return total;
    }

    public static List<LocalDate> recentDays(int days) {
        List<LocalDate> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            result.add(today.minusDays(i));
        }
        return result;
    }

    public static String weekdayShort(LocalDate date) {
        String name = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, CN);
        return name.replace("周", "");
    }

    public static String clockText(long epochMillis) {
        return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(epochMillis),
                java.time.ZoneId.systemDefault()
        ).format(ISO_TIME);
    }

    public static long secondsUntilNextReminder(int hour) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return Math.max(60, Duration.between(now, next).getSeconds());
    }

}
